package io.github.joke.percolate.spi.builtins.value;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.DirectiveInput;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.Offer;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

import static io.github.joke.percolate.spi.LiteralCoercion.coerce;
import static io.github.joke.percolate.spi.Nullability.NON_NULL;
import static io.github.joke.percolate.spi.Nullability.NULLABLE;
import static io.github.joke.percolate.spi.Offer.refusal;
import static io.github.joke.percolate.spi.Port.byTypeOrDecline;
import static io.github.joke.percolate.spi.Weights.NOOP;
import static java.util.stream.Stream.concat;

// The NULLABLE → NON_NULL crossing, target-driven (design D1/D2): keyed only on the demanded target, it over-
// emits the crossings that can produce it and reads no candidate. Each crossing's input is a reuse-only
// byTypeOrDecline — bound to an already-in-scope source of that shape or the operation does not apply
// (never minted), which is the candidate-free equivalent of the former "fire against an existing source":
//
//   [requireNonNull] (partial) for a NON_NULL reference-scalar demand — a reuse-only (T, NULLABLE) port
//           collapsed by Objects.requireNonNull(source, "source for slot '…' is null but target is
//           non-null"), naming the slot from ProduceDemand.bindingName()
//   [coalesce] (total) when the binding's directive declares a defaultValue — a reuse-only (T, NULLABLE)
//           scalar coalesces via Objects.requireNonNullElse(source, D), and a reuse-only (Optional<T>,
//           NON_NULL) source coalesces via source.orElse(D), both reusing constant literal-coercion for the
//           fallback
//
// The driver binds each reuse-only port to whichever in-scope source actually exists (a nullable
// scalar, an Optional<T>, …); the others simply do not apply, so the engine selects the realisable crossing
// without the strategy enumerating sources. When both a partial guard and a total coalesce can bind the same
// nullable scalar, the plan-extraction totality rule keeps the total [coalesce]. The strategy runs at a
// negative .priority() so its coalesce out-competes the plain identity assignment. It emits nothing for a
// primitive target (a primitive can never be absent) or an uncoercible default (the late diagnostic reports
// it). It is myopic: it reads only the demand.
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class NullnessCrossing implements ExpansionStrategy {

    static final String DEFAULT_VALUE_KEY = "defaultValue";
    private static final String VALUE_ROLE = "value";
    private static final int OUTCOMPETE_PRIORITY = -1;

    @Override
    public int priority() {
        return OUTCOMPETE_PRIORITY;
    }

    @Override
    public Stream<Offer> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var target = demand.targetType();
        final var defaultInput = demand.directive().flatMap(directive -> directive.input(DEFAULT_VALUE_KEY));
        // A requireNonNull guard is needed only for a NON_NULL target; a coalesce fires wherever a default is
        // declared, regardless of target nullness (a nullable/unknown target still uses its fallback).
        final var guardsNullness = demand.targetNullness() == NON_NULL;
        final var raw = defaultInput.flatMap(DirectiveInput::getValue);
        if (raw.isEmpty()) {
            return guardOnly(target, demand.bindingName(), guardsNullness, ctx);
        }
        final var input = defaultInput.orElseThrow();
        final var coerced = coerce(raw.get(), target);
        if (coerced.isEmpty()) {
            return Stream.of(
                    refusal(input.getSubject(), "cannot coerce '" + raw.get() + "' to " + ctx.simpleName(target)));
        }
        return concat(
                        requireNonNullGuard(target, demand.bindingName(), guardsNullness, ctx),
                        coalesce(target, coerced.get(), input, ctx))
                .map(Offer::of);
    }

    // No default declared: silence unless the target is NON_NULL, which still wants the guard alone.
    @VisibleForTesting
    Stream<Offer> guardOnly(
            final TypeMirror target, final String bindingName, final boolean guardsNullness, final ResolveCtx ctx) {
        if (!guardsNullness) {
            return Stream.empty();
        }
        return requireNonNullGuard(target, bindingName, true, ctx).map(Offer::of);
    }

    // The [requireNonNull] crossing, when the target is both NON_NULL and declared.
    @VisibleForTesting
    Stream<OperationSpec> requireNonNullGuard(
            final TypeMirror target, final String bindingName, final boolean guardsNullness, final ResolveCtx ctx) {
        return guardsNullness && ctx.isDeclared(target)
                ? Stream.of(requireNonNull(target, bindingName))
                : Stream.empty();
    }

    @VisibleForTesting
    OperationSpec requireNonNull(final TypeMirror target, final String slotName) {
        final var message = "source for slot '" + slotName + "' is null but target is non-null";
        final OperationCodegen codegen =
                inputs -> CodeBlock.of("$T.requireNonNull($L, $S)", Objects.class, inputs.single(), message);
        final var port = byTypeOrDecline(VALUE_ROLE, target, NULLABLE);
        return OperationSpec.ofPartial("requireNonNull", codegen, NOOP, List.of(port), target, NON_NULL);
    }

    // Over-emits the coalesce forms that can produce target: a nullable scalar and an Optional<T>.
    @VisibleForTesting
    Stream<OperationSpec> coalesce(
            final TypeMirror target, final CodeBlock literal, final DirectiveInput defaultInput, final ResolveCtx ctx) {
        final var specs = Stream.<OperationSpec>builder();
        if (ctx.isDeclared(target)) {
            specs.add(coalesceSpec(
                    target,
                    NULLABLE,
                    target,
                    inputs -> CodeBlock.of("$T.requireNonNullElse($L, $L)", Objects.class, inputs.single(), literal),
                    defaultInput));
        }
        optionalOf(target, ctx)
                .ifPresent(optional -> specs.add(coalesceSpec(
                        optional,
                        NON_NULL,
                        target,
                        inputs -> CodeBlock.of("$L$Z.orElse($L)", inputs.single(), literal),
                        defaultInput)));
        return specs.build();
    }

    @VisibleForTesting
    OperationSpec coalesceSpec(
            final TypeMirror from,
            final Nullability fromNullness,
            final TypeMirror target,
            final OperationCodegen codegen,
            final DirectiveInput defaultInput) {
        final var port = byTypeOrDecline(VALUE_ROLE, from, fromNullness);
        return OperationSpec.of("coalesce", codegen, NOOP, List.of(port), target, NON_NULL)
                .withConsumed(Set.of(defaultInput));
    }

    // Optional<element> for a reference element, or empty (no Optional of a primitive).
    @VisibleForTesting
    Optional<TypeMirror> optionalOf(final TypeMirror element, final ResolveCtx ctx) {
        if (!ctx.isReferenceType(element)) {
            return Optional.empty();
        }
        final var optionalElement = ctx.typeElementNamed("java.util.Optional");
        if (optionalElement == null) {
            return Optional.empty();
        }
        return Optional.of(ctx.declaredType(optionalElement, element));
    }
}

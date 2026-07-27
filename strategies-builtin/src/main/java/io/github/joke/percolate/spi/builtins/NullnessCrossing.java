package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.DirectiveInput;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.LiteralCoercion;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.Offer;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * The {@code NULLABLE → NON_NULL} crossing, target-driven (design D1/D2): keyed only on the demanded target, it
 * over-emits the crossings that can produce it and reads <b>no</b> candidate. Each crossing's input is a
 * <b>reuse-only</b> {@link Port#byTypeOrDecline} — bound to an already-in-scope source of that shape or the operation does not
 * apply (never minted), which is the candidate-free equivalent of the former "fire against an existing source":
 *
 * <ul>
 *   <li><b>{@code [requireNonNull]}</b> (<b>partial</b>) for a {@code NON_NULL} reference-scalar demand — a reuse-only
 *       {@code (T, NULLABLE)} port collapsed by {@code Objects.requireNonNull(source, "source for slot '…' is null but
 *       target is non-null")}, naming the slot from {@link ProduceDemand#bindingName()};</li>
 *   <li><b>{@code [coalesce]}</b> (<b>total</b>) when the binding's directive declares a {@code defaultValue}: a
 *       reuse-only {@code (T, NULLABLE)} scalar coalesces via {@code Objects.requireNonNullElse(source, D)}, and a
 *       reuse-only {@code (Optional<T>, NON_NULL)} source coalesces via {@code source.orElse(D)} — both reusing
 *       constant literal-coercion for the fallback.</li>
 * </ul>
 *
 * <p>The driver binds each reuse-only port to whichever in-scope source actually exists (a nullable scalar, an
 * {@code Optional<T>}, …); the others simply do not apply, so the engine selects the realisable crossing without the
 * strategy enumerating sources. When both a partial guard and a total coalesce can bind the same nullable scalar, the
 * plan-extraction totality rule keeps the total {@code [coalesce]}. The strategy runs at a negative {@link #priority()}
 * so its coalesce out-competes the plain identity assignment. It emits nothing for a primitive target (a primitive can
 * never be absent) or an uncoercible default (the late diagnostic reports it). It is myopic: it reads only the demand.
 */
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
        final var guardsNullness = demand.targetNullness() == Nullability.NON_NULL;
        final var raw = defaultInput.flatMap(DirectiveInput::getValue);
        if (raw.isEmpty()) {
            return guardOnly(target, demand.bindingName(), guardsNullness, ctx);
        }
        final var input = defaultInput.orElseThrow();
        final var coerced = LiteralCoercion.coerce(raw.get(), target);
        if (coerced.isEmpty()) {
            return Stream.of(
                    Offer.refusal(input.getSubject(), "cannot coerce '" + raw.get() + "' to " + typeName(target)));
        }
        return Stream.concat(
                        requireNonNullGuard(target, demand.bindingName(), guardsNullness, ctx),
                        coalesce(target, coerced.get(), input, ctx))
                .map(Offer::of);
    }

    /** No default declared: silence unless the target is {@code NON_NULL}, which still wants the guard alone. */
    static Stream<Offer> guardOnly(
            final TypeMirror target, final String bindingName, final boolean guardsNullness, final ResolveCtx ctx) {
        if (!guardsNullness) {
            return Stream.empty();
        }
        return requireNonNullGuard(target, bindingName, true, ctx).map(Offer::of);
    }

    static String typeName(final TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return type.toString();
        }
        final var element = ((DeclaredType) type).asElement();
        return element instanceof TypeElement
                ? ((TypeElement) element).getSimpleName().toString()
                : type.toString();
    }

    /** The {@code [requireNonNull]} crossing, when the target is both {@code NON_NULL} and declared. */
    static Stream<OperationSpec> requireNonNullGuard(
            final TypeMirror target, final String bindingName, final boolean guardsNullness, final ResolveCtx ctx) {
        return guardsNullness && ctx.isDeclared(target)
                ? Stream.of(requireNonNull(target, bindingName))
                : Stream.empty();
    }

    static OperationSpec requireNonNull(final TypeMirror target, final String slotName) {
        final var message = "source for slot '" + slotName + "' is null but target is non-null";
        final OperationCodegen codegen =
                inputs -> CodeBlock.of("$T.requireNonNull($L, $S)", Objects.class, inputs.single(), message);
        final var port = Port.byTypeOrDecline(VALUE_ROLE, target, Nullability.NULLABLE);
        return OperationSpec.ofPartial(
                "requireNonNull", codegen, Weights.NOOP, List.of(port), target, Nullability.NON_NULL);
    }

    /** Over-emits the coalesce forms that can produce {@code target}: a nullable scalar and an {@code Optional<T>}. */
    static Stream<OperationSpec> coalesce(
            final TypeMirror target, final CodeBlock literal, final DirectiveInput defaultInput, final ResolveCtx ctx) {
        final var specs = Stream.<OperationSpec>builder();
        if (ctx.isDeclared(target)) {
            specs.add(coalesceSpec(
                    target,
                    Nullability.NULLABLE,
                    target,
                    inputs -> CodeBlock.of("$T.requireNonNullElse($L, $L)", Objects.class, inputs.single(), literal),
                    defaultInput));
        }
        optionalOf(target, ctx)
                .ifPresent(optional -> specs.add(coalesceSpec(
                        optional,
                        Nullability.NON_NULL,
                        target,
                        inputs -> CodeBlock.of("$L$Z.orElse($L)", inputs.single(), literal),
                        defaultInput)));
        return specs.build();
    }

    static OperationSpec coalesceSpec(
            final TypeMirror from,
            final Nullability fromNullness,
            final TypeMirror target,
            final OperationCodegen codegen,
            final DirectiveInput defaultInput) {
        final var port = Port.byTypeOrDecline(VALUE_ROLE, from, fromNullness);
        return OperationSpec.of("coalesce", codegen, Weights.NOOP, List.of(port), target, Nullability.NON_NULL)
                .withConsumed(Set.of(defaultInput));
    }

    /** {@code Optional<element>} for a reference {@code element}, or empty (no {@code Optional} of a primitive). */
    static Optional<TypeMirror> optionalOf(final TypeMirror element, final ResolveCtx ctx) {
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

package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Directive;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.LiteralCoercion;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * The {@code NULLABLE → NON_NULL} crossing, target-driven (design D1/D2): keyed only on the demanded target, it
 * over-emits the crossings that can produce it and reads <b>no</b> candidate. Each crossing's input is a
 * <b>reuse-only</b> {@link Port#reuse} — bound to an already-in-scope source of that shape or the operation does not
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

    private static final String VALUE_ROLE = "value";
    private static final int OUTCOMPETE_PRIORITY = -1;

    @Override
    public int priority() {
        return OUTCOMPETE_PRIORITY;
    }

    @Override
    public Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var target = demand.targetType();
        final Optional<CodeBlock> defaultLiteral =
                demand.directive().flatMap(Directive::defaultValue).flatMap(raw -> LiteralCoercion.coerce(raw, target));
        // A requireNonNull guard is needed only for a NON_NULL target; a coalesce fires wherever a default is
        // declared, regardless of target nullness (a nullable/unknown target still uses its fallback).
        final var guardsNullness = demand.targetNullness() == Nullability.NON_NULL;
        if (!guardsNullness && defaultLiteral.isEmpty()) {
            return Stream.empty();
        }
        final var specs = Stream.<OperationSpec>builder();
        if (guardsNullness && ctx.isDeclared(target)) {
            specs.add(requireNonNull(target, demand.bindingName()));
        }
        defaultLiteral.ifPresent(literal -> coalesce(target, literal, ctx).forEach(specs::add));
        return specs.build();
    }

    private static OperationSpec requireNonNull(final TypeMirror target, final String slotName) {
        final var message = "source for slot '" + slotName + "' is null but target is non-null";
        final OperationCodegen codegen =
                inputs -> CodeBlock.of("$T.requireNonNull($L, $S)", Objects.class, inputs.single(), message);
        final var port = Port.reuse(VALUE_ROLE, target, Nullability.NULLABLE);
        return OperationSpec.ofPartial(
                "requireNonNull", codegen, Weights.NOOP, List.of(port), target, Nullability.NON_NULL);
    }

    /** Over-emits the coalesce forms that can produce {@code target}: a nullable scalar and an {@code Optional<T>}. */
    private static Stream<OperationSpec> coalesce(
            final TypeMirror target, final CodeBlock literal, final ResolveCtx ctx) {
        final var specs = Stream.<OperationSpec>builder();
        if (ctx.isDeclared(target)) {
            specs.add(coalesceSpec(
                    target,
                    Nullability.NULLABLE,
                    target,
                    inputs -> CodeBlock.of("$T.requireNonNullElse($L, $L)", Objects.class, inputs.single(), literal)));
        }
        optionalOf(target, ctx)
                .ifPresent(optional -> specs.add(coalesceSpec(
                        optional,
                        Nullability.NON_NULL,
                        target,
                        inputs -> CodeBlock.of("$L.orElse($L)", inputs.single(), literal))));
        return specs.build();
    }

    private static OperationSpec coalesceSpec(
            final TypeMirror from,
            final Nullability fromNullness,
            final TypeMirror target,
            final OperationCodegen codegen) {
        final var port = Port.reuse(VALUE_ROLE, from, fromNullness);
        return OperationSpec.of("coalesce", codegen, Weights.NOOP, List.of(port), target, Nullability.NON_NULL);
    }

    /** {@code Optional<element>} for a reference {@code element}, or empty (no {@code Optional} of a primitive). */
    private static Optional<TypeMirror> optionalOf(final TypeMirror element, final ResolveCtx ctx) {
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

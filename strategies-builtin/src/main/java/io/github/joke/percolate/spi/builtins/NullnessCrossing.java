package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Candidate;
import io.github.joke.percolate.spi.Containers;
import io.github.joke.percolate.spi.Demand;
import io.github.joke.percolate.spi.Directive;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.LiteralCoercion;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * The {@code NULLABLE → NON_NULL} crossing, as an ordinary strategy (design D2). On a {@code NON_NULL} demand it
 * fires against each in-scope {@link Candidate} of the demanded type and emits, per candidate:
 *
 * <ul>
 *   <li><b>{@code [requireNonNull]}</b> (<b>partial</b>) for a {@code NULLABLE} reference scalar of the target type —
 *       {@code Objects.requireNonNull(source, "source for slot '…' is null but target is non-null")}, naming the slot
 *       from {@link Demand#bindingName()};</li>
 *   <li><b>{@code [coalesce]}</b> (<b>total</b>) when the binding's directive declares a {@code defaultValue}: a
 *       {@code NULLABLE} reference scalar coalesces via {@code Objects.requireNonNullElse(source, D)}; an
 *       {@code Optional<T>} of the target type coalesces via {@code source.orElse(D)} — reusing constant
 *       literal-coercion for the fallback.</li>
 * </ul>
 *
 * <p>Both may be emitted for the same {@code (nullable scalar, default)} pair; the plan-extraction totality rule
 * selects the total {@code [coalesce]} over the partial {@code [requireNonNull]} without a bespoke either/or rule.
 * The strategy runs at a negative {@link #priority()} so its coalesce out-competes the plain identity assignment for
 * a non-null source. It emits nothing for a primitive source (a primitive can never be absent) or an uncoercible
 * default (the late diagnostic reports it). It is myopic: it reads only the demand, never the graph.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class NullnessCrossing implements ExpansionStrategy {

    private static final int OUTCOMPETE_PRIORITY = -1;

    @Override
    public int priority() {
        return OUTCOMPETE_PRIORITY;
    }

    @Override
    public Stream<OperationSpec> expand(final Demand demand, final ResolveCtx ctx) {
        final var target = demand.targetType();
        final Optional<CodeBlock> defaultLiteral =
                demand.directive().flatMap(Directive::defaultValue).flatMap(raw -> LiteralCoercion.coerce(raw, target));
        // A requireNonNull guard is needed only for a NON_NULL target; a coalesce fires wherever a default is
        // declared, regardless of target nullness (a nullable/unknown target still uses its fallback).
        final var guardsNullness = demand.targetNullness() == Nullability.NON_NULL;
        if (!guardsNullness && defaultLiteral.isEmpty()) {
            return Stream.empty();
        }
        return demand.candidates().stream()
                .flatMap(candidate -> cross(candidate, target, guardsNullness, defaultLiteral, demand, ctx));
    }

    private Stream<OperationSpec> cross(
            final Candidate candidate,
            final TypeMirror target,
            final boolean guardsNullness,
            final Optional<CodeBlock> defaultLiteral,
            final Demand demand,
            final ResolveCtx ctx) {
        final var specs = Stream.<OperationSpec>builder();
        final var from = candidate.getType();
        final var nullableScalar = candidate.getNullness() == Nullability.NULLABLE
                && from.getKind() == TypeKind.DECLARED
                && ctx.types().isSameType(from, target);
        if (guardsNullness && nullableScalar) {
            specs.add(requireNonNull(from, target, demand.bindingName()));
        }
        defaultLiteral.ifPresent(literal -> coalesce(from, target, literal, ctx).ifPresent(specs::add));
        return specs.build();
    }

    private static OperationSpec requireNonNull(final TypeMirror from, final TypeMirror target, final String slotName) {
        final var message = "source for slot '" + slotName + "' is null but target is non-null";
        final OperationCodegen codegen =
                (vars, inputs) -> CodeBlock.of("$T.requireNonNull($L, $S)", Objects.class, inputs.single(), message);
        final var port = new Port("value", from, Nullability.NULLABLE);
        return OperationSpec.ofPartial(codegen, Weights.NOOP, List.of(port), target, Nullability.NON_NULL);
    }

    private static Optional<OperationSpec> coalesce(
            final TypeMirror from, final TypeMirror target, final CodeBlock literal, final ResolveCtx ctx) {
        final var optionalElement = optionalElement(from, ctx);
        if (optionalElement.isPresent() && ctx.types().isSameType(optionalElement.get(), target)) {
            return Optional.of(coalesceSpec(
                    from,
                    Nullability.NON_NULL,
                    target,
                    (vars, inputs) -> CodeBlock.of("$L.orElse($L)", inputs.single(), literal)));
        }
        if (from.getKind() == TypeKind.DECLARED && ctx.types().isSameType(from, target)) {
            return Optional.of(coalesceSpec(
                    from,
                    Nullability.NULLABLE,
                    target,
                    (vars, inputs) ->
                            CodeBlock.of("$T.requireNonNullElse($L, $L)", Objects.class, inputs.single(), literal)));
        }
        return Optional.empty();
    }

    private static OperationSpec coalesceSpec(
            final TypeMirror from,
            final Nullability fromNullness,
            final TypeMirror target,
            final OperationCodegen codegen) {
        final var port = new Port("value", from, fromNullness);
        return OperationSpec.of(codegen, Weights.NOOP, List.of(port), target, Nullability.NON_NULL);
    }

    private static Optional<TypeMirror> optionalElement(final TypeMirror from, final ResolveCtx ctx) {
        if (!Containers.isOptional(from, ctx) || from.getKind() != TypeKind.DECLARED) {
            return Optional.empty();
        }
        final var args = ((DeclaredType) from).getTypeArguments();
        return args.size() == 1 ? Optional.of(args.get(0)) : Optional.empty();
    }
}

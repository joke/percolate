package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
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
 * Supplies a {@code @Map(..., defaultValue = "...")} fallback for an absent source value. On a demand whose
 * {@link Directive} declares a present {@code defaultValue}, it coerces the literal to the demanded type via
 * {@link LiteralCoercion} and emits a unary coalescing {@link OperationSpec} that produces a {@code NON_NULL}
 * value at the target type, per source kind:
 *
 * <ul>
 *   <li>a nullable reference scalar of the target type → {@code Objects.requireNonNullElse(source, D)} (a single
 *       {@code NULLABLE} port);</li>
 *   <li>an {@code Optional<T>} of the target type → {@code source.orElse(D)} (a present {@code Optional} port).</li>
 * </ul>
 *
 * <p>The operation is emitted at {@link Weights#NOOP} and the strategy runs at a negative {@link #priority()} so
 * its coalesce out-competes the plain {@code DirectAssign} identity for the same source. It emits nothing when no
 * {@code defaultValue} is present, when the literal cannot be coerced (the late diagnostic reports it), or for a
 * primitive source (a primitive can never be absent — the late dead-default diagnostic reports it).
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class DefaultValue implements ExpansionStrategy {

    private static final int OUTCOMPETE_PRIORITY = -1;

    @Override
    public int priority() {
        return OUTCOMPETE_PRIORITY;
    }

    @Override
    public Stream<OperationSpec> expand(final Demand demand, final ResolveCtx ctx) {
        final var declared = demand.directive().flatMap(Directive::defaultValue);
        if (declared.isEmpty()) {
            return Stream.empty();
        }
        final var target = demand.targetType();
        return LiteralCoercion.coerce(declared.get(), target)
                .map(literal -> demand.candidates().stream()
                        .flatMap(candidate -> coalesce(candidate.getType(), target, literal, ctx)))
                .orElseGet(Stream::empty);
    }

    private Stream<OperationSpec> coalesce(
            final TypeMirror from, final TypeMirror target, final CodeBlock literal, final ResolveCtx ctx) {
        final var optionalElement = optionalElement(from, ctx);
        if (optionalElement.isPresent() && ctx.types().isSameType(optionalElement.get(), target)) {
            return Stream.of(coalesceSpec(
                    from,
                    Nullability.NON_NULL,
                    target,
                    (vars, inputs) -> CodeBlock.of("$L.orElse($L)", inputs.single(), literal)));
        }
        if (from.getKind() == TypeKind.DECLARED && ctx.types().isSameType(from, target)) {
            return Stream.of(coalesceSpec(
                    from,
                    Nullability.NULLABLE,
                    target,
                    (vars, inputs) ->
                            CodeBlock.of("$T.requireNonNullElse($L, $L)", Objects.class, inputs.single(), literal)));
        }
        return Stream.empty();
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

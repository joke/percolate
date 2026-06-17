package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Demand;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Widening primitive conversion (JLS 5.1.2), authored target-to-source: a primitive target consumes each strictly
 * narrower primitive that widens to it, each as a single unary {@link OperationSpec} with an explicit cast. The
 * lattice (consumes-direction) is held as data. {@code boolean} appears nowhere (no widening); {@code char} is a
 * source only. The three precision-losing IEEE legs ({@code int → float}, {@code long → float}, {@code long →
 * double}) are included, matching javac's implicit-assignment behaviour. The engine composes cross-domain chains
 * (e.g. {@code Integer → long} as unbox-then-widen) through deduped intermediate Values.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class WidenPrimitive implements ExpansionStrategy {

    private static final Map<TypeKind, Set<TypeKind>> WIDENS_FROM = Map.of(
            TypeKind.SHORT, Set.of(TypeKind.BYTE),
            TypeKind.INT, Set.of(TypeKind.BYTE, TypeKind.SHORT, TypeKind.CHAR),
            TypeKind.LONG, Set.of(TypeKind.BYTE, TypeKind.SHORT, TypeKind.CHAR, TypeKind.INT),
            TypeKind.FLOAT, Set.of(TypeKind.BYTE, TypeKind.SHORT, TypeKind.CHAR, TypeKind.INT, TypeKind.LONG),
            TypeKind.DOUBLE,
                    Set.of(TypeKind.BYTE, TypeKind.SHORT, TypeKind.CHAR, TypeKind.INT, TypeKind.LONG, TypeKind.FLOAT));

    @Override
    public Stream<OperationSpec> expand(final Demand demand, final ResolveCtx ctx) {
        final var target = demand.targetType();
        final @Nullable Set<TypeKind> narrower = WIDENS_FROM.get(target.getKind());
        if (narrower == null) {
            return Stream.empty();
        }
        return narrower.stream().map(from -> wideningSpec(from, target, ctx));
    }

    private static OperationSpec wideningSpec(final TypeKind from, final TypeMirror target, final ResolveCtx ctx) {
        final TypeMirror inputType = ctx.types().getPrimitiveType(from);
        final OperationCodegen codegen = inputs -> CodeBlock.of("($T) $L", target, inputs.single());
        final var port = new Port("value", inputType, Nullability.NON_NULL);
        return OperationSpec.of(
                Labels.conversion(inputType, target),
                codegen,
                Weights.STEP,
                List.of(port),
                target,
                Nullability.NON_NULL);
    }
}

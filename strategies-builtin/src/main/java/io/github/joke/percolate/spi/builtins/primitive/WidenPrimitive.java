package io.github.joke.percolate.spi.builtins.primitive;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Conversion;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.builtins.Labels;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

import static io.github.joke.percolate.spi.Weights.STEP;
import static javax.lang.model.type.TypeKind.BYTE;
import static javax.lang.model.type.TypeKind.CHAR;
import static javax.lang.model.type.TypeKind.DOUBLE;
import static javax.lang.model.type.TypeKind.FLOAT;
import static javax.lang.model.type.TypeKind.LONG;
import static javax.lang.model.type.TypeKind.SHORT;

// Widening primitive conversion (JLS 5.1.2), authored target-to-source on the Conversion archetype base: a
// primitive target consumes each strictly narrower primitive that widens to it, each a single unary conversion
// with an explicit cast. The lattice (consumes-direction) is held as data. boolean appears nowhere (no
// widening); char is a source only. The three precision-losing IEEE legs (int → float, long → float, long →
// double) are included, matching javac's implicit-assignment behaviour. The engine composes cross-domain chains
// (e.g. Integer → long as unbox-then-widen) through deduped intermediate Values.
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class WidenPrimitive extends Conversion {

    private static final Map<TypeKind, Set<TypeKind>> WIDENS_FROM = Map.of(
            SHORT,
            Set.of(BYTE),
            TypeKind.INT,
            Set.of(BYTE, SHORT, CHAR),
            LONG,
            Set.of(BYTE, SHORT, CHAR, TypeKind.INT),
            FLOAT,
            Set.of(BYTE, SHORT, CHAR, TypeKind.INT, LONG),
            DOUBLE,
            Set.of(BYTE, SHORT, CHAR, TypeKind.INT, LONG, FLOAT));

    @Override
    @VisibleForTesting
    protected Stream<Step> conversions(final TypeMirror target, final ResolveCtx ctx) {
        final var narrower = WIDENS_FROM.get(ctx.kind(target));
        if (narrower == null) {
            return Stream.empty();
        }
        return narrower.stream().map(from -> wideningStep(from, target, ctx));
    }

    @VisibleForTesting
    Step wideningStep(final TypeKind from, final TypeMirror target, final ResolveCtx ctx) {
        final var inputType = ctx.primitiveType(from);
        final OperationCodegen codegen = inputs -> CodeBlock.of("($T) $L", target, inputs.single());
        return new Step(inputType, Labels.conversion(inputType, target), STEP, codegen);
    }
}

package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Bridge;
import io.github.joke.percolate.spi.BridgeStep;
import io.github.joke.percolate.spi.Containers;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import lombok.NoArgsConstructor;

import javax.lang.model.type.TypeMirror;
import java.util.stream.Stream;

@AutoService(Bridge.class)
@NoArgsConstructor
public final class OptionalWrap implements Bridge {

    @Override
    public Stream<BridgeStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
        if (!Containers.isOptional(to, ctx)) {
            return Stream.empty();
        }
        final var elementType = Containers.typeArgument(to, 0);
        final EdgeCodegen codegen =
                (vars, inputs) -> CodeBlock.of("$T.ofNullable($L)", java.util.Optional.class, inputs.single());
        return Stream.of(new BridgeStep(elementType, to, Weights.CONTAINER, codegen));
    }
}

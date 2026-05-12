package io.github.joke.percolate.processor.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.spi.Bridge;
import io.github.joke.percolate.processor.spi.BridgeStep;
import io.github.joke.percolate.processor.spi.Containers;
import io.github.joke.percolate.processor.spi.EdgeCodegen;
import io.github.joke.percolate.processor.spi.ResolveCtx;
import io.github.joke.percolate.processor.spi.Weights;
import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

@AutoService(Bridge.class)
@NoArgsConstructor
public final class OptionalUnwrap implements Bridge {

    @Override
    public Stream<BridgeStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
        if (!Containers.isOptional(from, ctx)) {
            return Stream.empty();
        }
        final var elementType = Containers.typeArgument(from, 0);
        final EdgeCodegen codegen = (vars, inputs) -> CodeBlock.of("$L.orElse(null)", inputs.single());
        return Stream.of(new BridgeStep(from, elementType, Weights.CONTAINER, codegen, List.of()));
    }
}

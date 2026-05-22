package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Bridge;
import io.github.joke.percolate.spi.BridgeStep;
import io.github.joke.percolate.spi.Containers;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.ScopeTransition;
import io.github.joke.percolate.spi.Weights;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

@AutoService(Bridge.class)
@NoArgsConstructor
public final class OptionalCollect implements Bridge {

    @Override
    public Stream<BridgeStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
        if (!Containers.isOptional(to, ctx)) {
            return Stream.empty();
        }
        final var elementType = Containers.typeArgument(to, 0);
        final EdgeCodegen codegen = (vars, inputs) -> CodeBlock.of("$L", inputs.single());
        return Stream.of(new BridgeStep(elementType, to, Weights.CONTAINER, codegen, ScopeTransition.EXITING, "element"));
    }
}

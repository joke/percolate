package io.github.joke.percolate.processor.spi.builtins;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.processor.spi.Bridge;
import io.github.joke.percolate.processor.spi.BridgeStep;
import io.github.joke.percolate.processor.spi.Containers;
import io.github.joke.percolate.processor.spi.EdgeCodegen;
import io.github.joke.percolate.processor.spi.ElementSeed;
import io.github.joke.percolate.processor.spi.ResolveCtx;
import io.github.joke.percolate.processor.spi.Weights;
import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

@AutoService(Bridge.class)
@NoArgsConstructor
public final class OptionalMap implements Bridge {

    @Override
    public Stream<BridgeStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
        if (!Containers.isOptional(from, ctx) || !Containers.isOptional(to, ctx)) {
            return Stream.empty();
        }
        final var innerFrom = Containers.typeArgument(from, 0);
        final var innerTo = Containers.typeArgument(to, 0);
        final EdgeCodegen codegen = (vars, inputs) -> {
            throw new UnsupportedOperationException(
                    "rendered by the codegen capability; element-scope inlining is not implemented in this change");
        };
        final var seed = new ElementSeed("element", innerFrom, innerTo);
        return Stream.of(new BridgeStep(from, to, Weights.CONTAINER, codegen, List.of(seed)));
    }
}

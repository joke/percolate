package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.spi.Bridge;
import io.github.joke.percolate.spi.BridgeStep;
import io.github.joke.percolate.spi.Containers;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.ElementSeed;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import lombok.NoArgsConstructor;

import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.stream.Stream;

@AutoService(Bridge.class)
@NoArgsConstructor
public final class SetMap implements Bridge {

    @Override
    public Stream<BridgeStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
        if (!Containers.isSet(to, ctx)) {
            return Stream.empty();
        }
        if (!isAcceptedInputShape(from, ctx)) {
            return Stream.empty();
        }
        final var innerFrom = resolveElementType(from);
        final var innerTo = Containers.typeArgument(to, 0);
        final EdgeCodegen codegen = (vars, inputs) -> {
            throw new UnsupportedOperationException(
                    "rendered by the codegen capability; element-scope inlining is not implemented in this change");
        };
        final var seed = new ElementSeed("element", innerFrom, innerTo);
        return Stream.of(new BridgeStep(from, to, Weights.CONTAINER, codegen, List.of(seed)));
    }

    private boolean isAcceptedInputShape(final TypeMirror from, final ResolveCtx ctx) {
        return Containers.isIterable(from, ctx) || Containers.isArray(from) || Containers.isOptional(from, ctx);
    }

    private TypeMirror resolveElementType(final TypeMirror from) {
        if (Containers.isArray(from)) {
            return Containers.arrayComponentType(from);
        }
        return Containers.typeArgument(from, 0);
    }
}

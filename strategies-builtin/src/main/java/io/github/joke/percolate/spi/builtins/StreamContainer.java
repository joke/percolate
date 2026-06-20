package io.github.joke.percolate.spi.builtins;

import io.github.joke.percolate.spi.Container;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.Objects;
import javax.lang.model.element.TypeElement;

/**
 * Shared base for the JDK containers whose element-sequence intermediate is {@code java.util.stream.Stream}. It names
 * {@code Stream} once for the built-in collection family (List/Set/array/Optional) so each need not repeat it; a
 * reactive container would instead declare its own intermediate ({@code Flux}/{@code Mono}) on the same hook, with no
 * engine change.
 */
abstract class StreamContainer extends Container {

    @Override
    protected TypeElement intermediateErasure(final ResolveCtx ctx) {
        return Objects.requireNonNull(
                ctx.elements().getTypeElement("java.util.stream.Stream"), "java.util.stream.Stream is unavailable");
    }
}

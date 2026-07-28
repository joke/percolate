package io.github.joke.percolate.spi.builtins.container;

import io.github.joke.percolate.spi.Container;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.Objects;
import javax.lang.model.element.TypeElement;
import org.jetbrains.annotations.VisibleForTesting;

// Shared base for the JDK containers whose element-sequence intermediate is java.util.stream.Stream. It names
// Stream once for the built-in collection family (List/Set/array/Optional) so each need not repeat it; a
// reactive container would instead declare its own intermediate (Flux/Mono) on the same hook, with no engine
// change.
abstract class StreamContainer extends Container {

    @Override
    @VisibleForTesting
    protected TypeElement intermediateErasure(final ResolveCtx ctx) {
        return Objects.requireNonNull(
                ctx.typeElementNamed("java.util.stream.Stream"), "java.util.stream.Stream is unavailable");
    }
}

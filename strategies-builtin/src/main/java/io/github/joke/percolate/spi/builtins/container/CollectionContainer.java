package io.github.joke.percolate.spi.builtins.container;

import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.Optional;
import javax.lang.model.element.TypeElement;
import org.jetbrains.annotations.VisibleForTesting;

// Shared stream snippets for the JDK collection sequence containers. List and Set differ only by their terminal
// Collectors collector and their single-element of(...) factory; everything else (open the stream, close it,
// wrap a scalar, the kind erasure for containerOf) is identical. Supplying collect makes the kind a sequence.
abstract class CollectionContainer extends StreamContainer {

    // The terminal collector snippet, e.g. Collectors.toList().
    protected abstract CodeBlock collector();

    // The single-element factory type, e.g. List so the wrap renders List.of(x); also the kind.
    protected abstract Class<?> factoryType();

    @Override
    @VisibleForTesting
    protected Optional<TypeElement> kindErasure(final ResolveCtx ctx) {
        return Optional.ofNullable(ctx.typeElementNamed(factoryType().getCanonicalName()));
    }

    @Override
    public Optional<UnarySnippet> iterate() {
        return Optional.of(container -> CodeBlock.of("$L$Z.stream()", container));
    }

    @Override
    public Optional<UnarySnippet> collect() {
        return Optional.of(stream -> CodeBlock.of("$L$Z.collect($L)", stream, collector()));
    }

    @Override
    public Optional<UnarySnippet> wrap() {
        return Optional.of(scalar -> CodeBlock.of("$T.of($L)", factoryType(), scalar));
    }
}

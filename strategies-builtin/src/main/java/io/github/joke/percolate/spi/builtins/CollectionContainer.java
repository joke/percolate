package io.github.joke.percolate.spi.builtins;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.lang.model.element.TypeElement;

/**
 * Shared stream snippets for the JDK collection sequence containers. List and Set differ only by their
 * terminal {@link Collectors} collector and their single-element {@code of(...)} factory; everything else
 * (open the stream, close it, wrap a scalar, the kind erasure for {@code containerOf}) is identical. Supplying
 * {@code collect} makes the kind a sequence.
 */
abstract class CollectionContainer extends StreamContainer {

    /** The terminal collector snippet, e.g. {@code Collectors.toList()}. */
    protected abstract CodeBlock collector();

    /** The single-element factory type, e.g. {@code List} so the wrap renders {@code List.of(x)}; also the kind. */
    protected abstract Class<?> factoryType();

    @Override
    protected Optional<TypeElement> kindErasure(final ResolveCtx ctx) {
        return Optional.ofNullable(ctx.typeElementNamed(factoryType().getCanonicalName()));
    }

    @Override
    public Optional<UnarySnippet> iterate() {
        return Optional.of(container -> CodeBlock.of("$L.stream()", container));
    }

    @Override
    public Optional<UnarySnippet> collect() {
        return Optional.of(stream -> CodeBlock.of("$L.collect($L)", stream, collector()));
    }

    @Override
    public Optional<UnarySnippet> wrap() {
        return Optional.of(scalar -> CodeBlock.of("$T.of($L)", factoryType(), scalar));
    }
}

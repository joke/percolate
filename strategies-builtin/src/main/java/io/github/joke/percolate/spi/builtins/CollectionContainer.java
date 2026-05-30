package io.github.joke.percolate.spi.builtins;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.SequenceContainer;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Shared stream snippets for the JDK collection sequence containers. List and Set differ only by their
 * terminal {@link Collectors} collector and their single-element {@code of(...)} factory; everything else
 * (open the stream, map, flat-map) is identical.
 */
abstract class CollectionContainer extends SequenceContainer {

    /** The terminal collector snippet, e.g. {@code Collectors.toList()}. */
    protected abstract CodeBlock collector();

    /** The single-element factory type, e.g. {@code List} so the wrap renders {@code List.of(x)}. */
    protected abstract Class<?> factoryType();

    @Override
    protected final Optional<EdgeCodegen> singleElementWrap() {
        return Optional.of((vars, inputs) -> CodeBlock.of("$T.of($L)", factoryType(), inputs.single()));
    }

    @Override
    public final CodeBlock iterate(final CodeBlock container) {
        return CodeBlock.of("$L.stream()", container);
    }

    @Override
    public final CodeBlock mapElements(final CodeBlock stream, final String var, final CodeBlock body) {
        return CodeBlock.of("$L.map($N -> $L)", stream, var, body);
    }

    @Override
    public final CodeBlock flatMapElements(final CodeBlock stream, final String var, final CodeBlock inner) {
        return CodeBlock.of("$L.flatMap($N -> $L)", stream, var, inner);
    }

    @Override
    public final CodeBlock collect(final CodeBlock stream) {
        return CodeBlock.of("$L.collect($L)", stream, collector());
    }
}

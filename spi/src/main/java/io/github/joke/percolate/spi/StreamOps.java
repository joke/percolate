package io.github.joke.percolate.spi;

import com.palantir.javapoet.CodeBlock;

/**
 * The paradigm-generic stream entry shared by every container kind: {@link #iterate} opens an element stream over
 * the container ({@code Collection.stream()}, {@code Arrays.stream}, {@code Optional.stream()}). Once a stream is
 * open, mapping and flat-mapping are kind-free and live on the generic stream strategy (a {@link ScopeCodegen}),
 * not here — a container only knows how to open its own stream and (for a sequence) close one
 * ({@link ContainerCodegen#collect}).
 */
public interface StreamOps extends Codegen {

    /** Open an element stream over {@code container}. */
    CodeBlock iterate(CodeBlock container);
}

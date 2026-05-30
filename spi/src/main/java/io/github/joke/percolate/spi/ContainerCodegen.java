package io.github.joke.percolate.spi;

import com.palantir.javapoet.CodeBlock;

/**
 * Codegen handle for a sequence container (List, Set, array, Flux). Adds the sequence terminal {@link #collect}
 * to the shared {@link StreamOps}: a sequence opens an element stream ({@link #iterate}), transforms it
 * (inherited map/flat-map), and closes it back into the container ({@link #collect}). The composer obtains every
 * such {@code CodeBlock} from this handle; it never writes {@code stream}/{@code collect}/{@code map} literally.
 */
public interface ContainerCodegen extends StreamOps {

    /** Close {@code stream} into this container. */
    CodeBlock collect(CodeBlock stream);
}

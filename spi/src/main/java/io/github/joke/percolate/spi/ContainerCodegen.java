package io.github.joke.percolate.spi;

import com.palantir.javapoet.CodeBlock;

/**
 * Per-operation snippets for a sequence container (List, Set, array, Flux). The composer obtains every
 * stream-touching {@link CodeBlock} from these methods; it never writes {@code stream}/{@code collect}/{@code map}
 * literally. A handle renders the stream paradigm: {@link #iterate} opens an element stream, {@link #mapElements}
 * and {@link #flatMapElements} transform it, {@link #collect} closes it back into the container.
 */
public interface ContainerCodegen extends Codegen {

    /** Open an element stream over {@code container}. */
    CodeBlock iterate(CodeBlock container);

    /** Map each element of {@code stream} through {@code body}, binding the element to {@code var}. */
    CodeBlock mapElements(CodeBlock stream, String var, CodeBlock body);

    /** Flat-map each element of {@code stream} through {@code inner}, binding the element to {@code var}. */
    CodeBlock flatMapElements(CodeBlock stream, String var, CodeBlock inner);

    /** Close {@code stream} into this container. */
    CodeBlock collect(CodeBlock stream);
}

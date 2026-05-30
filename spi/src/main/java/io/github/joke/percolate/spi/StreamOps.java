package io.github.joke.percolate.spi;

import com.palantir.javapoet.CodeBlock;

/**
 * The paradigm-generic stream operations shared by every container kind. Once a stream is open, mapping and
 * flat-mapping are the same regardless of which container opened or last touched it, so the composer threads
 * <em>whichever</em> handle owns the open stream and asks it for these. {@link #iterate} opens the stream;
 * {@link #mapElements} / {@link #flatMapElements} transform it. Closing a stream back into a container
 * ({@code collect}) is a <b>sequence</b> terminal and lives on {@link ContainerCodegen}, not here — a presence
 * wrapper participates in a stream (via {@link #iterate}) but never collects into itself.
 */
public interface StreamOps extends Codegen {

    /** Open an element stream over {@code container}. */
    CodeBlock iterate(CodeBlock container);

    /** Map each element of {@code stream} through {@code body}, binding the element to {@code var}. */
    CodeBlock mapElements(CodeBlock stream, String var, CodeBlock body);

    /** Flat-map each element of {@code stream} through {@code inner}, binding the element to {@code var}. */
    CodeBlock flatMapElements(CodeBlock stream, String var, CodeBlock inner);
}

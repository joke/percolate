package io.github.joke.percolate.spi;

import com.palantir.javapoet.CodeBlock;

/**
 * The code-generation handle of a scope-owning {@link OperationSpec} — a container element mapping (stream
 * {@code map}/{@code flatMap}, {@code Optional.map}). It weaves the container operation around the child plan
 * rendered as a per-element lambda: {@code operand} is the rendered source expression (the stream or wrapper),
 * {@code var} is the lambda parameter bound to the child param-root, and {@code body} is the rendered child
 * return-root expression. The composer holds no container syntax itself; it asks the handle to weave.
 *
 * <p>{@link #weave} chains a call onto {@code operand} (e.g. {@code "$L.map($N -> $L)"}); prefix that call's leading
 * {@code .} with JavaPoet's {@code $Z} (zero-width space) wrap marker, e.g. {@code "$L$Z.map($N -> $L)"}. Below
 * JavaPoet's column limit this renders identically to today's output; above it, the recursive composition of many
 * operations' rendered {@code CodeBlock}s (each spliced into the next via {@code $L}) wraps gracefully at a call
 * boundary instead of overflowing one line.
 */
public interface ScopeCodegen extends Codegen {

    /** Weave this scope-owning operation around {@code body}, binding the element to {@code var}. */
    CodeBlock weave(CodeBlock operand, String var, CodeBlock body);
}

package io.github.joke.percolate.spi;

import com.palantir.javapoet.CodeBlock;

/**
 * The code-generation handle of a scope-owning {@link OperationSpec} — a container element mapping (stream
 * {@code map}/{@code flatMap}, {@code Optional.map}). It weaves the container operation around the child plan
 * rendered as a per-element lambda: {@code operand} is the rendered source expression (the stream or wrapper),
 * {@code var} is the lambda parameter bound to the child param-root, and {@code body} is the rendered child
 * return-root expression. The composer holds no container syntax itself; it asks the handle to weave.
 */
public interface ScopeCodegen extends Codegen {

    /** Weave this scope-owning operation around {@code body}, binding the element to {@code var}. */
    CodeBlock weave(CodeBlock operand, String var, CodeBlock body);
}

package io.github.joke.percolate.processor.graph;

/**
 * Shape requested from {@link ValueNode#compose(java.util.Map, ComposeKind)}.
 *
 * <p>{@link #EXPRESSION} is the standard case: the node returns a single {@link
 * com.palantir.javapoet.CodeBlock} usable wherever an expression is expected. {@link
 * #STATEMENT_LIST} is reserved for future stateful builder/bean-update targets that need to emit a
 * sequence of statements; no built-in node produces it yet.
 */
public enum ComposeKind {
    EXPRESSION,
    STATEMENT_LIST
}

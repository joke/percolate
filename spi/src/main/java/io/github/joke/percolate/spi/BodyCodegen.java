package io.github.joke.percolate.spi;

import io.github.joke.percolate.lib.javapoet.CodeBlock;

/**
 * The code-generation handle of a production whose rendering is a <b>complete method body</b> (a statement
 * sequence — e.g. a classic {@code switch} statement ending in a {@code return}, or a single {@code return} of an
 * expression) rather than a bare inline expression. A sibling of {@link OperationCodegen}: additive, and it does
 * not replace it — {@link OperationCodegen#render(IncomingValues)} is unchanged. A strategy signals its production
 * renders as a whole body by supplying a {@code BodyCodegen} instead of an {@link OperationCodegen}; the engine
 * dispatches on which shape the production's chosen operation carries and renders it verbatim as the method body
 * (see the {@code code-generation} capability) — it makes no code-generation choice of its own.
 *
 * <p>{@code BodyCodegen} is valid only at a method's return-root: the production it backs is a flat leaf operation
 * (no child scope, no hoisted locals), so its complete body is self-contained.
 */
public interface BodyCodegen extends Codegen {

    /** Render this operation's complete method body from {@code context}. */
    CodeBlock render(BodyRenderContext context);
}

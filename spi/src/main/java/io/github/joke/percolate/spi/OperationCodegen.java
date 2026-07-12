package io.github.joke.percolate.spi;

import io.github.joke.percolate.javapoet.CodeBlock;

/**
 * The code-generation handle of a scalar (non-container) {@link OperationSpec}: it renders the operation's
 * expression from its incoming port values. The {@code render(IncomingValues)} contract is the former
 * {@code EdgeCodegen} contract, relocated from the edge onto the operation — incoming values are keyed by port
 * name ({@link IncomingValues#byName}). A container's kind-local snippets are wrapped into {@code OperationCodegen}s
 * by the {@link Container} base; a scope-owning container mapping carries a {@link ScopeCodegen} instead.
 *
 * <p>When {@link #render} chains a call onto one of its incoming values (e.g. an accessor {@code "$L.$N()"} or a
 * blocking bridge {@code "$L.block()"}), prefix that call's leading {@code .} with JavaPoet's {@code $Z} (zero-width
 * space) wrap marker, e.g. {@code "$L$Z.$N()"}. Below JavaPoet's column limit this renders identically to today's
 * output; above it, the recursive composition of many operations' rendered {@code CodeBlock}s (each spliced into the
 * next via {@code $L}) wraps gracefully at a call boundary instead of overflowing one line. A rendering that
 * prepends rather than chains (e.g. a cast {@code "($T) $L"}) has no such call to mark.
 */
public interface OperationCodegen extends Codegen {

    /** Render this operation's expression from its incoming port values (keyed by port name). */
    CodeBlock render(IncomingValues inputs);
}

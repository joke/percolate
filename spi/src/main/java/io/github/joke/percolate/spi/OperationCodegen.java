package io.github.joke.percolate.spi;

import com.palantir.javapoet.CodeBlock;

/**
 * The code-generation handle of a scalar (non-container) {@link OperationSpec}: it renders the operation's
 * expression from its incoming port values. The {@code render(VarNames, IncomingValues)} contract is the former
 * {@code EdgeCodegen} contract, relocated from the edge onto the operation — incoming values are keyed by port
 * name ({@link IncomingValues#byName}). Container operations carry a {@link StreamOps}-family handle instead.
 */
public interface OperationCodegen extends Codegen {

    /** Render this operation's expression from its incoming port values (keyed by port name). */
    CodeBlock render(VarNames vars, IncomingValues inputs);
}

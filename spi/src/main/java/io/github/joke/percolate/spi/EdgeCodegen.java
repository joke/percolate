package io.github.joke.percolate.spi;

import com.palantir.javapoet.CodeBlock;

public interface EdgeCodegen extends Codegen {
    CodeBlock render(VarNames vars, IncomingValues inputs);
}

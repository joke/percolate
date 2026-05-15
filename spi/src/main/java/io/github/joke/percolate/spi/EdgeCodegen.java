package io.github.joke.percolate.spi;

import com.palantir.javapoet.CodeBlock;

public interface EdgeCodegen {
    CodeBlock render(VarNames vars, IncomingValues inputs);
}

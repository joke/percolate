package io.github.joke.percolate.spi;

import com.palantir.javapoet.CodeBlock;

public interface GroupCodegen {
    CodeBlock render(VarNames vars, IncomingValues inputs);
}

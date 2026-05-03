package io.github.joke.percolate.processor.graph;

import com.palantir.javapoet.CodeBlock;

public interface GroupCodegen {
    CodeBlock render(VarNames vars, IncomingValues inputs);
}

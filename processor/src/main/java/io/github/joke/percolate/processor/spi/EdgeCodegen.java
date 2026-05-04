package io.github.joke.percolate.processor.spi;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.graph.IncomingValues;
import io.github.joke.percolate.processor.graph.VarNames;

public interface EdgeCodegen extends io.github.joke.percolate.processor.graph.EdgeCodegen {
    @Override
    CodeBlock render(VarNames vars, IncomingValues inputs);
}

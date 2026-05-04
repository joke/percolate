package io.github.joke.percolate.processor.spi;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.graph.IncomingValues;
import io.github.joke.percolate.processor.graph.VarNames;

public interface GroupCodegen extends io.github.joke.percolate.processor.graph.GroupCodegen {
    @Override
    CodeBlock render(VarNames vars, IncomingValues inputs);
}

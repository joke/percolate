package io.github.joke.percolate.processor.graph;

import com.palantir.javapoet.CodeBlock;

public interface IncomingValues {
    CodeBlock single();

    CodeBlock byGroupPosition(int idx);

    CodeBlock byName(String slotName);
}

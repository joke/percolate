package io.github.joke.percolate.spi;

import com.palantir.javapoet.CodeBlock;

public interface IncomingValues {
    CodeBlock single();

    CodeBlock byGroupPosition(int idx);

    CodeBlock byName(String slotName);
}

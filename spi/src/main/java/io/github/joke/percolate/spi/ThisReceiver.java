package io.github.joke.percolate.spi;

import io.github.joke.percolate.lib.javapoet.CodeBlock;

public enum ThisReceiver implements Receiver {
    INSTANCE;

    @Override
    public CodeBlock asExpression() {
        return CodeBlock.of("this");
    }
}

package io.github.joke.percolate.processor.spi;

import com.palantir.javapoet.CodeBlock;

public enum ThisReceiver implements Receiver {
    INSTANCE;

    @Override
    public CodeBlock asExpression() {
        return CodeBlock.of("this");
    }
}

package io.github.joke.percolate.processor.spi;

import com.palantir.javapoet.CodeBlock;

public interface Receiver {
    CodeBlock asExpression();
}

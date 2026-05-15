package io.github.joke.percolate.spi;

import com.palantir.javapoet.CodeBlock;

public interface Receiver {
    CodeBlock asExpression();
}

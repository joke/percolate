package io.github.joke.percolate.spi;

import io.github.joke.percolate.lib.javapoet.CodeBlock;

public interface Receiver {
    CodeBlock asExpression();
}

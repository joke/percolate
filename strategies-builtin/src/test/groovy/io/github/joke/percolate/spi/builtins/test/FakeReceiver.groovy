package io.github.joke.percolate.spi.builtins.test

import io.github.joke.percolate.javapoet.CodeBlock
import io.github.joke.percolate.spi.Receiver

final class FakeReceiver implements Receiver {

    static Receiver instance() {
        new FakeReceiver()
    }

    @Override
    CodeBlock asExpression() {
        CodeBlock.of('obj')
    }
}

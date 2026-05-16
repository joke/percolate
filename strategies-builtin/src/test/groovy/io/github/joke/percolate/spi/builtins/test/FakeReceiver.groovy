package io.github.joke.percolate.spi.builtins.test

import com.palantir.javapoet.CodeBlock
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

package io.github.joke.percolate.spi

import io.github.joke.percolate.lib.javapoet.CodeBlock
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class ThisReceiverSpec extends Specification {

    def 'INSTANCE renders the this expression'() {
        expect:
        ThisReceiver.INSTANCE.asExpression() == CodeBlock.of('this')
    }
}

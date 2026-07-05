package io.github.joke.percolate.spi

import com.palantir.javapoet.CodeBlock
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class ThisReceiverSpec extends Specification {

    def 'INSTANCE renders the this expression'() {
        expect:
        ThisReceiver.INSTANCE.asExpression() == CodeBlock.of('this')
    }
}

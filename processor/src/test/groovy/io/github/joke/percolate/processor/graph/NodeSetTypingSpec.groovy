package io.github.joke.percolate.processor.graph

import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class NodeSetTypingSpec extends Specification {

    def 'untyped Node has both type and nullability empty'() {
        given:
        def node = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), new HarnessScope('m()'))

        expect:
        node.type.empty
        node.nullability.empty
    }

    def 'setTyping populates both fields'() {
        given:
        def node = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), new HarnessScope('m()'))

        when:
        node.setTyping(TypeUniverse.STRING, Nullability.NULLABLE)

        then:
        node.type.present
        node.type.get() == TypeUniverse.STRING
        node.nullability.present
        node.nullability.get() == Nullability.NULLABLE
    }

    def 'setTyping is one-shot and throws on second invocation'() {
        given:
        def node = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), new HarnessScope('m()'))
        node.setTyping(TypeUniverse.STRING, Nullability.NON_NULL)

        when:
        node.setTyping(TypeUniverse.INTEGER, Nullability.NULLABLE)

        then:
        thrown(IllegalStateException)
    }

    def 'setTyping throws when typed at construction (both fields already set)'() {
        given:
        def node = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('x')), new HarnessScope('m()'))

        when:
        node.setTyping(TypeUniverse.STRING, Nullability.NON_NULL)

        then:
        thrown(IllegalStateException)
    }
}

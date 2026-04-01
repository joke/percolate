package io.github.joke.percolate.processor.transform

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.TransformEdge
import io.github.joke.percolate.processor.graph.TypeNode
import io.github.joke.percolate.processor.spi.TypeTransformStrategy
import io.github.joke.percolate.processor.transform.TransformProposal
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class TransformModelSpec extends Specification {

    def 'CodeTemplate identity returns input unchanged'() {
        given:
        final CodeTemplate identity = { CodeBlock input -> input }
        final input = CodeBlock.of('source.getName()')

        expect:
        identity.apply(input) == input
    }

    def 'CodeTemplate can transform input'() {
        given:
        final CodeTemplate template = { CodeBlock input -> CodeBlock.of('$L.stream()', input) }
        final input = CodeBlock.of('source.getItems()')

        expect:
        template.apply(input).toString() == 'source.getItems().stream()'
    }

    def 'TransformProposal holds all fields'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        final CodeTemplate template = { CodeBlock input -> input }
        final strategy = Mock(TypeTransformStrategy)

        when:
        final proposal = new TransformProposal(sourceType, targetType, template, strategy)

        then:
        proposal.requiredInput == sourceType
        proposal.producedOutput == targetType
        proposal.codeTemplate == template
        proposal.strategy == strategy
    }

    def 'TypeNode holds type and label'() {
        given:
        final type = Mock(TypeMirror)

        when:
        final node = new TypeNode(type, 'List<Person>')

        then:
        node.type == type
        node.label == 'List<Person>'
        node.toString() == 'List<Person>'
    }

    def 'TransformEdge holds strategy and proposal, resolves template lazily'() {
        given:
        final strategy = Mock(TypeTransformStrategy)
        final proposal = Stub(TransformProposal)
        final CodeTemplate template = { CodeBlock input -> input }

        when:
        final edge = new TransformEdge(strategy, proposal)

        then:
        edge.strategy == strategy
        edge.proposal == proposal
        edge.codeTemplate == null

        when:
        edge.resolveTemplate(template)

        then:
        edge.codeTemplate == template
    }
}

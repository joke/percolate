package io.github.joke.percolate.processor.spi

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Types

@Tag('unit')
class DirectAssignableStrategySpec extends Specification {

    Types types = Mock()
    ResolutionContext ctx = Stub() { getTypes() >> types }
    DirectAssignableStrategy strategy = new DirectAssignableStrategy()

    def 'returns proposal when types are assignable'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        types.isAssignable(sourceType, targetType) >> true

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)

        then:
        result.isPresent()
        result.get().requiredInput == sourceType
        result.get().producedOutput == targetType
        result.get().strategy == strategy
    }

    def 'identity template returns input unchanged'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        types.isAssignable(sourceType, targetType) >> true

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)
        final output = result.get().codeTemplate.apply(com.palantir.javapoet.CodeBlock.of('source.getName()'))

        then:
        output.toString() == 'source.getName()'
    }

    def 'returns empty when types are not assignable'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        types.isAssignable(sourceType, targetType) >> false

        expect:
        strategy.canProduce(sourceType, targetType, ctx).isEmpty()
    }
}

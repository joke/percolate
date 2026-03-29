package io.github.joke.percolate.processor.spi

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.model.DiscoveredMethod
import io.github.joke.percolate.processor.model.MappingMethodModel
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Types

@Tag('unit')
class MethodCallStrategySpec extends Specification {

    Types types = Mock()
    MethodCallStrategy strategy = new MethodCallStrategy()

    def 'returns proposal when sibling method matches'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        final methodName = Stub(Name) { toString() >> 'mapPerson' }
        final execElement = Stub(ExecutableElement) { getSimpleName() >> methodName }
        final siblingModel = new MappingMethodModel(execElement, sourceType, targetType, [])
        final siblingMethod = new DiscoveredMethod(siblingModel, [:], [:])
        final currentModel = new MappingMethodModel(Mock(ExecutableElement), Mock(TypeMirror), Mock(TypeMirror), [])
        final currentMethod = new DiscoveredMethod(currentModel, [:], [:])

        final ctx = Stub(ResolutionContext) {
            getTypes() >> types
            getMethods() >> [currentMethod, siblingMethod]
            getCurrentMethod() >> currentMethod
        }

        types.isAssignable(sourceType, sourceType) >> true
        types.isAssignable(targetType, targetType) >> true

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)

        then:
        result.isPresent()
        result.get().codeTemplate.apply(CodeBlock.of('value')).toString() == 'mapPerson(value)'
    }

    def 'excludes current method from matching'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        final execElement = Stub(ExecutableElement) { getSimpleName() >> Stub(Name) { toString() >> 'map' } }
        final model = new MappingMethodModel(execElement, sourceType, targetType, [])
        final method = new DiscoveredMethod(model, [:], [:])

        final ctx = Stub(ResolutionContext) {
            getTypes() >> types
            getMethods() >> [method]
            getCurrentMethod() >> method
        }

        types.isAssignable(_, _) >> true

        expect:
        strategy.canProduce(sourceType, targetType, ctx).isEmpty()
    }

    def 'returns empty when no method matches'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        final currentModel = new MappingMethodModel(Mock(ExecutableElement), Mock(TypeMirror), Mock(TypeMirror), [])
        final currentMethod = new DiscoveredMethod(currentModel, [:], [:])

        final ctx = Stub(ResolutionContext) {
            getTypes() >> types
            getMethods() >> [currentMethod]
            getCurrentMethod() >> currentMethod
        }

        expect:
        strategy.canProduce(sourceType, targetType, ctx).isEmpty()
    }
}

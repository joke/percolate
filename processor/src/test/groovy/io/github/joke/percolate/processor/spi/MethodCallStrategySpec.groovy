package io.github.joke.percolate.processor.spi

import com.palantir.javapoet.CodeBlock
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

@Tag('unit')
class MethodCallStrategySpec extends Specification {

    Types types = Mock()
    Elements elements = Mock()
    TypeElement mapperType = Mock()
    MethodCallStrategy strategy = new MethodCallStrategy()

    def 'returns proposal when abstract sibling method matches'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        final currentExec = Mock(ExecutableElement)
        final methodName = Stub(Name) { toString() >> 'mapPerson' }
        final param = Stub(VariableElement) { asType() >> sourceType }
        final siblingExec = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getSimpleName() >> methodName
            getParameters() >> [param]
            getReturnType() >> targetType
        }

        final ctx = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
            getMapperType() >> mapperType
            getCurrentMethod() >> currentExec
        }

        elements.getAllMembers(mapperType) >> [currentExec, siblingExec]
        types.isAssignable(sourceType, sourceType) >> true
        types.isAssignable(targetType, targetType) >> true

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)

        then:
        result.isPresent()
        result.get().codeTemplate.apply(CodeBlock.of('value')).toString() == 'mapPerson(value)'
    }

    def 'returns proposal when default method matches'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        final currentExec = Mock(ExecutableElement)
        final methodName = Stub(Name) { toString() >> 'mapAddress' }
        final param = Stub(VariableElement) { asType() >> sourceType }
        final defaultExec = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getSimpleName() >> methodName
            getParameters() >> [param]
            getReturnType() >> targetType
        }

        final ctx = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
            getMapperType() >> mapperType
            getCurrentMethod() >> currentExec
        }

        elements.getAllMembers(mapperType) >> [defaultExec]
        types.isAssignable(sourceType, sourceType) >> true
        types.isAssignable(targetType, targetType) >> true

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)

        then:
        result.isPresent()
        result.get().codeTemplate.apply(CodeBlock.of('value')).toString() == 'mapAddress(value)'
    }

    def 'returns proposal when inherited method matches'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        final currentExec = Mock(ExecutableElement)
        final methodName = Stub(Name) { toString() >> 'mapBase' }
        final param = Stub(VariableElement) { asType() >> sourceType }
        final inheritedExec = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getSimpleName() >> methodName
            getParameters() >> [param]
            getReturnType() >> targetType
        }

        final ctx = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
            getMapperType() >> mapperType
            getCurrentMethod() >> currentExec
        }

        elements.getAllMembers(mapperType) >> [inheritedExec]
        types.isAssignable(sourceType, sourceType) >> true
        types.isAssignable(targetType, targetType) >> true

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)

        then:
        result.isPresent()
        result.get().codeTemplate.apply(CodeBlock.of('value')).toString() == 'mapBase(value)'
    }

    def 'excludes current method from matching'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        final methodName = Stub(Name) { toString() >> 'map' }
        final param = Stub(VariableElement) { asType() >> sourceType }
        final currentExec = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getSimpleName() >> methodName
            getParameters() >> [param]
            getReturnType() >> targetType
        }

        final ctx = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
            getMapperType() >> mapperType
            getCurrentMethod() >> currentExec
        }

        elements.getAllMembers(mapperType) >> [currentExec]
        types.isAssignable(_, _) >> true

        expect:
        strategy.canProduce(sourceType, targetType, ctx).isEmpty()
    }

    def 'returns empty when no method matches'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        final currentExec = Mock(ExecutableElement)

        final ctx = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
            getMapperType() >> mapperType
            getCurrentMethod() >> currentExec
        }

        elements.getAllMembers(mapperType) >> []

        expect:
        strategy.canProduce(sourceType, targetType, ctx).isEmpty()
    }
}

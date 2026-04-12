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
            getUsing() >> ''
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
            getUsing() >> ''
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
            getUsing() >> ''
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
            getUsing() >> ''
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
            getUsing() >> ''
        }

        elements.getAllMembers(mapperType) >> []

        expect:
        strategy.canProduce(sourceType, targetType, ctx).isEmpty()
    }

    // 6.1 — using set filters to named method only
    def 'using filters candidates to named method'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        final currentExec = Mock(ExecutableElement)
        final param = Stub(VariableElement) { asType() >> sourceType }

        final matchingName = Stub(Name) { toString() >> 'namedMapper' }
        final otherName = Stub(Name) { toString() >> 'differentMapper' }

        final matchingExec = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getSimpleName() >> matchingName
            getParameters() >> [param]
            getReturnType() >> targetType
        }
        final otherExec = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getSimpleName() >> otherName
            getParameters() >> [param]
            getReturnType() >> targetType
        }

        final ctx = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
            getMapperType() >> mapperType
            getCurrentMethod() >> currentExec
            getUsing() >> 'namedMapper'
        }

        elements.getAllMembers(mapperType) >> [matchingExec, otherExec]
        types.isAssignable(sourceType, sourceType) >> true
        types.isAssignable(targetType, targetType) >> true

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)

        then:
        result.isPresent()
        result.get().codeTemplate.apply(CodeBlock.of('x')).toString() == 'namedMapper(x)'
    }

    // 6.2 — using not set considers all compatible methods
    def 'using not set considers all compatible methods'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        final currentExec = Mock(ExecutableElement)
        final param = Stub(VariableElement) { asType() >> sourceType }
        final methodName = Stub(Name) { toString() >> 'anyMapper' }
        final exec = Stub(ExecutableElement) {
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
            getUsing() >> ''
        }

        elements.getAllMembers(mapperType) >> [exec]
        types.isAssignable(sourceType, sourceType) >> true
        types.isAssignable(targetType, targetType) >> true

        expect:
        strategy.canProduce(sourceType, targetType, ctx).isPresent()
    }

    // 6.1 — using set, named method not present → empty
    def 'using set but named method not found returns empty'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        final currentExec = Mock(ExecutableElement)
        final param = Stub(VariableElement) { asType() >> sourceType }
        final wrongName = Stub(Name) { toString() >> 'wrongMapper' }
        final exec = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getSimpleName() >> wrongName
            getParameters() >> [param]
            getReturnType() >> targetType
        }

        final ctx = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
            getMapperType() >> mapperType
            getCurrentMethod() >> currentExec
            getUsing() >> 'namedMapper'
        }

        elements.getAllMembers(mapperType) >> [exec]
        types.isAssignable(_, _) >> true

        expect:
        strategy.canProduce(sourceType, targetType, ctx).isEmpty()
    }

    // 6.3 — most-specific-match: narrowest param type wins
    def 'most-specific-match selects narrowest param type'() {
        given:
        final baseType = Mock(TypeMirror)
        final subType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        final currentExec = Mock(ExecutableElement)

        final baseName = Stub(Name) { toString() >> 'mapBase' }
        final subName = Stub(Name) { toString() >> 'mapSub' }

        final baseParam = Stub(VariableElement) { asType() >> baseType }
        final subParam = Stub(VariableElement) { asType() >> subType }

        final baseExec = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getSimpleName() >> baseName
            getParameters() >> [baseParam]
            getReturnType() >> targetType
        }
        final subExec = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getSimpleName() >> subName
            getParameters() >> [subParam]
            getReturnType() >> targetType
        }

        final ctx = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
            getMapperType() >> mapperType
            getCurrentMethod() >> currentExec
            getUsing() >> ''
        }

        elements.getAllMembers(mapperType) >> [baseExec, subExec]
        // source is subType; both match (subType assignable to both subType and baseType)
        types.isAssignable(subType, subType) >> true
        types.isAssignable(subType, baseType) >> true
        types.isAssignable(targetType, targetType) >> true
        // subType is more specific than baseType: subType assignable to baseType, not vice versa
        types.isAssignable(subType, baseType) >> true
        types.isAssignable(baseType, subType) >> false

        when:
        final result = strategy.canProduce(subType, targetType, ctx)

        then:
        result.isPresent()
        result.get().codeTemplate.apply(CodeBlock.of('x')).toString() == 'mapSub(x)'
    }

    // 6.4 — return type tiebreaker
    def 'return type tiebreaker selects narrowest return type when param specificity is equal'() {
        given:
        final sourceType = Mock(TypeMirror)
        final baseReturn = Mock(TypeMirror)
        final subReturn = Mock(TypeMirror)
        final currentExec = Mock(ExecutableElement)

        final baseName = Stub(Name) { toString() >> 'mapToBase' }
        final subName = Stub(Name) { toString() >> 'mapToSub' }
        final param = Stub(VariableElement) { asType() >> sourceType }

        final baseExec = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getSimpleName() >> baseName
            getParameters() >> [param]
            getReturnType() >> baseReturn
        }
        final subExec = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getSimpleName() >> subName
            getParameters() >> [param]
            getReturnType() >> subReturn
        }

        final ctx = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
            getMapperType() >> mapperType
            getCurrentMethod() >> currentExec
            getUsing() >> ''
        }

        elements.getAllMembers(mapperType) >> [baseExec, subExec]
        // both params are same type — equal param specificity
        types.isAssignable(sourceType, sourceType) >> true
        // target is baseReturn; both return types are assignable to it
        types.isAssignable(subReturn, baseReturn) >> true
        types.isAssignable(baseReturn, baseReturn) >> true
        // subReturn is more specific: assignable to baseReturn, not vice versa
        types.isAssignable(subReturn, baseReturn) >> true
        types.isAssignable(baseReturn, subReturn) >> false

        when:
        final result = strategy.canProduce(sourceType, baseReturn, ctx)

        then:
        result.isPresent()
        result.get().codeTemplate.apply(CodeBlock.of('x')).toString() == 'mapToSub(x)'
    }

    // 6.5 — single candidate (no ranking needed)
    def 'single candidate is selected without ranking'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        final currentExec = Mock(ExecutableElement)
        final methodName = Stub(Name) { toString() >> 'onlyMethod' }
        final param = Stub(VariableElement) { asType() >> sourceType }
        final exec = Stub(ExecutableElement) {
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
            getUsing() >> ''
        }

        elements.getAllMembers(mapperType) >> [exec]
        types.isAssignable(sourceType, sourceType) >> true
        types.isAssignable(targetType, targetType) >> true

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)

        then:
        result.isPresent()
        result.get().codeTemplate.apply(CodeBlock.of('x')).toString() == 'onlyMethod(x)'
    }

    // 6.6 — no candidates → empty
    def 'no candidates returns empty optional'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        final currentExec = Mock(ExecutableElement)

        final ctx = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
            getMapperType() >> mapperType
            getCurrentMethod() >> currentExec
            getUsing() >> ''
        }

        elements.getAllMembers(mapperType) >> []

        expect:
        strategy.canProduce(sourceType, targetType, ctx).isEmpty()
    }

    // 6.7 — ambiguous candidates (incomparable param types) → empty
    def 'ambiguous candidates with incomparable param types returns empty'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        final currentExec = Mock(ExecutableElement)

        final nameA = Stub(Name) { toString() >> 'mapA' }
        final nameB = Stub(Name) { toString() >> 'mapB' }
        final paramA = Stub(VariableElement) { asType() >> sourceType }
        final paramB = Stub(VariableElement) { asType() >> sourceType }

        final execA = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getSimpleName() >> nameA
            getParameters() >> [paramA]
            getReturnType() >> targetType
        }
        final execB = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getSimpleName() >> nameB
            getParameters() >> [paramB]
            getReturnType() >> targetType
        }

        final ctx = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
            getMapperType() >> mapperType
            getCurrentMethod() >> currentExec
            getUsing() >> ''
        }

        elements.getAllMembers(mapperType) >> [execA, execB]
        types.isAssignable(sourceType, sourceType) >> true
        types.isAssignable(targetType, targetType) >> true

        expect:
        // both candidates have identical (incomparable) param types → ambiguous → empty
        strategy.canProduce(sourceType, targetType, ctx).isEmpty()
    }
}

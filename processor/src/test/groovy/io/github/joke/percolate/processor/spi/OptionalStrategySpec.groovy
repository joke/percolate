package io.github.joke.percolate.processor.spi

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.transform.CodeTemplate
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

@Tag('unit')
class OptionalStrategySpec extends Specification {

    Types types = Mock()
    Elements elements = Mock()

    def 'OptionalMapStrategy returns empty for non-Optional types'() {
        given:
        final sourceType = Mock(DeclaredType) { getTypeArguments() >> [Mock(TypeMirror)] }
        final targetType = Mock(DeclaredType) { getTypeArguments() >> [Mock(TypeMirror)] }
        final optionalElement = Mock(TypeElement) { asType() >> Mock(TypeMirror) }
        final ctx = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
        }

        elements.getTypeElement('java.util.Optional') >> optionalElement
        types.erasure(_) >> Mock(TypeMirror)
        types.isSameType(_, _) >> false

        expect:
        new OptionalMapStrategy().canProduce(sourceType, targetType, ctx).isEmpty()
    }

    def 'OptionalWrapStrategy returns empty when source is Optional'() {
        given:
        final erasedOptional = Mock(TypeMirror)
        final sourceType = Mock(DeclaredType)
        final targetType = Mock(DeclaredType) { getTypeArguments() >> [Mock(TypeMirror)] }
        final optionalElement = Mock(TypeElement) { asType() >> Mock(TypeMirror) }
        final ctx = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
        }

        elements.getTypeElement('java.util.Optional') >> optionalElement
        types.erasure(optionalElement.asType()) >> erasedOptional
        types.erasure(targetType) >> erasedOptional
        types.erasure(sourceType) >> erasedOptional
        types.isSameType(erasedOptional, erasedOptional) >> true

        expect:
        new OptionalWrapStrategy().canProduce(sourceType, targetType, ctx).isEmpty()
    }

    def 'OptionalUnwrapStrategy returns empty for non-Optional source'() {
        given:
        final sourceType = Mock(DeclaredType)
        final targetType = Mock(TypeMirror)
        final optionalElement = Mock(TypeElement) { asType() >> Mock(TypeMirror) }
        final ctx = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
        }

        elements.getTypeElement('java.util.Optional') >> optionalElement
        types.erasure(_) >> Mock(TypeMirror)
        types.isSameType(_, _) >> false

        expect:
        new OptionalUnwrapStrategy().canProduce(sourceType, targetType, ctx).isEmpty()
    }

    def 'OptionalWrap code template wraps value in Optional.of'() {
        given:
        final CodeTemplate template = { CodeBlock input -> CodeBlock.of('$T.of($L)', Optional, input) }

        expect:
        template.apply(CodeBlock.of('value')).toString() == 'java.util.Optional.of(value)'
    }

    def 'OptionalUnwrap code template calls get'() {
        given:
        final CodeTemplate template = { CodeBlock input -> CodeBlock.of('$L.get()', input) }

        expect:
        template.apply(CodeBlock.of('source.getName()')).toString() == 'source.getName().get()'
    }

    def 'OptionalMap template composer wraps inner template'() {
        given:
        final CodeTemplate innerTemplate = { CodeBlock input -> CodeBlock.of('mapPerson($L)', input) }
        final CodeTemplate composed = { CodeBlock input -> CodeBlock.of('$L.map(e -> $L)', input, innerTemplate.apply(CodeBlock.of('e'))) }

        expect:
        composed.apply(CodeBlock.of('source.getPerson()')).toString() == 'source.getPerson().map(e -> mapPerson(e))'
    }
}

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
class CollectionStrategySpec extends Specification {

    Types types = Mock()
    Elements elements = Mock()

    def 'StreamFromCollectionStrategy returns empty for non-DeclaredType'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        final ctx = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
        }

        expect:
        new StreamFromCollectionStrategy().canProduce(sourceType, targetType, ctx).isEmpty()
    }

    def 'CollectToListStrategy returns empty for non-List target'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(DeclaredType) { getTypeArguments() >> [Mock(TypeMirror)] }
        final setElement = Mock(TypeElement) { asType() >> Mock(TypeMirror) }
        final listElement = Mock(TypeElement) { asType() >> Mock(TypeMirror) }
        final ctx = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
        }

        elements.getTypeElement('java.util.List') >> listElement
        types.erasure(targetType) >> Mock(TypeMirror)
        types.erasure(listElement.asType()) >> Mock(TypeMirror)
        types.isSameType(_, _) >> false

        expect:
        new CollectToListStrategy().canProduce(sourceType, targetType, ctx).isEmpty()
    }

    def 'CollectToSetStrategy returns empty for non-Set target'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(DeclaredType) { getTypeArguments() >> [Mock(TypeMirror)] }
        final setElement = Mock(TypeElement) { asType() >> Mock(TypeMirror) }
        final ctx = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
        }

        elements.getTypeElement('java.util.Set') >> setElement
        types.erasure(targetType) >> Mock(TypeMirror)
        types.erasure(setElement.asType()) >> Mock(TypeMirror)
        types.isSameType(_, _) >> false

        expect:
        new CollectToSetStrategy().canProduce(sourceType, targetType, ctx).isEmpty()
    }

    def 'CollectToListStrategy code template produces collect expression'() {
        given:
        final CodeTemplate template = { CodeBlock input -> CodeBlock.of('$L.collect($T.toList())', input, java.util.stream.Collectors) }

        expect:
        template.apply(CodeBlock.of('items.stream()')).toString() == 'items.stream().collect(java.util.stream.Collectors.toList())'
    }

    def 'CollectToSetStrategy code template produces collect expression'() {
        given:
        final CodeTemplate template = { CodeBlock input -> CodeBlock.of('$L.collect($T.toSet())', input, java.util.stream.Collectors) }

        expect:
        template.apply(CodeBlock.of('items.stream()')).toString() == 'items.stream().collect(java.util.stream.Collectors.toSet())'
    }

    def 'StreamMapStrategy returns empty for non-Stream types'() {
        given:
        final sourceType = Mock(DeclaredType) { getTypeArguments() >> [Mock(TypeMirror)] }
        final targetType = Mock(DeclaredType) { getTypeArguments() >> [Mock(TypeMirror)] }
        final streamElement = Mock(TypeElement) { asType() >> Mock(TypeMirror) }
        final ctx = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
        }

        elements.getTypeElement('java.util.stream.Stream') >> streamElement
        types.erasure(_) >> Mock(TypeMirror)
        types.isSameType(_, _) >> false

        expect:
        new StreamMapStrategy().canProduce(sourceType, targetType, ctx).isEmpty()
    }

    def 'StreamMapStrategy template composer wraps inner template in map call'() {
        given:
        final CodeTemplate innerTemplate = { CodeBlock input -> CodeBlock.of('mapPerson($L)', input) }
        final CodeTemplate composed = { CodeBlock input -> CodeBlock.of('$L.map(e -> $L)', input, innerTemplate.apply(CodeBlock.of('e'))) }

        expect:
        composed.apply(CodeBlock.of('stream')).toString() == 'stream.map(e -> mapPerson(e))'
    }
}

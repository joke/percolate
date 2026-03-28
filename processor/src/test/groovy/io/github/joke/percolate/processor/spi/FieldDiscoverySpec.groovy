package io.github.joke.percolate.processor.spi

import io.github.joke.percolate.processor.model.FieldReadAccessor
import io.github.joke.percolate.processor.model.FieldWriteAccessor
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ElementKind
import javax.lang.model.element.Modifier
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

@Tag('unit')
class FieldDiscoverySpec extends Specification {

    Elements elements = Mock()
    Types types = Mock()

    def 'source priority is 50'() {
        expect:
        new FieldDiscovery.Source().priority() == 50
    }

    def 'target priority is 50'() {
        expect:
        new FieldDiscovery.Target().priority() == 50
    }

    def 'source discovers public fields as ReadAccessor'() {
        given:
        def fieldType = Mock(TypeMirror)
        def fieldName = Stub(Name) { toString() >> 'firstName' }
        def field = Stub(VariableElement) {
            getKind() >> ElementKind.FIELD
            getModifiers() >> ([Modifier.PUBLIC] as Set)
            getSimpleName() >> fieldName
            asType() >> fieldType
        }
        def typeElement = Stub(TypeElement) { getEnclosedElements() >> [field] }
        def type = Stub(DeclaredType) { asElement() >> typeElement }

        when:
        def result = new FieldDiscovery.Source().discover(type, elements, types)

        then:
        result.size() == 1
        result[0] instanceof FieldReadAccessor
        result[0].name() == 'firstName'
    }

    def 'target discovers public fields as WriteAccessor'() {
        given:
        def fieldType = Mock(TypeMirror)
        def fieldName = Stub(Name) { toString() >> 'firstName' }
        def field = Stub(VariableElement) {
            getKind() >> ElementKind.FIELD
            getModifiers() >> ([Modifier.PUBLIC] as Set)
            getSimpleName() >> fieldName
            asType() >> fieldType
        }
        def typeElement = Stub(TypeElement) { getEnclosedElements() >> [field] }
        def type = Stub(DeclaredType) { asElement() >> typeElement }

        when:
        def result = new FieldDiscovery.Target().discover(type, elements, types)

        then:
        result.size() == 1
        result[0] instanceof FieldWriteAccessor
        result[0].name() == 'firstName'
    }

    def 'ignores private fields'() {
        given:
        def fieldName = Stub(Name) { toString() >> 'secret' }
        def field = Stub(VariableElement) {
            getKind() >> ElementKind.FIELD
            getModifiers() >> ([Modifier.PRIVATE] as Set)
            getSimpleName() >> fieldName
            asType() >> Mock(TypeMirror)
        }
        def typeElement = Stub(TypeElement) { getEnclosedElements() >> [field] }
        def type = Stub(DeclaredType) { asElement() >> typeElement }

        when:
        def result = new FieldDiscovery.Source().discover(type, elements, types)

        then:
        result.isEmpty()
    }

    def 'ignores static fields'() {
        given:
        def fieldName = Stub(Name) { toString() >> 'CONSTANT' }
        def field = Stub(VariableElement) {
            getKind() >> ElementKind.FIELD
            getModifiers() >> ([Modifier.PUBLIC, Modifier.STATIC] as Set)
            getSimpleName() >> fieldName
            asType() >> Mock(TypeMirror)
        }
        def typeElement = Stub(TypeElement) { getEnclosedElements() >> [field] }
        def type = Stub(DeclaredType) { asElement() >> typeElement }

        when:
        def result = new FieldDiscovery.Source().discover(type, elements, types)

        then:
        result.isEmpty()
    }
}

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
        final fieldType = Mock(TypeMirror)
        final fieldName = Stub(Name) { toString() >> 'firstName' }
        final field = Stub(VariableElement) {
            kind >> ElementKind.FIELD
            modifiers >> ([Modifier.PUBLIC] as Set)
            simpleName >> fieldName
            asType() >> fieldType
        }
        final typeElement = Stub(TypeElement) { enclosedElements >> [field] }
        final type = Stub(DeclaredType) { asElement() >> typeElement }

        expect:
        final result = new FieldDiscovery.Source().discover(type, elements, types)
        result.size() == 1
        result[0] instanceof FieldReadAccessor
        result[0].name == 'firstName'
    }

    def 'target discovers public fields as WriteAccessor'() {
        given:
        final fieldType = Mock(TypeMirror)
        final fieldName = Stub(Name) { toString() >> 'firstName' }
        final field = Stub(VariableElement) {
            kind >> ElementKind.FIELD
            modifiers >> ([Modifier.PUBLIC] as Set)
            simpleName >> fieldName
            asType() >> fieldType
        }
        final typeElement = Stub(TypeElement) { enclosedElements >> [field] }
        final type = Stub(DeclaredType) { asElement() >> typeElement }

        expect:
        final result = new FieldDiscovery.Target().discover(type, elements, types)
        result.size() == 1
        result[0] instanceof FieldWriteAccessor
        result[0].name == 'firstName'
    }

    def 'ignores private fields'() {
        given:
        final fieldName = Stub(Name) { toString() >> 'secret' }
        final field = Stub(VariableElement) {
            kind >> ElementKind.FIELD
            modifiers >> ([Modifier.PRIVATE] as Set)
            simpleName >> fieldName
            asType() >> Mock(TypeMirror)
        }
        final typeElement = Stub(TypeElement) { enclosedElements >> [field] }
        final type = Stub(DeclaredType) { asElement() >> typeElement }

        expect:
        final result = new FieldDiscovery.Source().discover(type, elements, types)
        result.isEmpty()
    }

    def 'ignores static fields'() {
        given:
        final fieldName = Stub(Name) { toString() >> 'CONSTANT' }
        final field = Stub(VariableElement) {
            kind >> ElementKind.FIELD
            modifiers >> ([Modifier.PUBLIC, Modifier.STATIC] as Set)
            simpleName >> fieldName
            asType() >> Mock(TypeMirror)
        }
        final typeElement = Stub(TypeElement) { enclosedElements >> [field] }
        final type = Stub(DeclaredType) { asElement() >> typeElement }

        expect:
        final result = new FieldDiscovery.Source().discover(type, elements, types)
        result.isEmpty()
    }
}

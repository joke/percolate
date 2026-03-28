package io.github.joke.percolate.processor.spi

import io.github.joke.percolate.processor.model.GetterAccessor
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

@Tag('unit')
class GetterDiscoverySpec extends Specification {

    GetterDiscovery discovery = new GetterDiscovery()
    Elements elements = Mock()
    Types types = Mock()

    def 'priority is 100'() {
        expect:
        discovery.priority() == 100
    }

    def 'discovers getter method getFirstName()'() {
        given:
        final returnType = Mock(TypeMirror)
        final name = Stub(Name) { toString() >> 'getFirstName' }
        final method = Stub(ExecutableElement) {
            kind >> ElementKind.METHOD
            modifiers >> ([Modifier.PUBLIC] as Set)
            parameters >> []
            simpleName >> name
            getReturnType() >> returnType
        }
        final typeElement = Stub(TypeElement) { enclosedElements >> [method] }
        final type = Stub(DeclaredType) { asElement() >> typeElement }

        expect:
        final result = discovery.discover(type, elements, types)
        result.size() == 1
        result[0] instanceof GetterAccessor
        result[0].name == 'firstName'
        result[0].type == returnType
    }

    def 'discovers boolean getter isActive()'() {
        given:
        final returnType = Mock(TypeMirror)
        final name = Stub(Name) { toString() >> 'isActive' }
        final method = Stub(ExecutableElement) {
            kind >> ElementKind.METHOD
            modifiers >> ([Modifier.PUBLIC] as Set)
            parameters >> []
            simpleName >> name
            getReturnType() >> returnType
        }
        final typeElement = Stub(TypeElement) { enclosedElements >> [method] }
        final type = Stub(DeclaredType) { asElement() >> typeElement }

        expect:
        final result = discovery.discover(type, elements, types)
        result.size() == 1
        result[0].name == 'active'
    }

    def 'ignores non-getter methods'() {
        given:
        final name = Stub(Name) { toString() >> 'doSomething' }
        final method = Stub(ExecutableElement) {
            kind >> ElementKind.METHOD
            modifiers >> ([Modifier.PUBLIC] as Set)
            parameters >> []
            simpleName >> name
            returnType >> Mock(TypeMirror)
        }
        final typeElement = Stub(TypeElement) { enclosedElements >> [method] }
        final type = Stub(DeclaredType) { asElement() >> typeElement }

        expect:
        final result = discovery.discover(type, elements, types)
        result.isEmpty()
    }

    def 'ignores methods with parameters'() {
        given:
        final name = Stub(Name) { toString() >> 'getFirstName' }
        final method = Stub(ExecutableElement) {
            kind >> ElementKind.METHOD
            modifiers >> ([Modifier.PUBLIC] as Set)
            parameters >> [Mock(Element)]
            simpleName >> name
            returnType >> Mock(TypeMirror)
        }
        final typeElement = Stub(TypeElement) { enclosedElements >> [method] }
        final type = Stub(DeclaredType) { asElement() >> typeElement }

        expect:
        final result = discovery.discover(type, elements, types)
        result.isEmpty()
    }

    def 'ignores non-public methods'() {
        given:
        final name = Stub(Name) { toString() >> 'getFirstName' }
        final method = Stub(ExecutableElement) {
            kind >> ElementKind.METHOD
            modifiers >> ([Modifier.PRIVATE] as Set)
            parameters >> []
            simpleName >> name
            returnType >> Mock(TypeMirror)
        }
        final typeElement = Stub(TypeElement) { enclosedElements >> [method] }
        final type = Stub(DeclaredType) { asElement() >> typeElement }

        expect:
        final result = discovery.discover(type, elements, types)
        result.isEmpty()
    }
}

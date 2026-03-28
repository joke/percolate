package io.github.joke.percolate.processor.spi

import io.github.joke.percolate.processor.model.ConstructorParamAccessor
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

@Tag('unit')
class ConstructorDiscoverySpec extends Specification {

    ConstructorDiscovery discovery = new ConstructorDiscovery()
    Elements elements = Mock()
    Types types = Mock()

    def 'priority is 100'() {
        expect:
        discovery.priority() == 100
    }

    def 'discovers constructor parameters'() {
        given:
        final paramType = Mock(TypeMirror)
        final paramName = Stub(Name) { toString() >> 'givenName' }
        final param = Stub(VariableElement) {
            simpleName >> paramName
            asType() >> paramType
        }
        final constructor = Stub(ExecutableElement) {
            kind >> ElementKind.CONSTRUCTOR
            parameters >> [param]
        }
        final typeElement = Stub(TypeElement) { enclosedElements >> [constructor] }
        final type = Stub(DeclaredType) { asElement() >> typeElement }

        expect:
        final result = discovery.discover(type, elements, types)
        result.size() == 1
        result[0] instanceof ConstructorParamAccessor
        result[0].name() == 'givenName'
        result[0].type() == paramType
        (result[0] as ConstructorParamAccessor).paramIndex() == 0
    }

    def 'uses constructor with most parameters'() {
        given:
        final smallCtor = Stub(ExecutableElement) {
            kind >> ElementKind.CONSTRUCTOR
            parameters >> []
        }
        final nameA = Stub(Name) { toString() >> 'a' }
        final nameB = Stub(Name) { toString() >> 'b' }
        final largeCtor = Stub(ExecutableElement) {
            kind >> ElementKind.CONSTRUCTOR
            parameters >> [
                Stub(VariableElement) { simpleName >> nameA; asType() >> Mock(TypeMirror) },
                Stub(VariableElement) { simpleName >> nameB; asType() >> Mock(TypeMirror) }
            ]
        }
        final typeElement = Stub(TypeElement) { enclosedElements >> [smallCtor, largeCtor] }
        final type = Stub(DeclaredType) { asElement() >> typeElement }

        expect:
        final result = discovery.discover(type, elements, types)
        result.size() == 2
    }

    def 'returns empty for type without constructors'() {
        given:
        final typeElement = Stub(TypeElement) { enclosedElements >> [] }
        final type = Stub(DeclaredType) { asElement() >> typeElement }

        expect:
        final result = discovery.discover(type, elements, types)
        result.isEmpty()
    }
}

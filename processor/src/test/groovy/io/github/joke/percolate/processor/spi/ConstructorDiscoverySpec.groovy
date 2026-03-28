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
        def paramType = Mock(TypeMirror)
        def paramName = Stub(Name) { toString() >> 'givenName' }
        def param = Stub(VariableElement) {
            getSimpleName() >> paramName
            asType() >> paramType
        }
        def constructor = Stub(ExecutableElement) {
            getKind() >> ElementKind.CONSTRUCTOR
            getParameters() >> [param]
        }
        def typeElement = Stub(TypeElement) { getEnclosedElements() >> [constructor] }
        def type = Stub(DeclaredType) { asElement() >> typeElement }

        when:
        def result = discovery.discover(type, elements, types)

        then:
        result.size() == 1
        result[0] instanceof ConstructorParamAccessor
        result[0].name() == 'givenName'
        result[0].type() == paramType
        (result[0] as ConstructorParamAccessor).paramIndex() == 0
    }

    def 'uses constructor with most parameters'() {
        given:
        def smallCtor = Stub(ExecutableElement) {
            getKind() >> ElementKind.CONSTRUCTOR
            getParameters() >> []
        }
        def nameA = Stub(Name) { toString() >> 'a' }
        def nameB = Stub(Name) { toString() >> 'b' }
        def largeCtor = Stub(ExecutableElement) {
            getKind() >> ElementKind.CONSTRUCTOR
            getParameters() >> [
                Stub(VariableElement) { getSimpleName() >> nameA; asType() >> Mock(TypeMirror) },
                Stub(VariableElement) { getSimpleName() >> nameB; asType() >> Mock(TypeMirror) }
            ]
        }
        def typeElement = Stub(TypeElement) { getEnclosedElements() >> [smallCtor, largeCtor] }
        def type = Stub(DeclaredType) { asElement() >> typeElement }

        when:
        def result = discovery.discover(type, elements, types)

        then:
        result.size() == 2
    }

    def 'returns empty for type without constructors'() {
        given:
        def typeElement = Stub(TypeElement) { getEnclosedElements() >> [] }
        def type = Stub(DeclaredType) { asElement() >> typeElement }

        when:
        def result = discovery.discover(type, elements, types)

        then:
        result.isEmpty()
    }
}

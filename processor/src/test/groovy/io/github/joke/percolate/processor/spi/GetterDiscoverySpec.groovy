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
        def returnType = Mock(TypeMirror)
        def name = Stub(Name) { toString() >> 'getFirstName' }
        def method = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getModifiers() >> ([Modifier.PUBLIC] as Set)
            getParameters() >> []
            getSimpleName() >> name
            getReturnType() >> returnType
        }
        def typeElement = Stub(TypeElement) { getEnclosedElements() >> [method] }
        def type = Stub(DeclaredType) { asElement() >> typeElement }

        when:
        def result = discovery.discover(type, elements, types)

        then:
        result.size() == 1
        result[0] instanceof GetterAccessor
        result[0].name() == 'firstName'
        result[0].type() == returnType
    }

    def 'discovers boolean getter isActive()'() {
        given:
        def returnType = Mock(TypeMirror)
        def name = Stub(Name) { toString() >> 'isActive' }
        def method = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getModifiers() >> ([Modifier.PUBLIC] as Set)
            getParameters() >> []
            getSimpleName() >> name
            getReturnType() >> returnType
        }
        def typeElement = Stub(TypeElement) { getEnclosedElements() >> [method] }
        def type = Stub(DeclaredType) { asElement() >> typeElement }

        when:
        def result = discovery.discover(type, elements, types)

        then:
        result.size() == 1
        result[0].name() == 'active'
    }

    def 'ignores non-getter methods'() {
        given:
        def name = Stub(Name) { toString() >> 'doSomething' }
        def method = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getModifiers() >> ([Modifier.PUBLIC] as Set)
            getParameters() >> []
            getSimpleName() >> name
            getReturnType() >> Mock(TypeMirror)
        }
        def typeElement = Stub(TypeElement) { getEnclosedElements() >> [method] }
        def type = Stub(DeclaredType) { asElement() >> typeElement }

        when:
        def result = discovery.discover(type, elements, types)

        then:
        result.isEmpty()
    }

    def 'ignores methods with parameters'() {
        given:
        def name = Stub(Name) { toString() >> 'getFirstName' }
        def method = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getModifiers() >> ([Modifier.PUBLIC] as Set)
            getParameters() >> [Mock(Element)]
            getSimpleName() >> name
            getReturnType() >> Mock(TypeMirror)
        }
        def typeElement = Stub(TypeElement) { getEnclosedElements() >> [method] }
        def type = Stub(DeclaredType) { asElement() >> typeElement }

        when:
        def result = discovery.discover(type, elements, types)

        then:
        result.isEmpty()
    }

    def 'ignores non-public methods'() {
        given:
        def name = Stub(Name) { toString() >> 'getFirstName' }
        def method = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getModifiers() >> ([Modifier.PRIVATE] as Set)
            getParameters() >> []
            getSimpleName() >> name
            getReturnType() >> Mock(TypeMirror)
        }
        def typeElement = Stub(TypeElement) { getEnclosedElements() >> [method] }
        def type = Stub(DeclaredType) { asElement() >> typeElement }

        when:
        def result = discovery.discover(type, elements, types)

        then:
        result.isEmpty()
    }
}

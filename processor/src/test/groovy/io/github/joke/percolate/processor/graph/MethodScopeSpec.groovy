package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.VariableElement

@Tag('unit')
class MethodScopeSpec extends Specification {

    Name name(String value) {
        def n = Mock(Name)
        n.toString() >> value
        n
    }

    VariableElement param(String paramName, String type) {
        def p = Mock(VariableElement)
        p.getSimpleName() >> this.name(paramName)
        def t = Mock(javax.lang.model.type.TypeMirror)
        t.toString() >> type
        p.asType() >> t
        p
    }

    def 'encode returns method name with parameter types'() {
        given:
        def method = Mock(ExecutableElement)
        method.getSimpleName() >> name('map')
        method.getParameters() >> [param('person', 'Person')]

        when:
        def scope = new MethodScope(method)

        then:
        scope.encode() == 'map(Person)'
    }

    def 'encode handles multiple parameters'() {
        given:
        def method = Mock(ExecutableElement)
        method.getSimpleName() >> name('combine')
        method.getParameters() >> [param('bar', 'Bar'), param('baz', 'Baz')]

        when:
        def scope = new MethodScope(method)

        then:
        scope.encode() == 'combine(Bar,Baz)'
    }

    def 'encode handles no parameters'() {
        given:
        def method = Mock(ExecutableElement)
        method.getSimpleName() >> name('create')
        method.getParameters() >> []

        when:
        def scope = new MethodScope(method)

        then:
        scope.encode() == 'create()'
    }

    def 'encode is deterministic for the same method'() {
        given:
        def method = Mock(ExecutableElement)
        method.getSimpleName() >> name('map')
        method.getParameters() >> [param('person', 'Person')]

        when:
        def s1 = new MethodScope(method)
        def s2 = new MethodScope(method)

        then:
        s1.encode() == s2.encode()
    }

    def 'encode uses full type name for parameter types'() {
        given:
        def method = Mock(ExecutableElement)
        method.getSimpleName() >> name('map')
        method.getParameters() >> [param('input', 'java.lang.String')]

        when:
        def scope = new MethodScope(method)

        then:
        scope.encode() == 'map(java.lang.String)'
    }
}

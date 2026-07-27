package io.github.joke.percolate.processor.internal.graph

import io.github.joke.percolate.spi.Nullability
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.type.TypeMirror

/**
 * {@link MethodScope} unit-tested mock-only: its identity is {@code method} alone (design D5 of change
 * {@code decouple-engine-from-strategy-semantics}), so two instances for the same method are equal regardless of
 * their declarations, and a bare {@code new MethodScope(method)} is a valid map-key-only scope carrying no
 * declarations. {@code inputDecls()} is a plain accessor over whatever {@link InputDecl} list the caller supplied
 * — the annotation-reading rule that decides a parameter's name/visibility lives in
 * {@code internal.stages.discover.AmbientAnnotations}, exercised on its own.
 */
@Tag('unit')
class MethodScopeSpec extends Specification {

    ExecutableElement method = Mock()
    TypeMirror personType = Mock()

    def 'two instances for the same method are equal regardless of their declarations'() {
        def decl = new InputDecl(new SourceLocation(AccessPath.of('customer')), personType, Nullability.NON_NULL,
                'customer', Visibility.LOCAL)

        expect:
        new MethodScope(method) == new MethodScope(method, [decl])
        new MethodScope(method).hashCode() == new MethodScope(method, [decl]).hashCode()
    }

    def 'a bare MethodScope carries no input declarations'() {
        expect:
        new MethodScope(method).inputDecls().toList().empty
    }

    def 'inputDecls streams exactly the supplied declarations, in order'() {
        def first = new InputDecl(new SourceLocation(AccessPath.of('a')), personType, Nullability.NON_NULL, 'a', Visibility.LOCAL)
        def second = new InputDecl(new SourceLocation(AccessPath.of('b')), personType, Nullability.NON_NULL, 'b', Visibility.INHERITED)
        MethodScope scope = new MethodScope(method, [first, second])

        expect:
        scope.inputDecls().toList() == [first, second]
    }

    def 'parent is the mapper-root scope'() {
        expect:
        new MethodScope(method).parent() == Optional.of(MapperScope.INSTANCE)
    }

    def 'encode derives a stable string from the method name and parameter type strings'() {
        TypeMirror paramType = Stub(TypeMirror) { toString() >> 'Person' }
        method.simpleName >> Stub(javax.lang.model.element.Name) { toString() >> 'map' }
        method.parameters >> [Stub(javax.lang.model.element.VariableElement) { asType() >> paramType }]

        expect:
        new MethodScope(method).encode() == 'map(Person)'
    }
}

package io.github.joke.percolate.processor.internal.graph

import io.github.joke.percolate.Ambient
import io.github.joke.percolate.spi.Nullability
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror

/**
 * {@link MethodScope#ambientDecls} unit-tested over plain {@code javax.lang.model} mocks: one entry per
 * {@code @Ambient} parameter, keyed by {@link Ambient#value()} or the parameter's own simple name when unset, at
 * the same single-segment location an ordinary {@code @Map} source would resolve to for that parameter.
 */
@Tag('unit')
class MethodScopeSpec extends Specification {

    TypeMirror personType = Mock()
    TypeMirror orderType = Mock()

    def 'a parameter with no @Ambient annotation publishes no entry'() {
        MethodScope scope = new MethodScope(methodWith(plainParam('customer', personType)))

        expect:
        scope.ambientDecls { t, e -> Nullability.NON_NULL }.toList().empty
    }

    def 'an unqualified @Ambient parameter is keyed by its own simple name'() {
        MethodScope scope = new MethodScope(methodWith(ambientParam('order', orderType, '')))
        def decls = scope.ambientDecls { t, e -> Nullability.NON_NULL }.toList()

        expect:
        decls.size() == 1
        decls[0].key == 'order'
        decls[0].location == new SourceLocation(AccessPath.of('order'))
        decls[0].type.is(orderType)
        decls[0].nullness == Nullability.NON_NULL
    }

    def 'an explicit @Ambient value overrides the key but not the location'() {
        MethodScope scope = new MethodScope(methodWith(ambientParam('p', orderType, 'simon')))
        def decls = scope.ambientDecls { t, e -> Nullability.NON_NULL }.toList()

        expect:
        decls.size() == 1
        decls[0].key == 'simon'
        decls[0].location == new SourceLocation(AccessPath.of('p'))
    }

    def 'a mix of plain and ambient parameters yields one entry per ambient parameter, in declaration order'() {
        MethodScope scope = new MethodScope(methodWith(
                plainParam('customer', personType), ambientParam('order', orderType, ''), plainParam('other', personType)))
        def decls = scope.ambientDecls { t, e -> Nullability.NON_NULL }.toList()

        expect:
        decls.size() == 1
        decls[0].key == 'order'
    }

    private ExecutableElement methodWith(final VariableElement... parameters) {
        Mock(ExecutableElement) {
            getParameters() >> (parameters as List)
        }
    }

    private VariableElement plainParam(final String paramName, final TypeMirror type) {
        Mock(VariableElement) {
            getAnnotation(Ambient) >> null
            getSimpleName() >> name(paramName)
            asType() >> type
        }
    }

    private VariableElement ambientParam(final String paramName, final TypeMirror type, final String keyOverride) {
        Mock(VariableElement) {
            getAnnotation(Ambient) >> Mock(Ambient) { value() >> keyOverride }
            getSimpleName() >> name(paramName)
            asType() >> type
        }
    }

    private Name name(final String value) {
        Stub(Name) { toString() >> value }
    }
}

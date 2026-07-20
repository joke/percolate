package io.github.joke.percolate.spi

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link PortType} is the pseudo-sealed structural shape a type-variable {@link Port} template takes: a fully-known
 * {@link PortType.Concrete} leaf, an unbound {@link PortType.Var} slot, or a {@link PortType.App} application over
 * ordered argument shapes. Each leaf is a Lombok {@code @Value}, so equality is structural. Unit-tested over opaque
 * {@link TypeMirror}/{@link TypeElement} tokens; no javac.
 */
@Tag('unit')
class PortTypeSpec extends Specification {

    TypeMirror type = Mock()
    TypeElement erasure = Mock()

    def 'concrete wraps the given type'() {
        expect:
        PortType.concrete(type) == new PortType.Concrete(type)
    }

    def 'variable wraps the given index'() {
        expect:
        PortType.variable(3) == new PortType.Var(3)
    }

    def 'app wraps the erasure over a defensive copy of the argument shapes'() {
        def args = [PortType.variable(0), PortType.concrete(type)]

        when:
        def app = PortType.app(erasure, args)
        args.clear()

        then:
        app == new PortType.App(erasure, [PortType.variable(0), PortType.concrete(type)])
        app.args.size() == 2
    }

    def 'two Concrete instances over the same type are equal; over different types are not'() {
        TypeMirror otherType = Mock()

        expect:
        PortType.concrete(type) == PortType.concrete(type)
        PortType.concrete(type) != PortType.concrete(otherType)
    }

    def 'two Var instances at the same index are equal; at different indexes are not'() {
        expect:
        PortType.variable(1) == PortType.variable(1)
        PortType.variable(1) != PortType.variable(2)
    }

    def 'two App instances over the same erasure and args are equal; over different args are not'() {
        expect:
        PortType.app(erasure, [PortType.variable(0)]) == PortType.app(erasure, [PortType.variable(0)])
        PortType.app(erasure, [PortType.variable(0)]) != PortType.app(erasure, [PortType.variable(1)])
    }
}

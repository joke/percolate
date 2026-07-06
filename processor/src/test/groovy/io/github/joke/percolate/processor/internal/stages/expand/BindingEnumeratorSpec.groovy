package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.PortType
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link BindingEnumerator} unit-tested by mocking the injected {@link Unifier} — zero {@code ResolveCtx} — since its
 * own {@code assign} recursion is over the port index and match set, not self-recursion into its own type structure
 * (design D4/D5 of change {@code decompose-engine-stages}), so no {@code Spy} is needed.
 */
@Tag('unit')
@SuppressWarnings('UnnecessaryGetter') // { it.empty } mismatches these Map-typed interaction args; keep isEmpty()
class BindingEnumeratorSpec extends Specification {

    Unifier unifier = Mock()
    TypeMirror sourceA = Mock()
    TypeMirror sourceB = Mock()

    def 'enumerate with no template ports yields exactly one empty binding, for any source set'() {
        BindingEnumerator enumerator = new BindingEnumerator(unifier)

        when:
        def bindings = enumerator.enumerate([], [sourceA, sourceB])

        then:
        0 * unifier._

        expect:
        bindings == [[:]]
    }

    def 'enumerate yields one binding per source that unifies, dropping the rest'() {
        BindingEnumerator enumerator = new BindingEnumerator(unifier)
        def port = new Port('p', Mock(TypeElement).asType(), Nullability.NON_NULL, PortType.variable(0))

        when:
        def bindings = enumerator.enumerate([port], [sourceA, sourceB])

        then:
        1 * unifier.unify(port.template, sourceA, { it.isEmpty() }, 0) >> true
        1 * unifier.unify(port.template, sourceB, { it.isEmpty() }, 0) >> false
        0 * _

        expect:
        bindings.size() == 1
    }

    def 'enumerate extends only the trials that unify to the next port, forming the cross product'() {
        BindingEnumerator enumerator = new BindingEnumerator(unifier)
        def port0 = new Port('a', Mock(TypeElement).asType(), Nullability.NON_NULL, PortType.variable(0))
        def port1 = new Port('b', Mock(TypeElement).asType(), Nullability.NON_NULL, PortType.variable(1))

        when:
        def bindings = enumerator.enumerate([port0, port1], [sourceA, sourceB])

        then:
        1 * unifier.unify(port0.template, sourceA, { it.isEmpty() }, 0) >> true
        1 * unifier.unify(port0.template, sourceB, { it.isEmpty() }, 0) >> false
        1 * unifier.unify(port1.template, sourceA, { it.isEmpty() }, 0) >> true
        1 * unifier.unify(port1.template, sourceB, { it.isEmpty() }, 0) >> true
        0 * _

        expect:
        bindings.size() == 2
    }
}

package io.github.joke.percolate.spi

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

/**
 * {@link ChildScopeSpec} is a plain {@code @Value} data carrier for a scope-owning operation's child-scope
 * declaration: the four-argument constructor for a fully-known (concrete) child, and the
 * {@link ChildScopeSpec#lifted} factory for a functor-lift child whose element-in is a type-variable template
 * grounded later by the engine (the {@code elementIn} field is seeded with the concrete {@code elementOut} as a
 * representative placeholder until then). Unit-tested over opaque {@link TypeMirror} tokens and a real
 * {@link PortType}; no javac.
 */
@Tag('unit')
class ChildScopeSpecSpec extends Specification {

    TypeMirror elementIn = Mock()
    TypeMirror elementOut = Mock()
    PortType elementInTemplate = PortType.variable(0)

    def 'the concrete constructor carries both element types and nullnesses, with no templates'() {
        when:
        def spec = new ChildScopeSpec(elementIn, Nullability.NON_NULL, elementOut, Nullability.NULLABLE)

        then:
        spec.elementIn.is(elementIn)
        spec.elementInNullness == Nullability.NON_NULL
        spec.elementOut.is(elementOut)
        spec.elementOutNullness == Nullability.NULLABLE
        spec.elementInTemplate == null
        spec.elementOutTemplate == null
    }

    def 'lifted seeds elementIn with the concrete elementOut and carries the elementInTemplate'() {
        when:
        def spec = ChildScopeSpec.lifted(elementInTemplate, Nullability.NON_NULL, elementOut, Nullability.NULLABLE)

        then:
        spec.elementIn.is(elementOut)
        spec.elementInNullness == Nullability.NON_NULL
        spec.elementOut.is(elementOut)
        spec.elementOutNullness == Nullability.NULLABLE
        spec.elementInTemplate.is(elementInTemplate)
        spec.elementOutTemplate == null
    }
}

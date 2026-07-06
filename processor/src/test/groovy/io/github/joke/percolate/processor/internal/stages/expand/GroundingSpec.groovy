package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.spi.Codegen
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationSpec
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.PortType
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

/**
 * {@link Grounding} unit-tested as a pure orchestrator (design D1/D4 of change {@code decompose-engine-stages}):
 * widen &rarr; enumerate &rarr; instantiate, each collaborator mocked. The unification/substitution mechanics
 * themselves are covered by {@link SourceWidenerSpec}, {@link UnifierSpec}, {@link BindingEnumeratorSpec}, and
 * {@link SpecInstantiatorSpec}.
 */
@Tag('unit')
class GroundingSpec extends Specification {

    SourceWidener widener = Mock()
    BindingEnumerator enumerator = Mock()
    SpecInstantiator instantiator = Mock()
    Grounding grounding = new Grounding(widener, enumerator, instantiator)

    Codegen codegen = Mock()
    TypeMirror sourceA = Mock()
    TypeMirror concreteType = Mock()

    def 'a spec with no type-variable port passes through unchanged, touching no collaborator'() {
        def spec = OperationSpec.of('copy', codegen, 1,
                [new Port('src', concreteType, Nullability.NON_NULL)], concreteType, Nullability.NON_NULL)

        when:
        def result = grounding.ground(spec, [sourceA]).toList()

        then:
        0 * widener._
        0 * enumerator._
        0 * instantiator._

        expect:
        result == [spec]
    }

    def 'grounds a template spec by widening, enumerating, then instantiating one spec per binding'() {
        def port = new Port('src', concreteType, Nullability.NON_NULL, PortType.variable(0))
        def spec = OperationSpec.of('lift', codegen, 1, [port], concreteType, Nullability.NON_NULL)
        def widened = [sourceA]
        def binding0 = [0: concreteType]
        def binding1 = [0: sourceA]
        def grounded0 = OperationSpec.of('lift0', codegen, 1, [], concreteType, Nullability.NON_NULL)
        def grounded1 = OperationSpec.of('lift1', codegen, 1, [], concreteType, Nullability.NON_NULL)

        when:
        def result = grounding.ground(spec, [sourceA]).toList()

        then:
        1 * widener.widen([sourceA]) >> widened
        1 * enumerator.enumerate([port], widened) >> [binding0, binding1]
        1 * instantiator.instantiate(spec, binding0) >> grounded0
        1 * instantiator.instantiate(spec, binding1) >> grounded1
        0 * _

        expect:
        result == [grounded0, grounded1]
    }

    def 'only the template ports are handed to the enumerator, alongside the widened sources'() {
        def templatePort = new Port('a', concreteType, Nullability.NON_NULL, PortType.variable(0))
        def concretePort = new Port('b', concreteType, Nullability.NON_NULL)
        def spec = OperationSpec.of('merge', codegen, 1, [templatePort, concretePort], concreteType,
                Nullability.NON_NULL)

        when:
        grounding.ground(spec, [sourceA]).toList()

        then:
        1 * widener.widen([sourceA]) >> [sourceA]
        1 * enumerator.enumerate([templatePort], [sourceA]) >> []
        0 * _
    }

    def 'no consistent binding yields no grounded specs, without ever instantiating'() {
        def port = new Port('src', concreteType, Nullability.NON_NULL, PortType.variable(0))
        def spec = OperationSpec.of('lift', codegen, 1, [port], concreteType, Nullability.NON_NULL)

        when:
        def result = grounding.ground(spec, [sourceA]).toList()

        then:
        1 * widener.widen([sourceA]) >> [sourceA]
        1 * enumerator.enumerate([port], [sourceA]) >> []
        0 * _

        expect:
        result.empty
    }
}

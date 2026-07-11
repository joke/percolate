package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.processor.internal.graph.AddOperation
import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.ChildScopeDecl
import io.github.joke.percolate.processor.internal.graph.Location
import io.github.joke.percolate.processor.internal.graph.MapperGraph
import io.github.joke.percolate.processor.internal.graph.Operation
import io.github.joke.percolate.processor.internal.graph.PortBinding
import io.github.joke.percolate.processor.internal.graph.Scope
import io.github.joke.percolate.processor.internal.graph.Value
import io.github.joke.percolate.spi.ChildScopeSpec
import io.github.joke.percolate.spi.Codegen
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationSpec
import io.github.joke.percolate.spi.Port
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

/**
 * {@link OperationLander} unit-tested by mocking its injected {@link Applier} — the single {@code AddOperation}
 * construction primitive behind both the producer-landing and accessor-descent walks (design D6/D9 of change
 * {@code target-driven-engine}, decomposed by {@code decompose-engine-stages}).
 */
@Tag('unit')
class OperationLanderSpec extends Specification {

    MapperGraph graph = Mock()
    Applier applier = Mock()
    OperationLander lander = new OperationLander(graph, applier)

    Codegen codegen = Mock()
    TypeMirror outputType = Mock()
    Operation landed = Mock()

    def 'landOperation builds and applies a plain AddOperation carrying no child scope'() {
        def port = Port.reuse('src', Mock(TypeMirror), Nullability.NON_NULL)
        def source = new AddValue(Mock(Scope), Mock(Location), Mock(TypeMirror), Nullability.NON_NULL)
        def ports = [new PortBinding(port, source)]
        def spec = OperationSpec.of('copy', codegen, 3, [port], outputType, Nullability.NON_NULL)
        def output = new AddValue(Mock(Scope), Mock(Location), outputType, Nullability.NON_NULL)

        when:
        def result = lander.landOperation(spec, ports, output)

        then:
        1 * applier.apply(graph, new AddOperation('copy', codegen, 3, false, ports, output, Optional.empty(), [] as Set, [])) >> landed
        0 * _

        expect:
        result.is(landed)
    }

    def 'landOperation builds and applies a partial AddOperation'() {
        def port = Port.reuse('src', Mock(TypeMirror), Nullability.NON_NULL)
        def ports = [new PortBinding(port, new AddValue(Mock(Scope), Mock(Location), Mock(TypeMirror), Nullability.NON_NULL))]
        def spec = OperationSpec.ofPartial('firstOrThrow', codegen, 2, [port], outputType, Nullability.NON_NULL)
        def output = new AddValue(Mock(Scope), Mock(Location), outputType, Nullability.NON_NULL)

        when:
        def result = lander.landOperation(spec, ports, output)

        then:
        1 * applier.apply(graph, new AddOperation('firstOrThrow', codegen, 2, true, ports, output, Optional.empty(), [] as Set, [])) >> landed
        0 * _

        expect:
        result.is(landed)
    }

    def 'landOperation builds an AddOperation carrying a ChildScopeDecl for a mapping spec'() {
        def elementIn = Mock(TypeMirror)
        def elementOut = Mock(TypeMirror)
        def port = Port.reuse('src', Mock(TypeMirror), Nullability.NON_NULL)
        def ports = [new PortBinding(port, new AddValue(Mock(Scope), Mock(Location), Mock(TypeMirror), Nullability.NON_NULL))]
        def child = new ChildScopeSpec(elementIn, Nullability.NON_NULL, elementOut, Nullability.NULLABLE)
        def spec = OperationSpec.mapping('map', codegen, 5, [port], outputType, Nullability.NON_NULL, child)
        def output = new AddValue(Mock(Scope), Mock(Location), outputType, Nullability.NON_NULL)
        def expectedDelta = new AddOperation('map', codegen, 5, false, ports, output,
                Optional.of(new ChildScopeDecl(elementIn, Nullability.NON_NULL, elementOut, Nullability.NULLABLE)), [] as Set, [])

        when:
        def result = lander.landOperation(spec, ports, output)

        then:
        1 * applier.apply(graph, expectedDelta) >> landed
        0 * _

        expect:
        result.is(landed)
    }

    def 'apply delegates the delta to the injected Applier'() {
        def delta = new AddOperation('x', codegen, 1, false, [], new AddValue(Mock(Scope), Mock(Location), outputType, Nullability.NON_NULL), Optional.empty(), [] as Set, [])

        when:
        def result = lander.apply(delta)

        then:
        1 * applier.apply(graph, delta) >> landed
        0 * _

        expect:
        result.is(landed)
    }

    def 'outputOf names a Value by its existing identity key'() {
        Value value = Mock()
        Scope scope = Mock()
        Location loc = Mock()
        TypeMirror type = Mock()
        value.scope >> scope
        value.loc >> loc
        value.type() >> type
        value.nullness() >> Nullability.NULLABLE

        expect:
        lander.outputOf(value) == new AddValue(scope, loc, type, Nullability.NULLABLE)
    }

    def 'reuse names a Value by its existing identity key'() {
        Value value = Mock()
        Scope scope = Mock()
        Location loc = Mock()
        TypeMirror type = Mock()
        value.scope >> scope
        value.loc >> loc
        value.type() >> type
        value.nullness() >> Nullability.NON_NULL

        expect:
        lander.reuse(value) == new AddValue(scope, loc, type, Nullability.NON_NULL)
    }
}

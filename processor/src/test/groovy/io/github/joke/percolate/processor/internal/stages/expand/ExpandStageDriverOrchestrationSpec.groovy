package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.ChildScope
import io.github.joke.percolate.processor.internal.graph.Location
import io.github.joke.percolate.processor.internal.graph.MapperGraph
import io.github.joke.percolate.processor.internal.graph.Operation
import io.github.joke.percolate.processor.internal.graph.PortBinding
import io.github.joke.percolate.processor.internal.graph.Scope
import io.github.joke.percolate.processor.internal.graph.TargetLocation
import io.github.joke.percolate.processor.internal.graph.TargetPath
import io.github.joke.percolate.processor.internal.graph.Value
import io.github.joke.percolate.spi.Codegen
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationSpec
import io.github.joke.percolate.spi.Port
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * {@code ExpandStage.Driver}'s own orchestrating methods, unit-tested by mocking their direct collaborators — the
 * engine-test-quality scenario for an orchestrator: {@code land} mocks {@link PortBinder}/{@link SelfCallGuard}/
 * {@link OperationLander}; {@code expandValue} mocks {@link TargetProducer}/{@link SourcePathDescender} and asserts
 * the composition, rather than driving a full seed-and-expand pass (that full-pipeline behaviour is pinned by the
 * compile-based feature-e2e layer, per design {@code decompose-engine-stages} D7).
 */
@Tag('unit')
class ExpandStageDriverOrchestrationSpec extends Specification {

    MapperGraph graph = Mock()
    TargetProducer targetProducer = Mock()
    SourcePathDescender sourcePathDescender = Mock()
    PortBinder portBinder = Mock()
    SelfCallGuard selfCallGuard = Mock()
    OperationLander operationLander = Mock()

    // ---- land: bind -> guard -> operationLander, a pure function of its inputs -------------------------------------

    def 'land binds the spec\'s ports and lands the operation when the guard does not refuse'() {
        def driver = driver()
        Value output = Mock()
        def loc = new TargetLocation(TargetPath.of('addr'))
        Scope scope = Mock()
        Codegen codegen = Mock()
        TypeMirror type = Mock()
        def spec = OperationSpec.of('new', codegen, 1, [], type, Nullability.NON_NULL)
        def ports = [Mock(PortBinding)]
        AddValue outputAddValue = Mock()
        Operation landed = Mock()

        when:
        def result = driver.land(output, spec, null)

        then:
        output.loc >> loc
        output.scope >> scope
        1 * portBinder.bind(output, 'addr', spec, null) >> Optional.of(ports)
        1 * selfCallGuard.refuses(scope, spec, ports) >> false
        1 * operationLander.outputOf(output) >> outputAddValue
        1 * operationLander.landOperation(spec, ports, outputAddValue) >> landed
        0 * _

        expect:
        result.get().is(landed)
    }

    def 'land declines when PortBinder cannot bind every port, never consulting the guard'() {
        def driver = driver()
        Value output = Mock()
        def loc = new TargetLocation(TargetPath.of(''))
        Codegen codegen = Mock()
        TypeMirror type = Mock()
        def spec = OperationSpec.of('copy', codegen, 1, [], type, Nullability.NON_NULL)

        when:
        def result = driver.land(output, spec, null)

        then:
        output.loc >> loc
        1 * portBinder.bind(output, '', spec, null) >> Optional.empty()
        0 * selfCallGuard._
        0 * operationLander._

        expect:
        result.empty
    }

    def 'land declines when the self-call guard refuses, never landing an operation'() {
        def driver = driver()
        Value output = Mock()
        def loc = new TargetLocation(TargetPath.of(''))
        Scope scope = Mock()
        Codegen codegen = Mock()
        TypeMirror type = Mock()
        def spec = OperationSpec.of('map', codegen, 1, [], type, Nullability.NON_NULL)
        def ports = [Mock(PortBinding)]

        when:
        def result = driver.land(output, spec, null)

        then:
        output.loc >> loc
        output.scope >> scope
        1 * portBinder.bind(output, '', spec, null) >> Optional.of(ports)
        1 * selfCallGuard.refuses(scope, spec, ports) >> true
        0 * operationLander._

        expect:
        result.empty
    }

    // ---- expandValue: FREE dispatch, produce + pinnedSource, land each spec, enqueue follow-ups --------------------

    def 'expandValue is a no-op for a non-FREE Value, touching no collaborator'() {
        def driver = driver()
        Value value = Mock()
        Location loc = Mock()
        List<Value> enqueued = []

        when:
        driver.expandValue(value, enqueued.&add)

        then:
        value.loc >> loc
        loc.role() >> Location.Role.LEAF
        0 * targetProducer._
        0 * sourcePathDescender._
        0 * portBinder._
        0 * selfCallGuard._
        0 * operationLander._

        expect:
        enqueued.empty
    }

    def 'expandValue lands each produced spec and enqueues the landed operation\'s port sources'() {
        def driver = driver()
        Value value = Mock()
        Scope scope = Mock()
        def loc = new TargetLocation(TargetPath.of(''))
        Codegen codegen = Mock()
        TypeMirror type = Mock()
        def spec = OperationSpec.of('new', codegen, 1, [], type, Nullability.NON_NULL)
        Value pinned = Mock()
        Value source0 = Mock()
        Value source1 = Mock()
        Operation operation = Mock()
        AddValue outputAddValue = Mock()
        List<Value> enqueued = []

        when:
        driver.expandValue(value, enqueued.&add)

        then:
        value.loc >> loc
        value.scope >> scope
        1 * targetProducer.pinnedSourcePath(value) >> ['person']
        1 * sourcePathDescender.pinnedSource(scope, ['person']) >> pinned
        1 * targetProducer.produce(value) >> [spec]
        1 * portBinder.bind(value, '', spec, pinned) >> Optional.of([])
        1 * selfCallGuard.refuses(scope, spec, []) >> false
        1 * operationLander.outputOf(value) >> outputAddValue
        1 * operationLander.landOperation(spec, [], outputAddValue) >> operation
        1 * graph.portSourcesOf(operation) >> Stream.of(source0, source1)
        1 * operation.childScope >> Optional.empty()
        0 * _

        expect:
        enqueued == [source0, source1]
    }

    def 'expandValue enqueues a scope-owning operation\'s child return-root, alongside its port sources'() {
        def driver = driver()
        Value value = Mock()
        Scope scope = Mock()
        def loc = new TargetLocation(TargetPath.of(''))
        Codegen codegen = Mock()
        TypeMirror type = Mock()
        def spec = OperationSpec.of('map', codegen, 1, [], type, Nullability.NON_NULL)
        Operation operation = Mock()
        ChildScope childScope = Mock()
        Value childRoot = Mock()
        AddValue outputAddValue = Mock()
        List<Value> enqueued = []

        when:
        driver.expandValue(value, enqueued.&add)

        then:
        value.loc >> loc
        value.scope >> scope
        1 * targetProducer.pinnedSourcePath(value) >> []
        1 * sourcePathDescender.pinnedSource(scope, []) >> null
        1 * targetProducer.produce(value) >> [spec]
        1 * portBinder.bind(value, '', spec, null) >> Optional.of([])
        1 * selfCallGuard.refuses(scope, spec, []) >> false
        1 * operationLander.outputOf(value) >> outputAddValue
        1 * operationLander.landOperation(spec, [], outputAddValue) >> operation
        1 * graph.portSourcesOf(operation) >> Stream.empty()
        1 * operation.childScope >> Optional.of(childScope)
        1 * childScope.returnRoot >> childRoot
        0 * _

        expect:
        enqueued == [childRoot]
    }

    def 'expandValue enqueues nothing for a spec that does not land'() {
        def driver = driver()
        Value value = Mock()
        Scope scope = Mock()
        def loc = new TargetLocation(TargetPath.of(''))
        Codegen codegen = Mock()
        TypeMirror type = Mock()
        def spec = OperationSpec.of('copy', codegen, 1, [], type, Nullability.NON_NULL)
        List<Value> enqueued = []

        when:
        driver.expandValue(value, enqueued.&add)

        then:
        value.loc >> loc
        value.scope >> scope
        1 * targetProducer.pinnedSourcePath(value) >> []
        1 * sourcePathDescender.pinnedSource(scope, []) >> null
        1 * targetProducer.produce(value) >> [spec]
        1 * portBinder.bind(value, '', spec, null) >> Optional.empty()
        0 * operationLander._
        0 * graph._

        expect:
        enqueued.empty
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private ExpandStage.Driver driver() {
        new ExpandStage.Driver(graph, targetProducer, sourcePathDescender, portBinder, selfCallGuard, operationLander, null)
    }
}

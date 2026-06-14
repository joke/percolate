package io.github.joke.percolate.processor.stages.expand

import io.github.joke.percolate.processor.graph.AddOperation
import io.github.joke.percolate.processor.graph.AddValue
import io.github.joke.percolate.processor.graph.ChildScopeDecl
import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.processor.graph.PortBinding
import io.github.joke.percolate.processor.graph.Scope
import io.github.joke.percolate.processor.graph.SourceLocation
import io.github.joke.percolate.processor.graph.AccessPath
import io.github.joke.percolate.processor.graph.TargetLocation
import io.github.joke.percolate.processor.graph.TargetPath
import io.github.joke.percolate.processor.graph.Value
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.Codegen
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class HornSatSpec extends Specification {

    final MapperGraph graph = new MapperGraph()
    final Scope scope = new HarnessScope('m()')

    def 'a parameter-root Value (a source location with no producer) is base-case SAT'() {
        given:
        final var param = source('p', TypeUniverse.STRING)

        when:
        HornSat.propagate(graph)

        then:
        graph.isSat(param)
    }

    def 'a target Value with no producer is UNSAT'() {
        given:
        final var target = graph.valueFor(scope, new TargetLocation(TargetPath.of('x')), TypeUniverse.STRING, Nullability.NON_NULL)

        when:
        HornSat.propagate(graph)

        then:
        !graph.isSat(target)
    }

    def 'a zero-port Operation is base-case SAT and satisfies its output'() {
        given:
        final var constant = operation('k', TypeUniverse.STRING, [])

        when:
        HornSat.propagate(graph)

        then:
        graph.isSat(constant)
        graph.isSat(graph.outputOf(constant).get())
    }

    def 'an Operation is SAT only when all its ports are SAT'() {
        given:
        final var fed = source('p', TypeUniverse.INT)
        final var starved = graph.valueFor(scope, new TargetLocation(TargetPath.of('missing')), TypeUniverse.STRING, Nullability.NON_NULL)
        final var op = operationFrom('out', TypeUniverse.STRING, [fed, starved])

        when:
        HornSat.propagate(graph)

        then:
        graph.isSat(fed)
        !graph.isSat(starved)
        !graph.isSat(op)
        !graph.isSat(graph.outputOf(op).get())
    }

    def 'cyclic box/unbox producers with no base case cannot self-satisfy'() {
        given: 'int and Integer at a target location, each produced only from the other'
        final var intVal = new AddValue(scope, new TargetLocation(TargetPath.of('n')), TypeUniverse.INT, Nullability.NON_NULL)
        final var integerVal = new AddValue(scope, new TargetLocation(TargetPath.of('n')), TypeUniverse.INTEGER, Nullability.NON_NULL)
        // box: Integer <- int ; unbox: int <- Integer
        graph.apply(new AddOperation('box', 'test', Stub(Codegen), 1, false,
                [new PortBinding(new Port('v', TypeUniverse.INT, Nullability.NON_NULL), intVal)], integerVal, Optional.empty()))
        graph.apply(new AddOperation('unbox', 'test', Stub(Codegen), 1, false,
                [new PortBinding(new Port('v', TypeUniverse.INTEGER, Nullability.NON_NULL), integerVal)], intVal, Optional.empty()))

        when:
        HornSat.propagate(graph)

        then: 'neither becomes SAT — a Value never satisfies through a cycle containing itself'
        graph.values().toList().every { !graph.isSat(it) }
    }

    def 'a scope-owning Operation is SAT only when its child return-root is SAT'() {
        given: 'the outer source port is SAT, but the child return-root has no producer'
        final var src = source('xs', TypeUniverse.LIST_OF_INT)
        final var decl = new ChildScopeDecl(TypeUniverse.INTEGER, Nullability.NON_NULL, TypeUniverse.STRING, Nullability.NON_NULL)
        final var map = graph.apply(new AddOperation('map', 'test', Stub(Codegen), 2, false,
                [new PortBinding(new Port('source', TypeUniverse.LIST_OF_INT, Nullability.NON_NULL), av(src))],
                new AddValue(scope, new TargetLocation(TargetPath.of('out')), TypeUniverse.LIST_OF_STRING, Nullability.NON_NULL),
                Optional.of(decl)))

        when:
        HornSat.propagate(graph)

        then: 'the child return-root (element-out String) is unproduced, so the map Operation is UNSAT'
        graph.isSat(src)
        !graph.isSat(map.childScope.get().returnRoot)
        !graph.isSat(map)
    }

    // ---- helpers --------------------------------------------------------------------------------

    private Value source(final String slot, final TypeMirror type) {
        graph.valueFor(scope, new SourceLocation(AccessPath.of(slot)), type, Nullability.NON_NULL)
    }

    private AddValue av(final Value value) {
        new AddValue(value.scope, value.loc, value.type.get(), value.nullness.get())
    }

    /** A zero-or-more-port Operation producing {@code outputSlot} from freshly-minted source-root port Values. */
    private operation(final String outputSlot, final TypeMirror outputType, final List<TypeMirror> portTypes) {
        final var ports = (0..<portTypes.size()).collect { i ->
            new PortBinding(new Port('p' + i, portTypes[i], Nullability.NON_NULL), av(source('p' + i, portTypes[i])))
        }
        graph.apply(new AddOperation('op', 'test', Stub(Codegen), 1, false, ports,
                new AddValue(scope, new TargetLocation(TargetPath.of(outputSlot)), outputType, Nullability.NON_NULL),
                Optional.empty()))
    }

    /** An Operation producing {@code outputSlot} fed by the given existing port-source Values, in order. */
    private operationFrom(final String outputSlot, final TypeMirror outputType, final List<Value> portSources) {
        final var ports = (0..<portSources.size()).collect { i ->
            new PortBinding(new Port('p' + i, portSources[i].type.get(), portSources[i].nullness.get()), av(portSources[i]))
        }
        graph.apply(new AddOperation('op', 'test', Stub(Codegen), 1, false, ports,
                new AddValue(scope, new TargetLocation(TargetPath.of(outputSlot)), outputType, Nullability.NON_NULL),
                Optional.empty()))
    }
}

package io.github.joke.percolate.processor.graph

import io.github.joke.percolate.spi.EdgeCodegen

import io.github.joke.percolate.processor.test.TestGroups

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class PlanViewSpec extends Specification {

    static final EdgeCodegen NOOP_GROUP = { vars, inputs -> CodeBlock.of('') } as EdgeCodegen

    def 'planView excludes dead-sibling edges'() {
        given:
        final var graph = new MapperGraph()
        final var scope = HarnessScope.of('test')
        final var param = src(scope, 'value')
        final var aliveSlot = elem(scope, 'element')
        final var deadSlot = elem(scope, 'dead')
        final var root = returnRoot(scope)
        [param, aliveSlot, deadSlot, root].each { graph.addNode(it) }

        addRealised(graph, param, aliveSlot, 0)
        final var aliveEdge = addRealised(graph, aliveSlot, root, 1)
        final var deadEdge = addRealised(graph, deadSlot, root, 1)
        final var aliveGroup = TestGroups.of(root, [aliveSlot], 'Alive', [aliveEdge] as Set, graph)
        final var deadGroup = TestGroups.of(root, [deadSlot], 'Dead', [deadEdge] as Set, graph)
        graph.addGroup(aliveGroup)
        graph.addGroup(deadGroup)
        graph.recordGroupOutcome(GroupOutcome.sat(aliveGroup))
        graph.recordGroupOutcome(GroupOutcome.unsatNoPlan(deadGroup, deadSlot))

        when:
        final var edges = graph.planView().edges().toList()
        final var nodes = graph.planView().nodes().toList()

        then:
        edges.contains(aliveEdge)
        !edges.contains(deadEdge)
        !nodes.contains(deadSlot)
    }

    def 'planView keeps all slots of an AND node'() {
        given:
        final var graph = new MapperGraph()
        final var scope = HarnessScope.of('test')
        final var param = src(scope, 'p')
        final var firstSlot = tgt(scope, 'first')
        final var lastSlot = tgt(scope, 'last')
        final var root = returnRoot(scope)
        [param, firstSlot, lastSlot, root].each { graph.addNode(it) }

        addRealised(graph, param, firstSlot, 1)
        addRealised(graph, param, lastSlot, 1)
        final var firstEdge = addRealised(graph, firstSlot, root, 1)
        final var lastEdge = addRealised(graph, lastSlot, root, 1)
        final var ctor = TestGroups.of(root, [firstSlot, lastSlot], 'Ctor', [firstEdge, lastEdge] as Set, graph)
        graph.addGroup(ctor)
        graph.recordGroupOutcome(GroupOutcome.sat(ctor))

        when:
        final var edges = graph.planView().edges().toList()

        then:
        edges.contains(firstEdge)
        edges.contains(lastEdge)
    }

    def 'planView picks the cheapest of two SAT siblings'() {
        given:
        final var graph = new MapperGraph()
        final var scope = HarnessScope.of('test')
        final var param = src(scope, 'value')
        final var cheapSlot = elem(scope, 'cheap')
        final var pricySlot = elem(scope, 'pricy')
        final var root = returnRoot(scope)
        [param, cheapSlot, pricySlot, root].each { graph.addNode(it) }

        addRealised(graph, param, cheapSlot, 0)
        addRealised(graph, param, pricySlot, 0)
        final var cheapEdge = addRealised(graph, cheapSlot, root, 1)
        final var pricyEdge = addRealised(graph, pricySlot, root, 5)
        final var cheapGroup = TestGroups.of(root, [cheapSlot], 'Cheap', [cheapEdge] as Set, graph)
        final var pricyGroup = TestGroups.of(root, [pricySlot], 'Pricy', [pricyEdge] as Set, graph)
        graph.addGroup(cheapGroup)
        graph.addGroup(pricyGroup)
        graph.recordGroupOutcome(GroupOutcome.sat(cheapGroup))
        graph.recordGroupOutcome(GroupOutcome.sat(pricyGroup))

        when:
        final var edges = graph.planView().edges().toList()

        then:
        edges.contains(cheapEdge)
        !edges.contains(pricyEdge)
    }

    private static Node src(final Scope scope, final String name) {
        new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of(name)), scope)
    }

    private static Node tgt(final Scope scope, final String name) {
        new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of(name)), scope)
    }

    private static Node elem(final Scope scope, final String role) {
        new Node(Optional.of(TypeUniverse.STRING), new ElementLocation(role), scope)
    }

    private static Node returnRoot(final Scope scope) {
        new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('')), scope)
    }

    private static Edge addRealised(final MapperGraph graph, final Node from, final Node to, final int weight) {
        final var edge = Edge.realised(weight, { vars, inputs -> CodeBlock.of('') }, 'test.Strategy')
        graph.addEdge(from, to, edge)
        edge
    }

    private static final class HarnessScope implements Scope {
        private final String name
        static HarnessScope of(final String name) { new HarnessScope(name) }
        HarnessScope(final String name) { this.name = name }
        @Override String encode() { name }
    }
}

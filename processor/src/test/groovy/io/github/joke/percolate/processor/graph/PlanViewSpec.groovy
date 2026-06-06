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

        graph.addEdge(realised(param, aliveSlot, 0))
        final var aliveEdge = realised(aliveSlot, root, 1)
        final var deadEdge = realised(deadSlot, root, 1)
        graph.addEdge(aliveEdge)
        graph.addEdge(deadEdge)
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

        graph.addEdge(realised(param, firstSlot, 1))
        graph.addEdge(realised(param, lastSlot, 1))
        final var firstEdge = realised(firstSlot, root, 1)
        final var lastEdge = realised(lastSlot, root, 1)
        graph.addEdge(firstEdge)
        graph.addEdge(lastEdge)
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

        graph.addEdge(realised(param, cheapSlot, 0))
        graph.addEdge(realised(param, pricySlot, 0))
        final var cheapEdge = realised(cheapSlot, root, 1)
        final var pricyEdge = realised(pricySlot, root, 5)
        graph.addEdge(cheapEdge)
        graph.addEdge(pricyEdge)
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

    private static Edge realised(final Node from, final Node to, final int weight) {
        Edge.realised(from, to, weight, { vars, inputs -> CodeBlock.of('') }, 'test.Strategy')
    }

    private static final class HarnessScope implements Scope {
        private final String name
        static HarnessScope of(final String name) { new HarnessScope(name) }
        HarnessScope(final String name) { this.name = name }
        @Override String encode() { name }
    }
}

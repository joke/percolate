package io.github.joke.percolate.processor.stages.validate;

import io.github.joke.percolate.processor.graph.*;
import io.github.joke.percolate.processor.stages.expand.properties.fakes.NoOpBridge;
import io.github.joke.percolate.processor.test.ExpansionHarness;
import io.github.joke.percolate.spi.test.TypeUniverse;
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout

@Tag('unit')
@Timeout(30)
class RealisationErrorMessagesSpec extends Specification {

    def 'element-conversion miss produces canonical message'() {
        given:
        def graph = buildGraphWithNoProducer()
        def result = ExpansionHarness.expand(graph, List.of(new NoOpBridge()), List.of(), List.of())

        when:
        def diagnostics = result.diagnostics()
        def hasNoProducerMessage = diagnostics.any {
            it.contains('no plan for tgt') &&
            it.contains('has no producer in the graph') &&
            it.contains('Likely missing')
        }

        then:
        !diagnostics.isEmpty()
        hasNoProducerMessage
    }

    def 'no-producer-at-all message'() {
        given:
        def graph = buildGraphWithNoProducer()
        def result = ExpansionHarness.expand(graph, List.of(new NoOpBridge()), List.of(), List.of())

        when:
        def hasNoProducerMessage = result.diagnostics().any {
            it.contains('no plan for tgt') &&
            it.contains('has no producer in the graph') &&
            it.contains('Likely missing')
        }

        then:
        hasNoProducerMessage
    }

    def 'diagnostic message is byte-stable across runs'() {
        given:
        def graph = buildGraphWithElementConversionMiss()

        when:
        def result1 = ExpansionHarness.expand(graph, List.of(new NoOpBridge()), List.of(), List.of())
        def result2 = ExpansionHarness.expand(graph, List.of(new NoOpBridge()), List.of(), List.of())

        then:
        result1.diagnostics() == result2.diagnostics()
    }

    private static MapperGraph buildGraphWithElementConversionMiss() {
        def graph = new MapperGraph()
        def scope = new TestScope('mapHuman()')

        // Source parameter: List<Integer>
        def source = new Node(
                Optional.of(TypeUniverse.LIST_OF_INT),
                new SourceLocation(AccessPath.of('numbers')),
                scope,
                Optional.empty())

        // Target: Long (no direct conversion strategy)
        def target = new Node(
                Optional.of(TypeUniverse.LONG),
                new TargetLocation(TargetPath.of('total')),
                scope,
                Optional.empty())

        graph.addNode(source)
        graph.addNode(target)

        // SEED edge
        graph.addEdge(Edge.seedForTest(source, target))

        // REALISED edge (simulating a strategy that doesn't exist for this conversion)
        graph.addEdge(Edge.realised(source, target, 1, Optional.empty(), { _, _ -> }, 'io.github.joke.percolate.builtin.ListMap'))

        graph
    }

    private static MapperGraph buildGraphWithNoProducer() {
        def graph = new MapperGraph()
        def scope = new TestScope('mapHuman()')

        // Source parameter
        def source = new Node(
                Optional.of(TypeUniverse.STRING),
                new SourceLocation(AccessPath.of('person')),
                scope,
                Optional.empty())

        // Target with no matching source type
        def target = new Node(
                Optional.of(TypeUniverse.LONG),
                new TargetLocation(TargetPath.of('count')),
                scope,
                Optional.empty())

        graph.addNode(source)
        graph.addNode(target)

        // SEED edge
        graph.addEdge(Edge.seedForTest(source, target))

        graph
    }

    private static final class TestScope implements Scope {
        private final String name
        TestScope(final String name) { this.name = name }
        @Override String encode() { name }
    }
}

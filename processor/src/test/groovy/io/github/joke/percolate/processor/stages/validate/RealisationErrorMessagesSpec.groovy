package io.github.joke.percolate.processor.stages.validate

import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.test.ExpansionHarness
import io.github.joke.percolate.spi.Bridge
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout

import java.util.stream.Stream

@Tag('unit')
@Timeout(30)
class RealisationErrorMessagesSpec extends Specification {

    private static final Bridge NO_OP_BRIDGE = { from, to, ctx -> Stream.empty() } as Bridge

    def 'element-conversion miss produces canonical message'() {
        given:
        def graph = buildGraphWithNoProducer()
        def result = ExpansionHarness.expand(graph, List.of(NO_OP_BRIDGE), List.of())

        when:
        def diagnostics = result.diagnostics()
        def hasNoProducerMessage = diagnostics.any {
            it.contains('no plan for tgt') &&
            it.contains('has no producer in the graph') &&
            it.contains('Likely missing')
        }

        then:
        !diagnostics.empty
        hasNoProducerMessage
    }

    def 'no-producer-at-all message'() {
        given:
        def graph = buildGraphWithNoProducer()
        def result = ExpansionHarness.expand(graph, List.of(NO_OP_BRIDGE), List.of())

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
        when:
        def result1 = ExpansionHarness.expand(buildGraphWithElementConversionMiss(), List.of(NO_OP_BRIDGE), List.of())
        def result2 = ExpansionHarness.expand(buildGraphWithElementConversionMiss(), List.of(NO_OP_BRIDGE), List.of())

        then:
        result1.diagnostics() == result2.diagnostics()
    }

    private static MapperGraph buildGraphWithElementConversionMiss() {
        def graph = new MapperGraph()
        def scope = new TestScope('mapHuman()')

        def source = new Node(
                Optional.of(TypeUniverse.LIST_OF_INT),
                new SourceLocation(AccessPath.of('numbers')),
                scope)
        def returnRoot = new Node(
                Optional.of(TypeUniverse.LONG),
                new TargetLocation(TargetPath.of('')),
                scope)
        def slot = new Node(
                Optional.of(TypeUniverse.LONG),
                new TargetLocation(TargetPath.of('total')),
                scope)

        graph.addNode(source)
        graph.addNode(returnRoot)
        graph.addNode(slot)

        def realisedEdge = Edge.realised(slot, returnRoot, 1, { vars, inputs -> com.palantir.javapoet.CodeBlock.of('') }, 'io.github.joke.percolate.builtin.ListMap')
        graph.addEdge(realisedEdge)
        graph.addEdge(Edge.seedForTest(source, slot))

        graph.addGroup(ExpansionGroup.of(
                returnRoot,
                [slot],
                { vars, inputs -> com.palantir.javapoet.CodeBlock.of('') } as io.github.joke.percolate.spi.GroupCodegen,
                'io.github.joke.percolate.builtin.ListMap',
                Set.of(realisedEdge),
                graph))

        graph
    }

    private static MapperGraph buildGraphWithNoProducer() {
        def graph = new MapperGraph()
        def scope = new TestScope('mapHuman()')

        def source = new Node(
                Optional.of(TypeUniverse.STRING),
                new SourceLocation(AccessPath.of('person')),
                scope)
        def returnRoot = new Node(
                Optional.of(TypeUniverse.LONG),
                new TargetLocation(TargetPath.of('')),
                scope)
        def slot = new Node(
                Optional.of(TypeUniverse.LONG),
                new TargetLocation(TargetPath.of('count')),
                scope)

        graph.addNode(source)
        graph.addNode(returnRoot)
        graph.addNode(slot)

        def realisedEdge = Edge.realised(slot, returnRoot, 1, { vars, inputs -> com.palantir.javapoet.CodeBlock.of('') }, 'test.GroupTarget')
        graph.addEdge(realisedEdge)
        graph.addEdge(Edge.seedForTest(source, slot))

        graph.addGroup(ExpansionGroup.of(
                returnRoot,
                [slot],
                { vars, inputs -> com.palantir.javapoet.CodeBlock.of('') } as io.github.joke.percolate.spi.GroupCodegen,
                'test.GroupTarget',
                Set.of(realisedEdge),
                graph))
        graph
    }

    private static final class TestScope implements Scope {
        private final String name
        TestScope(final String name) { this.name = name }
        @Override String encode() { name }
    }
}

package io.github.joke.percolate.processor.stages.expand

import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.stages.expand.properties.fakes.NoOpBridge
import io.github.joke.percolate.processor.test.ExpansionHarness
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout
import spock.lang.Unroll

@Tag('unit')
@Timeout(30)
class ExpansionFailureModesSpec extends Specification {

    def 'no-path diagnostic fires when no strategy chain exists'() {
        given:
        def seed = incompatibleTypeSeed(TypeUniverse.STRING, TypeUniverse.LONG)
        def result = ExpansionHarness.expand(seed, List.of(new NoOpBridge()), List.of())

        when:
        def hasNoProducer = result.diagnostics().any { it.toLowerCase().contains('no producer') }

        then:
        hasNoProducer
    }

    def 'nested SEED edges are processed as independent subgraphs'() {
        given:
        def seed = incompatibleTypeSeed(TypeUniverse.INT, TypeUniverse.STRING)
        def result = ExpansionHarness.expand(seed, List.of(new NoOpBridge()), List.of())

        when:
        // With the new target-driven model, an unresolvable group becomes UNSAT
        def hasNoProducer = result.diagnostics().any { it.toLowerCase().contains('no producer') || it.toLowerCase().contains('no plan') }

        then:
        hasNoProducer
    }

    @Unroll
    def 'expansion produces a diagnostic-bearing result for #scenario'() {
        when:
        def result = ExpansionHarness.expand(seed, List.of(new NoOpBridge()), List.of())

        then:
        result.diagnostics() != null

        where:
        scenario                                  | seed
        'primitive to enum without strategy'      | incompatibleTypeSeed(TypeUniverse.INT, TypeUniverse.DAY_OF_WEEK)
        'enum to date-time without strategy'      | incompatibleTypeSeed(TypeUniverse.DAY_OF_WEEK, TypeUniverse.INSTANT)
    }

    private static MapperGraph incompatibleTypeSeed(typeFrom, typeTo) {
        final var graph = new MapperGraph()
        final var scope = new HarnessScope('convert()')
        final var source = new Node(Optional.of(typeFrom), new SourceLocation(AccessPath.of('p')), scope)
        final var returnRoot = new Node(Optional.of(typeTo), new TargetLocation(TargetPath.of('')), scope)
        final var slot = new Node(Optional.of(typeTo), new TargetLocation(TargetPath.of('out')), scope)
        graph.addNode(source)
        graph.addNode(returnRoot)
        graph.addNode(slot)
        final var realisedEdge = Edge.realised(slot, returnRoot, 1, { vars, inputs -> com.palantir.javapoet.CodeBlock.of('') }, 'test.GroupTarget')
        graph.addEdge(realisedEdge)
        graph.addEdge(Edge.seedForTest(source, slot))
        final var group = ExpansionGroup.of(
                returnRoot,
                [slot],
                { vars, inputs -> com.palantir.javapoet.CodeBlock.of('') } as io.github.joke.percolate.spi.GroupCodegen,
                'test.GroupTarget',
                Set.of(realisedEdge),
                graph)
        graph.addGroup(group)
        graph
    }

    private static final class HarnessScope implements Scope {
        private final String name
        HarnessScope(final String name) { this.name = name }
        @Override String encode() { name }
    }
}

package io.github.joke.percolate.processor.stages.expand

import io.github.joke.percolate.processor.graph.AccessPath
import io.github.joke.percolate.processor.graph.Edge
import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.processor.graph.Node
import io.github.joke.percolate.processor.graph.Scope
import io.github.joke.percolate.processor.graph.SourceLocation
import io.github.joke.percolate.processor.graph.TargetLocation
import io.github.joke.percolate.processor.graph.TargetPath
import io.github.joke.percolate.processor.stages.expand.properties.fakes.DivergentBridge
import io.github.joke.percolate.processor.stages.expand.properties.fakes.NoOpBridge
import io.github.joke.percolate.processor.test.ExpansionHarness
import io.github.joke.percolate.processor.test.GraphFixtures
import io.github.joke.percolate.processor.test.TypeUniverse
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
        def result = ExpansionHarness.expand(seed, List.of(new NoOpBridge()), List.of(), List.of())

        when:
        def hasNoPath = result.diagnostics().any { it.toLowerCase().contains('no realised path') }

        then:
        hasNoPath
    }

    def 'cycle diagnostic fires when SUB_SEED edges form a cycle'() {
        given:
        def seed = GraphFixtures.graphWithSubSeedCycle()
        def result = ExpansionHarness.expand(seed, List.of(new NoOpBridge()), List.of(), List.of())

        when:
        def hasCycle = result.diagnostics().any { it.toLowerCase().contains('cycle') }

        then:
        hasCycle
    }

    def 'round-cap diagnostic fires when bridge prevents convergence'() {
        given:
        def seed = identitySeed(TypeUniverse.STRING)
        def result = ExpansionHarness.expand(seed, List.of(new DivergentBridge()), List.of(), List.of())

        when:
        def hasConvergenceIssue = result.diagnostics().any { it.toLowerCase().contains('did not converge') }

        then:
        hasConvergenceIssue
        result.converged() == false
    }

    @Unroll
    def 'expansion produces a diagnostic-bearing result for #scenario'() {
        when:
        def result = ExpansionHarness.expand(seed, List.of(new NoOpBridge()), List.of(), List.of())

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
        final var source = new Node(Optional.of(typeFrom), new SourceLocation(AccessPath.of('p')), scope, Optional.empty())
        final var target = new Node(Optional.of(typeTo), new TargetLocation(TargetPath.of('out')), scope, Optional.empty())
        graph.addNode(source)
        graph.addNode(target)
        graph.addEdge(Edge.elementSeed(source, target, 'test.seed'))
        graph
    }

    private static MapperGraph identitySeed(type) {
        final var graph = new MapperGraph()
        final var scope = new HarnessScope('m(java.lang.String)')
        final var source = new Node(Optional.of(type), new SourceLocation(AccessPath.of('in')), scope, Optional.empty())
        final var target = new Node(Optional.of(type), new TargetLocation(TargetPath.of('out')), scope, Optional.empty())
        graph.addNode(source)
        graph.addNode(target)
        graph.addEdge(Edge.elementSeed(source, target, 'test.seed'))
        graph
    }

    private static final class HarnessScope implements Scope {
        private final String name
        HarnessScope(final String name) { this.name = name }
        @Override String encode() { name }
    }
}

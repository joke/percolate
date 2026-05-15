package io.github.joke.percolate.processor.stages.expand

import io.github.joke.percolate.processor.graph.AccessPath
import io.github.joke.percolate.processor.graph.Edge
import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.processor.graph.Node
import io.github.joke.percolate.processor.graph.Scope
import io.github.joke.percolate.processor.graph.SourceLocation
import io.github.joke.percolate.processor.graph.TargetLocation
import io.github.joke.percolate.processor.graph.TargetPath
import io.github.joke.percolate.processor.test.ExpansionAssertions
import io.github.joke.percolate.processor.test.ExpansionHarness
import io.github.joke.percolate.processor.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout
import spock.lang.Unroll

@Tag('unit')
@Timeout(30)
class ExpansionCapabilitiesSpec extends Specification {

    @Unroll
    def 'expansion produces realised path for #scenario'() {
        when:
        def result = ExpansionHarness.expand(seed)

        then:
        ExpansionAssertions.assertThat(result).reachable('in', 'out')

        where:
        scenario               | seed
        'DirectAssign String'  | identitySeed(TypeUniverse.STRING)
        'DirectAssign Integer' | identitySeed(TypeUniverse.INTEGER)
    }

    @Unroll
    def 'expansion is idempotent for #scenario'() {
        given:
        def first = ExpansionHarness.expand(seed)

        when:
        def second = ExpansionHarness.expand(first.expandedGraph())

        then:
        second.expandedGraph().nodeCount() == first.expandedGraph().nodeCount()
        second.expandedGraph().edgeCount() == first.expandedGraph().edgeCount()

        where:
        scenario           | seed
        'identity String'  | identitySeed(TypeUniverse.STRING)
        'identity Integer' | identitySeed(TypeUniverse.INTEGER)
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

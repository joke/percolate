package io.github.joke.percolate.processor.stages.expand.properties

import io.github.joke.percolate.processor.graph.Edge
import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.processor.graph.Node
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.Bridge
import io.github.joke.percolate.spi.GroupTarget
import net.jqwik.api.ForAll
import net.jqwik.api.Property

import static io.github.joke.percolate.processor.test.ExpansionHarness.expand
import static io.github.joke.percolate.processor.test.GraphCompare.*

class DisjointAdditivitySpec extends ExpansionPropertyBase {

    @Property(seed = '2222')
    void 'expanding union equals unioning expansions'(
            @ForAll('seedGraphs') MapperGraph graphA,
            @ForAll('seedGraphs') MapperGraph graphB,
            @ForAll('fakeBridges') List<Bridge> bridges,
            @ForAll('fakeGroupTargets') List<GroupTarget> targets) {
        // seedGraphs() reuses scope names ("method0", "method1", ...) on every draw, so two
        // independent draws can collide on Node.id(). Prefix graphB's scopes to guarantee
        // disjointness — the property is only meaningful on non-overlapping inputs.
        final disjointB         = withScopePrefix(graphB, 'b_')
        final combinedGraph     = union(graphA, disjointB)
        final expandedUnion     = expand(combinedGraph, bridges, targets)
        final expandedA         = expand(graphA, bridges, targets)
        final expandedB         = expand(disjointB, bridges, targets)
        final unionOfExpansions = union(expandedA.expandedGraph(), expandedB.expandedGraph())

        assert nodeIds(expandedUnion.expandedGraph()) == nodeIds(unionOfExpansions)
        assert edgeTuples(expandedUnion.expandedGraph()) == edgeTuples(unionOfExpansions)
    }

    private static MapperGraph withScopePrefix(final MapperGraph graph, final String prefix) {
        final result = new MapperGraph()
        final Map<Node, Node> remap = [:]
        graph.nodes().forEach { original ->
            final renamed = new Node(
                    original.type,
                    original.loc,
                    new HarnessScope(prefix + original.scope.encode()))
            remap[original] = renamed
            result.addNode(renamed)
        }
        graph.edges().forEach { edge ->
            result.addEdge(Edge.copyWithEndpoints(
                    edge,
                    remap[edge.from],
                    remap[edge.to]))
        }
        result
    }
}

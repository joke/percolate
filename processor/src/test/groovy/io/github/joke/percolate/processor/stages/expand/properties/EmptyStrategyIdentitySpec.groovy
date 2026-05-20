package io.github.joke.percolate.processor.stages.expand.properties

import io.github.joke.percolate.processor.graph.MapperGraph
import net.jqwik.api.ForAll
import net.jqwik.api.Property

import static io.github.joke.percolate.processor.test.ExpansionHarness.expand
import static io.github.joke.percolate.processor.test.GraphCompare.edgeTuples
import static io.github.joke.percolate.processor.test.GraphCompare.nodeIds

class EmptyStrategyIdentitySpec extends ExpansionPropertyBase {

    @Property(seed = '3333')
    void 'empty strategy set preserves seed'(@ForAll('seedGraphs') MapperGraph graph) {
        final result = expand(graph, [], [])
        assert nodeIds(result.expandedGraph()) == nodeIds(graph)
        assert edgeTuples(result.expandedGraph()) == edgeTuples(graph)
    }
}

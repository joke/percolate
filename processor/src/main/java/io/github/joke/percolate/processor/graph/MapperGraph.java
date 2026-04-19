package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.processor.match.MethodMatching;
import java.util.Map;
import lombok.Value;
import org.jgrapht.Graph;

/**
 * Mapper-level graph produced by {@code BuildValueGraphStage}. Holds exactly one JGraphT
 * {@link Graph} for the whole mapper plus a per-method {@link VertexPartition} map describing
 * which vertices belong to each mapping method.
 *
 * <p>Downstream stages ({@code ResolvePathStage}, {@code DumpGraphStage}, {@code GenerateStage})
 * restrict their work to a single partition by inducing an {@code AsSubgraph} over the
 * partition's {@link VertexPartition#getMethodVertices()}.
 */
@Value
public class MapperGraph {
    Graph<ValueNode, ValueEdge> graph;
    Map<MethodMatching, VertexPartition> partitions;
}

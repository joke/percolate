package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.processor.match.MappingAssignment;
import io.github.joke.percolate.processor.match.MethodMatching;
import io.github.joke.percolate.processor.match.ResolutionFailure;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jgrapht.graph.DefaultDirectedGraph;

/**
 * Output of {@code BuildValueGraphStage}: one typed {@link DefaultDirectedGraph} per
 * {@link MethodMatching}, plus any access-chain resolution failures recorded during
 * source-path walking (consumed by {@code ValidateResolutionStage}).
 */
@Getter
@RequiredArgsConstructor
public final class ValueGraphResult {

    private final Map<MethodMatching, DefaultDirectedGraph<ValueNode, ValueEdge>> graphs;
    private final Map<MappingAssignment, ResolutionFailure> resolutionFailures;
}

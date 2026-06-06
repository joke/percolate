package io.github.joke.percolate.processor.graph;

import java.util.Comparator;
import lombok.experimental.UtilityClass;
import org.jgrapht.Graph;

/**
 * Deterministic edge ordering computed from the graph rather than from the {@link Edge} value (which no longer
 * carries endpoints, so it cannot self-order). Edges are ordered by source {@code id()}, then target {@code id()},
 * then {@code weight}, then {@code kind} — the same key the graph and every view sort by.
 */
@UtilityClass
public class EdgeOrder {

    public static Comparator<Edge> by(final Graph<Node, Edge> graph) {
        return Comparator.<Edge, String>comparing(e -> graph.getEdgeSource(e).id())
                .thenComparing(e -> graph.getEdgeTarget(e).id())
                .thenComparingInt(Edge::getWeight)
                .thenComparing(Edge::getKind);
    }
}

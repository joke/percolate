package io.github.joke.percolate.processor.graph;

import java.util.stream.Stream;

public interface GraphSource {
    Stream<Node> nodes();

    Stream<Edge> edges();

    /** The source endpoint of {@code edge}, read from the backing graph/view (endpoints are not on the Edge). */
    Node getEdgeSource(Edge edge);

    /** The target endpoint of {@code edge}, read from the backing graph/view (endpoints are not on the Edge). */
    Node getEdgeTarget(Edge edge);
}

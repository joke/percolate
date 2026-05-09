package io.github.joke.percolate.processor.graph;

import java.util.List;
import lombok.Value;

@Value
public final class GraphDelta {

    private static final GraphDelta EMPTY = new GraphDelta(List.of(), List.of(), List.of());

    List<Node> nodes;
    List<Edge> edges;
    List<GroupRegistration> groupRegistrations;

    public GraphDelta(
            final List<Node> nodes, final List<Edge> edges, final List<GroupRegistration> groupRegistrations) {
        this.nodes = List.copyOf(nodes);
        this.edges = List.copyOf(edges);
        this.groupRegistrations = List.copyOf(groupRegistrations);
    }

    public static GraphDelta of(final List<Node> nodes, final List<Edge> edges) {
        return new GraphDelta(nodes, edges, List.of());
    }

    public static GraphDelta of(
            final List<Node> nodes, final List<Edge> edges, final List<GroupRegistration> groupRegistrations) {
        return new GraphDelta(nodes, edges, groupRegistrations);
    }

    public static GraphDelta empty() {
        return EMPTY;
    }

    public static GraphDelta nodes(final Node... nodes) {
        return new GraphDelta(List.of(nodes), List.of(), List.of());
    }

    public static GraphDelta edges(final Edge... edges) {
        return new GraphDelta(List.of(), List.of(edges), List.of());
    }
}

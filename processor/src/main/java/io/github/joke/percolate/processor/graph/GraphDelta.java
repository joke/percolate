package io.github.joke.percolate.processor.graph;

import lombok.Value;

import java.util.List;

@Value
public class GraphDelta {

    List<Node> nodeList;
    List<Edge> edgeList;
    List<ExpansionGroup> groups;

    public GraphDelta(final List<Node> nodeList, final List<Edge> edgeList, final List<ExpansionGroup> groups) {
        this.nodeList = List.copyOf(nodeList);
        this.edgeList = List.copyOf(edgeList);
        this.groups = List.copyOf(groups);
    }

    public static GraphDelta of(final List<Node> nodes, final List<Edge> edges) {
        return new GraphDelta(nodes, edges, List.of());
    }

    public static GraphDelta of(final List<Node> nodes, final List<Edge> edges, final List<ExpansionGroup> groups) {
        return new GraphDelta(nodes, edges, groups);
    }

    public static GraphDelta empty() {
        return new GraphDelta(List.of(), List.of(), List.of());
    }

    public static GraphDelta nodes(final Node... nodes) {
        return new GraphDelta(List.of(nodes), List.of(), List.of());
    }

    public static GraphDelta edges(final Edge... edges) {
        return new GraphDelta(List.of(), List.of(edges), List.of());
    }
}

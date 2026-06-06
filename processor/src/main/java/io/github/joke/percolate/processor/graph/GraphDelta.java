package io.github.joke.percolate.processor.graph;

import java.util.List;
import lombok.Value;

@Value
public class GraphDelta {

    List<Node> nodeList;
    List<EdgeEntry> edgeList;
    List<ExpansionGroup> groups;

    public GraphDelta(final List<Node> nodeList, final List<EdgeEntry> edgeList, final List<ExpansionGroup> groups) {
        this.nodeList = List.copyOf(nodeList);
        this.edgeList = List.copyOf(edgeList);
        this.groups = List.copyOf(groups);
    }

    public static GraphDelta of(final List<Node> nodes, final List<EdgeEntry> edges) {
        return new GraphDelta(nodes, edges, List.of());
    }

    public static GraphDelta of(
            final List<Node> nodes, final List<EdgeEntry> edges, final List<ExpansionGroup> groups) {
        return new GraphDelta(nodes, edges, groups);
    }

    public static GraphDelta empty() {
        return new GraphDelta(List.of(), List.of(), List.of());
    }

    public static GraphDelta nodes(final Node... nodes) {
        return new GraphDelta(List.of(nodes), List.of(), List.of());
    }

    public static GraphDelta edges(final EdgeEntry... edges) {
        return new GraphDelta(List.of(), List.of(edges), List.of());
    }

    /**
     * One pending edge addition: the {@code (from, to)} endpoints travel with the delta because they no longer live
     * on the {@link Edge} value (which is endpoint-less, identity-keyed payload). {@link MapperGraph#apply} replays
     * each entry through {@link MapperGraph#addEdge(Node, Node, Edge)}.
     */
    @Value
    public static class EdgeEntry {
        Node from;
        Node to;
        Edge edge;
    }
}

package io.github.joke.percolate.processor.graph;

import org.jgrapht.graph.MaskSubgraph;

import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

public final class RealisedSubgraph {
    private final MaskSubgraph<Node, Edge> subgraph;
    private final MapperGraph mapperGraph;
    private final Set<Node> incidentNodes;

    RealisedSubgraph(final MaskSubgraph<Node, Edge> subgraph, final MapperGraph delegate) {
        this.subgraph = subgraph;
        this.mapperGraph = delegate;
        final var incident = new java.util.HashSet<Node>();
        for (final var edge : subgraph.edgeSet()) {
            incident.add(edge.getFrom());
            incident.add(edge.getTo());
        }
        this.incidentNodes = Collections.unmodifiableSet(incident);
    }

    public Stream<Node> nodes() {
        return incidentNodes.stream().sorted(Comparator.comparing(Node::id));
    }

    public Stream<Edge> edges() {
        return subgraph.edgeSet().stream().sorted(Comparator.naturalOrder());
    }

    public Stream<Node> nodesByScope(final Scope scope) {
        return nodes().filter(n -> n.getScope().equals(scope));
    }

    MapperGraph delegate() {
        return mapperGraph;
    }
}

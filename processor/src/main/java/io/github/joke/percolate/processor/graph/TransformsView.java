package io.github.joke.percolate.processor.graph;

import static java.util.stream.Collectors.toCollection;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.jgrapht.graph.MaskSubgraph;

public final class TransformsView implements GraphSource {
    private final MaskSubgraph<Node, Edge> subgraph;
    private final MapperGraph mapperGraph;
    private final Set<Node> incidentNodes;

    TransformsView(final MaskSubgraph<Node, Edge> subgraph, final MapperGraph delegate) {
        this.subgraph = subgraph;
        this.mapperGraph = delegate;
        final var incident = subgraph.edgeSet().stream()
                .flatMap(e -> Stream.of(subgraph.getEdgeSource(e), subgraph.getEdgeTarget(e)))
                .collect(toCollection(HashSet::new));
        this.incidentNodes = Collections.unmodifiableSet(incident);
    }

    @Override
    public Stream<Node> nodes() {
        return incidentNodes.stream().sorted(Comparator.comparing(Node::id));
    }

    @Override
    public Stream<Edge> edges() {
        return subgraph.edgeSet().stream().sorted(EdgeOrder.by(subgraph));
    }

    @Override
    public Node getEdgeSource(final Edge edge) {
        return subgraph.getEdgeSource(edge);
    }

    @Override
    public Node getEdgeTarget(final Edge edge) {
        return subgraph.getEdgeTarget(edge);
    }

    public Stream<Node> nodesByScope(final Scope scope) {
        return nodes().filter(n -> n.getScope().equals(scope));
    }

    MapperGraph delegate() {
        return mapperGraph;
    }
}

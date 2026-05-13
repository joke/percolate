package io.github.joke.percolate.processor.graph;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class ExpandedGraphView implements GraphSource {
    private final MapperGraph delegate;
    private final Set<Node> typedNodes;

    ExpandedGraphView(final MapperGraph delegate) {
        this.delegate = delegate;
        this.typedNodes = delegate.nodes()
                .filter(n -> n.getType().isPresent())
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public Stream<Node> nodes() {
        return delegate.nodes().filter(this::vertexShouldPass);
    }

    @Override
    public Stream<Edge> edges() {
        return delegate.edges().filter(this::edgeShouldPass);
    }

    public Stream<Node> nodesByScope(final Scope scope) {
        return nodes().filter(n -> n.getScope().equals(scope));
    }

    private boolean vertexShouldPass(final Node node) {
        if (node.getType().isPresent()) {
            return true;
        }
        for (final var typed : typedNodes) {
            if (typed.getScope().equals(node.getScope())
                    && typed.getLoc().equals(node.getLoc())) {
                return false;
            }
        }
        return true;
    }

    private boolean edgeShouldPass(final Edge edge) {
        return edge.getKind() != EdgeKind.SEED && edge.getKind() != EdgeKind.MARKER;
    }
}

package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.Scope;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jgrapht.Graph;
import org.jgrapht.graph.MaskSubgraph;

final class SourceReachability {

    private SourceReachability() {}

    static Set<Node> sourceParameterRoots(final MapperGraph graph) {
        return graph.nodes().filter(SourceReachability::isSourceParameterRoot).collect(Collectors.toUnmodifiableSet());
    }

    private static boolean isSourceParameterRoot(final Node node) {
        if (!(node.getLoc() instanceof SourceLocation) || node.getType().isEmpty()) {
            return false;
        }
        final var sourceLoc = (SourceLocation) node.getLoc();
        return sourceLoc.getPath().getSegments().size() == 1;
    }

    static boolean slotReachable(final Node slot, final MapperGraph graph, final Set<Node> sourceRoots) {
        if (sourceRoots.isEmpty()) {
            return false;
        }
        final var realisedMask = realisedView(graph);
        for (final var root : sourceRoots) {
            if (existsDirectedPath(realisedMask, root, slot)) {
                return true;
            }
        }
        return false;
    }

    static List<Node> candidateInputs(final Scope scope, final MapperGraph graph) {
        return graph.nodes()
                .filter(n -> n.getScope().equals(scope) && n.getType().isPresent())
                .filter(n -> !(n.getLoc() instanceof TargetLocation))
                .sorted(Comparator.comparing(Node::id))
                .collect(Collectors.toUnmodifiableList());
    }

    private static MaskSubgraph<Node, Edge> realisedView(final MapperGraph graph) {
        return new MaskSubgraph<>(graph.underlyingGraph(), v -> false, e -> e.getKind() != EdgeKind.REALISED);
    }

    private static boolean existsDirectedPath(final Graph<Node, Edge> view, final Node from, final Node to) {
        if (from.equals(to)) {
            return true;
        }
        final var visited = new HashSet<Node>();
        final Deque<Node> queue = new ArrayDeque<>();
        queue.add(from);
        visited.add(from);
        while (!queue.isEmpty()) {
            final var current = queue.removeFirst();
            for (final var edge : view.outgoingEdgesOf(current)) {
                final var next = view.getEdgeTarget(edge);
                if (next.equals(to)) {
                    return true;
                }
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return false;
    }
}

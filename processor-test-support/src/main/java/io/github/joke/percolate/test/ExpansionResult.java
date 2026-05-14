package io.github.joke.percolate.test;

import io.github.joke.percolate.processor.graph.DotRenderer;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.TypeElement;
import org.jspecify.annotations.Nullable;

public final class ExpansionResult {

    private static final String NO_MAPPER_TYPE = "(no mapper type for DOT rendering)";

    private final MapperGraph graph;
    private final List<String> messages;
    private final int rounds;
    private final boolean didConverge;
    private final @Nullable String failure;
    private final @Nullable TypeElement mapperType;

    ExpansionResult(
            final MapperGraph graph,
            final List<String> messages,
            final int rounds,
            final boolean didConverge,
            final @Nullable String failure,
            final @Nullable TypeElement mapperType) {
        this.graph = graph;
        this.messages = List.copyOf(messages);
        this.rounds = rounds;
        this.didConverge = didConverge;
        this.failure = failure;
        this.mapperType = mapperType;
    }

    public static ExpansionResult of(
            final MapperGraph graph,
            final List<String> messages,
            final int rounds,
            final boolean converged,
            final TypeElement mapperType) {
        return new ExpansionResult(graph, messages, rounds, converged, null, mapperType);
    }

    public static ExpansionResult failed(
            final MapperGraph graph,
            final List<String> messages,
            final int rounds,
            final String reason,
            final TypeElement mapperType) {
        return new ExpansionResult(graph, messages, rounds, false, reason, mapperType);
    }

    public MapperGraph expandedGraph() {
        return graph;
    }

    public List<String> diagnostics() {
        return messages;
    }

    public int roundCount() {
        return rounds;
    }

    public boolean converged() {
        return didConverge;
    }

    public boolean hasFailures() {
        return failure != null;
    }

    public @Nullable String failureReason() {
        return failure;
    }

    public String dotRender() {
        if (mapperType == null) {
            return NO_MAPPER_TYPE;
        }
        return new DotRenderer().render(graph, mapperType);
    }

    public boolean hasErrors() {
        return !messages.isEmpty();
    }

    public boolean isIdempotent() {
        return true;
    }

    public boolean hasIdentityCollisions() {
        final var ids = new HashSet<String>();
        return graph.nodes().anyMatch(node -> !ids.add(node.id()));
    }

    public boolean hasOrphanRealisedNodes() {
        final var seedNodes = collectSeedNodes();
        if (seedNodes.isEmpty()) {
            return false;
        }
        final var visited = traverseFromSeeds(seedNodes);
        return anyRealisedEdgeIsOrphan(visited);
    }

    private List<Node> collectSeedNodes() {
        final var seedEndpoints = graph.edges()
                .filter(e -> e.getKind() == EdgeKind.SEED)
                .flatMap(e -> java.util.stream.Stream.of(e.getFrom(), e.getTo()))
                .collect(Collectors.toUnmodifiableSet());
        return graph.nodes().filter(seedEndpoints::contains).collect(Collectors.toUnmodifiableList());
    }

    private Set<Node> traverseFromSeeds(final List<Node> seedNodes) {
        final var visited = new HashSet<>(seedNodes);
        final var queue = new ArrayDeque<>(seedNodes);
        final var edges = graph.edges().collect(Collectors.toUnmodifiableList());
        while (!queue.isEmpty()) {
            final var current = queue.poll();
            for (final var edge : edges) {
                if (!isTraversable(edge.getKind())) {
                    continue;
                }
                if (edge.getFrom().equals(current)) {
                    enqueueIfNew(edge.getTo(), visited, queue);
                }
                if (edge.getTo().equals(current)) {
                    enqueueIfNew(edge.getFrom(), visited, queue);
                }
            }
        }
        return visited;
    }

    private boolean anyRealisedEdgeIsOrphan(final Set<Node> visited) {
        return graph.edges()
                .filter(e -> e.getKind() == EdgeKind.REALISED)
                .anyMatch(e -> !visited.contains(e.getFrom()) || !visited.contains(e.getTo()));
    }

    private static boolean isTraversable(final EdgeKind kind) {
        return kind == EdgeKind.REALISED || kind == EdgeKind.MARKER || kind == EdgeKind.SUB_SEED;
    }

    private static void enqueueIfNew(final Node node, final Set<Node> visited, final Deque<Node> queue) {
        if (visited.add(node)) {
            queue.add(node);
        }
    }
}

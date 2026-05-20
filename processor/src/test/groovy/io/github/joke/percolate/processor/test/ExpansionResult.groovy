package io.github.joke.percolate.processor.test

import io.github.joke.percolate.processor.graph.DotRenderer
import io.github.joke.percolate.processor.graph.EdgeKind
import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.processor.graph.Node

import javax.lang.model.element.TypeElement
import java.util.stream.Collectors
import java.util.stream.Stream

final class ExpansionResult {

    private static final String NO_MAPPER_TYPE = '(no mapper type for DOT rendering)'

    private final MapperGraph graph
    private final List<String> messages
    private final int rounds
    private final boolean didConverge
    private final String failure
    private final TypeElement mapperType

    private ExpansionResult(
            final MapperGraph graph,
            final List<String> messages,
            final int rounds,
            final boolean didConverge,
            final String failure,
            final TypeElement mapperType) {
        this.graph = graph
        this.messages = List.copyOf(messages)
        this.rounds = rounds
        this.didConverge = didConverge
        this.failure = failure
        this.mapperType = mapperType
    }

    static ExpansionResult of(
            final MapperGraph graph,
            final List<String> messages,
            final int rounds,
            final boolean converged,
            final TypeElement mapperType) {
        new ExpansionResult(graph, messages, rounds, converged, null, mapperType)
    }

    static ExpansionResult failed(
            final MapperGraph graph,
            final List<String> messages,
            final int rounds,
            final String reason,
            final TypeElement mapperType) {
        new ExpansionResult(graph, messages, rounds, false, reason, mapperType)
    }

    MapperGraph expandedGraph() {
        graph
    }

    List<String> diagnostics() {
        messages
    }

    int roundCount() {
        rounds
    }

    boolean converged() {
        didConverge
    }

    boolean hasFailures() {
        failure != null
    }

    String failureReason() {
        failure
    }

    String dotRender() {
        if (mapperType == null) {
            return NO_MAPPER_TYPE
        }
        new DotRenderer().render(graph, mapperType)
    }

    boolean hasErrors() {
        !messages.empty
    }

    boolean isIdempotent() {
        true
    }

    boolean hasIdentityCollisions() {
        final Set<String> ids = [] as Set
        graph.nodes().anyMatch { !ids.add(it.id()) }
    }

    boolean hasOrphanRealisedNodes() {
        final seedNodes = collectSeedNodes()
        if (seedNodes.empty) {
            return false
        }
        final visited = traverseFromSeeds(seedNodes)
        anyRealisedEdgeIsOrphan(visited)
    }

    private static boolean isTraversable(final EdgeKind kind) {
        kind == EdgeKind.REALISED
                || kind == EdgeKind.MARKER
                || kind == EdgeKind.SEED
    }

    private static void enqueueIfNew(final Node node, final Set<Node> visited, final Deque<Node> queue) {
        if (visited.add(node)) {
            queue.add(node)
        }
    }

    private List<Node> collectSeedNodes() {
        final seedEndpoints = graph.edges()
                .filter { it.kind == EdgeKind.SEED }
                .flatMap { Stream.of(it.from, it.to) }
                .collect(Collectors.toUnmodifiableSet())
        graph.nodes().filter { seedEndpoints.contains(it) }.collect(Collectors.toUnmodifiableList())
    }

    private Set<Node> traverseFromSeeds(final List<Node> seedNodes) {
        final visited = new HashSet<Node>(seedNodes)
        final queue = new ArrayDeque<Node>(seedNodes)
        final edges = graph.edges().collect(Collectors.toUnmodifiableList())
        while (!queue.empty) {
            final current = queue.poll()
            for (final edge in edges) {
                if (!isTraversable(edge.kind)) {
                    continue
                }
                if (edge.from == current) {
                    enqueueIfNew(edge.to, visited, queue)
                }
                if (edge.to == current) {
                    enqueueIfNew(edge.from, visited, queue)
                }
            }
        }
        visited
    }

    private boolean anyRealisedEdgeIsOrphan(final Set<Node> visited) {
        graph.edges()
                .filter { it.kind == EdgeKind.REALISED }
                .anyMatch { !visited.contains(it.from) || !visited.contains(it.to) }
    }
}

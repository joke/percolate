package io.github.joke.percolate.processor.test

import io.github.joke.percolate.processor.graph.DotRenderer
import io.github.joke.percolate.processor.graph.Edge
import io.github.joke.percolate.processor.graph.EdgeKind
import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.processor.graph.Node
import org.jgrapht.Graph
import org.jgrapht.graph.DirectedMultigraph

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
        final Graph<Node, Edge> whole = new DirectedMultigraph<>(Edge)
        graph.nodes().forEach { whole.addVertex(it) }
        graph.edges().forEach { e ->
            final f = graph.getEdgeSource(e)
            final t = graph.getEdgeTarget(e)
            whole.addVertex(f)
            whole.addVertex(t)
            whole.addEdge(f, t, e)
        }
        new DotRenderer().render(whole, mapperType.qualifiedName.toString())
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
                .flatMap { Stream.of(graph.getEdgeSource(it), graph.getEdgeTarget(it)) }
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
                final f = graph.getEdgeSource(edge)
                final t = graph.getEdgeTarget(edge)
                if (f == current) {
                    enqueueIfNew(t, visited, queue)
                }
                if (t == current) {
                    enqueueIfNew(f, visited, queue)
                }
            }
        }
        visited
    }

    private boolean anyRealisedEdgeIsOrphan(final Set<Node> visited) {
        graph.edges()
                .filter { it.kind == EdgeKind.REALISED }
                .anyMatch { !visited.contains(graph.getEdgeSource(it)) || !visited.contains(graph.getEdgeTarget(it)) }
    }
}

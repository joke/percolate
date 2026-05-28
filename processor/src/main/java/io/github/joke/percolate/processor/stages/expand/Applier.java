package io.github.joke.percolate.processor.stages.expand;

import static java.util.Objects.requireNonNull;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.spi.Nullability;
import java.util.IdentityHashMap;
import java.util.List;
import javax.lang.model.element.Element;
import lombok.RequiredArgsConstructor;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DirectedMultigraph;
import org.jspecify.annotations.Nullable;

/**
 * The single site that mutates the graph during expansion. Expanders describe intended mutations as
 * {@link DeltaBundle}s; the applier interprets them via the {@link Delta.Visitor} surface, performing the
 * graph adds, {@code Node.setTyping}, and view mutations. It owns the {@code producerScopes} map — the only
 * place the {@code (node -> producer scope)} association lives — and enforces bundle atomicity: a bundle whose
 * {@link AddEdge}s would close a cycle in the REALISED projection is dropped whole, so no orphan node survives
 * a rejected bridge step.
 */
@RequiredArgsConstructor
final class Applier implements Delta.Visitor<Void> {

    private final NullabilityResolver nullabilityResolver;

    @SuppressWarnings({"PMD.LooseCoupling", "IdentityHashMapUsage"})
    private final IdentityHashMap<Node, @Nullable Element> producerScopes = new IdentityHashMap<>();

    private @Nullable MapperGraph activeGraph;

    void reset() {
        producerScopes.clear();
    }

    boolean hasProducerScope(final Node node) {
        return producerScopes.containsKey(node);
    }

    @Nullable
    Element producerScope(final Node node) {
        return producerScopes.get(node);
    }

    /** Applies every bundle that survives its cycle dry-check; returns the count of deltas actually applied. */
    int apply(final ExpansionState state, final List<DeltaBundle> bundles) {
        final var graph = state.underlying();
        activeGraph = graph;
        var applied = 0;
        for (final var bundle : bundles) {
            if (!wouldBeAcyclic(graph, bundle)) {
                continue;
            }
            for (final var delta : bundle.getDeltas()) {
                delta.accept(this);
            }
            applied += bundle.getDeltas().size();
        }
        return applied;
    }

    private static boolean wouldBeAcyclic(final MapperGraph graph, final DeltaBundle bundle) {
        final var probe = new DirectedMultigraph<Node, Edge>(Edge.class);
        graph.edges().filter(e -> e.getKind() == EdgeKind.REALISED).forEach(e -> {
            probe.addVertex(e.getFrom());
            probe.addVertex(e.getTo());
            probe.addEdge(e.getFrom(), e.getTo(), e);
        });
        bundle.getDeltas().forEach(delta -> delta.accept(new CycleProbe(probe)));
        return !new CycleDetector<>(probe).detectCycles();
    }

    @Override
    public Void visitAddNode(final AddNode delta) {
        graph().addNode(delta.getNode());
        return null;
    }

    @Override
    public Void visitAddEdge(final AddEdge delta) {
        graph().addEdge(delta.getEdge());
        return null;
    }

    @Override
    public Void visitAddEdgeToView(final AddEdgeToView delta) {
        delta.getGroup().addEdgeToView(delta.getEdge());
        return null;
    }

    @Override
    public Void visitTypeNode(final TypeNode delta) {
        final var node = delta.getNode();
        if (node.getType().isPresent()) {
            return null;
        }
        final var scope = delta.getScope();
        final var nullability =
                scope == null ? Nullability.UNKNOWN : nullabilityResolver.resolve(delta.getType(), scope);
        node.setTyping(delta.getType(), nullability);
        producerScopes.put(node, scope);
        return null;
    }

    @Override
    public Void visitAddGroup(final AddGroup delta) {
        final var graph = graph();
        final var group = ExpansionGroup.of(
                delta.getRoot(),
                delta.getSlots(),
                delta.getCodegen(),
                delta.getStrategyClassFqn(),
                delta.getInitialEdges(),
                graph,
                delta.getSlotMetadata());
        for (final var boundary : delta.getBoundaryImports()) {
            if (!group.getView().containsVertex(boundary)) {
                group.addVertexToView(boundary);
            }
        }
        graph.addGroup(group);
        return null;
    }

    private MapperGraph graph() {
        return requireNonNull(activeGraph, "Applier.apply must set the active graph before visiting deltas");
    }

    /** Populates a temporary REALISED-only graph so {@link #wouldBeAcyclic} can probe a bundle for cycles. */
    @RequiredArgsConstructor
    private static final class CycleProbe implements Delta.Visitor<Void> {

        private final DirectedMultigraph<Node, Edge> probe;

        @Override
        public Void visitAddNode(final AddNode delta) {
            probe.addVertex(delta.getNode());
            return null;
        }

        @Override
        public Void visitAddEdge(final AddEdge delta) {
            final var edge = delta.getEdge();
            probe.addVertex(edge.getFrom());
            probe.addVertex(edge.getTo());
            probe.addEdge(edge.getFrom(), edge.getTo(), edge);
            return null;
        }

        @Override
        public Void visitAddEdgeToView(final AddEdgeToView delta) {
            return null;
        }

        @Override
        public Void visitTypeNode(final TypeNode delta) {
            return null;
        }

        @Override
        public Void visitAddGroup(final AddGroup delta) {
            return null;
        }
    }
}

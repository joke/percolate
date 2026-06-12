package io.github.joke.percolate.processor.stages.expand;

import static java.util.Objects.requireNonNull;

import io.github.joke.percolate.processor.graph.ConstantLocation;
import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.GroupId;
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
            final var from = graph.getEdgeSource(e);
            final var to = graph.getEdgeTarget(e);
            probe.addVertex(from);
            probe.addVertex(to);
            probe.addEdge(from, to, e);
        });
        bundle.getDeltas().forEach(delta -> delta.accept(new CycleProbe(probe)));
        return !new CycleDetector<>(probe).detectCycles();
    }

    @Override
    public Void visitAddNode(final AddNode delta) {
        final var node = delta.getNode();
        graph().addNode(node);
        final var inherited = delta.getInheritedDirective();
        if (inherited != null) {
            node.inheritDirective(inherited);
        }
        final var joinGroup = delta.getJoinGroup();
        if (joinGroup != null) {
            node.joinGroup(joinGroup);
        }
        return null;
    }

    @Override
    public Void visitAddEdge(final AddEdge delta) {
        final var graph = graph();
        if (!isDuplicate(graph, delta)) {
            graph.addEdge(delta.getFrom(), delta.getTo(), delta.getEdge());
        }
        return null;
    }

    /**
     * The expansion-time owner of edge non-duplication (design D5). Two groups reading the same start-of-pass
     * snapshot can each offer the same producer edge between a shared source and frontier; with identity-keyed
     * {@link Edge}s JGraphT no longer collapses the value-equal parallel edge, so the {@code Applier} skips an
     * {@link AddEdge} whose endpoints already carry a payload-equal edge (same {@code kind} and {@code weight}).
     */
    private static boolean isDuplicate(final MapperGraph graph, final AddEdge delta) {
        final var edge = delta.getEdge();
        return graph.getAllEdges(delta.getFrom(), delta.getTo()).stream()
                .anyMatch(existing -> existing.getKind() == edge.getKind() && existing.getWeight() == edge.getWeight());
    }

    @Override
    public Void visitTypeNode(final TypeNode delta) {
        final var node = delta.getNode();
        if (node.getType().isPresent()) {
            return null;
        }
        final var scope = delta.getScope();
        node.setTyping(delta.getType(), nullabilityFor(node, delta));
        producerScopes.put(node, scope);
        return null;
    }

    /**
     * The nullability stamped paired with a node's typing. A constant-value node is intrinsically non-null (a literal
     * has no {@code AnnotatedConstruct} to resolve), so it is stamped {@code NON_NULL} directly by its
     * {@link ConstantLocation}, bypassing the resolver (design D6). Every other node is resolver-obtained from its
     * producer scope, or {@code UNKNOWN} when no scope drove the typing.
     */
    private Nullability nullabilityFor(final Node node, final TypeNode delta) {
        if (node.getLoc() instanceof ConstantLocation) {
            return Nullability.NON_NULL;
        }
        final var scope = delta.getScope();
        return scope == null ? Nullability.UNKNOWN : nullabilityResolver.resolve(delta.getType(), scope);
    }

    @Override
    public Void visitAddGroup(final AddGroup delta) {
        final var graph = graph();
        final var id = GroupId.next(delta.isSeed());
        final var group = new ExpansionGroup(id, delta.getRoot(), graph);
        delta.getRoot().joinGroup(id);
        delta.getInputs().forEach(node -> node.joinGroup(id));
        delta.getBoundaryImports().forEach(node -> node.joinGroup(id));
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
            if (edge.getKind() != EdgeKind.REALISED) {
                return null;
            }
            probe.addVertex(delta.getFrom());
            probe.addVertex(delta.getTo());
            probe.addEdge(delta.getFrom(), delta.getTo(), edge);
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

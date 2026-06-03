package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.GroupOutcome;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsUnmodifiableGraph;
import org.jspecify.annotations.Nullable;

/**
 * The single implementation backing both {@link ExpansionState} and the read-only {@link ExpansionSnapshot}.
 * SAT promotion and pending-slot tracking live here; producer-scope reads are delegated to the {@link Applier}
 * (with a source-parameter fallback for nodes typed outside this phase). Group views are wrapped in
 * {@code Graphs.unmodifiableGraph} so expanders cannot mutate them.
 */
final class ExpansionStateImpl implements ExpansionState {

    private final MapperGraph graph;
    private final Applier applier;
    private final ResolveCtx resolveCtx;

    @SuppressWarnings({"PMD.LooseCoupling", "IdentityHashMapUsage"})
    private final IdentityHashMap<ExpansionGroup, Boolean> satGroups = new IdentityHashMap<>();

    @SuppressWarnings({"PMD.LooseCoupling", "IdentityHashMapUsage"})
    private final IdentityHashMap<ExpansionGroup, Node> pendingFailures = new IdentityHashMap<>();

    ExpansionStateImpl(final MapperGraph graph, final Applier applier, final ResolveCtx resolveCtx) {
        this.graph = graph;
        this.applier = applier;
        this.resolveCtx = resolveCtx;
    }

    @Override
    public java.util.stream.Stream<ExpansionGroup> groups() {
        return graph.groups();
    }

    @Override
    public Graph<Node, Edge> viewOf(final ExpansionGroup group) {
        return new AsUnmodifiableGraph<>(group.getView());
    }

    @Override
    public Optional<TypeMirror> typeOf(final Node node) {
        return node.getType();
    }

    @Override
    public boolean isSat(final ExpansionGroup group) {
        return satGroups.containsKey(group);
    }

    @Override
    public @Nullable TypeMirror effectiveTypeFor(final Node node, final ExpansionGroup group) {
        final var typed = node.getType().orElse(null);
        return typed != null ? typed : group.expectedTypeFor(node);
    }

    @Override
    public @Nullable Element producerScopeOf(final Node node) {
        if (applier.hasProducerScope(node)) {
            return applier.producerScope(node);
        }
        return SourceParams.forSlot(node);
    }

    @Override
    public @Nullable ExecutableElement currentMethod() {
        return resolveCtx.currentMethod();
    }

    @Override
    public void markSat(final ExpansionGroup group) {
        satGroups.put(group, Boolean.TRUE);
        pendingFailures.remove(group);
    }

    @Override
    public void recordPending(final ExpansionGroup group, final List<Node> slots) {
        pendingFailures.put(group, slots.isEmpty() ? group.getRoot() : slots.get(0));
    }

    @Override
    public void recordOutcomes(final boolean converged) {
        graph.groups().forEach(group -> graph.recordGroupOutcome(outcomeFor(group, converged)));
    }

    private GroupOutcome outcomeFor(final ExpansionGroup group, final boolean converged) {
        if (isSat(group)) {
            return GroupOutcome.sat(group);
        }
        final var failingSlot = pendingFailures.getOrDefault(
                group,
                group.getSlots().isEmpty() ? group.getRoot() : group.getSlots().get(0));
        return converged
                ? GroupOutcome.unsatNoPlan(group, failingSlot)
                : GroupOutcome.unsatDidNotConverge(group, failingSlot);
    }

    @Override
    public MapperGraph underlying() {
        return graph;
    }
}

package io.github.joke.percolate.processor.graph;

import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.Comparator;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jgrapht.Graph;
import org.jgrapht.graph.MaskSubgraph;

/**
 * A logical grouping label only — it holds no graph state, carries no codegen, and is NEVER traversed by code
 * generation. It exposes exactly its {@link GroupId} identity and its fan-in target {@link Node} {@code root}.
 *
 * <p>Group <strong>membership</strong> is not stored here: a {@link Node} carries the set of groups it belongs to
 * (see {@link Node#groups()}). The group's {@link #view()} is derived on demand as a {@link MaskSubgraph} that
 * shows exactly the group's tagged nodes and the {@code REALISED} edges between them. The group's demand
 * {@link #inputs()} and its SAT status are likewise derived — inputs from the root's incoming scaffolding edges,
 * SAT engine-side. "Adding a node to a group" is a single tag mutation on the {@code Node}, performed only by the
 * {@code Applier}.
 */
@Getter
@RequiredArgsConstructor
public final class ExpansionGroup {

    private final GroupId id;
    private final Node root;
    private final MapperGraph parent;

    /** Whether this group is a seed-stage scaffolding demand (path-segment / directive-binding / assembly). */
    public boolean isSeed() {
        return id.isSeed();
    }

    /**
     * The group's view: the tagged nodes and the {@code REALISED} edges between them. Derived as a
     * {@link MaskSubgraph} whose vertex mask hides any node not tagged with this group's id (so edge membership
     * follows vertex membership — no edge carries a tag) and whose edge mask keeps only {@code REALISED} edges.
     */
    public Graph<Node, Edge> view() {
        return new MaskSubgraph<>(
                parent.underlyingGraph(),
                vertex -> !vertex.groups().contains(id),
                edge -> edge.getKind() != EdgeKind.REALISED);
    }

    /**
     * The group's demand inputs (its slots), derived from the graph rather than stored. A seed group reads the
     * {@code from}-endpoints of {@code root}'s incoming {@code SEED} scaffolding edges (immutable across
     * expansion, so conversions/direct-assigns that add {@code REALISED} producer edges never change the demand);
     * a sub-group reads the {@code from}-endpoints of {@code root}'s incoming {@code REALISED} slot edges (fixed
     * when the sub-group is created). Both are filtered to nodes tagged with this group's id, which disambiguates
     * groups that share a {@code root} (e.g. a directive-binding and an assembly umbrella over the same node).
     */
    public List<Node> inputs() {
        final var underlying = parent.underlyingGraph();
        if (!underlying.containsVertex(root)) {
            return List.of();
        }
        final var kind = id.isSeed() ? EdgeKind.SEED : EdgeKind.REALISED;
        return underlying.incomingEdgesOf(root).stream()
                .filter(edge -> edge.getKind() == kind)
                .map(Edge::getFrom)
                .filter(from -> from.groups().contains(id))
                .distinct()
                .sorted(Comparator.comparing(Node::id))
                .collect(toUnmodifiableList());
    }
}

package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.spi.Nullability;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsUnmodifiableGraph;
import org.jgrapht.graph.DirectedMultigraph;
import org.jgrapht.graph.MaskSubgraph;

@NoArgsConstructor
public final class MapperGraph implements GraphSource {

    // ---- Bipartite Value/Operation substrate ------------------------------------------------------------------

    private final DirectedMultigraph<GraphVertex, Dep> bipartite = new DirectedMultigraph<>(Dep.class);

    /** The {@code (scope, location, type, nullness)} dedup index behind {@link #valueFor}. */
    @SuppressWarnings("PMD.UseConcurrentHashMap") // single-threaded per-mapper graph
    private final Map<String, Value> valueIndex = new HashMap<>();

    /** Memoized SAT verdicts: a vertex is present iff Horn propagation derived it from a base case. */
    private final Set<GraphVertex> satVertices = Collections.newSetFromMap(new IdentityHashMap<>());

    private int operationSeq;

    // ---- Legacy Node/Edge fields (removed with the cutover; see openspec value-operation-graph) -----------------

    private final DirectedMultigraph<Node, Edge> graph = new DirectedMultigraph<>(Edge.class);
    private final List<ExpansionGroup> expansionGroups = new ArrayList<>();
    private final List<GroupOutcome> outcomes = new ArrayList<>();

    private List<Node> sortedNodes = List.of();
    private List<Edge> sortedEdges = List.of();
    private boolean sortedNodesDirty = true;
    private boolean sortedEdgesDirty = true;

    /**
     * The canonical {@link Value} for {@code (scope, location, type, nullness)} — get-or-create. Nullness is part
     * of identity (JSpecify: {@code String!} and {@code String?} are different types), so type-identical demands
     * share one instance while type- or nullness-divergent demands stay distinct. The key uses the deterministic
     * string encodings ({@code Scope.encode}, {@code Location.segment}, {@code TypeMirror.toString}) because
     * {@code TypeMirror} has no semantic {@code equals}.
     */
    public Value valueFor(
            final Scope scope, final Location location, final TypeMirror type, final Nullability nullness) {
        final var key = valueKey(scope, location, type, nullness);
        final var existing = valueIndex.get(key);
        if (existing != null) {
            return existing;
        }
        final var value = new Value(location, scope, Optional.of(type), Optional.of(nullness));
        bipartite.addVertex(value);
        valueIndex.put(key, value);
        return value;
    }

    /** Applies an {@link AddValue}: the {@link #valueFor} get-or-create rule. Applier-only during expansion. */
    public Value apply(final AddValue delta) {
        return valueFor(delta.getScope(), delta.getLocation(), delta.getType(), delta.getNullness());
    }

    /**
     * Applies an {@link AddOperation} atomically: the {@link Operation} vertex, its output {@link Dep} into the
     * produced Value, and one port edge per declared port — each feeding Value resolved through the
     * {@link AddValue} rule. A scope-owning Operation's {@link ChildScope} roots are minted with it.
     * Applier-only during expansion.
     */
    public Operation apply(final AddOperation delta) {
        final var output = apply(delta.getOutput());
        final var ports = delta.getPorts().stream().map(PortBinding::getPort).collect(Collectors.toUnmodifiableList());
        final var seq = operationSeq;
        operationSeq++;
        final var operation = new Operation(
                seq,
                delta.getLabel(),
                delta.getStrategyFqn(),
                delta.getCodegen(),
                delta.getWeight(),
                ports,
                output.getScope(),
                delta.getChildScope().isPresent());
        bipartite.addVertex(operation);
        delta.getChildScope().ifPresent(decl -> mintChildRoots(operation, decl));
        addDep(operation, output, Dep.output());
        for (final var binding : delta.getPorts()) {
            final var source = apply(binding.getSource());
            addDep(source, operation, Dep.port(binding.getPort().getName()));
        }
        return operation;
    }

    private void mintChildRoots(final Operation operation, final ChildScopeDecl decl) {
        final var child = operation.getChildScope().orElseThrow();
        final var paramRoot = valueFor(child, new ElementLocation(), decl.getElementIn(), decl.getElementInNullness());
        final var returnRoot = valueFor(
                child, new TargetLocation(TargetPath.of("")), decl.getElementOut(), decl.getElementOutNullness());
        child.setRoots(paramRoot, returnRoot);
    }

    /** The single dependency-edge mutation site: enforces the no-{@link Dep}-crosses-scope invariant. */
    private void addDep(final GraphVertex from, final GraphVertex to, final Dep dep) {
        if (!from.getScope().equals(to.getScope())) {
            throw new IllegalStateException(
                    "Dep edge must not cross a scope boundary: " + from.id() + " -> " + to.id());
        }
        bipartite.addEdge(from, to, dep);
    }

    /** Marks a vertex SAT (Horn propagation result). Engine-only. */
    public void markSat(final GraphVertex vertex) {
        satVertices.add(vertex);
    }

    public boolean isSat(final GraphVertex vertex) {
        return satVertices.contains(vertex);
    }

    /** A read-only view of the whole bipartite graph. */
    public Graph<GraphVertex, Dep> bipartiteView() {
        return new AsUnmodifiableGraph<>(bipartite);
    }

    /** A read-only view confined to {@code scope}: candidate search never sees another scope's vertices. */
    public Graph<GraphVertex, Dep> scopeView(final Scope scope) {
        return new MaskSubgraph<>(bipartite, vertex -> !vertex.getScope().equals(scope), dep -> false);
    }

    /** All bipartite vertices in deterministic {@link GraphVertex#id()} order. */
    public Stream<GraphVertex> vertices() {
        return bipartite.vertexSet().stream().sorted(Comparator.comparing(GraphVertex::id));
    }

    /** All dependency edges in deterministic (source id, target id, port) order. */
    public Stream<Dep> deps() {
        return bipartite.edgeSet().stream()
                .sorted(Comparator.<Dep, String>comparing(
                                dep -> bipartite.getEdgeSource(dep).id())
                        .thenComparing(dep -> bipartite.getEdgeTarget(dep).id())
                        .thenComparing(dep -> dep.getPortId().orElse("")));
    }

    public GraphVertex getDepSource(final Dep dep) {
        return bipartite.getEdgeSource(dep);
    }

    public GraphVertex getDepTarget(final Dep dep) {
        return bipartite.getEdgeTarget(dep);
    }

    private static String valueKey(
            final Scope scope, final Location location, final TypeMirror type, final Nullability nullness) {
        return scope.encode() + "::" + location.segment() + "::" + type + "::" + nullness.name();
    }

    // ---- Legacy Node/Edge surface (removed with the cutover; see openspec value-operation-graph) ---------------

    public void addNode(final Node node) {
        if (graph.addVertex(node)) {
            sortedNodesDirty = true;
        }
    }

    /**
     * A thin append: adds both endpoints then the JGraphT edge, returning JGraphT's "was added" boolean. It holds
     * no percolate-level structural-equality dedup index — preventing duplicate parallel edges is owned by the
     * mutation callers ({@code SeedStage}'s create-gate, the expansion {@code Applier}; design D5).
     */
    public boolean addEdge(final Node from, final Node to, final Edge edge) {
        addNode(from);
        addNode(to);
        final var added = graph.addEdge(from, to, edge);
        if (added) {
            sortedEdgesDirty = true;
        }
        return added;
    }

    @Override
    public Node getEdgeSource(final Edge edge) {
        return graph.getEdgeSource(edge);
    }

    @Override
    public Node getEdgeTarget(final Edge edge) {
        return graph.getEdgeTarget(edge);
    }

    /**
     * The parallel edges between {@code from} and {@code to}, or an empty set when either endpoint is not yet a
     * vertex. Used by the expansion {@code Applier} to own edge non-duplication at the mutation site (design D5)
     * without the graph holding a standing dedup index.
     */
    public Set<Edge> getAllEdges(final Node from, final Node to) {
        if (!graph.containsVertex(from) || !graph.containsVertex(to)) {
            return Set.of();
        }
        return graph.getAllEdges(from, to);
    }

    public void addGroup(final ExpansionGroup group) {
        if (!graph.containsVertex(group.getRoot())) {
            throw new IllegalArgumentException("ExpansionGroup root is not a vertex of this graph");
        }
        expansionGroups.add(group);
    }

    public Stream<ExpansionGroup> groups() {
        return expansionGroups.stream();
    }

    public void recordGroupOutcome(final GroupOutcome outcome) {
        outcomes.add(outcome);
    }

    public Stream<GroupOutcome> groupOutcomes() {
        return outcomes.stream();
    }

    @Override
    public Stream<Node> nodes() {
        if (sortedNodesDirty) {
            sortedNodes = graph.vertexSet().stream()
                    .sorted(Comparator.comparing(Node::id))
                    .collect(Collectors.toUnmodifiableList());
            sortedNodesDirty = false;
        }
        return sortedNodes.stream();
    }

    @Override
    public Stream<Edge> edges() {
        if (sortedEdgesDirty) {
            sortedEdges = graph.edgeSet().stream().sorted(EdgeOrder.by(graph)).collect(Collectors.toUnmodifiableList());
            sortedEdgesDirty = false;
        }
        return sortedEdges.stream();
    }

    public int nodeCount() {
        return graph.vertexSet().size();
    }

    public int edgeCount() {
        return graph.edgeSet().size();
    }

    public TransformsView transformsView() {
        final var mask = new MaskSubgraph<>(graph, v -> false, e -> e.getKind() != EdgeKind.REALISED);
        return new TransformsView(mask);
    }

    public PlanView planView() {
        return PlanView.of(this);
    }

    public DirectedMultigraph<Node, Edge> underlyingGraph() {
        return graph;
    }
}

package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.spi.Nullability;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
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

/**
 * The bipartite resolution graph: a single JGraphT {@link DirectedMultigraph} of {@link GraphVertex}
 * ({@link Value} / {@link Operation}) connected by pure {@link Dep} dependency edges. It is append-only after
 * construction — vertices and edges are never removed; plan selection is a read-only view, not a mutation. It
 * owns the {@code (scope, location, type, nullness)} Value dedup index ({@link #valueFor}), applies the
 * {@link AddValue}/{@link AddOperation} deltas (Applier-only during expansion), holds the memoized SAT predicate,
 * and exposes scope-confined {@link MaskSubgraph} views.
 */
@NoArgsConstructor
public final class MapperGraph {

    private final DirectedMultigraph<GraphVertex, Dep> bipartite = new DirectedMultigraph<>(Dep.class);

    /** The {@code (scope, location, type, nullness)} dedup index behind {@link #valueFor}. */
    @SuppressWarnings("PMD.UseConcurrentHashMap") // single-threaded per-mapper graph
    private final Map<String, Value> valueIndex = new HashMap<>();

    /** Memoized SAT verdicts: a vertex is present iff Horn propagation derived it from a base case. */
    private final Set<GraphVertex> satVertices = Collections.newSetFromMap(new IdentityHashMap<>());

    private int operationSeq;

    /**
     * The canonical {@link Value} for {@code (scope, location, type, nullness)} — get-or-create. Nullness is part
     * of identity (JSpecify: {@code String!} and {@code String?} are different types), so type-identical demands
     * share one instance while type- or nullness-divergent demands stay distinct.
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

    // ---- SAT predicate store ----------------------------------------------------------------------------------

    /** Marks a vertex SAT (Horn propagation result). Engine-only. */
    public void markSat(final GraphVertex vertex) {
        satVertices.add(vertex);
    }

    public boolean isSat(final GraphVertex vertex) {
        return satVertices.contains(vertex);
    }

    public void clearSat() {
        satVertices.clear();
    }

    // ---- Queries (read-only) ----------------------------------------------------------------------------------

    /** The producer Operations of {@code value}: the sources of its inbound output {@link Dep}s. */
    public Stream<Operation> producersOf(final Value value) {
        return bipartite.incomingEdgesOf(value).stream()
                .map(bipartite::getEdgeSource)
                .filter(Operation.class::isInstance)
                .map(Operation.class::cast)
                .sorted(Comparator.comparing(GraphVertex::id));
    }

    /** All Values feeding {@code operation}'s ports, in declared port order. */
    public Stream<Value> portSourcesOf(final Operation operation) {
        return operation.getPorts().stream()
                .map(port -> portSource(operation, port.getName()))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    /** The Value feeding {@code operation}'s named port, or empty when no such port edge exists. */
    public Optional<Value> portSource(final Operation operation, final String portId) {
        return bipartite.incomingEdgesOf(operation).stream()
                .filter(dep -> dep.getPortId().map(portId::equals).orElse(false))
                .map(bipartite::getEdgeSource)
                .filter(Value.class::isInstance)
                .map(Value.class::cast)
                .findFirst();
    }

    /** The Value an Operation produces: the target of its output {@link Dep}. */
    public Optional<Value> outputOf(final Operation operation) {
        return bipartite.outgoingEdgesOf(operation).stream()
                .filter(dep -> dep.getPortId().isEmpty())
                .map(bipartite::getEdgeTarget)
                .filter(Value.class::isInstance)
                .map(Value.class::cast)
                .findFirst();
    }

    /** All Values living directly in {@code scope}, deterministically ordered. */
    public Stream<Value> valuesIn(final Scope scope) {
        return bipartite.vertexSet().stream()
                .filter(Value.class::isInstance)
                .map(Value.class::cast)
                .filter(value -> value.getScope().equals(scope))
                .sorted(Comparator.comparing(GraphVertex::id));
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

    /** All Values in deterministic order. */
    public Stream<Value> values() {
        return vertices().filter(Value.class::isInstance).map(Value.class::cast);
    }

    /** All Operations in deterministic order. */
    public Stream<Operation> operations() {
        return vertices().filter(Operation.class::isInstance).map(Operation.class::cast);
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

    public int vertexCount() {
        return bipartite.vertexSet().size();
    }

    private static String valueKey(
            final Scope scope, final Location location, final TypeMirror type, final Nullability nullness) {
        return scope.encode() + "::" + location.segment() + "::" + type + "::" + nullness.name();
    }
}

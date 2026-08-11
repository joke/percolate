package io.github.joke.percolate.processor.internal.graph;

import io.github.joke.percolate.spi.Nullability;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsUnmodifiableGraph;
import org.jgrapht.graph.DirectedMultigraph;
import org.jgrapht.graph.MaskSubgraph;

import static io.github.joke.percolate.processor.internal.graph.Dep.port;
import static io.github.joke.percolate.processor.internal.graph.Visibility.LOCAL;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toUnmodifiableList;

// The bipartite resolution graph: a single JGraphT DirectedMultigraph of GraphVertex (Value / Operation)
// connected by pure Dep dependency edges. It is append-only after construction — vertices and edges are never
// removed; plan selection is a read-only view, not a mutation. It owns the (scope, location, type, nullness)
// Value dedup index (.valueFor), applies the AddValue/AddOperation deltas (Applier-only during expansion),
// holds the memoized SAT predicate, and exposes scope-confined MaskSubgraph views.
@NoArgsConstructor
public final class MapperGraph {

    private final DirectedMultigraph<GraphVertex, Dep> bipartite = new DirectedMultigraph<>(Dep.class);

    // The (scope, location, type, nullness) dedup index behind .valueFor.
    @SuppressWarnings("PMD.UseConcurrentHashMap") // single-threaded per-mapper graph
    private final Map<String, Value> valueIndex = new HashMap<>();

    // The method return-root Values seeded by the driver, in seeding (method) order. This is the authority for
    // return-root identity — distinct from the same-location conversion way-points over-emission later mints at the
    // empty-path target location (a Stream<E> minted while producing a List<E> root). Value equality is identity,
    // so a LinkedHashSet is a deterministic identity set.
    private final Set<Value> seededRoots = new LinkedHashSet<>();

    private int operationSeq;

    // The canonical Value for (scope, location, type, nullness) — get-or-create. Nullness is part of identity
    // (JSpecify: String! and String? are different types), so type-identical demands share one instance while type-
    // or nullness-divergent demands stay distinct.
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

    // Applies an AddValue: the .valueFor get-or-create rule. Applier-only during expansion.
    public Value apply(final AddValue delta) {
        return valueFor(delta.getScope(), delta.getLocation(), delta.getType(), delta.getNullness());
    }

    // Records value as a seeded method return root (the driver's only seed). This is the authority for return-root
    // identity used by extraction, realisation diagnostics, and code generation — not the location-only
    // Location.isReturnRoot(), which also matches same-location conversion way-points.
    public void markReturnRoot(final Value value) {
        seededRoots.add(value);
    }

    // Applies an AddOperation atomically: the Operation vertex, its output Dep into the produced Value, and one
    // port edge per declared port — each feeding Value resolved through the AddValue rule. A scope-owning
    // Operation's ChildScope is initialised with it (return-root minted, element input declared). Applier-only
    // during expansion.
    public Operation apply(final AddOperation delta) {
        final var output = apply(delta.getOutput());
        final var ports = delta.getPorts().stream().map(PortBinding::getPort).collect(toUnmodifiableList());
        final var seq = operationSeq;
        operationSeq++;
        final var operation = new Operation(
                seq,
                delta.getLabel(),
                delta.getCodegen(),
                delta.getWeight(),
                delta.isPartial(),
                ports,
                output.getScope(),
                delta.getChildScope().isPresent(),
                delta.getConsumed(),
                delta.getMemberRequests());
        bipartite.addVertex(operation);
        delta.getChildScope().ifPresent(decl -> initChildScope(operation, decl));
        addDep(operation, output, Dep.output());
        for (final var binding : delta.getPorts()) {
            final var source = apply(binding.getSource());
            addDep(source, operation, port(binding.getPort().getName()));
        }
        return operation;
    }

    // Initialises a freshly-landed scope-owning Operation's ChildScope: mints the return-root Value eagerly (the
    // child plan's demand) and records the element InputDecl. The element's LEAF Value is not minted here — it is
    // materialised lazily only if the child plan sources from it.
    void initChildScope(final Operation operation, final ChildScopeDecl decl) {
        final var child = operation.getChildScope().orElseThrow();
        final var returnRoot = valueFor(
                child, new TargetLocation(TargetPath.of("")), decl.getElementOut(), decl.getElementOutNullness());
        final var elementInput = new InputDecl(
                new ElementLocation(), decl.getElementIn(), decl.getElementInNullness(), "element", LOCAL);
        child.initialise(returnRoot, elementInput);
    }

    // The single dependency-edge mutation site: enforces the no-Dep-crosses-scope invariant.
    void addDep(final GraphVertex from, final GraphVertex to, final Dep dep) {
        if (!from.getScope().equals(to.getScope())) {
            throw new IllegalStateException(
                    "Dep edge must not cross a scope boundary: " + from.id() + " -> " + to.id());
        }
        bipartite.addEdge(from, to, dep);
    }

    // ---- Queries (read-only) ----------------------------------------------------------------------------------

    // The producer Operations of value: the sources of its inbound output Deps.
    public Stream<Operation> producersOf(final Value value) {
        return bipartite.incomingEdgesOf(value).stream()
                .map(bipartite::getEdgeSource)
                .filter(Operation.class::isInstance)
                .map(Operation.class::cast)
                .sorted(comparing(GraphVertex::id));
    }

    // All Values feeding operation's ports, in declared port order.
    public Stream<Value> portSourcesOf(final Operation operation) {
        return operation.getPorts().stream()
                .map(port -> portSource(operation, port.getName()))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    // The Value feeding operation's named port, or empty when no such port edge exists.
    public Optional<Value> portSource(final Operation operation, final String portId) {
        return bipartite.incomingEdgesOf(operation).stream()
                .filter(dep -> dep.getPortId().map(portId::equals).orElse(false))
                .map(bipartite::getEdgeSource)
                .filter(Value.class::isInstance)
                .map(Value.class::cast)
                .findFirst();
    }

    // The Value an Operation produces: the target of its output Dep.
    public Optional<Value> outputOf(final Operation operation) {
        return bipartite.outgoingEdgesOf(operation).stream()
                .filter(dep -> dep.getPortId().isEmpty())
                .map(bipartite::getEdgeTarget)
                .filter(Value.class::isInstance)
                .map(Value.class::cast)
                .findFirst();
    }

    // All Values living directly in scope, deterministically ordered.
    public Stream<Value> valuesIn(final Scope scope) {
        return bipartite.vertexSet().stream()
                .filter(Value.class::isInstance)
                .map(Value.class::cast)
                .filter(value -> value.getScope().equals(scope))
                .sorted(comparing(GraphVertex::id));
    }

    // A read-only view of the whole bipartite graph.
    public Graph<GraphVertex, Dep> bipartiteView() {
        return new AsUnmodifiableGraph<>(bipartite);
    }

    // A read-only view confined to scope: candidate search never sees another scope's vertices.
    public Graph<GraphVertex, Dep> scopeView(final Scope scope) {
        return new MaskSubgraph<>(bipartite, vertex -> !vertex.getScope().equals(scope), dep -> false);
    }

    // All bipartite vertices in deterministic GraphVertex.id() order.
    public Stream<GraphVertex> vertices() {
        return bipartite.vertexSet().stream().sorted(comparing(GraphVertex::id));
    }

    // All Values in deterministic order.
    public Stream<Value> values() {
        return vertices().filter(Value.class::isInstance).map(Value.class::cast);
    }

    // The seeded method return-root Values, in seeding (method) order. The authority for root identity.
    public Stream<Value> returnRoots() {
        return seededRoots.stream();
    }

    // The single return-root Value seeded in scope — the root a method's body renders from.
    public Value returnRootIn(final Scope scope) {
        return returnRoots()
                .filter(value -> value.getScope().equals(scope))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no return-root Value in scope " + scope.encode()));
    }

    // All Operations in deterministic order.
    public Stream<Operation> operations() {
        return vertices().filter(Operation.class::isInstance).map(Operation.class::cast);
    }

    // All dependency edges in deterministic (source id, target id, port) order.
    // Comparator.comparing needs an explicit type witness here, which a static import cannot carry.
    @SuppressWarnings("PMD.UseStaticImports")
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

    String valueKey(final Scope scope, final Location location, final TypeMirror type, final Nullability nullness) {
        return scope.encode() + "::" + location.segment() + "::" + type + "::" + nullness.name();
    }
}

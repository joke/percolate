## ADDED Requirements

### Requirement: Edge endpoints are graph-maintained, not stored on the Edge

`Edge` SHALL NOT carry `from`/`to` fields. The `(source, target)` topology of every edge SHALL be maintained solely by the underlying JGraphT `DirectedMultigraph<Node, Edge>`. Consumers SHALL obtain an edge's endpoints from the graph (or a JGraphT view) via `getEdgeSource(edge)` / `getEdgeTarget(edge)`, or by iterating `incomingEdgesOf(node)` / `outgoingEdgesOf(node)` — never from the `Edge` value.

Every mutation that adds an edge SHALL supply the edge's endpoints alongside the `Edge` payload: `MapperGraph.addEdge(Node from, Node to, Edge edge)`, the `GraphDelta`/`AddEdge` carriers (see their requirements), and the expansion `Applier`. "Re-parenting" an edge (formerly `copyWithEndpoints`) SHALL be expressed as adding the same payload between new vertices, not as cloning endpoint fields.

Because endpoints no longer live on the value, deterministic edge ordering (the `edges()` stream) SHALL be computed by the graph using `getEdgeSource`/`getEdgeTarget` ids, not by an `Edge`-internal `Comparable`.

#### Scenario: Edge exposes no endpoint accessors
- **WHEN** the public surface of `Edge` is inspected
- **THEN** it exposes no `getFrom()` / `getTo()` (or equivalent endpoint) accessor

#### Scenario: Endpoints are read from the graph
- **WHEN** a consumer needs the source or target of an edge `e` in a `MapperGraph` or view `g`
- **THEN** it obtains them via `g.getEdgeSource(e)` / `g.getEdgeTarget(e)` (or `incomingEdgesOf`/`outgoingEdgesOf`)
- **AND** the same edge added between vertices `(a, b)` reports `getEdgeSource == a` and `getEdgeTarget == b`

## MODIFIED Requirements

### Requirement: EdgeKind enum

The processor SHALL define an enum `EdgeKind` in `io.github.joke.percolate.processor.graph` with exactly two values:

- `SEED` — directive-seeded framing edges produced by `SeedStage` from user `@Map` directives. ∞-weight (`Weights.SENTINEL_UNREALISED`).
- `REALISED` — transformation edges produced by `ExpansionStrategy` strategies (bridge, assembly, conversion, and container) during expansion. These are the codegen substrate.

`EdgeKind` SHALL be referenced by `Edge.kind` and SHALL participate in `Edge` comparison and view filtering.

The earlier `SUB_SEED`, `ELEMENT_SEED`, and `MARKER` values were removed: `SUB_SEED`/`ELEMENT_SEED` when target-to-source per-group expansion replaced forward-driven emission, and `MARKER` because it had no production producer (it once linked an untyped seed node to its typed counterpart, a role that vanished when typing became in-place `Node.setTyping`). Nested expansion work is expressed via `ExpansionGroup` registration, not via new edge variants.

#### Scenario: EdgeKind has exactly two values
- **WHEN** `EdgeKind.values()` is invoked
- **THEN** the result contains exactly `SEED`, `REALISED` in declaration order

#### Scenario: SUB_SEED, ELEMENT_SEED, and MARKER are not present
- **WHEN** the source of `EdgeKind` is inspected
- **THEN** no `SUB_SEED`, `ELEMENT_SEED`, or `MARKER` constant is declared

### Requirement: RealisedSubgraph view
The processor SHALL define a class `RealisedSubgraph` in `io.github.joke.percolate.processor.graph` exposing a read-only view over a `MapperGraph` filtered to:
- **edges:** only edges with `kind == EdgeKind.REALISED` (excludes `SEED`),
- **nodes:** only nodes incident on at least one `REALISED` edge.

`RealisedSubgraph` SHALL expose:
- `nodes()` returning `Stream<Node>` in ascending `id()` order,
- `edges()` returning `Stream<Edge>` in ascending natural order,
- `nodesByScope(Scope)` returning the filtered subset of nodes for that scope, in ascending `id()` order.

`RealisedSubgraph` SHALL be read-only — no `addNode` / `addEdge` surface. The wrapper SHALL be implemented over `org.jgrapht.graph.MaskSubgraph` or an equivalent JGraphT view; no graph copy is performed.

#### Scenario: RealisedSubgraph is empty for seed-only graphs
- **WHEN** `MapperGraph.realisedSubgraph()` is invoked on a graph produced by `SeedStage` for any non-empty mapper
- **THEN** `nodes().count() == 0` and `edges().count() == 0`

#### Scenario: RealisedSubgraph is read-only
- **WHEN** the public surface of `RealisedSubgraph` is inspected
- **THEN** no method exposes adding nodes or edges to the underlying graph

#### Scenario: RealisedSubgraph filter excludes SEED edges
- **WHEN** a `MapperGraph` is constructed with one `SEED` edge and one `REALISED` edge (via direct construction in tests)
- **THEN** `realisedSubgraph().edges().count() == 1` and the retained edge is the `REALISED` one

#### Scenario: RealisedSubgraph includes only nodes incident on REALISED edges
- **WHEN** a `MapperGraph` is constructed with a `REALISED` edge between nodes A and B, plus a third unconnected node C (via direct construction in tests)
- **THEN** `realisedSubgraph().nodes()` contains A and B and SHALL NOT contain C

### Requirement: Edge value type
The processor SHALL define a `Edge` class in `io.github.joke.percolate.processor.graph` carrying only edge *payload* (no endpoints — see "Edge endpoints are graph-maintained"):
- `int weight` — uses the scale documented in `Weights`. `SEED` edges use `Weights.SENTINEL_UNREALISED`. `REALISED` edges use a value in `{0, 1, 2, 3}`.
- `EdgeKind kind` — categorises the edge for view filtering, DOT styling, and dispatch.
- `Optional<AnnotationMirror> directive` — present when the edge was seeded by a user `@Map` directive (`kind == SEED`, emitted from `SeedStage`); empty for `REALISED` edges.
- `Optional<Codegen> codegen` — present on `REALISED` edges; empty on `SEED` edges. The value is a member of the `Codegen` family: an `EdgeCodegen` for a scalar edge, or a container provider for a container edge.
- `Optional<ElementScope> elementScope` — present (`ENTERING` / `EXITING`) on a container edge crossing element scope; empty on a scalar edge.
- `Optional<String> strategyClassFqn` — fully-qualified class name of the emitting strategy; empty for `SeedStage`-emitted edges.
- `Optional<Slot> consumerSlot` — the consumer `Slot` for a `REALISED` operand edge (see "Edge carries the consumer Slot contract").

`Edge.equals` SHALL return `this == other` and `Edge.hashCode` SHALL return `System.identityHashCode(this)` — edge equality is **instance identity**, matching `Node`. Structural value-equality (the former `@EqualsAndHashCode(exclude = …)` over `(from, to, weight, kind, directive)`) is removed; it existed only to drive the graph's now-removed dedup index. Duplicate-edge prevention is owned by the mutation sites (`SeedStage`, `Applier`), not by `Edge` equality.

`Edge` SHALL NOT implement an endpoint-based `Comparable`; deterministic edge ordering is computed by the graph (see "MapperGraph wrapper").

`Edge` SHALL provide static factory methods that construct endpoint-less payload; endpoints are supplied to `MapperGraph.addEdge(from, to, edge)`:
- `Edge.seed(Optional<AnnotationMirror> directive)` — a SEED payload with `kind = SEED`, `weight = Weights.SENTINEL_UNREALISED`, empty `codegen`/`elementScope`/`strategyClassFqn`/`consumerSlot`.
- `Edge.realised(int weight, EdgeCodegen codegen, String strategyClassFqn[, Slot consumerSlot])` — scalar REALISED payload.
- `Edge.realised(int weight, Codegen provider, ElementScope elementScope, String strategyClassFqn[, Slot consumerSlot])` — container REALISED payload.

The `Edge.marker(...)` factory is removed (see "EdgeKind enum"). The forward-expansion factories `Edge.subSeed(...)`/`Edge.elementSeed(...)` remain removed. Group membership is determined by `Node` group tags, not by `Edge`.

#### Scenario: Edge carries no endpoints
- **WHEN** an `Edge` value is inspected
- **THEN** it exposes `kind`, `weight`, `directive`, `codegen`, `elementScope`, `strategyClassFqn`, `consumerSlot`
- **AND** it exposes no `from`/`to`

#### Scenario: Directive-seeded edge carries the mirror, kind SEED, sentinel weight
- **WHEN** `Edge.seed(...)` is invoked with a `@Map(target = "lastName", source = "lastName")` directive's mirror
- **THEN** the resulting edge has `kind == EdgeKind.SEED`, `weight == Weights.SENTINEL_UNREALISED`, non-empty `directive`, empty `codegen`

#### Scenario: Realised edge factory populates codegen and strategyClassFqn
- **WHEN** `Edge.realised(Weights.STEP, <closure>, "com.example.SomeBridge")` is invoked
- **THEN** the resulting edge has `kind == EdgeKind.REALISED`, `weight == 1`, empty `directive`, non-empty `codegen`, empty `elementScope`, `strategyClassFqn == "com.example.SomeBridge"`

#### Scenario: Edge equality is identity
- **WHEN** two `Edge` instances are constructed with field-equal payload `(weight, kind, directive, …)`
- **THEN** they compare unequal under `equals` (distinct objects) and their `hashCode`s are the respective identity hashes
- **AND** no `Edge.marker` factory exists

### Requirement: GraphDelta value type

The processor SHALL define an immutable `io.github.joke.percolate.processor.graph.GraphDelta` carrying:
- `List<Node> nodes` — nodes to add. Unmodifiable; empty permitted.
- a list of edge entries to add, each pairing an `Edge` payload with its `(from, to)` endpoints (endpoints are no longer on `Edge` — see "Edge endpoints are graph-maintained"). Unmodifiable; empty permitted.

`GraphDelta` SHALL provide static factories `of(...)`, `empty()`, and single-kind convenience constructors. `GraphDelta` SHALL NOT reference `MapperGraph` or mutable graph state; instances are pure values.

#### Scenario: GraphDelta exposes its node and edge entries
- **WHEN** a `GraphDelta` is constructed with nodes and edge-entries
- **THEN** `getNodes()` returns the nodes in declared order
- **AND** the edge-entries are returned in declared order, each exposing its `(from, to, edge)`

#### Scenario: GraphDelta.empty has no nodes or edges
- **WHEN** `GraphDelta.empty()` is invoked
- **THEN** the returned delta has no nodes and no edge-entries

#### Scenario: GraphDelta is immutable
- **WHEN** a caller attempts to mutate the lists returned by the accessors
- **THEN** the operation throws `UnsupportedOperationException`

### Requirement: MapperGraph wrapper
The processor SHALL define a class `MapperGraph` in `io.github.joke.percolate.processor.graph` that:
- internally holds a `org.jgrapht.graph.DirectedMultigraph<Node, Edge>`,
- exposes `addNode(Node)` (idempotent on equal nodes),
- exposes `addEdge(Node from, Node to, Edge edge)` — a **thin append** that adds both endpoints then the JGraphT edge and returns JGraphT's "was added" boolean. It SHALL NOT maintain a percolate-level structural-equality dedup index; preventing duplicate parallel edges is the responsibility of the mutation callers (`SeedStage`, `Applier`),
- exposes `apply(GraphDelta)` (commits all nodes then all edge-entries, each via `addEdge(from, to, edge)`),
- exposes `nodes()` returning a `Stream<Node>` in ascending `id()` order,
- exposes `edges()` returning a `Stream<Edge>` in ascending order, where the order is computed by the graph from `getEdgeSource(e).id()`, then `getEdgeTarget(e).id()`, then `weight`, then `kind` (the `Edge` no longer self-orders),
- exposes `nodeCount()`, `edgeCount()`, `nodesByScope(Scope)`,
- exposes `realisedSubgraph()`,
- exposes `addGroup(ExpansionGroup)` / `groups()` / `recordGroupOutcome(...)` / `groupOutcomes()`,
- exposes `getEdgeSource(Edge)` / `getEdgeTarget(Edge)` (delegating to JGraphT) for endpoint reads,
- is mutable only via `addNode` / `addEdge` / `apply(GraphDelta)` / `addGroup` / `recordGroupOutcome`; no removal is exposed.

`MapperGraph` SHALL NOT expose node canonicalization (`variableFor`/`registerVariable`) — node identity for seed-time structural variables is owned by `SeedStage` (see "Idempotent node and edge addition" in seed-graph). A fresh `MapperGraph` SHALL be constructed per `Pipeline.process(TypeElement)` invocation.

#### Scenario: addNode is idempotent
- **WHEN** `addNode(n)` is invoked twice with the same `Node` instance
- **THEN** `nodes()` contains exactly one node equal to `n`

#### Scenario: addEdge appends without structural dedup
- **WHEN** `addEdge(a, b, e)` is invoked
- **THEN** the edge is present with `getEdgeSource(e) == a`, `getEdgeTarget(e) == b`
- **AND** `MapperGraph` exposes no structural-equality dedup index

#### Scenario: edges() iteration is sorted by graph-derived order
- **WHEN** several edges are added in arbitrary order
- **THEN** `edges()` returns them ordered by source `id()`, then target `id()`, then `weight`, then `kind`

#### Scenario: MapperGraph exposes no variable canonicalization
- **WHEN** the public surface of `MapperGraph` is inspected
- **THEN** it exposes no `variableFor` or `registerVariable` method

### Requirement: MapperGraph is append-only after construction

After the discover, seed, and expand stages have populated a `MapperGraph`, no stage SHALL remove nodes, edges, registered `ExpansionGroup`s, or `GroupOutcome`s from it. `MapperGraph` exposes no removal methods; the invariant is structural rather than enforced at runtime.

Filtering for any downstream consumer (validation, dumping, future codegen) SHALL be expressed as a view (`RealisedSubgraph`, `transformsView`, `ExpansionGroup.getView()`, or another `MaskSubgraph`) rather than as a destructive mutation.

`SEED` edges and other expansion artifacts SHALL remain in the underlying graph after expansion. The decision whether to render them is made at the view / renderer layer.

#### Scenario: MapperGraph exposes no node, edge, or group removal
- **WHEN** the public surface of `MapperGraph` is inspected
- **THEN** no method removes a node, edge, `ExpansionGroup`, or `GroupOutcome`

## REMOVED Requirements

### Requirement: MapperGraph variable identity

**Reason**: Node canonicalization for seed-time structural variables moves out of the dumb graph container and into `SeedStage`, which already (per the `Node value type` requirement) "is responsible for emitting one `Node` instance per directive-segment-prefix key via its own internal deduplication." `MapperGraph` no longer owns a `variableFor`/`registerVariable`/`variableIndex` surface.

**Migration**: `SeedStage` holds the `(scope, location) → Node` canonical map in a per-`apply` helper and calls only `MapperGraph.addNode`. Expansion-minted nodes remain fresh instances as before (they never routed through `variableFor`). No other component used `variableFor`/`registerVariable`.

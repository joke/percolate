## ADDED Requirements

### Requirement: GraphDelta value type

The processor SHALL define an immutable Lombok `@Value` type `io.github.joke.percolate.processor.graph.GraphDelta` with these fields, in this order:
- `List<Node> nodes` — nodes to add to a `MapperGraph`. The list is unmodifiable; an empty list is permitted.
- `List<Edge> edges` — edges to add to a `MapperGraph`. The list is unmodifiable; an empty list is permitted.

`GraphDelta` SHALL provide static factory methods:
- `GraphDelta of(List<Node> nodes, List<Edge> edges)` — constructs a delta with the given nodes and edges.
- `GraphDelta empty()` — returns a shared, immutable empty delta.
- `GraphDelta nodes(Node... nodes)` and `GraphDelta edges(Edge... edges)` — convenience constructors for single-kind deltas (optional; either may be omitted if not used in v1).

`GraphDelta` SHALL NOT contain references to `MapperGraph` or any other mutable graph state. Instances are pure values.

#### Scenario: GraphDelta exposes its two list fields
- **WHEN** a `GraphDelta` is constructed with a list of nodes and a list of edges
- **THEN** `getNodes()` returns the same nodes in declared order
- **AND** `getEdges()` returns the same edges in declared order

#### Scenario: GraphDelta.empty has no nodes or edges
- **WHEN** `GraphDelta.empty()` is invoked
- **THEN** the returned delta's `getNodes()` is empty
- **AND** the returned delta's `getEdges()` is empty

#### Scenario: GraphDelta is immutable
- **WHEN** a caller attempts to mutate the list returned by `getNodes()` or `getEdges()`
- **THEN** the operation throws `UnsupportedOperationException`

## MODIFIED Requirements

### Requirement: MapperGraph wrapper

The processor SHALL define a class `MapperGraph` in `io.github.joke.percolate.processor.graph` that:
- internally holds a `org.jgrapht.graph.DirectedMultigraph<Node, Edge>`,
- exposes `addNode(Node)` (idempotent on equal nodes),
- exposes `addEdge(Edge)` (rejects duplicate edges by structural equality of the `Edge` non-excluded fields),
- exposes `apply(GraphDelta)` (commits all nodes then all edges from the delta in a single call; equivalent to invoking `addNode` for each node followed by `addEdge` for each edge),
- exposes `nodes()` returning a `Stream<Node>` in ascending `id()` order,
- exposes `edges()` returning a `Stream<Edge>` in ascending natural order,
- exposes `nodeCount()` returning the current number of vertices,
- exposes `edgeCount()` returning the current number of edges,
- exposes `nodesByScope(Scope)` returning the subset of nodes matching that scope, in ascending `id()` order,
- exposes `realisedSubgraph()` returning a `RealisedSubgraph` view (see the corresponding requirement),
- exposes `addGroupCodegen(String groupId, GroupCodegen codegen)` and `groupCodegen(String groupId)` for storing and retrieving group-level codegen closures,
- is mutable only via `addNode` / `addEdge` / `apply(GraphDelta)` / `addGroupCodegen`; no removal is exposed.

`addGroupCodegen` SHALL throw an unchecked exception when invoked twice with the same `groupId` (strict — duplicate group IDs from independently-firing strategies indicate a bug). `groupCodegen` SHALL return `Optional<GroupCodegen>`.

`apply(GraphDelta)` is the preferred mutation entry point for `ExpansionPhase` implementations (see graph-expansion spec). `addNode` and `addEdge` remain available and are used by `SeedGraph` and internally by `apply`.

A fresh `MapperGraph` SHALL be constructed for each `Pipeline.process(TypeElement)` invocation; instances SHALL NOT be retained across processor rounds.

#### Scenario: addNode is idempotent
- **WHEN** `addNode(n)` is invoked twice with two `Node` values that compare equal
- **THEN** `nodes()` contains exactly one node equal to `n`

#### Scenario: nodes() iteration is sorted by id
- **WHEN** three nodes are added in arbitrary order
- **THEN** `nodes().collect(...)` returns them in ascending `id()` order

#### Scenario: edges() iteration is sorted by natural order
- **WHEN** several edges are added in arbitrary order
- **THEN** `edges().collect(...)` returns them in ascending edge order

#### Scenario: nodesByScope filters and preserves order
- **WHEN** a graph contains nodes scoped to `map(Person)` and nodes scoped to `map(Address)`
- **THEN** `nodesByScope(MethodScope(<map(Person)>))` returns only the `map(Person)`-scoped nodes
- **AND** they are returned in ascending `id()` order

#### Scenario: realisedSubgraph returns an empty view for seed-only graphs
- **WHEN** `realisedSubgraph()` is invoked on a graph populated only with `SEED` edges by `SeedGraph`
- **THEN** the returned view's `nodes().count()` and `edges().count()` are both `0`

#### Scenario: addGroupCodegen rejects duplicates
- **WHEN** `addGroupCodegen("Foo(S,S)", c1)` is invoked, then `addGroupCodegen("Foo(S,S)", c2)` is invoked
- **THEN** the second invocation throws an unchecked exception

#### Scenario: groupCodegen returns the stored closure
- **WHEN** `addGroupCodegen("Foo(S,S)", c)` is invoked, then `groupCodegen("Foo(S,S)")` is invoked
- **THEN** the returned `Optional` is non-empty and contains `c`

#### Scenario: groupCodegen returns empty for unknown groupId
- **WHEN** `groupCodegen("nonexistent")` is invoked on a fresh graph
- **THEN** the returned `Optional` is empty

#### Scenario: apply commits all nodes and edges from a delta
- **WHEN** `apply(GraphDelta.of(List.of(n1, n2), List.of(e1, e2)))` is invoked on a fresh graph
- **THEN** `nodes()` contains `n1` and `n2`
- **AND** `edges()` contains `e1` and `e2`

#### Scenario: apply with an empty delta is a no-op
- **WHEN** `apply(GraphDelta.empty())` is invoked
- **THEN** `nodeCount()` and `edgeCount()` are unchanged

#### Scenario: apply is idempotent on duplicate deltas
- **WHEN** the same `GraphDelta` is committed twice via `apply`
- **THEN** the post-state of `nodes()` and `edges()` matches the post-state of a single commit
- **AND** no exception is thrown for the duplicate edges or nodes

#### Scenario: apply commits nodes before edges
- **WHEN** a delta carries an edge whose endpoint nodes are also in the delta's `nodes` list
- **THEN** the call succeeds (nodes are added before the edge references them)

## ADDED Requirements

### Requirement: EdgeKind enum
The processor SHALL define an enum `EdgeKind` in `io.github.joke.percolate.processor.graph` with exactly four values:
- `SEED` — directive-seeded edges produced by `SeedGraph` from user `@Map` directives.
- `REALISED` — edges produced by expansion strategies from real Java machinery (getter call, setter call, method call, container extract/collect, conversion). Not produced in this change.
- `MARKER` — `realises` edges linking a seed node to its realised typed counterpart. Not produced in this change.
- `SUB_SEED` — sub-directive edges emitted by strategies mid-expansion to mark "this still needs realising". Not produced in this change.

`EdgeKind` SHALL be referenced by `Edge.kind` and SHALL participate in `Edge` equality, hash, and comparison.

#### Scenario: EdgeKind has exactly four values
- **WHEN** `EdgeKind.values()` is invoked
- **THEN** the result contains exactly the values `SEED`, `REALISED`, `MARKER`, `SUB_SEED` in that declaration order

### Requirement: Weights constants
The processor SHALL define a final class `Weights` in `io.github.joke.percolate.processor.graph` exposing the documented edge-weight scale as `public static final int` constants:
- `Weights.NOOP = 0` — reference / view / no-op (markers, container extract, identity).
- `Weights.STEP = 1` — single Java operation (getter, setter, method call, optional wrap, conversion).
- `Weights.COPY = 2` — full structural copy / O(n) op (collect, materialise stream).
- `Weights.EXPENSIVE = 3` — reserved for unusually costly operations.
- `Weights.SENTINEL_UNREALISED = Integer.MAX_VALUE / 2` — sentinel weight for `SEED` and `SUB_SEED` edges.

The class SHALL NOT be instantiable.

#### Scenario: Sentinel value is Integer.MAX_VALUE / 2
- **WHEN** `Weights.SENTINEL_UNREALISED` is evaluated
- **THEN** its value is exactly `Integer.MAX_VALUE / 2`

#### Scenario: Realised-scale constants are 0..3
- **WHEN** the constants `Weights.NOOP`, `Weights.STEP`, `Weights.COPY`, `Weights.EXPENSIVE` are read
- **THEN** their values are `0`, `1`, `2`, `3` respectively

### Requirement: ElementLocation case
The processor SHALL define a `Location` implementation `ElementLocation` for phantom container element nodes. `ElementLocation` SHALL be a marker — it carries no payload fields. Its `segment()` SHALL return the literal string `"elem"`.

`ElementLocation` SHALL be a Lombok `@Value` (or equivalent immutable) type. All `ElementLocation` instances SHALL compare equal under `equals` (since they carry no state) and produce identical `segment()` values.

#### Scenario: ElementLocation carries no payload
- **WHEN** an `ElementLocation` is constructed
- **THEN** it has no payload fields; two `ElementLocation` instances are equal under `equals`

#### Scenario: ElementLocation segment is "elem"
- **WHEN** `ElementLocation.segment()` is invoked
- **THEN** the returned string is exactly `"elem"`

### Requirement: EdgeCodegen marker interface
The processor SHALL define an interface `EdgeCodegen` in `io.github.joke.percolate.processor.graph` representing a closure attached to a `REALISED` edge that renders its corresponding code fragment at codegen time. The interface SHALL declare:

```java
interface EdgeCodegen {
    CodeBlock render(VarNames vars, IncomingValues inputs);
}
```

No implementation is shipped in this change. `Edge.codegen` references this type.

#### Scenario: EdgeCodegen is referenced by Edge.codegen
- **WHEN** an `Edge` instance is inspected for the type of `codegen`
- **THEN** the field type is `Optional<EdgeCodegen>`

### Requirement: IncomingValues interface
The processor SHALL define an interface `IncomingValues` in `io.github.joke.percolate.processor.graph` exposing the upstream-rendered inputs to an `EdgeCodegen` closure. The interface SHALL declare:

```java
interface IncomingValues {
    CodeBlock single();
    CodeBlock byGroupPosition(int idx);
    CodeBlock byName(String slotName);
}
```

No implementation is shipped in this change.

#### Scenario: IncomingValues exposes three accessors
- **WHEN** the `IncomingValues` interface is inspected
- **THEN** it declares exactly the methods `single()`, `byGroupPosition(int)`, `byName(String)`, all returning `CodeBlock`

### Requirement: VarNames placeholder type
The processor SHALL define a type `VarNames` in `io.github.joke.percolate.processor.graph` representing a typed handle to the codegen scope's local variable names. In this change `VarNames` SHALL be a marker interface or empty type with no methods; concrete API is reserved for the first realised-edge-emitting strategy.

#### Scenario: VarNames type exists
- **WHEN** the `VarNames` type is inspected
- **THEN** it exists in the `io.github.joke.percolate.processor.graph` package

### Requirement: GroupCodegen interface
The processor SHALL define an interface `GroupCodegen` in `io.github.joke.percolate.processor.graph` representing a closure that renders the wrapping expression for a multi-edge group (constructor parameters, builder chain). The interface SHALL declare:

```java
interface GroupCodegen {
    CodeBlock render(VarNames vars, IncomingValues inputs);
}
```

No implementation is shipped in this change.

#### Scenario: GroupCodegen is referenced by MapperGraph group-storage
- **WHEN** the `MapperGraph` group-codegen storage is inspected for value type
- **THEN** the value type is `GroupCodegen`

### Requirement: RealisedSubgraph view
The processor SHALL define a class `RealisedSubgraph` in `io.github.joke.percolate.processor.graph` exposing a read-only view over a `MapperGraph` filtered to:
- **edges:** only edges with `kind == EdgeKind.REALISED` (excludes `SEED`, `MARKER`, `SUB_SEED`),
- **nodes:** only nodes incident on at least one `REALISED` edge.

`RealisedSubgraph` SHALL expose:
- `nodes()` returning `Stream<Node>` in ascending `id()` order,
- `edges()` returning `Stream<Edge>` in ascending natural order,
- `nodesByScope(Scope)` returning the filtered subset of nodes for that scope, in ascending `id()` order.

`RealisedSubgraph` SHALL be read-only — no `addNode` / `addEdge` surface. The wrapper SHALL be implemented over `org.jgrapht.graph.MaskSubgraph` or an equivalent JGraphT view; no graph copy is performed.

In this change `MapperGraph.realisedSubgraph()` always returns an empty subgraph (zero nodes, zero edges) because no `REALISED` edges are produced.

#### Scenario: RealisedSubgraph is empty for seed-only graphs
- **WHEN** `MapperGraph.realisedSubgraph()` is invoked on a graph produced by `SeedGraph` for any non-empty mapper
- **THEN** `nodes().count() == 0` and `edges().count() == 0`

#### Scenario: RealisedSubgraph is read-only
- **WHEN** the public surface of `RealisedSubgraph` is inspected
- **THEN** no method exposes adding nodes or edges to the underlying graph

#### Scenario: RealisedSubgraph filter excludes SEED, MARKER, SUB_SEED edges
- **WHEN** a `MapperGraph` is constructed with one `SEED` edge, one `MARKER` edge, and one `SUB_SEED` edge (via direct construction in tests)
- **THEN** `realisedSubgraph().edges().count() == 0`

#### Scenario: RealisedSubgraph includes only nodes incident on REALISED edges
- **WHEN** a `MapperGraph` is constructed with a `REALISED` edge between nodes A and B, plus a third unconnected node C (via direct construction in tests)
- **THEN** `realisedSubgraph().nodes()` contains A and B and SHALL NOT contain C

## MODIFIED Requirements

### Requirement: Node value type
The processor SHALL define a Lombok `@Value` class `Node` in the `io.github.joke.percolate.processor.graph` package with the following fields and contract:
- `Optional<TypeMirror> type` — the type of the value the node represents; empty when the type is not yet known (directive-seeded source/target nodes at seed time).
- `Location loc` — where the node sits in the mapping (`SourceLocation`, `TargetLocation`, or `ElementLocation` for phantom container element nodes).
- `Scope scope` — the method or mapper-level scope this node belongs to.
- `Optional<Node> parent` — set ONLY for nodes whose `loc` is `ElementLocation` (phantom container element nodes); empty for every other node. Carries the live reference to the container node from which this phantom was derived.
- `String id()` — a deterministic, stable identity string assembled by `Node` (NOT delegated to `Location`):
  - if `loc` is `ElementLocation`: `id()` = `parent.orElseThrow().id() + "::elem"`,
  - otherwise: `id()` = `scope.encode() + "::" + loc.segment() + "::" + typeEncode()` where `typeEncode()` is the qualified type name when `type` is present, or `"?"` when absent.

For non-phantom nodes, `id()` SHALL produce the same string as the Phase 1 implementation produced for the equivalent `(scope, loc, type)` triple — id stability is required so existing seed-graph tests are unaffected by the ownership flip.

The `Node` class SHALL implement `Comparable<Node>` via `id()` so that sorted iteration of nodes is well-defined.

`Node.parent` SHALL participate in `equals` and `hashCode` (default Lombok `@Value` behaviour). Two non-phantom nodes both with `parent = Optional.empty()` therefore compare equal under the same conditions as in Phase 1.

#### Scenario: Source-rooted node id
- **WHEN** a `Node` is constructed with `loc = SourceLocation(["person"])`, `type = Optional.of(<Person>)`, `scope = MethodScope(<map(Person)>)`, `parent = Optional.empty()`
- **THEN** `id()` is the string `"v::map(Person)::person"` (or the equivalent encoding documented in the implementation)
- **AND** repeated invocations of `id()` return the same string

#### Scenario: Target-rooted node id with unknown type
- **WHEN** a `Node` is constructed with `loc = TargetLocation(["lastName"])`, `type = Optional.empty()`, `scope = MethodScope(<map(Person)>)`, `parent = Optional.empty()`
- **THEN** `id()` includes the `?` marker for the unknown type so that later type discovery does not collide with the seed-time identity

#### Scenario: Phantom node id derives from parent
- **WHEN** a phantom `Node` is constructed with `loc = ElementLocation`, `type = Optional.of(<String>)`, `scope` matching its parent, and `parent = Optional.of(<container node with id "v::map(Foo)::input">)`
- **THEN** `id()` is the string `"v::map(Foo)::input::elem"`

#### Scenario: Phantom node without parent throws
- **WHEN** a `Node` is constructed with `loc = ElementLocation` and `parent = Optional.empty()`, then `id()` is invoked
- **THEN** an unchecked exception is thrown (the construction violates the schema invariant)

#### Scenario: Two non-phantom nodes with equal data have equal ids
- **WHEN** two `Node` instances are constructed with field-equal `loc`, `type`, `scope`, and both `parent = Optional.empty()`
- **THEN** they compare equal under `equals` and produce identical `id()` values

#### Scenario: Two phantoms with different parents have different ids
- **WHEN** two `Node` instances are constructed with the same `loc = ElementLocation`, same `type`, same `scope`, but different `parent` values
- **THEN** they produce different `id()` values and compare unequal under `equals`

### Requirement: Location interface and cases
The processor SHALL define a `Location` interface (package-private in the `graph` sub-package) with three implementations:
- `SourceLocation(AccessPath path)` — rooted at a method parameter; the first segment of `path` is the parameter name.
- `TargetLocation(TargetPath path)` — rooted at the method return type; an empty path denotes the return-type root itself.
- `ElementLocation` — marker for phantom container element nodes; carries no payload (parent reference lives on `Node.parent`).

All cases SHALL be Lombok `@Value`. The interface SHALL declare:
```java
interface Location {
    String segment();   // this location's contribution to Node.id()
}
```

`SourceLocation.segment()` SHALL return a stable encoding of the access path's segments. `TargetLocation.segment()` SHALL return a stable encoding of the target path's segments (with empty path encoded as `"[]"` or equivalent). `ElementLocation.segment()` SHALL return the literal `"elem"`. The renderer is permitted to use `segment()` as a label fallback for DOT output.

#### Scenario: Empty target path denotes the return-type root
- **WHEN** a `TargetLocation` is constructed with an empty `TargetPath`
- **THEN** the location's text-encoding identifies the return-type root unambiguously (e.g., `[]`)
- **AND** a node with this location is the destination of any chain of target slots produced by a `@Map` directive on the same method

#### Scenario: Multi-segment access path
- **WHEN** a `SourceLocation` is constructed with `AccessPath` of `["person", "address", "street"]`
- **THEN** the location's text-encoding preserves the segment order

#### Scenario: ElementLocation segment is "elem"
- **WHEN** `ElementLocation.segment()` is invoked
- **THEN** the returned string is exactly `"elem"`

### Requirement: Edge value type
The processor SHALL define a Lombok `@Value` class `Edge` in `io.github.joke.percolate.processor.graph` with the following fields:
- `Node from`
- `Node to`
- `int weight` — uses the scale documented in `Weights`. `SEED` and `SUB_SEED` edges use `Weights.SENTINEL_UNREALISED`. `REALISED` edges use a value in `{0, 1, 2, 3}`. `MARKER` edges use `Weights.NOOP`.
- `EdgeKind kind` — categorises the edge for view filtering, DOT styling, and dispatch.
- `Optional<AnnotationMirror> directive` — present when the edge was seeded by a user `@Map` directive (i.e., `kind == SEED` and emitted from `SeedGraph`); empty for `SUB_SEED`, `REALISED`, and `MARKER` edges.
- `Optional<String> groupId` — present when the edge participates in a coordinated multi-edge group (constructor parameters, builder chain); empty otherwise.
- `Optional<EdgeCodegen> codegen` — present on `REALISED` edges; empty on `SEED`, `SUB_SEED`, `MARKER` edges.
- `Optional<String> strategyClassFqn` — fully-qualified class name of the strategy that emitted this edge; populated by strategies via `getClass().getName()` at edge construction; empty for edges emitted by `SeedGraph` (which is not a strategy).

`Edge` SHALL be annotated `@Value @EqualsAndHashCode(exclude = {"codegen", "strategyClassFqn"})` so that equality and hashing are structural over `(from, to, weight, kind, directive, groupId)`. The `codegen` and `strategyClassFqn` fields are metadata and SHALL NOT participate in equality.

`Edge` SHALL implement `Comparable<Edge>` ordered by `(from.id(), to.id(), weight, kind, presence-of-directive, groupId)` so that sorted iteration of edges is well-defined.

`Edge` SHALL provide static factory methods that enforce per-kind invariants. The all-args constructor SHALL be package-private; consumers SHALL go through the factories:
- `Edge.seed(Node from, Node to, AnnotationMirror directive)` — produces an edge with `kind = SEED`, `weight = Weights.SENTINEL_UNREALISED`, `directive = Optional.of(directive)`, all other Optional fields empty.
- `Edge.realised(Node from, Node to, int weight, Optional<String> groupId, EdgeCodegen codegen, String strategyClassFqn)` — produces an edge with `kind = REALISED`, the supplied weight, `directive = Optional.empty()`, `codegen = Optional.of(codegen)`, `strategyClassFqn = Optional.of(strategyClassFqn)`.
- `Edge.marker(Node from, Node to, String strategyClassFqn)` — produces an edge with `kind = MARKER`, `weight = Weights.NOOP`, `directive`, `groupId`, `codegen` empty, `strategyClassFqn` populated.
- `Edge.subSeed(Node from, Node to, String strategyClassFqn)` — produces an edge with `kind = SUB_SEED`, `weight = Weights.SENTINEL_UNREALISED`, `directive`, `groupId`, `codegen` empty, `strategyClassFqn` populated.

#### Scenario: Directive-seeded edge carries the mirror, kind SEED, sentinel weight
- **WHEN** `Edge.seed(...)` is invoked with a `@Map(target = "lastName", source = "lastName")` directive's mirror
- **THEN** the resulting edge has `kind == EdgeKind.SEED`, `weight == Weights.SENTINEL_UNREALISED`, non-empty `directive` containing that `AnnotationMirror`, and empty `groupId`, `codegen`, `strategyClassFqn`

#### Scenario: Realised edge factory populates codegen and strategyClassFqn
- **WHEN** `Edge.realised(from, to, Weights.STEP, Optional.empty(), <closure>, "com.example.GetterReadStrategy")` is invoked
- **THEN** the resulting edge has `kind == EdgeKind.REALISED`, `weight == 1`, empty `directive`, empty `groupId`, non-empty `codegen` referencing the closure, non-empty `strategyClassFqn` equal to `"com.example.GetterReadStrategy"`

#### Scenario: Marker edge has weight zero, no codegen
- **WHEN** `Edge.marker(seedNode, realisedNode, "com.example.GetterReadStrategy")` is invoked
- **THEN** the resulting edge has `kind == EdgeKind.MARKER`, `weight == 0`, empty `directive`, `groupId`, `codegen`, and non-empty `strategyClassFqn`

#### Scenario: SubSeed edge has sentinel weight, no directive
- **WHEN** `Edge.subSeed(from, to, "com.example.SomeStrategy")` is invoked
- **THEN** the resulting edge has `kind == EdgeKind.SUB_SEED`, `weight == Weights.SENTINEL_UNREALISED`, empty `directive`, `groupId`, `codegen`, and non-empty `strategyClassFqn`

#### Scenario: Edge equality excludes codegen and strategyClassFqn
- **WHEN** two `Edge` instances are constructed with field-equal `(from, to, weight, kind, directive, groupId)` but different `codegen` closures and different `strategyClassFqn` strings
- **THEN** they compare equal under `equals` and produce identical hash codes

#### Scenario: Edge equality includes kind
- **WHEN** two `Edge` instances are constructed with field-equal `(from, to, weight, directive, groupId)` but different `kind` values
- **THEN** they compare unequal under `equals`

#### Scenario: Edges have stable ordering
- **WHEN** two `Edge` values are compared
- **THEN** the comparison is determined entirely by `from.id()`, then `to.id()`, then `weight`, then `kind`, then directive-presence, then `groupId` (no reliance on identity hashes)

### Requirement: MapperGraph wrapper
The processor SHALL define a class `MapperGraph` in `io.github.joke.percolate.processor.graph` that:
- internally holds a `org.jgrapht.graph.DirectedMultigraph<Node, Edge>`,
- exposes `addNode(Node)` (idempotent on equal nodes),
- exposes `addEdge(Edge)` (rejects duplicate edges by structural equality of the `Edge` non-excluded fields),
- exposes `nodes()` returning a `Stream<Node>` in ascending `id()` order,
- exposes `edges()` returning a `Stream<Edge>` in ascending natural order,
- exposes `nodesByScope(Scope)` returning the subset of nodes matching that scope, in ascending `id()` order,
- exposes `realisedSubgraph()` returning a `RealisedSubgraph` view (see the corresponding requirement),
- exposes `addGroupCodegen(String groupId, GroupCodegen codegen)` and `groupCodegen(String groupId)` for storing and retrieving group-level codegen closures,
- is mutable only via `addNode` / `addEdge` / `addGroupCodegen`; no removal is exposed.

`addGroupCodegen` SHALL throw an unchecked exception when invoked twice with the same `groupId` (strict — duplicate group IDs from independently-firing strategies indicate a bug). `groupCodegen` SHALL return `Optional<GroupCodegen>`.

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

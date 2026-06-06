# Graph Model Spec

## Purpose

This spec defines the core graph model value types (`Node`, `Edge`, `Location`, `AccessPath`, `TargetPath`, `Scope`) and the `MapperGraph` wrapper used by the seed-graph and debug-output stages.

## Requirements

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

### Requirement: Weights constants
The processor SHALL define a final class `Weights` in `io.github.joke.percolate.processor.graph` exposing the documented edge-weight scale as `public static final int` constants:
- `Weights.NOOP = 0` — reference / view / no-op (markers, container extract, identity).
- `Weights.STEP = 1` — single Java operation (getter, setter, method call, optional wrap, conversion).
- `Weights.COPY = 2` — full structural copy / O(n) op (collect, materialise stream).
- `Weights.EXPENSIVE = 3` — reserved for unusually costly operations.
- `Weights.SENTINEL_UNREALISED = Integer.MAX_VALUE / 2` — sentinel weight for `SEED` edges.

The class SHALL NOT be instantiable.

#### Scenario: Sentinel value is Integer.MAX_VALUE / 2
- **WHEN** `Weights.SENTINEL_UNREALISED` is evaluated
- **THEN** its value is exactly `Integer.MAX_VALUE / 2`

#### Scenario: Realised-scale constants are 0..3
- **WHEN** the constants `Weights.NOOP`, `Weights.STEP`, `Weights.COPY`, `Weights.EXPENSIVE` are read
- **THEN** their values are `0`, `1`, `2`, `3` respectively

### Requirement: ElementLocation case

The processor SHALL define a `Location` implementation `ElementLocation` for phantom container element nodes. `ElementLocation` SHALL carry a single `String role` field that discriminates between scopes within multi-role containers; the default value for single-element-scope containers is the literal string `"element"`.

`ElementLocation` SHALL be a Lombok `@Value` (or equivalent immutable) type. Its `segment()` SHALL return the string `"elem(" + role + ")"` — e.g. `"elem(element)"` for the default role, `"elem(key)"` / `"elem(value)"` for future `Map<K,V>` element scopes.

A no-argument public constructor SHALL be preserved (whether via a static factory or a secondary constructor) that produces an `ElementLocation` with `role = "element"`. This preserves call-site compatibility with existing test fixtures and node-construction code that does not yet thread a role.

Two `ElementLocation` instances SHALL compare equal iff their `role` fields are equal.

#### Scenario: ElementLocation carries role
- **WHEN** an `ElementLocation` is constructed with `role = "key"`
- **THEN** `getRole()` returns `"key"`
- **AND** `segment()` returns `"elem(key)"`

#### Scenario: ElementLocation default role is "element"
- **WHEN** an `ElementLocation` is constructed via the no-argument factory / constructor
- **THEN** `getRole()` returns `"element"`
- **AND** `segment()` returns `"elem(element)"`

#### Scenario: ElementLocation equality by role
- **WHEN** two `ElementLocation` instances are constructed with the same `role`
- **THEN** they are `equal` and have equal `hashCode`s

#### Scenario: ElementLocation inequality by role
- **WHEN** two `ElementLocation` instances are constructed with different `role` strings (e.g. `"key"` vs `"value"`)
- **THEN** they are NOT `equal`

### Requirement: EdgeCodegen marker interface
`EdgeCodegen` SHALL be a member of the `Codegen` handle family: it SHALL extend the `Codegen` marker interface defined by the container-codegen SPI. It represents a scalar closure attached to a `REALISED` edge that renders one expression at codegen time:

```java
interface EdgeCodegen extends Codegen {
    CodeBlock render(VarNames vars, IncomingValues inputs);
}
```

`Edge.codegen` references the `Codegen` family (an `EdgeCodegen` for a scalar edge, a container provider for a container edge), not `EdgeCodegen` exclusively.

#### Scenario: EdgeCodegen is a Codegen
- **WHEN** the `EdgeCodegen` interface is inspected
- **THEN** it extends `Codegen`

#### Scenario: Edge.codegen holds the Codegen family
- **WHEN** an `Edge` instance is inspected for the type of `codegen`
- **THEN** the field type is `Optional<Codegen>`

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

### Requirement: Node value type

The processor SHALL define a class `Node` in `io.github.joke.percolate.processor.graph` with the following fields and contract:

- `Optional<TypeMirror> type` — the type of the value the node represents; empty when the type is not yet known.
- `Optional<Nullability> nullability` — the nullability of the value the node represents; empty when the type is not yet known. **Always paired with `type`**: both are empty before producer commit; both are populated after.
- `Location loc` — where the node sits in the mapping (`SourceLocation`, `TargetLocation`, or `ElementLocation` for phantom container element nodes). Immutable.
- `Scope scope` — the method or mapper-level scope this node belongs to. Immutable.
- `Optional<Node> parent` — set ONLY for nodes whose `loc` is `ElementLocation`; empty for every other node. Immutable.
- `String id()` — a deterministic identity string for debug rendering and sorted iteration:
  - if `loc` is `ElementLocation`: `id()` = `parent.orElseThrow().id() + "::" + loc.segment() + "::" + typeEncode() + "@" + System.identityHashCode(this)`.
  - otherwise: `id()` = `scope.encode() + "::" + loc.segment() + "::" + typeEncode() + "@" + System.identityHashCode(this)`.
  - `typeEncode()` is the qualified type name when `type` is present, or `"?"` when absent.

Both `type` and `nullability` are **mutable after construction** under one paired one-shot accessor:

```java
public void setTyping(TypeMirror type, Nullability nullability);
```

`setTyping` SHALL throw if **either** `type` or `nullability` is already present at the call site. After a successful call, both fields are populated. The legacy single-field `setType(TypeMirror)` accessor SHALL be removed; callers SHALL migrate to `setTyping(...)`.

The `@<identityHashCode>` suffix disambiguates same-shape instances in DOT output and sorted iteration. It is the visible reflection of the underlying instance-identity rule.

`Node.equals(Object)` SHALL return `this == other`. `Node.hashCode()` SHALL return `System.identityHashCode(this)`. Two `Node` instances with field-equal `(loc, type, nullability, scope, parent)` are distinct nodes if they are different objects in memory.

The `Node` class SHALL implement `Comparable<Node>` via `id()` so that sorted iteration of nodes is well-defined.

Instance-identity is the load-bearing structural property that makes cross-sub-group cycles impossible during expansion: a `Node` freshly allocated by one sub-group's bridge expansion is a distinct object from any same-shape `Node` allocated by a sibling sub-group, so candidate scans within a sub-group's view cannot pick up downstream instances even when their `(scope, loc, type)` would match.

`SeedGraph` is responsible for emitting **one `Node` instance per directive-segment-prefix key** via its own internal deduplication (a `Map<KeyTuple, Node>` populated as the seed is walked). Idempotence at the graph level (`MapperGraph.addNode(node)` being a no-op on `node == existingNode`) means `SeedGraph`'s own dedup is the convergence mechanism for seed nodes; bridge expansion creates fresh distinct instances.

#### Scenario: Two field-equal Nodes are distinct under instance identity
- **WHEN** two `Node` instances are constructed with field-equal `loc`, `type`, `nullability`, `scope`, and `parent`
- **THEN** they compare unequal under `equals`
- **AND** their `hashCode`s differ
- **AND** their `id()` strings differ in the `@<identityHashCode>` suffix

#### Scenario: SeedGraph dedup yields a single Node instance per directive prefix
- **WHEN** two directives `@Map(source = "person.addresses")` and `@Map(source = "person.lastName")` are processed by `SeedGraph` for the same method
- **THEN** exactly one `Node` instance exists for `SourceLocation(["person"])` in the resulting graph
- **AND** both directives' downstream SEED edges share that single `Node` object

#### Scenario: setTyping updates both type and nullability in place
- **WHEN** a `Node` constructed with both `type` and `nullability` empty has `setTyping(<List<Opt<PA>>>, NULLABLE)` invoked
- **THEN** `getType()` returns `Optional.of(<List<Opt<PA>>>)`
- **AND** `getNullability()` returns `Optional.of(NULLABLE)`

#### Scenario: setTyping is one-shot — calling twice throws
- **WHEN** a `Node` has had `setTyping(...)` invoked once
- **AND** `setTyping(...)` is invoked again
- **THEN** an `IllegalStateException` is thrown

#### Scenario: setTyping rejects partial prior state
- **WHEN** a hypothetical Node has `type` set but `nullability` empty (or vice versa)
- **AND** `setTyping(...)` is invoked
- **THEN** an `IllegalStateException` is thrown — both fields must be empty before the call

#### Scenario: Legacy setType is removed
- **WHEN** the public API of `Node` is inspected
- **THEN** no method named `setType(TypeMirror)` exists
- **AND** the only typing-mutation accessor is `setTyping(TypeMirror, Nullability)`

#### Scenario: Source-rooted node id includes identity suffix
- **WHEN** a `Node` is constructed with `loc = SourceLocation(["person"])`, `type = Optional.of(<Person>)`, `nullability = Optional.of(NON_NULL)`, `scope = MethodScope(<map(Person)>)`, `parent = Optional.empty()`
- **THEN** `id()` is the string `"v::map(Person)::person@" + identityHashCode` (or the equivalent documented encoding)
- **AND** repeated invocations of `id()` return the same string

#### Scenario: Target-rooted node id with unknown type
- **WHEN** a `Node` is constructed with `loc = TargetLocation(["lastName"])`, `type = Optional.empty()`, `nullability = Optional.empty()`, `scope = MethodScope(<map(Person)>)`, `parent = Optional.empty()`
- **THEN** `id()` includes the `?` marker for the unknown type so that later type discovery (via `setTyping`) does not collide

#### Scenario: Phantom node id derives from parent, role, and type
- **WHEN** a phantom `Node` is constructed with `loc = ElementLocation("element")`, `type = Optional.of(<String>)`, `nullability = Optional.of(NON_NULL)`, `scope` matching its parent, and `parent = Optional.of(<container node>)`
- **THEN** `id()` starts with the parent's `id()` prefix, followed by `::elem(element)::String@<identityHashCode>`

#### Scenario: Phantom node without parent throws
- **WHEN** a `Node` is constructed with `loc = ElementLocation` and `parent = Optional.empty()`, then `id()` is invoked
- **THEN** an unchecked exception is thrown

#### Scenario: Two phantoms with different parents are distinct
- **WHEN** two `Node` instances are constructed with the same `loc = ElementLocation("element")`, same `type`, same `nullability`, same `scope`, but different `parent` values
- **THEN** they compare unequal under `equals`

#### Scenario: Two phantoms with same parent and role but different types are distinct
- **WHEN** two phantom `Node` instances are constructed with the same `loc = ElementLocation("element")`, same `scope`, same `parent`, but different `type` values
- **THEN** they compare unequal under `equals` (which is true vacuously under instance identity, but the contract holds without relying on it)

#### Scenario: Two phantoms with same parent and type but different roles are distinct
- **WHEN** two phantom `Node` instances are constructed with `loc = ElementLocation("key")` and `loc = ElementLocation("value")` respectively, same `scope`, same `parent`, same `type`
- **THEN** they compare unequal under `equals`

### Requirement: Location interface and cases
The processor SHALL define a `Location` interface (package-private in the `graph` sub-package) with three implementations:
- `SourceLocation(AccessPath path)` — rooted at a method parameter; the first segment of `path` is the parameter name.
- `TargetLocation(TargetPath path)` — rooted at the method return type; an empty path denotes the return-type root itself.
- `ElementLocation(String role)` — marker for phantom container element nodes; the `role` field discriminates between element scopes within multi-role containers. The default role for single-element-scope containers is `"element"`. The parent reference lives on `Node.parent`.

All cases SHALL be Lombok `@Value`. The interface SHALL declare:
```java
interface Location {
    String segment();   // this location's contribution to Node.id()
}
```

`SourceLocation.segment()` SHALL return a stable encoding of the access path's segments. `TargetLocation.segment()` SHALL return a stable encoding of the target path's segments (with empty path encoded as `"[]"` or equivalent). `ElementLocation.segment()` SHALL return the string `"elem(" + role + ")"`. The renderer is permitted to use `segment()` as a label fallback for DOT output.

#### Scenario: Empty target path denotes the return-type root
- **WHEN** a `TargetLocation` is constructed with an empty `TargetPath`
- **THEN** the location's text-encoding identifies the return-type root unambiguously (e.g., `[]`)
- **AND** a node with this location is the destination of any chain of target slots produced by a `@Map` directive on the same method

#### Scenario: Multi-segment access path
- **WHEN** a `SourceLocation` is constructed with `AccessPath` of `["person", "address", "street"]`
- **THEN** the location's text-encoding preserves the segment order

#### Scenario: ElementLocation segment includes role
- **WHEN** an `ElementLocation("element").segment()` is invoked
- **THEN** the returned string is exactly `"elem(element)"`
- **WHEN** an `ElementLocation("key").segment()` is invoked
- **THEN** the returned string is exactly `"elem(key)"`

### Requirement: AccessPath and TargetPath value types
The processor SHALL define `AccessPath` and `TargetPath` Lombok `@Value` classes wrapping `List<String>` of segments. Each SHALL expose:
- a `segments()` accessor returning an immutable list,
- an `append(String segment)` factory returning a new path with the additional segment,
- equality and `hashCode` by segment-list content,
- a deterministic `toString()` encoding (e.g. dot-joined).

`AccessPath` and `TargetPath` SHALL be distinct types so that source and target paths cannot be assigned to each other by accident.

#### Scenario: Append produces a new path
- **WHEN** an `AccessPath` of `["person"]` has `append("address")` invoked
- **THEN** a new `AccessPath` of `["person", "address"]` is returned
- **AND** the original `AccessPath` is unchanged

#### Scenario: AccessPath and TargetPath are not interchangeable
- **WHEN** an `AccessPath` value is supplied where a `TargetPath` is required
- **THEN** the code SHALL not compile (different types)

### Requirement: Scope interface and cases
The processor SHALL define a `Scope` interface with two implementations:
- `MethodScope(ExecutableElement method)` — the scope produced by the seed stage for every node and edge.
- `MapperScope` — reserved for future mapper-shared elements (e.g., routable methods); not produced by the seed stage in this change.

`Scope` SHALL produce a stable text-encoding suitable for embedding into `Node.id()` and DOT cluster names.

#### Scenario: Method scope encodes the method signature
- **WHEN** a `MethodScope` is constructed for an `ExecutableElement` representing `Human map(Person person)`
- **THEN** its text-encoding is a stable string derived from the method name and erased parameter types (e.g., `map(Person)`) and is identical for repeated invocations

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

#### Scenario: nodesByScope filters and preserves order
- **WHEN** a graph contains nodes scoped to `map(Person)` and nodes scoped to `map(Address)`
- **THEN** `nodesByScope(MethodScope(<map(Person)>))` returns only the `map(Person)`-scoped nodes
- **AND** they are returned in ascending `id()` order

#### Scenario: addGroup appends to the registry
- **WHEN** `addGroup(g1)` is invoked, then `addGroup(g2)` is invoked
- **THEN** `groups()` returns a stream containing `g1` then `g2` in that order

#### Scenario: addGroup validates membership
- **WHEN** `addGroup(group)` is invoked with a `group` whose root is not in `underlyingGraph().vertexSet()`
- **THEN** an `IllegalArgumentException` is thrown and the registry is unchanged

#### Scenario: apply commits all nodes and edge-entries from a delta
- **WHEN** `apply(...)` is invoked on a fresh graph with a delta carrying nodes `n1, n2` and an edge-entry `(n1, n2, e1)`
- **THEN** `nodes()` contains `n1` and `n2`
- **AND** `edges()` contains `e1` with `getEdgeSource(e1) == n1`, `getEdgeTarget(e1) == n2`

#### Scenario: apply with an empty delta is a no-op
- **WHEN** `apply(GraphDelta.empty())` is invoked
- **THEN** `nodeCount()` and `edgeCount()` are unchanged

#### Scenario: apply commits nodes before edges
- **WHEN** a delta carries an edge-entry whose endpoint nodes are also in the delta's `nodes` list
- **THEN** the call succeeds (nodes are added before the edge references them)

### Requirement: ExpansionGroup value type

The processor SHALL define a final type `ExpansionGroup` in
`io.github.joke.percolate.processor.graph` that is a **logical grouping label only** — it holds no
graph state, carries no codegen, and is NEVER traversed by code generation. It SHALL expose exactly:

- `GroupId id` — a thin value-type identity for the group (not a raw `String`), used for membership
  filtering and for the engine's SAT bookkeeping.
- `Node root` — the fan-in target node this group's demand produces.

Group **membership** is NOT stored on `ExpansionGroup`. A `Node` carries the set of groups it belongs
to (see "Node carries group-membership labels"); a group's **view** is derived on demand as a
`org.jgrapht.graph.MaskSubgraph` over `parent.underlyingGraph()`:

```
vertexMask = v -> !v.groups().contains(this.id)
edgeMask   = e -> e.getKind() != EdgeKind.REALISED
```

so the view shows exactly the group's tagged nodes and the `REALISED` edges between them. Because the
vertex mask hides edges with a masked endpoint, edge membership follows vertex membership; no edge
carries a group tag.

`ExpansionGroup` SHALL NOT expose `slots`, `getCodegen`, `strategyClassFqn`, `addVertexToView`,
`addEdgeToView`, `slotMetadata`, `expectedTypeFor`, `consumerContractFor`, `conversionFrontiers`, or
an `AsSubgraph` view. The `of(...)` factory `initialEdges` parameter and `validateInitialEdge` are
removed. "Adding a node to a group" is a single tag mutation on the `Node`, performed only by the
`Applier`.

The slots of a group (its demand's inputs) and its SAT status are derived: the input nodes are the
`from` endpoints of the `root`'s incoming REALISED edges within the view; SAT is recorded
engine-side, not on the group.

#### Scenario: ExpansionGroup exposes only id and root
- **WHEN** the public surface of `ExpansionGroup` is inspected
- **THEN** it exposes `getId()` and `getRoot()` and a derived `view()` (a `MaskSubgraph`)
- **AND** it exposes no `getSlots`, `getCodegen`, `getStrategyClassFqn`, `addVertexToView`,
  `addEdgeToView`, `slotMetadata`, `expectedTypeFor`, `consumerContractFor`, or `conversionFrontiers`

#### Scenario: Group view is a MaskSubgraph derived from node tags
- **WHEN** a group with `id = G` and `root = r` is constructed and nodes `r`, `a` are tagged with `G`
  and a `REALISED` edge `a → r` is added to the underlying graph
- **THEN** `group.view().vertexSet()` equals `{r, a}`
- **AND** `group.view().edgeSet()` contains the `a → r` REALISED edge
- **AND** a node not tagged with `G` is absent from `group.view().vertexSet()`

#### Scenario: Group view excludes non-REALISED edges and cross-group leak on shared nodes
- **WHEN** node `person.address` is tagged with both group `A` (where it is `root`) and group `B`
  (where it is an input), and `REALISED` edges `person → person.address` (in A) and
  `person.address → person.address.street` (in B) exist
- **THEN** `A.view().edgeSet()` contains `person → person.address` and NOT
  `person.address → person.address.street`
- **AND** `B.view()` contains the converse — no REALISED edge leaks across the shared boundary node

### Requirement: Node carries group-membership labels

`Node` SHALL carry an insertion-ordered, mutable set of `GroupId` labels recording the
`ExpansionGroup`s the node belongs to, exposed as `Set<GroupId> groups()`. A `Node` MAY belong to
many groups simultaneously (e.g. `person.address` is the `root` of one source-descent group and an
input of another). Group membership SHALL be mutated **only** by the `Applier` (the single mutation
site). The membership set SHALL be insertion-ordered for deterministic iteration. Group membership
SHALL NOT participate in `Node.equals`/`hashCode` (which remain instance-identity).

#### Scenario: A node can belong to multiple groups
- **WHEN** node `person.address` is tagged with group ids `G1` (as root) and `G2` (as input)
- **THEN** `node.groups()` contains both `G1` and `G2`
- **AND** iteration order is the order in which they were added

#### Scenario: Membership does not affect node identity
- **WHEN** two field-equal `Node` instances have different `groups()` sets
- **THEN** they remain unequal under `equals` purely by instance identity, and membership is not
  consulted

### Requirement: Edge carries the consumer Slot contract

A `REALISED` `Edge` SHALL carry the consumer `Slot` for the input it wires (the declared input type
and the `AnnotatedConstruct producedFrom` consumer contract). Code generation SHALL read a slot's
consumer contract from the **consuming edge's** `Slot` (the operand edge), not from any
`ExpansionGroup`. For an n-ary producer, each operand edge in the fan-in carries its own `Slot`.

#### Scenario: REALISED edge exposes its consumer Slot
- **WHEN** a `REALISED` operand edge feeding a constructor parameter is inspected
- **THEN** it exposes the consumer `Slot` carrying the declared parameter type and the
  `AnnotatedConstruct producedFrom`
- **AND** code generation derives the consumer contract from that edge, not from a group

### Requirement: GroupOutcome value type

The processor SHALL define a final value type `GroupOutcome` in `io.github.joke.percolate.processor.graph` with three Lombok-generated factory constants/methods:

- `GroupOutcome.sat(ExpansionGroup group)` — all slots and the root satisfied via REALISED reachability.
- `GroupOutcome.unsatNoPlan(ExpansionGroup group, Node failingSlot)` — `resolveSlot(failingSlot)` exhausted its expansion without producing new nodes.
- `GroupOutcome.unsatDidNotConverge(ExpansionGroup group, Node failingSlot)` — `resolveSlot(failingSlot)` exceeded the round budget or the work-list budget was exhausted.

`GroupOutcome` exposes `getKind()` (enum: `SAT`, `UNSAT_NO_PLAN`, `UNSAT_DID_NOT_CONVERGE`), `getGroup()`, and `getFailingSlot()` (returns an `Optional<Node>`; empty for SAT).

`MapperGraph` SHALL expose `recordGroupOutcome(GroupOutcome outcome)` and `groupOutcomes()` to register and iterate outcomes during validation.

#### Scenario: GroupOutcome.sat carries the group only
- **WHEN** `GroupOutcome.sat(group)` is constructed
- **THEN** `getKind() == SAT`, `getGroup() == group`, `getFailingSlot().isEmpty()`

#### Scenario: GroupOutcome.unsatNoPlan carries the failing slot
- **WHEN** `GroupOutcome.unsatNoPlan(group, slot)` is constructed
- **THEN** `getKind() == UNSAT_NO_PLAN`, `getFailingSlot()` is non-empty and contains `slot`

### Requirement: MapperGraph is append-only after construction

After the discover, seed, and expand stages have populated a `MapperGraph`, no stage SHALL remove nodes, edges, registered `ExpansionGroup`s, or `GroupOutcome`s from it. `MapperGraph` exposes no removal methods; the invariant is structural rather than enforced at runtime.

Filtering for any downstream consumer (validation, dumping, future codegen) SHALL be expressed as a view (`RealisedSubgraph`, `transformsView`, `ExpansionGroup.getView()`, or another `MaskSubgraph`) rather than as a destructive mutation.

`SEED` edges and other expansion artifacts SHALL remain in the underlying graph after expansion. The decision whether to render them is made at the view / renderer layer.

#### Scenario: MapperGraph exposes no node, edge, or group removal
- **WHEN** the public surface of `MapperGraph` is inspected
- **THEN** no method removes a node, edge, `ExpansionGroup`, or `GroupOutcome`

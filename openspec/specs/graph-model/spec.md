# Graph Model Spec

## Purpose

This spec defines the core graph model value types (`Node`, `Edge`, `Location`, `AccessPath`, `TargetPath`, `Scope`) and the `MapperGraph` wrapper used by the seed-graph and debug-output stages.

## Requirements

### Requirement: Node value type
The processor SHALL define a Lombok `@Value` class `Node` in the `io.github.joke.percolate.processor.graph` package with the following fields and contract:
- `Optional<TypeMirror> type` — the type of the value the node represents; empty when the type is not yet known (directive-seeded source/target nodes at seed time).
- `Location loc` — where the node sits in the mapping (a `SourceLocation` rooted at a parameter, a `TargetLocation` rooted at the return type, or no `Location` for future intermediate values).
- `Scope scope` — the method or mapper-level scope this node belongs to.
- `String id()` — a deterministic, stable identity string derived from `loc`, `type` (encoded as the qualified type name, or `?` when absent), and `scope`. Equal nodes (per `equals`) SHALL produce equal `id()` values; the `id()` SHALL NOT depend on hash codes, identity, or insertion order.

The `Node` class SHALL implement `Comparable<Node>` via `id()` so that sorted iteration of nodes is well-defined.

#### Scenario: Source-rooted node id
- **WHEN** a `Node` is constructed with `loc = SourceLocation(["person"])`, `type = Optional.of(<Person>)`, `scope = MethodScope(<map(Person)>)`
- **THEN** `id()` is the string `"v::map(Person)::person"` (or the equivalent encoding documented in the implementation)
- **AND** repeated invocations of `id()` return the same string

#### Scenario: Target-rooted node id with unknown type
- **WHEN** a `Node` is constructed with `loc = TargetLocation(["lastName"])`, `type = Optional.empty()`, `scope = MethodScope(<map(Person)>)`
- **THEN** `id()` includes the `?` marker for the unknown type so that later type discovery does not collide with the seed-time identity

#### Scenario: Two nodes with equal data have equal ids
- **WHEN** two `Node` instances are constructed with field-equal `loc`, `type`, and `scope`
- **THEN** they compare equal under `equals` and produce identical `id()` values

### Requirement: Location interface and cases
The processor SHALL define a `Location` interface (package-private in the `graph` sub-package) with two implementations:
- `SourceLocation(AccessPath path)` — rooted at a method parameter; the first segment of `path` is the parameter name.
- `TargetLocation(TargetPath path)` — rooted at the method return type; an empty path denotes the return-type root itself.

Both cases SHALL be Lombok `@Value`. `Location` SHALL provide a textual segment-encoding suitable for embedding into `Node.id()`.

#### Scenario: Empty target path denotes the return-type root
- **WHEN** a `TargetLocation` is constructed with an empty `TargetPath`
- **THEN** the location's text-encoding identifies the return-type root unambiguously (e.g., `[]`)
- **AND** a node with this location is the destination of any chain of target slots produced by a `@Map` directive on the same method

#### Scenario: Multi-segment access path
- **WHEN** a `SourceLocation` is constructed with `AccessPath` of `["person", "address", "street"]`
- **THEN** the location's text-encoding preserves the segment order

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

### Requirement: Edge value type
The processor SHALL define a Lombok `@Value` class `Edge` with the following fields:
- `Node from`
- `Node to`
- `int weight` — at seed time uniformly `1`. Future expansion edges may carry different weights.
- `Optional<AnnotationMirror> directive` — present when the edge was seeded by a `@Map` directive; carries the `AnnotationMirror` of that directive for IDE-quality error positioning. Empty for non-directive edges (none in this change).

`Edge` SHALL implement `Comparable<Edge>` ordered by `(from.id(), to.id(), weight, presence-of-directive)` so that sorted iteration of edges is well-defined.

#### Scenario: Directive-seeded edge carries the mirror
- **WHEN** the seed stage emits an edge for a `@Map(target = "lastName", source = "lastName")` directive
- **THEN** the edge's `directive` `Optional` is non-empty and contains the `AnnotationMirror` of that `@Map` annotation

#### Scenario: Edges have stable ordering
- **WHEN** two `Edge` values are compared
- **THEN** the comparison is determined entirely by `from.id()`, then `to.id()`, then `weight`, then directive-presence (no reliance on identity hashes)

### Requirement: MapperGraph wrapper
The processor SHALL define a class `MapperGraph` in `io.github.joke.percolate.processor.graph` that:
- internally holds a `org.jgrapht.graph.DirectedMultigraph<Node, Edge>`,
- exposes `addNode(Node)` (idempotent on equal nodes),
- exposes `addEdge(Edge)` (rejects duplicate edges by structural equality of `(from, to, weight, directive)`),
- exposes `nodes()` returning a `Stream<Node>` in ascending `id()` order,
- exposes `edges()` returning a `Stream<Edge>` in ascending natural order,
- exposes `nodesByScope(Scope)` returning the subset of nodes matching that scope, in ascending `id()` order,
- is mutable only via `addNode` / `addEdge`; no removal is exposed.

A fresh `MapperGraph` SHALL be constructed for each `Pipeline.process(TypeElement)` invocation; instances SHALL NOT be retained across processor rounds.

#### Scenario: addNode is idempotent
- **WHEN** `addNode(n)` is invoked twice with two `Node` values that compare equal
- **THEN** `nodes()` contains exactly one node equal to `n`

#### Scenario: nodes() iteration is sorted by id
- **WHEN** three nodes are added in arbitrary order
- **THEN** `nodes().collect(...)` returns them in ascending `id()` order

#### Scenario: edges() iteration is sorted by natural order
- **WHEN** several edges are added in arbitrary order
- **THEN** `edges().collect(...)` returns them in ascending edge order (by `(from.id(), to.id(), weight, directive-presence)`)

#### Scenario: nodesByScope filters and preserves order
- **WHEN** a graph contains nodes scoped to `map(Person)` and nodes scoped to `map(Address)`
- **THEN** `nodesByScope(MethodScope(<map(Person)>))` returns only the `map(Person)`-scoped nodes
- **AND** they are returned in ascending `id()` order

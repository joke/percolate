# Graph Model Spec

## Purpose

This spec defines the bipartite graph model: two `GraphVertex` kinds — `Value` (a typed, nullness-keyed variable; OR over its producers) and `Operation` (an n-ary production; AND over its ports) — joined by `Dep` dependency edges (an edge into an Operation carries the port id it feeds). It covers Value identity and dedup (location + type + nullness), the `Scope` tree with the no-`Dep`-crosses-scope invariant, the `AddValue` / `AddOperation` deltas, and the `MapperGraph` wrapper over the JGraphT `DirectedMultigraph` used by the expansion, plan-extraction, and debug-output stages (`Location`, `AccessPath`, `TargetPath` retained).

## Requirements

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

### Requirement: MapperGraph is append-only after construction

After the discover, seed, and expand stages have populated a `MapperGraph`, no stage SHALL remove nodes, edges, registered `ExpansionGroup`s, or `GroupOutcome`s from it. `MapperGraph` exposes no removal methods; the invariant is structural rather than enforced at runtime.

Filtering for any downstream consumer (validation, dumping, future codegen) SHALL be expressed as a view (`RealisedSubgraph`, `transformsView`, `ExpansionGroup.getView()`, or another `MaskSubgraph`) rather than as a destructive mutation.

`SEED` edges and other expansion artifacts SHALL remain in the underlying graph after expansion. The decision whether to render them is made at the view / renderer layer.

#### Scenario: MapperGraph exposes no node, edge, or group removal
- **WHEN** the public surface of `MapperGraph` is inspected
- **THEN** no method removes a node, edge, `ExpansionGroup`, or `GroupOutcome`

### Requirement: GraphVertex closed hierarchy

The processor SHALL define a vertex interface `GraphVertex` in
`io.github.joke.percolate.processor.graph` with exactly two implementations, `Value` and
`Operation`, both final with package-private constructors so the hierarchy is closed by package
boundary (Java 11; no sealed types). Both use instance identity for `equals`/`hashCode`. The
underlying graph is a single JGraphT `DirectedMultigraph<GraphVertex, Dep>`.

#### Scenario: Exactly two vertex kinds
- **WHEN** the `processor.graph` package is inspected
- **THEN** `Value` and `Operation` are the only types implementing `GraphVertex`, both final, with
  package-private constructors

### Requirement: Value vertex type

`Value` represents a typed variable: it SHALL carry a `Location`, a `Scope`, an optional
`TypeMirror` type, and an optional `Nullability` nullness. Type and nullness are write-once
(unknown → determined → frozen), set together at the single mutation site. `Value` SHALL NOT carry
group labels, directives, codegen, or weight. A `Value` is an OR over its inbound producer
`Operation`s.

#### Scenario: Typing is write-once
- **WHEN** `setTyping` is invoked on an already-typed Value
- **THEN** an `IllegalStateException` is raised

#### Scenario: Value carries no engine bookkeeping
- **WHEN** the public surface of `Value` is inspected
- **THEN** it exposes no group membership, no directive, no codegen, and no weight

### Requirement: Operation vertex type

`Operation` SHALL represent a single production (constructor call, accessor, conversion, container
operation, constant), carrying its codegen, its weight, an ordered **port signature** (per
port: name, declared type, declared nullness — the former consumer `Slot` contract), a **totality**
flag (`total`/`partial`, where partial means the production may throw on a structurally-valid input,
e.g. `unwrap`/`orElseThrow` and `[requireNonNull]`), the producing strategy's FQN, and optionally an
owned child `Scope` (container element mapping). An `Operation` is an AND over its ports: it is usable
only when every port is fed.

#### Scenario: Operation owns the consumer contract
- **WHEN** code generation needs a port's declared type and nullness
- **THEN** it reads the Operation's port signature, never an edge label or an `ExpansionGroup`

#### Scenario: Partial production is flagged
- **WHEN** an `unwrap` (`Optional.orElseThrow`) or `[requireNonNull]` Operation is added
- **THEN** it is flagged `partial`; a total production (e.g. `flatMap`, `[coalesce]`, a constructor)
  is flagged `total` (consumed by `plan-extraction` totality dominance)

#### Scenario: Zero-port Operations are valid
- **WHEN** a constant production is added
- **THEN** it is an `Operation` with an empty port signature and no inbound dependency edges

### Requirement: Dep edge payload

Edges SHALL be `Dep` payload objects with instance identity, carrying no endpoints (topology is
graph-maintained, supplied at mutation time). A `Dep` into an `Operation` SHALL carry the port id it
feeds; a `Dep` into a `Value` (from its producer Operation) carries no port. `Dep` SHALL NOT carry
codegen, weight, kind, element scope, or consumer slot. Parallel `Dep`s between one `Value` and one
`Operation` (one per fed port) are permitted and distinguished by port id.

#### Scenario: Port-labelled dependency
- **WHEN** the Value `x:int` feeds both ports of `Range(int low, int high)`
- **THEN** two distinct `Dep` instances connect `x` to the Operation, carrying port ids `low` and
  `high`

### Requirement: Value identity and dedup

`MapperGraph.valueFor(scope, location, type, nullness)` SHALL get-or-create a `Value` keyed by all
four components. Nullness is part of identity (under JSpecify, `String!` and `String?` are different
types): every `Value` has one definite nullness, never resolved per chosen producer. Type-identical
demands dedup to a shared `Value`; type- or nullness-divergent demands are distinct `Value`s.
Expansion-minted conversion intermediates route through the same rule (no per-producer leaf
duplication).

#### Scenario: Type-identical ports share a Value
- **WHEN** two overloaded constructors both declare a port `street:String` with equal nullness
- **THEN** both Operations' port edges originate from the same `Value` instance

#### Scenario: Type-divergent ports get distinct Values
- **WHEN** overloaded constructors declare ports `number:int` and `number:long`
- **THEN** two distinct `Value`s exist and both are independently producible

### Requirement: Scope tree and child-scope ownership

`Scope`s SHALL form a tree: method scopes under the mapper scope, and element scopes owned by
scope-owning `Operation`s. **No `Dep` edge crosses a scope boundary**; the only coupling between a
child scope and its parent is the owning `Operation` (outer ports in the parent scope, param-root
and return-root `Value`s inside the child). The graph SHALL expose this invariant as a checkable
assertion.

#### Scenario: Scope-crossing edge is rejected
- **WHEN** a mutation would connect a parent-scope Value directly to a child-scope vertex
- **THEN** the mutation is rejected by the scope invariant check

### Requirement: Graph deltas are AddValue and AddOperation

Graph mutation SHALL be expressed as `AddValue` and `AddOperation` deltas. An `AddOperation` delta
lands atomically: the Operation vertex, its output edge to the produced `Value`, and one port edge
per port (each naming the feeding `Value`, existing or created by an accompanying `AddValue`).

#### Scenario: AddOperation is atomic
- **WHEN** an `AddOperation` delta is applied
- **THEN** after application the Operation has its output edge and exactly one inbound edge per
  declared port

### Requirement: MapperGraph wrapper over the bipartite graph

`MapperGraph` SHALL wrap the underlying `DirectedMultigraph<GraphVertex, Dep>`, exposing
`valueFor`, delta application (Applier-only), scope-tree access, SAT state, and read-only views
(`MaskSubgraph`-based). It remains append-only after construction: vertices and edges are never
removed; plan selection is a view, not a mutation.

#### Scenario: Append-only mutation surface
- **WHEN** the public surface of `MapperGraph` is inspected
- **THEN** no vertex- or edge-removal operation is exposed

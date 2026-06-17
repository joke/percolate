# Graph Model Spec

## Purpose

This spec defines the bipartite graph model: two `GraphVertex` kinds — `Value` (a typed, nullness-keyed variable; OR over its producers) and `Operation` (an n-ary production; AND over its ports) — joined by `Dep` dependency edges (an edge into an Operation carries the port id it feeds). It covers Value identity and dedup (location + type + nullness), the `Scope` tree with the no-`Dep`-crosses-scope invariant, the `AddValue` / `AddOperation` deltas, and the `MapperGraph` wrapper over the JGraphT `DirectedMultigraph` used by the expansion, plan-extraction, and debug-output stages (`Location`, `AccessPath`, `TargetPath` retained).

## Requirements

### Requirement: Weights constants
The processor SHALL define a non-instantiable utility class `Weights` in `io.github.joke.percolate.processor.graph` exposing only the unreachable sentinel used by cost extraction:
- `Weights.SENTINEL_UNREALISED = Integer.MAX_VALUE / 2` — the cost a producerless / unreachable Value carries.
- `Weights.isSentinel(int)` — true when a weight is at or above the sentinel.

The strategy-facing realised-cost scale (`NOOP`, `STEP`, `METHOD`, `STEP_GETTER`/`STEP_METHOD`/`STEP_FIELD`, `COPY`, `CONTAINER`, `EXPENSIVE`) lives on the **SPI** `io.github.joke.percolate.spi.Weights` utility, where the built-in strategies read it; the processor `Weights` does not duplicate that scale.

#### Scenario: Sentinel value is Integer.MAX_VALUE / 2
- **WHEN** `Weights.SENTINEL_UNREALISED` is evaluated
- **THEN** its value is exactly `Integer.MAX_VALUE / 2`

#### Scenario: isSentinel detects the unreachable cost
- **WHEN** `Weights.isSentinel(weight)` is evaluated for a `weight` at or above `SENTINEL_UNREALISED`
- **THEN** it returns `true`; for a finite realised weight it returns `false`

### Requirement: ElementLocation case

The processor SHALL define a `Location` implementation `ElementLocation` for phantom container element nodes. `ElementLocation` SHALL carry a single `String name` field that discriminates between scopes within multi-role containers; the default value for single-element-scope containers is the literal string `"element"`. Its `role()` SHALL be `LEAF`.

`ElementLocation` SHALL be a Lombok `@Value` (or equivalent immutable) type. Its `segment()` SHALL return the string `"elem(" + name + ")"` — e.g. `"elem(element)"` for the default name, `"elem(key)"` / `"elem(value)"` for future `Map<K,V>` element scopes.

A no-argument public constructor SHALL be preserved (whether via a static factory or a secondary constructor) that produces an `ElementLocation` with `name = "element"`. This preserves call-site compatibility with existing test fixtures and vertex-construction code that does not yet thread a name.

Two `ElementLocation` instances SHALL compare equal iff their `name` fields are equal.

#### Scenario: ElementLocation carries name
- **WHEN** an `ElementLocation` is constructed with `name = "key"`
- **THEN** `getName()` returns `"key"`
- **AND** `segment()` returns `"elem(key)"`

#### Scenario: ElementLocation default name is "element"
- **WHEN** an `ElementLocation` is constructed via the no-argument factory / constructor
- **THEN** `getName()` returns `"element"`
- **AND** `segment()` returns `"elem(element)"`

#### Scenario: ElementLocation equality by name
- **WHEN** two `ElementLocation` instances are constructed with the same `name`
- **THEN** they are `equal` and have equal `hashCode`s

#### Scenario: ElementLocation inequality by name
- **WHEN** two `ElementLocation` instances are constructed with different `name` strings (e.g. `"key"` vs `"value"`)
- **THEN** they are NOT `equal`

### Requirement: IncomingValues interface
The `IncomingValues` interface SHALL be defined in the SPI package `io.github.joke.percolate.spi` (it is part of the codegen author surface, consumed by `OperationCodegen.render`), exposing the upstream-rendered inputs to an operation's codegen. The interface SHALL declare:

```java
interface IncomingValues {
    CodeBlock single();
    CodeBlock byGroupPosition(int idx);
    CodeBlock byName(String slotName);
}
```

The processor SHALL supply a concrete `IncomingValuesImpl` in its generate stage that keys the rendered port values by name.

#### Scenario: IncomingValues exposes three accessors
- **WHEN** the `IncomingValues` interface is inspected
- **THEN** it declares exactly the methods `single()`, `byGroupPosition(int)`, `byName(String)`, all returning `CodeBlock`

### Requirement: Location interface and cases
The processor SHALL define a public `Location` interface in the `graph` sub-package with four implementations:
- `SourceLocation(AccessPath path)` — rooted at a method parameter; the first segment of `path` is the parameter name.
- `TargetLocation(TargetPath path)` — rooted at the method return type; an empty path denotes the return-type root itself.
- `ElementLocation(String name)` — marker for phantom container element nodes; the `name` field discriminates between element scopes within multi-role containers. The default name for single-element-scope containers is `"element"`.
- `ConstantLocation(String raw)` — a literal origin carrying the raw `@Map(constant = "...")` string, deliberately neither source nor target.

All cases SHALL be Lombok `@Value`. The interface SHALL declare:
```java
interface Location {
    Role role();        // resolution mode: FREE | ACCESS | LEAF | CONSTANT
    String segment();   // this location's contribution to GraphVertex.id()
    String slotName();  // the binding/slot name this location binds under
    default boolean isReturnRoot() { return false; }
}
```

`SourceLocation.segment()` SHALL return a stable encoding of the access path's segments. `TargetLocation.segment()` SHALL return a stable encoding of the target path's segments. `ElementLocation.segment()` SHALL return the string `"elem(" + name + ")"`. `ConstantLocation.segment()` SHALL return `"const[" + raw + "]"`. The renderer is permitted to use `segment()` as a label fallback for DOT output.

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
- a `getSegments()` accessor returning the segment list,
- a static `of(String segment)` factory building a single-segment path (`TargetPath.of` maps a `null`/empty segment to the empty path),
- a `lastSegment()` accessor returning the final segment, or the empty string when the path is empty,
- equality and `hashCode` by segment-list content,
- a deterministic `toString()` encoding (dot-joined segments).

`AccessPath` and `TargetPath` SHALL be distinct types so that source and target paths cannot be assigned to each other by accident. Multi-segment paths are built from a segment list via the constructor (e.g. `new AccessPath(List.copyOf(segments))`), not by an in-place append.

#### Scenario: A single-segment path is built from its segment
- **WHEN** `AccessPath.of("person")` is invoked
- **THEN** a new `AccessPath` whose `getSegments()` is `["person"]` is returned
- **AND** `lastSegment()` returns `"person"`

#### Scenario: AccessPath and TargetPath are not interchangeable
- **WHEN** an `AccessPath` value is supplied where a `TargetPath` is required
- **THEN** the code SHALL not compile (different types)

### Requirement: Scope interface and cases
The processor SHALL define a `Scope` interface — declaring `String encode()` and a `default Optional<Scope> parent()` — with three implementations forming a tree:
- `MapperScope` — the tree root, reserved for mapper-shared elements (e.g. routable methods).
- `MethodScope(ExecutableElement method)` — one per abstract mapper method; the scope of that method's Values and Operations.
- `ChildScope` — an element scope owned by a scope-owning `Operation` (a container element mapping); its `parent()` is the owning Operation's scope and its `encode()` nests the owning Operation's id.

`Scope` SHALL produce a stable text-encoding (`encode()`) suitable for embedding into `GraphVertex.id()` and DOT cluster names.

#### Scenario: Method scope encodes the method signature
- **WHEN** a `MethodScope` is constructed for an `ExecutableElement` representing `Human map(Person person)`
- **THEN** its text-encoding is a stable string derived from the method name and its parameter type strings (each parameter's `TypeMirror.toString()`, comma-joined — e.g., `map(Person)`) and is identical for repeated invocations

#### Scenario: Child scope nests its owning Operation
- **WHEN** a scope-owning Operation lands and owns a `ChildScope`
- **THEN** the child scope's `encode()` includes the owning Operation's id and its `parent()` is the Operation's scope

### Requirement: MapperGraph is append-only after construction

After the discover and expand stages have populated a `MapperGraph`, no stage SHALL remove vertices or edges from it. `MapperGraph` exposes no removal methods; the invariant is structural rather than enforced at runtime.

Filtering for any downstream consumer (validation, dumping, codegen) SHALL be expressed as a view (`bipartiteView` via `AsUnmodifiableGraph`, a scope-confined `scopeView` via `MaskSubgraph`, or the extracted plan's reachability query) rather than as a destructive mutation.

Over-emitted candidate Operations and unreachable Values SHALL remain in the underlying graph after expansion. The decision whether to render or select them is made at the view / extraction layer.

#### Scenario: MapperGraph exposes no vertex or edge removal
- **WHEN** the public surface of `MapperGraph` is inspected
- **THEN** no method removes a vertex (`Value`/`Operation`) or `Dep` edge

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
operation, constant), carrying a **human-readable `label`** (the strategy-supplied, fully-typed
production description, e.g. `int→long` or `new Address(int, String)`), its codegen, its weight, an
ordered **port signature** (per port: name, declared type, declared nullness — the former consumer
`Slot` contract), a **totality** flag (`total`/`partial`, where partial means the production may throw
on a structurally-valid input, e.g. `unwrap`/`orElseThrow` and `[requireNonNull]`), and optionally an
owned child `Scope` (container element mapping). It SHALL NOT carry a producing-strategy FQN. An
`Operation` is an AND over its ports: it is usable only when every port is fed.

#### Scenario: Operation owns the consumer contract
- **WHEN** code generation needs a port's declared type and nullness
- **THEN** it reads the Operation's port signature, never an edge label or a grouping label

#### Scenario: Operation label is the typed production, not a codegen class
- **WHEN** an Operation's `label` is read
- **THEN** it is the strategy-supplied production description (e.g. `int→long`), never a codegen
  lambda's class name
- **AND** the Operation exposes no `strategyFqn`

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

`MapperGraph` SHALL wrap the underlying `DirectedMultigraph<GraphVertex, Dep>`, exposing `valueFor`,
delta application (Applier-only), scope-confined queries, and read-only views (`bipartiteView` via
`AsUnmodifiableGraph`, `scopeView` via `MaskSubgraph`). It
SHALL NOT store a satisfaction predicate (no `markSat`/`isSat`/`clearSat`): reachability is a derived
query over extraction cost (see `plan-extraction`). It remains append-only after construction:
vertices and edges are never removed; plan selection is a view, not a mutation.

#### Scenario: Append-only mutation surface
- **WHEN** the public surface of `MapperGraph` is inspected
- **THEN** no vertex- or edge-removal operation is exposed

#### Scenario: No stored satisfaction predicate
- **WHEN** the `MapperGraph` surface is inspected
- **THEN** no `markSat` / `isSat` / `clearSat` / SAT-set member is present; reachability is obtained
  from the extracted plan instead

### Requirement: Location carries a role

`Location` SHALL expose a `role()` returning its **resolution mode** — one of `FREE` (target values
and conversion intermediates), `ACCESS` (multi-segment source-path values), `LEAF` (single-segment
source-path parameter roots and container element roots), or `CONSTANT`. The mode SHALL be derivable
from the concrete `Location` (kind plus access-path length): a `TargetLocation` is `FREE`; a
`SourceLocation` is `ACCESS` when its access path has more than one segment and `LEAF` when it has
exactly one; an `ElementLocation` is `LEAF`; a `ConstantLocation` is `CONSTANT`. The work-list
dispatch and the cost base-case rule SHALL be made through `role()`, not through `instanceof` on the
concrete `Location` implementations, so the distinction lives in one place.

#### Scenario: Base case keys off LEAF
- **WHEN** the cost fold determines whether a producerless `Value` is a base case
- **THEN** it consults `value.getLoc().role() == LEAF`, not an `instanceof` chain

#### Scenario: A multi-segment source location reports ACCESS
- **WHEN** a `SourceLocation` with access path `[p, address, street]` reports its `role()`
- **THEN** it returns `ACCESS`

#### Scenario: A single-segment source location reports LEAF
- **WHEN** a `SourceLocation` with access path `[p]` reports its `role()`
- **THEN** it returns `LEAF`

#### Scenario: Each remaining Location implementation reports its mode
- **WHEN** `TargetLocation`, `ElementLocation`, and `ConstantLocation` are inspected
- **THEN** they report `FREE`, `LEAF`, and `CONSTANT` respectively

## ADDED Requirements

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

## REMOVED Requirements

### Requirement: EdgeKind enum
**Reason**: Edge kinds (`SEED`/`REALISED`) encoded phase provenance on a single-kind graph; the
bipartite model has no seed edges and no realisation marker — dependencies are uniform `Dep`s.
**Migration**: Seed structure becomes param/return-root `Value`s plus goal specs (see `seed-graph`);
"realised" becomes vertex SAT state (see `graph-expansion`).

### Requirement: RealisedSubgraph view
**Reason**: The REALISED-edge filter has no equivalent; selection is the extraction view.
**Migration**: Use the extracted plan view (see `plan-extraction`).

### Requirement: Node value type
**Reason**: Replaced by `Value` (no group tags, no directive, nullness in identity).
**Migration**: See ADDED "Value vertex type" and "Value identity and dedup".

### Requirement: Edge value type
**Reason**: `Edge` carried function payload (codegen, weight, slot, element scope) — that payload is
the `Operation` vertex; edges reduce to `Dep` dependencies.
**Migration**: See ADDED "Operation vertex type" and "Dep edge payload".

### Requirement: Edge endpoints are graph-maintained, not stored on the Edge
**Reason**: Superseded; the rule itself survives, restated for `Dep`.
**Migration**: See ADDED "Dep edge payload".

### Requirement: Edge carries the consumer Slot contract
**Reason**: The consumer contract is the Operation's port signature.
**Migration**: See ADDED "Operation vertex type".

### Requirement: ExpansionGroup value type
**Reason**: A group was the implicit encoding of an n-ary function; the `Operation` vertex is the
explicit one. No grouping entity remains.
**Migration**: Group root → Operation output; group slots → port signature; group view → ordinary
graph neighborhood; group SAT → Operation SAT (see `graph-expansion`).

### Requirement: Node carries group-membership labels
**Reason**: Membership labels existed only to reconstruct function identity; identity is structural.
**Migration**: None needed; delete with `ExpansionGroup`.

### Requirement: GroupOutcome value type
**Reason**: SAT/UNSAT is a memoized vertex predicate, not a per-group record.
**Migration**: Diagnostics walk unsatisfied demands (see `realisation-validation`).

### Requirement: GraphDelta value type
**Reason**: Reshaped to the bipartite mutation vocabulary.
**Migration**: See ADDED "Graph deltas are AddValue and AddOperation".

### Requirement: EdgeCodegen marker interface
**Reason**: Codegen attaches to `Operation` vertices, not edges.
**Migration**: See `expansion-strategy-spi` ADDED "OperationSpec result type"; the
`render(VarNames, IncomingValues)` contract survives on the Operation's codegen.

### Requirement: MapperGraph wrapper
**Reason**: Rebuilt over `GraphVertex`/`Dep`; the old surface (`underlyingGraph()` of
`Node`/`Edge`, groups, group outcomes) is gone.
**Migration**: See ADDED "MapperGraph wrapper over the bipartite graph".

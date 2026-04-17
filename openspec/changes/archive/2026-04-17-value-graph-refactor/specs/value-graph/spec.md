## ADDED Requirements

### Requirement: ValueGraph is a single typed per-method graph

For each method in the `MatchedModel`, `BuildValueGraphStage` SHALL produce exactly one `ValueGraph` — a JGraphT `DefaultDirectedGraph<ValueNode, ValueEdge>` — covering every `MappingAssignment` of that method. There SHALL NOT be a separate per-assignment graph; assignments share nodes when their paths converge (e.g. reading `customer` once for `customer.name` and `customer.age`).

#### Scenario: One ValueGraph per method

- **WHEN** a mapper has methods `map(Order): OrderDTO` and `mapAddress(Address): AddressDTO`
- **THEN** `BuildValueGraphStage` SHALL produce two distinct `ValueGraph` instances — one per method

#### Scenario: Shared source path reuses PropertyNode

- **WHEN** a method has `@Map(source="customer.name", target="customerName")` and `@Map(source="customer.age", target="customerAge")`
- **THEN** the `ValueGraph` SHALL contain exactly one `PropertyNode` for `customer`, with two outgoing `PropertyReadEdge`s (to `name` and `age`)

### Requirement: ValueNode is a sealed hierarchy with four subtypes

`ValueNode` SHALL be a sealed interface (or abstract class) permitting exactly four subtypes:

- `SourceParamNode` — represents the method parameter (root of reads). Carries the parameter `VariableElement`, the parameter `TypeMirror`, and the parameter name.
- `PropertyNode` — represents an intermediate property reached via getter/field on a source type. Carries the property name, the declared `TypeMirror`, and the `ReadAccessor` used to read it.
- `TypedValueNode` — represents an anonymous typed value in the middle of a transform chain (e.g. the `Optional<String>` produced by `OptionalWrapStrategy`). Carries a `TypeMirror` and a debug label. Two `TypedValueNode`s with identical `TypeMirror` SHALL be considered equal (so BFS can reuse them).
- `TargetSlotNode` — represents a constructor argument or setter slot. Carries the target name, the `TypeMirror`, and the `WriteAccessor`.

Every `ValueNode` SHALL expose `TypeMirror type()`. The `Nullness` tag on nodes is added by the `jspecify-nullability` change and SHALL NOT be required by this refactor (the refactor stores nullness as `UNSPECIFIED` on every node).

#### Scenario: ValueNode permits exactly four subtypes

- **WHEN** a developer attempts to define a fifth `ValueNode` subtype
- **THEN** the compiler SHALL reject it because `ValueNode` is sealed

#### Scenario: SourceParamNode carries the parameter element

- **WHEN** a `SourceParamNode` is created for parameter `Order order`
- **THEN** `getName()` SHALL return `"order"`, `getType()` SHALL return the `TypeMirror` for `Order`, and `getElement()` SHALL return the parameter `VariableElement`

#### Scenario: PropertyNode carries a ReadAccessor

- **WHEN** a `PropertyNode` is created for `Order.getCustomer()`
- **THEN** it SHALL carry the property name `"customer"`, the `TypeMirror` for `Customer`, and the `ReadAccessor` that emits `$srcVar.getCustomer()`

#### Scenario: TargetSlotNode carries a WriteAccessor

- **WHEN** a `TargetSlotNode` is created for a setter `setCustomerName(String)`
- **THEN** it SHALL carry the target name `"customerName"`, the `TypeMirror` for `String`, and the `WriteAccessor` that emits `$tgtVar.setCustomerName($val)`

#### Scenario: TypedValueNode equality by type mirror

- **WHEN** two `TypedValueNode`s are created for the same declared type `Optional<String>` on the same graph
- **THEN** they SHALL be `.equals()` and share one JGraphT vertex — the graph SHALL NOT contain duplicates

### Requirement: ValueEdge is a sealed hierarchy with four subtypes

`ValueEdge` SHALL be a sealed interface (or abstract class) permitting exactly four subtypes:

- `PropertyReadEdge` — invokes a `ReadAccessor`. Source is a `SourceParamNode` or `PropertyNode`; target is a `PropertyNode`. Carries no template of its own (it derives from the target `PropertyNode.getReadAccessor()`).
- `TypeTransformEdge` — contributed by a `TypeTransformStrategy`. Carries the `TypeTransformStrategy`, the `TypeMirror` of input and output (for debug), and a `@Nullable CodeTemplate codeTemplate`. The `codeTemplate` field SHALL be `null` when the edge is initially proposed by `BuildValueGraphStage` and SHALL be populated exactly once, by `OptimizePathStage`.
- `NullWidenEdge` — carries no code (no-op edge) and exists only to change nullness of the typed value flowing through it. In this refactor, `NullWidenEdge` is DECLARED but NEVER constructed by any stage — it ships as a dormant subtype for the `jspecify-nullability` change to wire up.
- `LiftEdge` — wraps a sub-path under a container/null scope. Carries:
  - `kind: LiftKind` with values `NULL_CHECK`, `OPTIONAL`, `STREAM`, `COLLECTION`
  - `innerPath: GraphPath<ValueNode, ValueEdge>` — the wrapped transformation applied per element / on-non-null
  - `@Nullable CodeTemplate codeTemplate` — populated by `OptimizePathStage` (same rule as `TypeTransformEdge`)

In this refactor, `LiftKind.NULL_CHECK` is DECLARED but NEVER constructed (deferred to `jspecify-nullability`). `OPTIONAL`, `STREAM`, and `COLLECTION` lifts ARE constructed and replace the current `OptionalMapStrategy` / container-strategy `templateComposer` machinery.

#### Scenario: ValueEdge permits exactly four subtypes

- **WHEN** a developer attempts to define a fifth `ValueEdge` subtype
- **THEN** the compiler SHALL reject it

#### Scenario: TypeTransformEdge.codeTemplate is null until optimize

- **WHEN** `BuildValueGraphStage` constructs a `TypeTransformEdge` from a `TransformProposal`
- **THEN** the edge's `codeTemplate` SHALL be `null` on exit from `BuildValueGraphStage`

#### Scenario: TypeTransformEdge.codeTemplate is non-null after optimize for on-path edges

- **WHEN** `OptimizePathStage` completes for a resolved `GraphPath` containing a `TypeTransformEdge`
- **THEN** that edge's `codeTemplate` SHALL be non-null

#### Scenario: Off-path TypeTransformEdge never materialises a template

- **WHEN** `BuildValueGraphStage` produces 7 `TypeTransformEdge`s and `ResolvePathStage` selects a path containing only 3 of them
- **THEN** the 4 edges not on the path SHALL still have `codeTemplate == null` after `OptimizePathStage`

#### Scenario: LiftKind enumerates exactly four values

- **WHEN** `LiftKind.values()` is read
- **THEN** it SHALL return exactly `[NULL_CHECK, OPTIONAL, STREAM, COLLECTION]`

#### Scenario: NullWidenEdge is not constructed by this refactor

- **WHEN** the full processor test suite runs on the value-graph-refactor branch
- **THEN** no `NullWidenEdge` instance SHALL appear in any produced `ValueGraph`

#### Scenario: LiftEdge(NULL_CHECK) is not constructed by this refactor

- **WHEN** the full processor test suite runs on the value-graph-refactor branch
- **THEN** no `LiftEdge` with `kind == NULL_CHECK` SHALL appear in any produced `ValueGraph`

#### Scenario: LiftEdge(OPTIONAL) replaces OptionalMapStrategy composition

- **WHEN** a mapping goes `Optional<Foo> -> Optional<Bar>` with a sibling method `Bar map(Foo)`
- **THEN** the `ValueGraph` SHALL contain a `LiftEdge` with `kind == OPTIONAL` wrapping an `innerPath` that contains the `TypeTransformEdge(MethodCallStrategy)` for `Foo -> Bar`

### Requirement: BuildValueGraphStage constructs the ValueGraph from MatchedModel

`BuildValueGraphStage` SHALL accept a `MatchedModel` and produce a `Map<MethodMatching, ValueGraph>` (or equivalent container). For each `MethodMatching`:

1. Create one `SourceParamNode` per method parameter.
2. For each `MappingAssignment` with `sourcePath = [s0, s1, ..., sN]`:
   - Walk the read chain on the source parameter type. For each segment, discover the property via `SourcePropertyDiscovery` SPI. Create a `PropertyNode` (or reuse an existing one) with the discovered `ReadAccessor` and `TypeMirror`. Connect the previous node to the new node with a `PropertyReadEdge`.
   - Discover the target slot via `TargetPropertyDiscovery` SPI and create a `TargetSlotNode`.
   - Invoke all registered `TypeTransformStrategy` implementations to propose `TypeTransformEdge`s between reachable `TypedValueNode`s and the `TargetSlotNode`, iterating BFS-style until no new edges are produced (same 30-iteration budget as the pre-refactor code) — but NOT running `BFSShortestPath` here. Path search belongs to `ResolvePathStage`.
   - Container and optional lifting strategies contribute `LiftEdge`s with their `innerPath` populated as a sub-`GraphPath` over the same `ValueGraph`.

Unresolvable access segments (property not found) SHALL be recorded on the `MethodMatching` as a resolution-pending failure; they SHALL NOT abort `BuildValueGraphStage`. The absence of any transform edge between a reachable source and a target is NOT a failure at this stage either — that's `ValidateResolutionStage`'s job.

#### Scenario: Flat assignment builds SourceParamNode → PropertyNode → TargetSlotNode

- **WHEN** `MappingAssignment(["name"], "name", ...)` with source `Order { String name }` and target `OrderDTO { String name }`
- **THEN** the `ValueGraph` SHALL contain nodes `SourceParamNode("order")`, `PropertyNode("name", String, GetterAccessor(getName))`, `TargetSlotNode("name", String, SetterAccessor(setName))` connected by `PropertyReadEdge` and a `TypeTransformEdge(DirectAssignableStrategy)`

#### Scenario: Nested chain reuses intermediate PropertyNodes

- **WHEN** two assignments in the same method read `customer.name` and `customer.age`
- **THEN** the `ValueGraph` SHALL contain exactly one `PropertyNode` for `customer`, with two outgoing `PropertyReadEdge`s to two distinct `PropertyNode`s (`name` and `age`)

#### Scenario: No BFSShortestPath call in BuildValueGraphStage

- **WHEN** `BuildValueGraphStage.execute(...)` runs
- **THEN** it SHALL NOT invoke `org.jgrapht.alg.shortestpath.BFSShortestPath` — path search is `ResolvePathStage`'s responsibility

#### Scenario: Unresolvable access segment does not abort BuildValueGraphStage

- **WHEN** a `MappingAssignment` has `sourcePath = ["customer", "adress"]` and `Customer` has no property `adress`
- **THEN** `BuildValueGraphStage` SHALL still return success with a `ValueGraph` that marks the assignment as resolution-pending; the missing-property diagnostic SHALL come from `ValidateResolutionStage`

### Requirement: ValueGraph invariants

Every `ValueGraph` produced by `BuildValueGraphStage` and held through the remaining stages SHALL satisfy:

- Exactly one `SourceParamNode` per method parameter of the matched method.
- Every `PropertyReadEdge` has source ∈ `{SourceParamNode, PropertyNode}` and target ∈ `PropertyNode`.
- Every `TypeTransformEdge` has source ∈ `{PropertyNode, TypedValueNode}` and target ∈ `{TypedValueNode, TargetSlotNode}`.
- Every `LiftEdge.innerPath` is a `GraphPath` whose vertices and edges are all present in the same `ValueGraph`.
- `TargetSlotNode`s have no outgoing edges.
- The graph is a DAG (no cycles).

These invariants SHALL be enforced by `BuildValueGraphStage` construction and MAY be checked assertively by `DumpValueGraphStage` in debug mode.

#### Scenario: Target slot has no outgoing edges

- **WHEN** a `ValueGraph` is inspected after `BuildValueGraphStage`
- **THEN** no `TargetSlotNode` SHALL have any outgoing edge

#### Scenario: LiftEdge innerPath edges all belong to the parent graph

- **WHEN** a `LiftEdge` with non-empty `innerPath` is on the graph
- **THEN** every vertex and every edge in `innerPath` SHALL be present in the parent `ValueGraph` (checked by vertex/edge identity)

#### Scenario: Graph is acyclic

- **WHEN** a `ValueGraph` is inspected
- **THEN** it SHALL contain no directed cycle (`new CycleDetector<>(g).detectCycles()` returns false)

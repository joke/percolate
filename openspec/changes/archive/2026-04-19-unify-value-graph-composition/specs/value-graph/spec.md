## ADDED Requirements

### Requirement: ValueNode exposes a uniform compose contract

Every `ValueNode` SHALL expose a method `CodeBlock compose(Map<ValueEdge, CodeBlock> inputs, ComposeKind kind)`. The method takes the expressions produced by the node's incoming winning edges (keyed by edge identity) and returns the `CodeBlock` that represents the value at that node. `ComposeKind` SHALL be an enum with values `EXPRESSION` and `STATEMENT_LIST`; in this slice only `EXPRESSION` is used by `GenerateStage`. `STATEMENT_LIST` is scaffolding for slice 2 (bean-update sinks) and MAY be unused this slice but SHALL exist.

Per-subtype contracts:

- `SourceParamNode.compose(...)` SHALL ignore `inputs` (always empty) and return a `CodeBlock` referencing the parameter name.
- `PropertyNode.compose(...)` SHALL require exactly one entry in `inputs` and return that entry's value unchanged — the read has already been applied by the inbound `PropertyReadEdge`.
- `TypedValueNode.compose(...)` SHALL require exactly one entry in `inputs` and return it unchanged.
- `TargetSlotNode.compose(...)` SHALL require exactly one entry in `inputs` and return it unchanged — the slot is a passive sink in this slice (it gains real behaviour in slice 2).

#### Scenario: SourceParamNode composes to parameter reference

- **WHEN** `compose(Map.of(), EXPRESSION)` is called on a `SourceParamNode` for parameter `order`
- **THEN** the returned `CodeBlock` SHALL render as `order`

#### Scenario: PropertyNode forwards the single incoming expression

- **WHEN** `compose(Map.of(readEdge, CodeBlock.of("order.getCustomer()")), EXPRESSION)` is called on a `PropertyNode`
- **THEN** the returned `CodeBlock` SHALL render as `order.getCustomer()`

#### Scenario: TypedValueNode forwards the single incoming expression

- **WHEN** `compose(Map.of(transformEdge, CodeBlock.of("order.getItems().stream()")), EXPRESSION)` is called on a `TypedValueNode`
- **THEN** the returned `CodeBlock` SHALL render as `order.getItems().stream()`

#### Scenario: TargetSlotNode forwards the single incoming expression

- **WHEN** `compose(Map.of(transformEdge, CodeBlock.of("mapAddress(order.getAddress())")), EXPRESSION)` is called on a `TargetSlotNode`
- **THEN** the returned `CodeBlock` SHALL render as `mapAddress(order.getAddress())`

#### Scenario: Unexpected input arity is an invariant violation

- **WHEN** `PropertyNode.compose(...)` is called with zero or two-plus entries
- **THEN** an `IllegalStateException` SHALL be thrown; the error SHALL NOT be reported as a user `Diagnostic`

### Requirement: ComposeKind enumerates expression and statement-list modes

`ComposeKind` SHALL be a public enum with exactly two values: `EXPRESSION` and `STATEMENT_LIST`. `EXPRESSION` indicates the composed `CodeBlock` is a Java expression suitable for use inside a larger expression. `STATEMENT_LIST` indicates the composed `CodeBlock` is a sequence of statements (scaffolding for slice 2's bean-update sinks). In this slice only `EXPRESSION` SHALL be passed to any `compose(...)` call.

#### Scenario: ComposeKind has exactly two values

- **WHEN** `ComposeKind.values()` is read
- **THEN** it SHALL return exactly `[EXPRESSION, STATEMENT_LIST]`

#### Scenario: GenerateStage uses only EXPRESSION in this slice

- **WHEN** `GenerateStage` composes a method body in this slice
- **THEN** every `compose(...)` call SHALL pass `ComposeKind.EXPRESSION`

## MODIFIED Requirements

### Requirement: ValueNode is a sealed hierarchy with four subtypes

`ValueNode` SHALL be a sealed interface (or abstract class) permitting exactly four subtypes:

- `SourceParamNode` — represents the method parameter (root of reads). Carries the parameter `VariableElement`, the parameter `TypeMirror`, and the parameter name.
- `PropertyNode` — represents an intermediate property reached via getter/field on a source type. Carries the property name and the declared `TypeMirror`. Accessor rendering lives on the inbound `PropertyReadEdge` (via its code template), not on `PropertyNode`.
- `TypedValueNode` — represents an anonymous typed value in the middle of a transform chain (e.g. the `Optional<String>` produced by `OptionalWrapStrategy`). Carries a `TypeMirror` and a debug label. Two `TypedValueNode`s with identical `TypeMirror` SHALL be considered equal (so BFS can reuse them).
- `TargetSlotNode` — represents a constructor argument or setter slot. Carries the target name, the `TypeMirror`, and the `WriteAccessor`.

Every `ValueNode` SHALL expose `TypeMirror type()` and the uniform `compose(Map<ValueEdge, CodeBlock>, ComposeKind)` method. The `Nullness` tag on nodes is added by the `jspecify-nullability` change and SHALL NOT be required by this refactor (the refactor stores nullness as `UNSPECIFIED` on every node).

#### Scenario: ValueNode permits exactly four subtypes

- **WHEN** a developer attempts to define a fifth `ValueNode` subtype
- **THEN** the compiler SHALL reject it because `ValueNode` is sealed

#### Scenario: SourceParamNode carries the parameter element

- **WHEN** a `SourceParamNode` is created for parameter `Order order`
- **THEN** `getName()` SHALL return `"order"`, `getType()` SHALL return the `TypeMirror` for `Order`, and `getElement()` SHALL return the parameter `VariableElement`

#### Scenario: PropertyNode does not carry a ReadAccessor

- **WHEN** a `PropertyNode` is created for `Order.getCustomer()`
- **THEN** it SHALL carry the property name `"customer"` and the `TypeMirror` for `Customer`; it SHALL NOT carry a `ReadAccessor` — the getter/field access template is held by the inbound `PropertyReadEdge`

#### Scenario: TargetSlotNode carries a WriteAccessor

- **WHEN** a `TargetSlotNode` is created for a setter `setCustomerName(String)`
- **THEN** it SHALL carry the target name `"customerName"`, the `TypeMirror` for `String`, and the `WriteAccessor` that emits `$tgtVar.setCustomerName($val)`

#### Scenario: TypedValueNode equality by type mirror

- **WHEN** two `TypedValueNode`s are created for the same declared type `Optional<String>` on the same graph
- **THEN** they SHALL be `.equals()` and share one JGraphT vertex — the graph SHALL NOT contain duplicates

### Requirement: ValueEdge is a sealed hierarchy with four subtypes

`ValueEdge` SHALL be a sealed interface (or abstract class) permitting exactly four subtypes:

- `PropertyReadEdge` — reads a property from a source. Source is a `SourceParamNode` or `PropertyNode`; target is a `PropertyNode`. Carries a `CodeTemplate` populated at construction: `"$L.getFoo()"` for getter-based access (constructed by `GetterDiscovery`), `"$L.foo"` for field-based access (constructed by `FieldDiscovery.Source`).
- `TypeTransformEdge` — contributed by a `TypeTransformStrategy`. Carries the `TypeTransformStrategy`, the `TypeMirror` of input and output (for debug), and a `CodeTemplate` populated by `BuildValueGraphStage` at construction (via `strategy.resolveCodeTemplate(...)`). The `codeTemplate` field SHALL NOT be `null` on any `TypeTransformEdge` in a produced `ValueGraph`.
- `NullWidenEdge` — carries no code (no-op edge) and exists only to change nullness of the typed value flowing through it. In this refactor, `NullWidenEdge` is DECLARED but NEVER constructed by any stage — it ships as a dormant subtype for the `jspecify-nullability` change to wire up.
- `LiftEdge` — wraps a sub-transformation under a container/null scope. Carries:
  - `kind: LiftKind` with values `NULL_CHECK`, `OPTIONAL`, `STREAM`, `COLLECTION`
  - `innerInputNode: ValueNode` — the node representing the per-element / on-non-null input (the inner source vertex in the parent graph)
  - `innerOutputNode: ValueNode` — the node representing the per-element / on-non-null output (the inner target vertex in the parent graph)
  - `LiftEdge` SHALL NOT pre-commit a `CodeTemplate` at construction. Its template SHALL be composed at `GenerateStage` time by running on-demand BFS over the parent graph (restricted to winning edges when available) from `innerInputNode` to `innerOutputNode`, composing the inner path's edge templates, then wrapping the inner expression according to `kind` (`OPTIONAL`: `$src.map(x -> $inner(x))`; `STREAM`: `$src.map(x -> $inner(x))`; `COLLECTION`: element-wise loop / stream shape; `NULL_CHECK`: not constructed this slice).

In this refactor, `LiftKind.NULL_CHECK` is DECLARED but NEVER constructed (deferred to `jspecify-nullability`). `OPTIONAL`, `STREAM`, and `COLLECTION` lifts ARE constructed.

#### Scenario: ValueEdge permits exactly four subtypes

- **WHEN** a developer attempts to define a fifth `ValueEdge` subtype
- **THEN** the compiler SHALL reject it

#### Scenario: PropertyReadEdge carries a getter-shaped template when constructed by GetterDiscovery

- **WHEN** `GetterDiscovery` contributes a `PropertyReadEdge` for `Order.getCustomer()`
- **THEN** the edge's `codeTemplate.apply(CodeBlock.of("order"))` SHALL render as `order.getCustomer()`

#### Scenario: PropertyReadEdge carries a field-shaped template when constructed by FieldDiscovery.Source

- **WHEN** `FieldDiscovery.Source` contributes a `PropertyReadEdge` for a public field `firstName`
- **THEN** the edge's `codeTemplate.apply(CodeBlock.of("source"))` SHALL render as `source.firstName`

#### Scenario: TypeTransformEdge.codeTemplate is non-null from construction

- **WHEN** `BuildValueGraphStage` constructs a `TypeTransformEdge` from a `TransformProposal`
- **THEN** the edge's `codeTemplate` SHALL be non-null immediately, and SHALL remain unchanged through the rest of the pipeline

#### Scenario: LiftEdge carries no pre-committed CodeTemplate

- **WHEN** `BuildValueGraphStage` constructs a `LiftEdge`
- **THEN** the edge SHALL NOT hold a pre-computed `CodeTemplate` field; it SHALL carry `(innerInputNode, innerOutputNode, kind)` and compute its template on demand at `GenerateStage` time

#### Scenario: LiftEdge template composition is idempotent

- **WHEN** `LiftEdge.compose(...)` is invoked twice with the same `(graph, winningEdges)` inputs
- **THEN** it SHALL return equal `CodeBlock`s both times

#### Scenario: LiftKind enumerates exactly four values

- **WHEN** `LiftKind.values()` is read
- **THEN** it SHALL return exactly `[NULL_CHECK, OPTIONAL, STREAM, COLLECTION]`

#### Scenario: NullWidenEdge is not constructed by this refactor

- **WHEN** the full processor test suite runs on the unify-value-graph-composition branch
- **THEN** no `NullWidenEdge` instance SHALL appear in any produced `ValueGraph`

#### Scenario: LiftEdge(NULL_CHECK) is not constructed by this refactor

- **WHEN** the full processor test suite runs on the unify-value-graph-composition branch
- **THEN** no `LiftEdge` with `kind == NULL_CHECK` SHALL appear in any produced `ValueGraph`

#### Scenario: LiftEdge(OPTIONAL) replaces OptionalMapStrategy composition

- **WHEN** a mapping goes `Optional<Foo> -> Optional<Bar>` with a sibling method `Bar map(Foo)`
- **THEN** the `ValueGraph` SHALL contain a `LiftEdge` with `kind == OPTIONAL` whose `innerInputNode` and `innerOutputNode` are the `TypedValueNode`s for `Foo` and `Bar` respectively, and whose lazily composed template renders as `$src.map(x -> map(x))`

### Requirement: BuildValueGraphStage constructs the ValueGraph from MatchedModel

`BuildValueGraphStage` SHALL accept a `MatchedModel` and produce a `Map<MethodMatching, ValueGraph>` (or equivalent container). For each `MethodMatching`:

1. Create one `SourceParamNode` per method parameter.
2. For each `MappingAssignment` with `sourcePath = [s0, s1, ..., sN]`:
   - Walk the read chain on the source parameter type. For each segment, discover the property via `SourcePropertyDiscovery` SPI. Create a `PropertyNode` (or reuse an existing one) carrying the property name and `TypeMirror`. Connect the previous node to the new node with a `PropertyReadEdge` whose `CodeTemplate` is constructed per accessor kind (`"$L.getFoo()"` for getters, `"$L.foo"` for public fields).
   - Discover the target slot via `TargetPropertyDiscovery` SPI and create a `TargetSlotNode`.
   - Invoke all registered `TypeTransformStrategy` implementations to propose `TypeTransformEdge`s between reachable `TypedValueNode`s and the `TargetSlotNode`, iterating BFS-style until no new edges are produced (same 30-iteration budget as the pre-refactor code) — but NOT running `BFSShortestPath` here. Each `TypeTransformEdge` SHALL carry its final `CodeTemplate` at construction (from `strategy.resolveCodeTemplate(...)`).
   - Container and optional lifting strategies contribute `LiftEdge`s that capture `(innerInputNode, innerOutputNode, kind)` against the same `ValueGraph`; they SHALL NOT pre-commit a `CodeTemplate`.

Unresolvable access segments (property not found) SHALL be recorded on the `MethodMatching` as a resolution-pending failure; they SHALL NOT abort `BuildValueGraphStage`. The absence of any transform edge between a reachable source and a target is NOT a failure at this stage either — that's `ValidateResolutionStage`'s job.

#### Scenario: Flat assignment builds SourceParamNode → PropertyNode → TargetSlotNode

- **WHEN** `MappingAssignment(["name"], "name", ...)` with source `Order { String name }` and target `OrderDTO { String name }`
- **THEN** the `ValueGraph` SHALL contain nodes `SourceParamNode("order")`, `PropertyNode("name", String)`, `TargetSlotNode("name", String, SetterAccessor(setName))` connected by a `PropertyReadEdge` whose template is `"$L.getName()"` and a `TypeTransformEdge(DirectAssignableStrategy)` whose template is set at construction

#### Scenario: Nested chain reuses intermediate PropertyNodes

- **WHEN** two assignments in the same method read `customer.name` and `customer.age`
- **THEN** the `ValueGraph` SHALL contain exactly one `PropertyNode` for `customer`, with two outgoing `PropertyReadEdge`s (carrying `"$L.getName()"` / `"$L.getAge()"` templates) to two distinct `PropertyNode`s

#### Scenario: No BFSShortestPath call in BuildValueGraphStage

- **WHEN** `BuildValueGraphStage.execute(...)` runs
- **THEN** it SHALL NOT invoke `org.jgrapht.alg.shortestpath.BFSShortestPath` — path search is `ResolvePathStage`'s responsibility

#### Scenario: Unresolvable access segment does not abort BuildValueGraphStage

- **WHEN** a `MappingAssignment` has `sourcePath = ["customer", "adress"]` and `Customer` has no property `adress`
- **THEN** `BuildValueGraphStage` SHALL still return success with a `ValueGraph` that marks the assignment as resolution-pending; the missing-property diagnostic SHALL come from `ValidateResolutionStage`

#### Scenario: All TypeTransformEdge templates are populated at construction

- **WHEN** `BuildValueGraphStage` completes for a mapper
- **THEN** every `TypeTransformEdge` in every produced `ValueGraph` SHALL have a non-null `codeTemplate` — including edges not on any winning path

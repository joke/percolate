# Type Transform Strategy Spec

## Purpose

Defines the TypeTransformStrategy SPI interface, the TransformProposal and CodeTemplate value types, the JGraphT-based type transformation graph, and the BFS resolution algorithm used to bridge type gaps between source and target properties.
## Requirements
### Requirement: TypeTransformStrategy SPI interface
The processor SHALL provide a `TypeTransformStrategy` interface with a single method `canProduce(TypeMirror sourceType, TypeMirror targetType, ResolutionContext ctx)` that returns `Optional<TransformProposal>`. Implementations SHALL be discovered via `ServiceLoader` using `@AutoService`. Each strategy examines the source and target types and either proposes a transformation edge or returns empty. The `ResolutionContext` SHALL provide access to `Types`, `Elements`, the mapper's `TypeElement`, the current method's `ExecutableElement`, per-mapping options as `Map<MapOptKey, String>`, and a String `using` field for method name routing.

#### Scenario: Strategy matches and proposes a transformation
- **WHEN** a `TypeTransformStrategy` implementation recognizes the source-target type pair
- **THEN** it SHALL return a `TransformProposal` containing the required input type, produced output type, and a `CodeTemplate`

#### Scenario: Strategy does not match
- **WHEN** a `TypeTransformStrategy` implementation does not recognize the source-target type pair
- **THEN** it SHALL return `Optional.empty()`

#### Scenario: Strategy discovers callable methods via ResolutionContext
- **WHEN** a strategy needs to find a method that converts source type to target type
- **THEN** it SHALL use `ctx.getElements().getAllMembers(ctx.getMapperType())` to discover all methods including abstract, default, concrete, and inherited methods

#### Scenario: Strategy excludes current method from candidates
- **WHEN** a strategy searches for callable methods
- **THEN** it SHALL exclude `ctx.getCurrentMethod()` to prevent self-referential calls

#### Scenario: Strategy reads per-mapping options
- **WHEN** a strategy needs to check for a mapping option (e.g., `DATE_FORMAT`)
- **THEN** it SHALL read from `ctx.getOptions()` which returns `Map<MapOptKey, String>`

#### Scenario: Options are empty for auto-mapped properties
- **WHEN** a strategy is invoked for an auto-mapped property (no explicit `@Map` directive)
- **THEN** `ctx.getOptions()` SHALL return an empty map

#### Scenario: Strategy reads using for method name routing
- **WHEN** a strategy needs to check if a specific method name is requested for this mapping
- **THEN** it SHALL read from `ctx.getUsing()` which returns a `String` (empty string means not set)

#### Scenario: Using is empty for auto-mapped properties
- **WHEN** a strategy is invoked for an auto-mapped property (no explicit `@Map` directive)
- **THEN** `ctx.getUsing()` SHALL return an empty string

### Requirement: TransformProposal carries edge metadata

A `TransformProposal` SHALL contain: the `TypeMirror` required as input, the `TypeMirror` produced as output, a `CodeTemplate` for code generation, and a reference to the contributing `TypeTransformStrategy` for error reporting. It SHALL NOT carry an `ElementConstraint` and SHALL NOT carry a `templateComposer: Function<CodeTemplate, CodeTemplate>`. Composition and lifting are graph operations expressed as `LiftEdge`s in the `ValueGraph`, not per-proposal closures.

#### Scenario: Proposal contains code template

- **WHEN** a strategy returns a `TransformProposal`
- **THEN** the proposal SHALL include a `CodeTemplate` that can transform an input `CodeBlock` into an output `CodeBlock`

#### Scenario: TransformProposal has no elementConstraint field

- **WHEN** callers inspect the `TransformProposal` API
- **THEN** there SHALL be no `elementConstraint` accessor and no constructor accepting an `ElementConstraint`

#### Scenario: TransformProposal has no templateComposer field

- **WHEN** callers inspect the `TransformProposal` API
- **THEN** there SHALL be no `templateComposer` accessor and no constructor accepting a `Function<CodeTemplate, CodeTemplate>`

### Requirement: CodeTemplate is a CodeBlock-to-CodeBlock function

The `CodeTemplate` interface SHALL define a single method `apply(CodeBlock innerExpression)` that returns a `CodeBlock`. Strategies SHALL use this to encode their code generation logic. `CodeTemplate` instances SHALL only be resolved inside `OptimizePathStage`; `GenerateStage` SHALL read already-resolved templates from edges without calling any resolution function.

#### Scenario: Identity template for direct assignability

- **WHEN** `DirectAssignableStrategy` creates a `CodeTemplate`
- **THEN** `apply(input)` SHALL return the input `CodeBlock` unchanged

#### Scenario: Method call template

- **WHEN** `MethodCallStrategy` creates a `CodeTemplate` for method `mapPerson`
- **THEN** `apply(input)` SHALL return `CodeBlock.of("mapPerson($L)", input)`

#### Scenario: Stream collect template

- **WHEN** `CollectToSetStrategy` creates a `CodeTemplate`
- **THEN** `apply(input)` SHALL return `CodeBlock.of("$L.collect($T.toSet())", input, Collectors.class)`

### Requirement: Type transformation graph per property edge

Type transform edges SHALL live as `TypeTransformEdge` subtype of `ValueEdge` inside the per-method `ValueGraph` (see `value-graph/spec.md`), not as a separate per-mapping `DefaultDirectedGraph<TypeNode, TransformEdge>`. `BuildValueGraphStage` SHALL propose edges between `PropertyNode`s, `TypedValueNode`s, and `TargetSlotNode`s. `TypeNode` as a standalone vertex type SHALL be removed; its role is taken by `TypedValueNode` (for mid-chain anonymous types) and by `PropertyNode` / `TargetSlotNode` (for named endpoints).

#### Scenario: Simple direct mapping yields a single TypeTransformEdge

- **WHEN** source type `String` maps to target type `String` via `DirectAssignableStrategy`
- **THEN** the `ValueGraph` SHALL contain a `TypeTransformEdge(DirectAssignableStrategy)` connecting the relevant `PropertyNode` (or `SourceParamNode`) to the `TargetSlotNode` directly; `codeTemplate` SHALL be null until `OptimizePathStage` runs

#### Scenario: Container mapping yields a multi-edge sub-chain through TypedValueNodes

- **WHEN** source type `List<Person>` maps to target type `Set<PersonDTO>` via stream expansion
- **THEN** the `ValueGraph` SHALL contain `TypedValueNode(Stream<Person>)` and `TypedValueNode(Stream<PersonDTO>)` connected via three `TypeTransformEdge`s, each with `codeTemplate == null` until `OptimizePathStage` runs

### Requirement: BFS resolution algorithm

`BuildValueGraphStage` SHALL use a BFS edge-proposal fixpoint: each iteration asks all registered strategies for proposals on reachable type gaps, adds the resulting `TypeTransformEdge`s (and, from container / optional strategies, `LiftEdge`s) to the `ValueGraph`, and halts when an iteration produces no new edges. The loop SHALL terminate after at most 30 iterations. `BFSShortestPath` SHALL NOT run inside `BuildValueGraphStage` — shortest-path search belongs to `ResolvePathStage`.

#### Scenario: Direct assignability contributes in first iteration

- **WHEN** source type `String` and target type `String`
- **THEN** `DirectAssignableStrategy` SHALL contribute an edge in iteration 1; subsequent iterations produce no new edges and the loop exits

#### Scenario: Container mapping reaches fixpoint across multiple iterations

- **WHEN** source type `List<Person>` and target type `Set<PersonDTO>` with a sibling method `PersonDTO map(Person)`
- **THEN** strategies SHALL contribute edges across multiple iterations until the edge set stabilizes

#### Scenario: Expansion terminates within 30 iterations

- **WHEN** strategies would contribute edges indefinitely
- **THEN** `BuildValueGraphStage` SHALL stop after 30 iterations; `ValidateResolutionStage` surfaces any resulting gap as unresolved

### Requirement: Resolved path is a GraphPath

The resolved path for a mapping SHALL be a JGraphT `GraphPath<ValueNode, ValueEdge>`. Downstream stages consume `path.getEdgeList()` to walk the value transformation, exhaustively switching on `ValueEdge` subtypes (`PropertyReadEdge`, `TypeTransformEdge`, `NullWidenEdge`, `LiftEdge`). The previous `GraphPath<TypeNode, TransformEdge>` typing is removed.

#### Scenario: GraphPath edge list preserves order

- **WHEN** `List<Person>` → `Set<PersonDTO>` resolves through `Stream<Person>` → `Stream<PersonDTO>`
- **THEN** `path.getEdgeList()` SHALL return the sequence of `TypeTransformEdge`s (plus any leading `PropertyReadEdge`s) in the order they are traversed during emission

### Requirement: Lifting strategies contribute LiftEdges

Container and optional strategies that previously composed templates via a `templateComposer` closure SHALL instead contribute `LiftEdge`s to the `ValueGraph`. A lift strategy emits a `LiftEdge(kind, innerPath)` where `innerPath` is a sub-`GraphPath` over the same `ValueGraph` representing the per-element / on-non-null transformation. The outer lift and its inner path together express the same semantics the `templateComposer` previously encoded, but in a graph-observable form.

Specifically:

- `OptionalMapStrategy`: emits `LiftEdge(OPTIONAL, innerPath)` where `innerPath` maps the wrapped value.
- Container strategies (`CollectToListStrategy`, `CollectToSetStrategy`, etc.) absorb their element-level mapping into `LiftEdge(STREAM, innerPath)` / `LiftEdge(COLLECTION, innerPath)`.
- `LiftEdge(NULL_CHECK, ...)` is reserved for the `jspecify-nullability` change and SHALL NOT be constructed by any strategy in this refactor.

#### Scenario: OptionalMapStrategy emits a LiftEdge

- **WHEN** `OptionalMapStrategy` is invoked for `Optional<Foo> → Optional<Bar>` with a sibling `Bar map(Foo)` method
- **THEN** the strategy SHALL contribute a `LiftEdge(OPTIONAL, innerPath)` to the `ValueGraph`, where `innerPath` contains `TypeTransformEdge(MethodCallStrategy)` for `Foo → Bar`

#### Scenario: Container strategy emits a LiftEdge with inner path

- **WHEN** `CollectToSetStrategy` is invoked for `List<Person> → Set<PersonDTO>` with a sibling `PersonDTO map(Person)` method
- **THEN** the strategy SHALL contribute a `LiftEdge(COLLECTION or STREAM, innerPath)` whose `innerPath` contains the `Person → PersonDTO` transform; the composed code template falls out of `OptimizePathStage`

#### Scenario: No strategy constructs LiftEdge(NULL_CHECK) in this refactor

- **WHEN** the full processor test suite runs on this refactor branch
- **THEN** no `LiftEdge` with `kind == NULL_CHECK` SHALL exist in any emitted `ValueGraph`

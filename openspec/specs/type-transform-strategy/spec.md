# Type Transform Strategy Spec

## Purpose

Defines the TypeTransformStrategy SPI interface, the TransformProposal and CodeTemplate value types, the JGraphT-based type transformation graph, and the BFS resolution algorithm used to bridge type gaps between source and target properties.

## Requirements

### Requirement: TypeTransformStrategy SPI interface
The processor SHALL provide a `TypeTransformStrategy` interface with a single method `canProduce(TypeMirror sourceType, TypeMirror targetType, ResolutionContext ctx)` that returns `Optional<TransformProposal>`. Implementations SHALL be discovered via `ServiceLoader` using `@AutoService`. Each strategy examines the source and target types and either proposes a transformation edge or returns empty. The `ResolutionContext` SHALL provide access to `Types`, `Elements`, the mapper's `TypeElement`, and the current method's `ExecutableElement`.

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

### Requirement: TransformProposal carries edge metadata
A `TransformProposal` SHALL contain: the `TypeMirror` required as input, the `TypeMirror` produced as output, and a `CodeTemplate` for code generation. It SHALL also carry a reference to the contributing strategy for error reporting.

#### Scenario: Proposal contains code template
- **WHEN** a strategy returns a `TransformProposal`
- **THEN** the proposal SHALL include a `CodeTemplate` that can transform an input `CodeBlock` into an output `CodeBlock`

### Requirement: CodeTemplate is a CodeBlock-to-CodeBlock function
The `CodeTemplate` interface SHALL define a single method `apply(CodeBlock innerExpression)` that returns a `CodeBlock`. Strategies SHALL use this to encode their code generation logic.

#### Scenario: Identity template for direct assignability
- **WHEN** the `DirectAssignableStrategy` creates a `CodeTemplate`
- **THEN** `apply(input)` SHALL return the input `CodeBlock` unchanged

#### Scenario: Method call template
- **WHEN** the `MethodCallStrategy` creates a `CodeTemplate` for method `mapPerson`
- **THEN** `apply(input)` SHALL return `CodeBlock.of("mapPerson($L)", input)`

#### Scenario: Stream collect template
- **WHEN** the `CollectToSetStrategy` creates a `CodeTemplate`
- **THEN** `apply(input)` SHALL return `CodeBlock.of("$L.collect($T.toSet())", input, Collectors.class)`

### Requirement: Type transformation graph per property edge
The resolver SHALL construct a JGraphT `DefaultDirectedGraph<TypeNode, TransformEdge>` for each property mapping edge. `TypeNode` SHALL wrap a `TypeMirror` and a label for debugging. `TransformEdge` SHALL carry the contributing `TypeTransformStrategy` and its `CodeTemplate`.

#### Scenario: Simple direct mapping produces single-edge graph
- **WHEN** source type `String` maps to target type `String` via `DirectAssignableStrategy`
- **THEN** the type graph SHALL contain two `TypeNode`s (`String` -> `String`) connected by one `TransformEdge`

#### Scenario: Container mapping produces multi-edge graph
- **WHEN** source type `List<Person>` maps to target type `Set<PersonDTO>` via stream expansion
- **THEN** the type graph SHALL contain nodes for `List<Person>`, `Stream<Person>`, `Stream<PersonDTO>`, `Set<PersonDTO>` connected by three `TransformEdge`s

### Requirement: BFS resolution algorithm
The resolver SHALL use a BFS expansion loop: each iteration asks all registered strategies for proposals on open type gaps, adds resulting edges to the graph, and checks for a complete path using `BFSShortestPath.findPathBetween()`. Resolution SHALL succeed when a path from source type to target type exists, or fail when no strategy contributes new edges (unresolved). The loop SHALL terminate after at most 30 iterations.

#### Scenario: Direct assignability resolves in first iteration
- **WHEN** source type `String` and target type `String`
- **THEN** `DirectAssignableStrategy` SHALL contribute an edge in the first iteration and `BFSShortestPath` SHALL find the path immediately

#### Scenario: Container mapping resolves across multiple iterations
- **WHEN** source type `List<Person>` and target type `Set<PersonDTO>` with a sibling method `PersonDTO map(Person)`
- **THEN** strategies SHALL contribute edges across multiple iterations until `BFSShortestPath` finds the complete path

#### Scenario: No strategy matches produces unresolved result
- **WHEN** source type `Foo` and target type `Bar` and no strategy can produce `Bar` from `Foo`
- **THEN** the resolver SHALL report the gap as unresolved after no new edges are contributed

#### Scenario: Expansion terminates within 30 iterations
- **WHEN** strategies keep contributing edges
- **THEN** the resolver SHALL stop after 30 iterations and treat any remaining gaps as unresolved

### Requirement: Resolved path is a GraphPath
The resolver SHALL return the result as a JGraphT `GraphPath<TypeNode, TransformEdge>`. Downstream stages SHALL consume `path.getEdgeList()` to walk the transformation chain.

#### Scenario: GraphPath edge list preserves order
- **WHEN** `List<Person>` -> `Set<PersonDTO>` resolves through `Stream<Person>` -> `Stream<PersonDTO>`
- **THEN** `path.getEdgeList()` SHALL return edges in order: StreamFromCollection, StreamMap, CollectToSet

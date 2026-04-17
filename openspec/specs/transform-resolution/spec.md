# Transform Resolution Spec

## Purpose

Defines the ResolveTransforms stage that accepts a symbolic property graph (name-only nodes) and produces a fully resolved model by sequentially resolving `AccessEdge` source chains via property discovery and bridging type gaps between source and target properties using BFS expansion with registered `TypeTransformStrategy` implementations, producing a `ResolvedModel` with per-method accessor chains and `GraphPath` chains for code generation.
## Requirements
### Requirement: ResolvePathStage finds a GraphPath per assignment

The `ResolvePathStage` (renamed from `ResolveTransformsStage`) SHALL accept the `Map<MethodMatching, ValueGraph>` produced by `BuildValueGraphStage` and produce `Map<MethodMatching, List<ResolvedAssignment>>`. For each `MappingAssignment` on each `MethodMatching`, the stage SHALL run `BFSShortestPath` (or equivalent) over that method's `ValueGraph` from the `SourceParamNode` for the assignment's source parameter to the `TargetSlotNode` for the assignment's target name. The result for each assignment SHALL be a `ResolvedAssignment` carrying:

- `assignment: MappingAssignment` — the source decision
- `path: @Nullable GraphPath<ValueNode, ValueEdge>` — the shortest path, or `null` if no path was found
- `failure: @Nullable ResolutionFailure` — populated when `path` is null, with context for `ValidateResolutionStage`

The stage SHALL NOT walk access chains on source types, construct `ReadAccessor`s, or resolve code templates — those responsibilities live in `BuildValueGraphStage` (chain walking) and `OptimizePathStage` (templates). The stage SHALL NOT emit `Diagnostic`s; resolution failures are annotated on the `ResolvedAssignment` and surface via `ValidateResolutionStage`.

#### Scenario: Flat property resolves to a short path

- **WHEN** a `ValueGraph` has `SourceParamNode("order") --PropertyReadEdge--> PropertyNode("name", String) --TypeTransformEdge(DirectAssignable)--> TargetSlotNode("name", String)`
- **THEN** `ResolvePathStage` SHALL emit a `ResolvedAssignment` whose `path.getEdgeList()` contains exactly `[PropertyReadEdge, TypeTransformEdge]` in that order

#### Scenario: Nested chain resolves through reused PropertyNodes

- **WHEN** two assignments in the same method read `customer.name` and `customer.age`, and the `ValueGraph` shares one `PropertyNode("customer")`
- **THEN** each assignment's `path.getEdgeList()` SHALL begin with the shared `PropertyReadEdge` to the `customer` node, followed by the assignment-specific segments

#### Scenario: Container mapping produces multi-edge path through TypedValueNodes

- **WHEN** a mapping resolves `List<Person>` to `Set<PersonDTO>` via stream expansion
- **THEN** `ResolvePathStage` SHALL emit a path whose edges are `[..., TypeTransformEdge(StreamFromCollection), TypeTransformEdge(StreamMap), TypeTransformEdge(CollectToSet)]` routed through `TypedValueNode(Stream<Person>)` and `TypedValueNode(Stream<PersonDTO>)`

#### Scenario: No path produces ResolvedAssignment with null path and failure context

- **WHEN** no path exists between the source and target for an assignment
- **THEN** `ResolvePathStage` SHALL emit a `ResolvedAssignment` with `path = null` and a `ResolutionFailure` carrying the source `TypeMirror`, target `TypeMirror`, and assignment identity — but SHALL NOT emit a `Diagnostic` itself

#### Scenario: Code templates remain null after ResolvePathStage

- **WHEN** `ResolvePathStage` emits a `ResolvedAssignment` with a non-null `path`
- **THEN** every `TypeTransformEdge` and `LiftEdge` on `path` SHALL still have `codeTemplate == null` until `OptimizePathStage` runs

### Requirement: ResolvedAssignment replaces ResolvedMapping

`ResolvedAssignment` SHALL be the per-assignment output of `ResolvePathStage`. It SHALL carry the `MappingAssignment` (for origin, options, `using`), a `@Nullable GraphPath<ValueNode, ValueEdge>` path, and a `@Nullable ResolutionFailure`. It SHALL NOT carry separate `List<ReadAccessor> sourceChain` fields — the read chain is recoverable from the `path` by filtering for `PropertyReadEdge`s.

- `isResolved()` SHALL return `true` iff `failure == null && path != null`.
- `getReadChainEdges()` SHALL return the sub-list of `path.getEdgeList()` whose edges are `PropertyReadEdge`, preserving order — provided as a convenience for `GenerateStage`.

#### Scenario: isResolved mirrors path presence

- **WHEN** a `ResolvedAssignment` has `path != null` and `failure == null`
- **THEN** `isResolved()` SHALL return `true`

#### Scenario: Read chain is derived from PropertyReadEdges

- **WHEN** a `ResolvedAssignment`'s path has edges `[PropertyReadEdge(customer), PropertyReadEdge(name), TypeTransformEdge(DirectAssignable)]`
- **THEN** `getReadChainEdges()` SHALL return the first two edges in order

### Requirement: TransformResolution retains exploration graph for debug, but is per-assignment

The previous `TransformResolution` type carried a per-mapping exploration graph alongside the winning path; with a single shared `ValueGraph` per method, the exploration "graph" for each assignment is simply the reachable sub-graph from its `SourceParamNode`. `TransformResolution` SHALL be either:

- removed (replaced by the combination of `ResolvedAssignment.path` and the method's `ValueGraph`), **or**
- retained as a thin accessor record exposing `(methodValueGraph, assignmentPath)` for `DumpResolvedPathsStage`.

The decision SHALL NOT surface in `GenerateStage`. `GenerateStage` SHALL consume `ResolvedAssignment` directly without needing `TransformResolution`.

#### Scenario: GenerateStage does not reference TransformResolution

- **WHEN** `GenerateStage` emits code for a resolved assignment
- **THEN** it SHALL read the path and edges from `ResolvedAssignment.getPath()` without going through `TransformResolution`

#### Scenario: Debug dump stage surfaces exploration context

- **WHEN** `DumpResolvedPathsStage` writes a debug artifact for a resolved assignment
- **THEN** it SHALL have access to both the `ValueGraph` (for exploration context) and the resolved `GraphPath` (for the winning trace)

### Requirement: 30-iteration exploration budget moves to BuildValueGraphStage

The 30-iteration budget that previously lived in `ResolveTransformsStage` BFS expansion SHALL now live in `BuildValueGraphStage`'s edge-proposal loop (the loop that asks registered strategies for new edges until fixpoint). `ResolvePathStage` SHALL NOT have an iteration budget — shortest-path search over a finite graph terminates naturally.

#### Scenario: Edge proposal fixpoint bounded to 30 iterations

- **WHEN** strategies keep contributing new edges to the `ValueGraph`
- **THEN** `BuildValueGraphStage` SHALL stop after 30 iterations and treat any remaining gaps as unresolved-for-validation

#### Scenario: ResolvePathStage has no iteration cap

- **WHEN** `ResolvePathStage` runs `BFSShortestPath` on a large `ValueGraph`
- **THEN** it SHALL run the algorithm to completion without applying an external iteration cap

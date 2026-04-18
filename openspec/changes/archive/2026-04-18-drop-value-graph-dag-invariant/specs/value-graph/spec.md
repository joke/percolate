## MODIFIED Requirements

### Requirement: ValueGraph invariants

Every `ValueGraph` produced by `BuildValueGraphStage` and held through the remaining stages SHALL satisfy:

- Exactly one `SourceParamNode` per method parameter of the matched method.
- Every `PropertyReadEdge` has source ∈ `{SourceParamNode, PropertyNode}` and target ∈ `PropertyNode`.
- Every `TypeTransformEdge` has source ∈ `{PropertyNode, TypedValueNode}` and target ∈ `{TypedValueNode, TargetSlotNode}`.
- Every `LiftEdge.innerPath` is a `GraphPath` whose vertices and edges are all present in the same `ValueGraph`.
- `TargetSlotNode`s have no outgoing edges.

The graph MAY contain directed cycles. In particular, inverse `TypeTransformStrategy` pairs (such as `OptionalWrap` / `OptionalUnwrap` and `TemporalToString` / `StringToTemporal`) coexist as 2-cycles between the same two `TypedValueNode`s when both directions are reachable. Downstream consumers (`ResolvePathStage`, `OptimizePathStage`, `GenerateStage`) walk only resolved `GraphPath`s, and `BFSShortestPath` is cycle-safe by construction, so cycles in the graph do not affect correctness.

These invariants SHALL be enforced by `BuildValueGraphStage` construction and MAY be checked assertively by `DumpValueGraphStage` in debug mode.

#### Scenario: Target slot has no outgoing edges

- **WHEN** a `ValueGraph` is inspected after `BuildValueGraphStage`
- **THEN** no `TargetSlotNode` SHALL have any outgoing edge

#### Scenario: LiftEdge innerPath edges all belong to the parent graph

- **WHEN** a `LiftEdge` with non-empty `innerPath` is on the graph
- **THEN** every vertex and every edge in `innerPath` SHALL be present in the parent `ValueGraph` (checked by vertex/edge identity)

#### Scenario: Inverse strategy pair forms a 2-cycle

- **WHEN** a method's `ValueGraph` contains a typed node `Optional<X>` and a typed node `X`, and both `OptionalWrapStrategy` and `OptionalUnwrapStrategy` propose edges between them
- **THEN** the graph SHALL contain both edges `X → Optional<X>` and `Optional<X> → X`, and `BuildValueGraphStage` SHALL NOT reject either edge as a cycle violation

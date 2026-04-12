## ADDED Requirements

### Requirement: TransformResolution captures full exploration graph alongside winning path
A `TransformResolution` value class SHALL hold:
- `explorationGraph`: the complete `DefaultDirectedGraph<TypeNode, TransformEdge>` built during BFS expansion, including all candidate edges
- `path`: `@Nullable GraphPath<TypeNode, TransformEdge>` — the shortest path found by `BFSShortestPath`, or `null` if no path was found

`ResolveTransformsStage.resolveTransformPath()` SHALL return `@Nullable TransformResolution` instead of `@Nullable GraphPath`. When no path is found the method SHALL still return a `TransformResolution` with `path = null` and the exploration graph as-is, so that debug output can show what was explored even for failed resolutions. When no edges were explored at all (e.g., identical types handled before BFS), the method SHALL return `null`.

#### Scenario: Successful resolution includes full graph
- **WHEN** BFS expansion explores 5 edges and finds a 2-edge shortest path
- **THEN** `TransformResolution.getExplorationGraph()` SHALL contain all 5 edges and `getPath()` SHALL contain the 2-edge path

#### Scenario: Failed resolution preserves exploration graph
- **WHEN** BFS expansion explores 3 edges but no path connects source to target
- **THEN** `resolveTransformPath` SHALL return a `TransformResolution` with `path = null` and `explorationGraph` containing all 3 explored edges

## MODIFIED Requirements

### Requirement: ResolvedMapping carries transform resolution context
`ResolvedMapping` SHALL hold a `@Nullable TransformResolution` field instead of (or in addition to) the bare `@Nullable GraphPath<TypeNode, TransformEdge>`. The `getEdges()` method SHALL continue to return the edge list from the path (via `TransformResolution.getPath()`) for backward compatibility with `ValidateTransformsStage` and `GenerateStage`. The `isResolved()` method SHALL check that `failure == null` and `transformResolution != null` and `transformResolution.getPath() != null`.

#### Scenario: ResolvedMapping exposes edges from TransformResolution
- **WHEN** a `ResolvedMapping` has a `TransformResolution` with a 2-edge path
- **THEN** `getEdges()` SHALL return the 2 edges from the path

#### Scenario: ResolvedMapping exposes exploration graph for debug
- **WHEN** a debug stage accesses `ResolvedMapping.getTransformResolution()`
- **THEN** it SHALL obtain the full `TransformResolution` including the exploration graph

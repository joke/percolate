# Transform Resolution Spec

## Purpose

Defines the ResolveTransforms stage that accepts a symbolic property graph (name-only nodes) and produces a fully resolved model by sequentially resolving `AccessEdge` source chains via property discovery and bridging type gaps between source and target properties using BFS expansion with registered `TypeTransformStrategy` implementations, producing a `ResolvedModel` with per-method accessor chains and `GraphPath` chains for code generation.

## Requirements

### Requirement: ResolveTransforms stage resolves type-gap transforms
The `ResolveTransforms` stage SHALL accept a symbolic property graph (name-only nodes with `AccessEdge` and `MappingEdge` connections) and produce a fully resolved model. For each method's symbolic graph, the stage SHALL:
1. Resolve each `AccessEdge` by discovering the accessor (getter/field) on the parent node's resolved type, determining the child node's type
2. Resolve each `MappingEdge` by constructing a JGraphT `DefaultDirectedGraph<TypeNode, TransformEdge>` and running BFS expansion with registered `TypeTransformStrategy` implementations to find a path from the resolved source type to the resolved target type. During BFS expansion, `TransformEdge` instances SHALL carry the raw `TransformProposal` without resolving code templates. After `BFSShortestPath` selects the shortest path, the stage SHALL resolve code templates only for edges on the selected path.
3. Discover target property write accessors via `TargetPropertyDiscovery` SPI

The `ResolutionContext` passed to type transform strategies SHALL contain the mapper's `TypeElement`, the current method's `ExecutableElement`, and the `Types` and `Elements` utilities. The BFS loop SHALL terminate after at most 30 iterations. The stage SHALL annotate unresolved edges with failure context rather than producing errors directly.

#### Scenario: Flat property resolves accessor and type transform
- **WHEN** symbolic graph has `SourceRootNode("order") → AccessEdge → SourcePropertyNode("name") → MappingEdge → TargetPropertyNode("name")` and `Order` has getter `getName()` returning `String` and target property `name` is type `String`
- **THEN** the stage SHALL resolve the access edge to a `GetterAccessor`, resolve the mapping edge via `DirectAssignableStrategy`, and produce a resolved mapping with the accessor and single-edge transform path with resolved `CodeTemplate`

#### Scenario: Nested chain resolves each segment sequentially
- **WHEN** symbolic graph has chain `SourceRootNode → "customer" → "address"` and `Order.getCustomer()` returns `Customer` and `Customer.getAddress()` returns `Address`
- **THEN** the stage SHALL resolve `"customer"` on `Order` yielding `Customer`, then resolve `"address"` on `Customer` yielding `Address`, producing an accessor chain `[GetterAccessor(customer), GetterAccessor(address)]`

#### Scenario: Non-assignable types with sibling method resolve via MethodCallStrategy
- **WHEN** a mapping edge connects resolved source type `Address` to target type `AddressDTO`, and the mapper has a sibling method `mapAddress(Address): AddressDTO`
- **THEN** `MethodCallStrategy` SHALL discover the method and `BFSShortestPath` SHALL find a single-edge path with method call code template resolved after path selection

#### Scenario: Container types resolve via multi-step strategy chain
- **WHEN** a mapping edge connects resolved source type `List<Person>` to target type `Set<PersonDTO>`, and the mapper has method `PersonDTO map(Person)`
- **THEN** strategies SHALL contribute edges producing a path: `List<Person>` → `Stream<Person>` → `Stream<PersonDTO>` → `Set<PersonDTO>`, with code templates resolved only for these path edges after BFS completes

#### Scenario: Dead-end edges do not trigger code template resolution
- **WHEN** BFS expansion adds edges that do not appear on the final shortest path
- **THEN** the stage SHALL NOT invoke `resolveCodeTemplate` for those edges

#### Scenario: Unresolved access edge annotated with failure context
- **WHEN** resolving access edge for segment `"adress"` on type `Customer` and no property `"adress"` exists
- **THEN** the stage SHALL annotate the failure with segment name, segment index, full chain, searched type, and available property names — without producing an error

#### Scenario: Unresolved type gap annotated for validation
- **WHEN** no strategy can bridge source type `Foo` to target type `Bar`
- **THEN** the mapping SHALL be marked unresolved with source and target types recorded

### Requirement: Transform chain model per mapping edge
Each resolved mapping SHALL carry a `GraphPath<TypeNode, TransformEdge>` representing the ordered sequence of transformation steps from source type to target type. Each `TransformEdge` in the path SHALL carry a `CodeTemplate` for code generation. Paths MAY be single-edge (direct, method call) or multi-edge (container mapping).

#### Scenario: DIRECT transform produces single-edge path
- **WHEN** a mapping resolves via `DirectAssignableStrategy`
- **THEN** the path SHALL contain one `TransformEdge` with identity code template

#### Scenario: Container transform produces multi-edge path
- **WHEN** `List<Person>` -> `Set<PersonDTO>` resolves via stream expansion
- **THEN** the path SHALL contain three `TransformEdge`s: StreamFromCollection, StreamMap, CollectToSet

### Requirement: ResolveTransforms produces a ResolvedModel
The `ResolveTransforms` stage SHALL produce a `ResolvedModel` containing per-method resolved mappings. Each resolved mapping SHALL carry: the source accessor chain (`List<ReadAccessor>` for chain segments), the target `WriteAccessor`, and the resolved `GraphPath<TypeNode, TransformEdge>` for the type transform. The `ResolvedModel` SHALL also carry the `TypeElement` mapper type and method metadata.

#### Scenario: Single-segment source produces single-element accessor chain
- **WHEN** `@Map(source = "name", target = "name")` resolves with getter `getName()`
- **THEN** the resolved mapping's source accessor chain SHALL contain one `GetterAccessor`

#### Scenario: Multi-segment source produces multi-element accessor chain
- **WHEN** `@Map(source = "customer.address", target = "addr")` resolves with `getCustomer()` and `getAddress()`
- **THEN** the resolved mapping's source accessor chain SHALL contain `[GetterAccessor(customer), GetterAccessor(address)]`

#### Scenario: Multi-method mapper produces per-method resolved mappings
- **WHEN** a mapper has methods `map(Order): OrderDTO` and `mapAddress(Address): AddressDTO`
- **THEN** the `ResolvedModel` SHALL contain resolved mappings for each method independently

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

### Requirement: ResolvedMapping carries transform resolution context
`ResolvedMapping` SHALL hold a `@Nullable TransformResolution` field instead of (or in addition to) the bare `@Nullable GraphPath<TypeNode, TransformEdge>`. The `getEdges()` method SHALL continue to return the edge list from the path (via `TransformResolution.getPath()`) for backward compatibility with `ValidateTransformsStage` and `GenerateStage`. The `isResolved()` method SHALL check that `failure == null` and `transformResolution != null` and `transformResolution.getPath() != null`.

#### Scenario: ResolvedMapping exposes edges from TransformResolution
- **WHEN** a `ResolvedMapping` has a `TransformResolution` with a 2-edge path
- **THEN** `getEdges()` SHALL return the 2 edges from the path

#### Scenario: ResolvedMapping exposes exploration graph for debug
- **WHEN** a debug stage accesses `ResolvedMapping.getTransformResolution()`
- **THEN** it SHALL obtain the full `TransformResolution` including the exploration graph

### Requirement: Unresolvable type gaps are left unresolved for ValidateTransforms
When no strategy contributes new edges and no path exists from source to target, the `ResolveTransforms` stage SHALL mark the mapping as unresolved rather than failing immediately. The `ValidateTransforms` stage is responsible for producing error diagnostics.

#### Scenario: No strategy matches for type gap
- **WHEN** resolved source type `Foo` maps to target type `Bar` and no strategy can bridge the gap
- **THEN** the resolved mapping SHALL be marked unresolved with the source and target types recorded for diagnostic purposes

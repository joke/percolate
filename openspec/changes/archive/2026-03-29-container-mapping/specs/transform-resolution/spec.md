## MODIFIED Requirements

### Requirement: ResolveTransforms stage resolves type-gap transforms
The `ResolveTransforms` stage SHALL walk each mapping edge in the validated `MappingGraph` and determine the transformation needed to bridge the source property type to the target property type. For each edge, it SHALL construct a JGraphT `DefaultDirectedGraph<TypeNode, TransformEdge>` and populate it using a BFS expansion loop: each iteration asks all registered `TypeTransformStrategy` implementations (discovered via `ServiceLoader`) for proposals on open type gaps, adds resulting edges to the graph, and checks for a complete path using `BFSShortestPath.findPathBetween()`. Resolution SHALL succeed when a path from source type node to target type node exists. If no strategy contributes new edges in an iteration, the gap SHALL be marked as unresolved. The loop SHALL terminate after at most 30 iterations.

#### Scenario: Assignable types resolve via DirectAssignableStrategy
- **WHEN** a mapping edge connects source property `name` (type `String`) to target property `name` (type `String`)
- **THEN** `DirectAssignableStrategy` SHALL contribute an edge and `BFSShortestPath` SHALL find a single-edge path with identity code template

#### Scenario: Non-assignable types with sibling method resolve via MethodCallStrategy
- **WHEN** a mapping edge connects source property `billingAddress` (type `Address`) to target property `address` (type `AddressDTO`), and the mapper has a sibling method `mapAddress(Address): AddressDTO`
- **THEN** `MethodCallStrategy` SHALL contribute an edge and `BFSShortestPath` SHALL find a single-edge path with method call code template

#### Scenario: Container types resolve via multi-step strategy chain
- **WHEN** a mapping edge connects source property `persons` (type `List<Person>`) to target property `persons` (type `Set<PersonDTO>`), and the mapper has method `PersonDTO map(Person)`
- **THEN** strategies SHALL contribute edges across iterations producing a path: `List<Person>` → `Stream<Person>` → `Stream<PersonDTO>` → `Set<PersonDTO>`

#### Scenario: Subtype assignability counts as DIRECT
- **WHEN** a mapping edge connects source property `value` (type `String`) to target property `value` (type `Object`)
- **THEN** `DirectAssignableStrategy` SHALL contribute an edge (since `String` is assignable to `Object`)

### Requirement: Transform chain model per mapping edge
Each resolved mapping SHALL carry a `GraphPath<TypeNode, TransformEdge>` representing the ordered sequence of transformation steps from source type to target type. Each `TransformEdge` in the path SHALL carry a `CodeTemplate` for code generation. Paths MAY be single-edge (direct, method call) or multi-edge (container mapping).

#### Scenario: DIRECT transform produces single-edge path
- **WHEN** a mapping resolves via `DirectAssignableStrategy`
- **THEN** the path SHALL contain one `TransformEdge` with identity code template

#### Scenario: Container transform produces multi-edge path
- **WHEN** `List<Person>` → `Set<PersonDTO>` resolves via stream expansion
- **THEN** the path SHALL contain three `TransformEdge`s: StreamFromCollection, StreamMap, CollectToSet

### Requirement: ResolveTransforms produces a ResolvedModel
The `ResolveTransforms` stage SHALL produce a `ResolvedModel` containing per-method resolved mappings. Each resolved mapping SHALL associate a source property, target property, and resolved `GraphPath`. The `ResolvedModel` SHALL also carry the `TypeElement` mapper type and the list of `DiscoveredMethod` entries for use by downstream stages.

#### Scenario: Multi-method mapper produces per-method resolved mappings
- **WHEN** a mapper has methods `map(Order): OrderDTO` and `mapAddress(Address): AddressDTO`
- **THEN** the `ResolvedModel` SHALL contain resolved mappings for each method independently

### Requirement: Unresolvable type gaps are left unresolved for ValidateTransforms
When no strategy contributes new edges and no path exists from source to target, the `ResolveTransforms` stage SHALL mark the mapping as unresolved rather than failing immediately. The `ValidateTransforms` stage is responsible for producing error diagnostics.

#### Scenario: No strategy matches for type gap
- **WHEN** source property `data` (type `Foo`) maps to target property `data` (type `Bar`) and no strategy can bridge the gap
- **THEN** the resolved mapping SHALL be marked unresolved with the source and target types recorded for diagnostic purposes

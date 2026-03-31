## MODIFIED Requirements

### Requirement: ResolveTransforms stage resolves type-gap transforms
The `ResolveTransforms` stage SHALL walk each mapping edge in the validated `MappingGraph` and determine the transformation needed to bridge the source property type to the target property type. For each edge, it SHALL construct a JGraphT `DefaultDirectedGraph<TypeNode, TransformEdge>` and populate it using a BFS expansion loop: each iteration asks all registered `TypeTransformStrategy` implementations (discovered via `ServiceLoader`) for proposals on open type gaps, adds resulting edges to the graph, and checks for a complete path using `BFSShortestPath.findPathBetween()`. The `ResolutionContext` passed to strategies SHALL contain the mapper's `TypeElement`, the current method's `ExecutableElement`, and the `Types` and `Elements` utilities. Resolution SHALL succeed when a path from source type node to target type node exists. If no strategy contributes new edges in an iteration, the gap SHALL be marked as unresolved. The loop SHALL terminate after at most 30 iterations.

#### Scenario: Assignable types resolve via DirectAssignableStrategy
- **WHEN** a mapping edge connects source property `name` (type `String`) to target property `name` (type `String`)
- **THEN** `DirectAssignableStrategy` SHALL contribute an edge and `BFSShortestPath` SHALL find a single-edge path with identity code template

#### Scenario: Non-assignable types with sibling abstract method resolve via MethodCallStrategy
- **WHEN** a mapping edge connects source property `billingAddress` (type `Address`) to target property `address` (type `AddressDTO`), and the mapper has an abstract sibling method `mapAddress(Address): AddressDTO`
- **THEN** `MethodCallStrategy` SHALL discover the method via `Elements.getAllMembers()` and `BFSShortestPath` SHALL find a single-edge path with method call code template

#### Scenario: Non-assignable types with default method resolve via MethodCallStrategy
- **WHEN** a mapping edge connects source property `address` (type `Address`) to target property `address` (type `AddressDTO`), and the mapper has a default method `default AddressDTO mapAddress(Address a) { ... }`
- **THEN** `MethodCallStrategy` SHALL discover the default method via `Elements.getAllMembers()` and propose a method call transformation

#### Scenario: Inherited method from supertype resolves via MethodCallStrategy
- **WHEN** a mapping edge requires converting `Address` to `AddressDTO`, and the mapper extends a base interface that declares `AddressDTO mapAddress(Address a)`
- **THEN** `MethodCallStrategy` SHALL discover the inherited method via `Elements.getAllMembers()` and propose a method call transformation

#### Scenario: Container types resolve via multi-step strategy chain
- **WHEN** a mapping edge connects source property `persons` (type `List<Person>`) to target property `persons` (type `Set<PersonDTO>`), and the mapper has method `PersonDTO map(Person)`
- **THEN** strategies SHALL contribute edges across iterations producing a path: `List<Person>` -> `Stream<Person>` -> `Stream<PersonDTO>` -> `Set<PersonDTO>`

#### Scenario: Subtype assignability counts as DIRECT
- **WHEN** a mapping edge connects source property `value` (type `String`) to target property `value` (type `Object`)
- **THEN** `DirectAssignableStrategy` SHALL contribute an edge (since `String` is assignable to `Object`)

### Requirement: ResolveTransforms produces a ResolvedModel
The `ResolveTransforms` stage SHALL produce a `ResolvedModel` containing per-method resolved mappings. Each resolved mapping SHALL associate a source property, target property, and resolved `GraphPath`. The `ResolvedModel` SHALL also carry the `TypeElement` mapper type and the list of `DiscoveredMethod` entries for use by downstream stages.

#### Scenario: Multi-method mapper produces per-method resolved mappings
- **WHEN** a mapper has methods `map(Order): OrderDTO` and `mapAddress(Address): AddressDTO`
- **THEN** the `ResolvedModel` SHALL contain resolved mappings for each method independently

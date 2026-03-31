## MODIFIED Requirements

### Requirement: ResolveTransforms stage resolves type-gap transforms
The `ResolveTransforms` stage SHALL accept a symbolic property graph (name-only nodes with `AccessEdge` and `MappingEdge` connections) and produce a fully resolved model. For each method's symbolic graph, the stage SHALL:
1. Resolve each `AccessEdge` by discovering the accessor (getter/field) on the parent node's resolved type, determining the child node's type
2. Resolve each `MappingEdge` by constructing a JGraphT `DefaultDirectedGraph<TypeNode, TransformEdge>` and running BFS expansion with registered `TypeTransformStrategy` implementations to find a path from the resolved source type to the resolved target type
3. Discover target property write accessors via `TargetPropertyDiscovery` SPI

The `ResolutionContext` passed to type transform strategies SHALL contain the mapper's `TypeElement`, the current method's `ExecutableElement`, and the `Types` and `Elements` utilities. The BFS loop SHALL terminate after at most 30 iterations. The stage SHALL annotate unresolved edges with failure context rather than producing errors directly.

#### Scenario: Flat property resolves accessor and type transform
- **WHEN** symbolic graph has `SourceRootNode("order") → AccessEdge → SourcePropertyNode("name") → MappingEdge → TargetPropertyNode("name")` and `Order` has getter `getName()` returning `String` and target property `name` is type `String`
- **THEN** the stage SHALL resolve the access edge to a `GetterAccessor`, resolve the mapping edge via `DirectAssignableStrategy`, and produce a resolved mapping with the accessor and single-edge transform path

#### Scenario: Nested chain resolves each segment sequentially
- **WHEN** symbolic graph has chain `SourceRootNode → "customer" → "address"` and `Order.getCustomer()` returns `Customer` and `Customer.getAddress()` returns `Address`
- **THEN** the stage SHALL resolve `"customer"` on `Order` yielding `Customer`, then resolve `"address"` on `Customer` yielding `Address`, producing an accessor chain `[GetterAccessor(customer), GetterAccessor(address)]`

#### Scenario: Non-assignable types with sibling method resolve via MethodCallStrategy
- **WHEN** a mapping edge connects resolved source type `Address` to target type `AddressDTO`, and the mapper has a sibling method `mapAddress(Address): AddressDTO`
- **THEN** `MethodCallStrategy` SHALL discover the method and `BFSShortestPath` SHALL find a single-edge path with method call code template

#### Scenario: Container types resolve via multi-step strategy chain
- **WHEN** a mapping edge connects resolved source type `List<Person>` to target type `Set<PersonDTO>`, and the mapper has method `PersonDTO map(Person)`
- **THEN** strategies SHALL contribute edges producing a path: `List<Person>` → `Stream<Person>` → `Stream<PersonDTO>` → `Set<PersonDTO>`

#### Scenario: Unresolved access edge annotated with failure context
- **WHEN** resolving access edge for segment `"adress"` on type `Customer` and no property `"adress"` exists
- **THEN** the stage SHALL annotate the failure with segment name, segment index, full chain, searched type, and available property names — without producing an error

#### Scenario: Unresolved type gap annotated for validation
- **WHEN** no strategy can bridge source type `Foo` to target type `Bar`
- **THEN** the mapping SHALL be marked unresolved with source and target types recorded

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

### Requirement: Unresolvable type gaps are left unresolved for ValidateTransforms
When no strategy contributes new edges and no path exists from source to target, the `ResolveTransforms` stage SHALL mark the mapping as unresolved rather than failing immediately. The `ValidateTransforms` stage is responsible for producing error diagnostics.

#### Scenario: No strategy matches for type gap
- **WHEN** resolved source type `Foo` maps to target type `Bar` and no strategy can bridge the gap
- **THEN** the resolved mapping SHALL be marked unresolved with the source and target types recorded for diagnostic purposes

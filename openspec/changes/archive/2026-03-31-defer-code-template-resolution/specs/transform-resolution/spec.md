## MODIFIED Requirements

### Requirement: ResolveTransforms stage resolves type-gap transforms
The `ResolveTransforms` stage SHALL accept a symbolic property graph (name-only nodes with `AccessEdge` and `MappingEdge` connections) and produce a fully resolved model. For each method's symbolic graph, the stage SHALL:
1. Resolve each `AccessEdge` by discovering the accessor (getter/field) on the parent node's resolved type, determining the child node's type
2. Resolve each `MappingEdge` by constructing a JGraphT `DefaultDirectedGraph<TypeNode, TransformEdge>` and running BFS expansion with registered `TypeTransformStrategy` implementations to find a path from the resolved source type to the resolved target type. During BFS expansion, `TransformEdge` instances SHALL carry the raw `TransformProposal` without resolving code templates. After `BFSShortestPath` selects the shortest path, the stage SHALL resolve code templates only for edges on the selected path.
3. Discover target property write accessors via `TargetPropertyDiscovery` SPI

The `ResolutionContext` passed to type transform strategies SHALL contain the mapper's `TypeElement`, the current method's `ExecutableElement`, and the `Types` and `Elements` utilities. The BFS loop SHALL terminate after at most 30 iterations. The stage SHALL annotate unresolved edges with failure context rather than producing errors directly.

#### Scenario: Flat property resolves accessor and type transform
- **WHEN** symbolic graph has `SourceRootNode("order") -> AccessEdge -> SourcePropertyNode("name") -> MappingEdge -> TargetPropertyNode("name")` and `Order` has getter `getName()` returning `String` and target property `name` is type `String`
- **THEN** the stage SHALL resolve the access edge to a `GetterAccessor`, resolve the mapping edge via `DirectAssignableStrategy`, and produce a resolved mapping with the accessor and single-edge transform path with resolved `CodeTemplate`

#### Scenario: Nested chain resolves each segment sequentially
- **WHEN** symbolic graph has chain `SourceRootNode -> "customer" -> "address"` and `Order.getCustomer()` returns `Customer` and `Customer.getAddress()` returns `Address`
- **THEN** the stage SHALL resolve `"customer"` on `Order` yielding `Customer`, then resolve `"address"` on `Customer` yielding `Address`, producing an accessor chain `[GetterAccessor(customer), GetterAccessor(address)]`

#### Scenario: Non-assignable types with sibling method resolve via MethodCallStrategy
- **WHEN** a mapping edge connects resolved source type `Address` to target type `AddressDTO`, and the mapper has a sibling method `mapAddress(Address): AddressDTO`
- **THEN** `MethodCallStrategy` SHALL discover the method and `BFSShortestPath` SHALL find a single-edge path with method call code template resolved after path selection

#### Scenario: Container types resolve via multi-step strategy chain
- **WHEN** a mapping edge connects resolved source type `List<Person>` to target type `Set<PersonDTO>`, and the mapper has method `PersonDTO map(Person)`
- **THEN** strategies SHALL contribute edges across iterations producing a path: `List<Person>` -> `Stream<Person>` -> `Stream<PersonDTO>` -> `Set<PersonDTO>`, with code templates resolved only for these path edges after BFS completes

#### Scenario: Dead-end edges do not trigger code template resolution
- **WHEN** BFS expansion adds edges that do not appear on the final shortest path
- **THEN** the stage SHALL NOT invoke `resolveCodeTemplate` for those edges

#### Scenario: Unresolved access edge annotated with failure context
- **WHEN** resolving access edge for segment `"adress"` on type `Customer` and no property `"adress"` exists
- **THEN** the stage SHALL annotate the failure with segment name, segment index, full chain, searched type, and available property names — without producing an error

#### Scenario: Unresolved type gap annotated for validation
- **WHEN** no strategy can bridge source type `Foo` to target type `Bar`
- **THEN** the mapping SHALL be marked unresolved with source and target types recorded

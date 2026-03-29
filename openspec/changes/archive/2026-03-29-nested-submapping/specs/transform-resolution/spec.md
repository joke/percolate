## ADDED Requirements

### Requirement: ResolveTransforms stage resolves type-gap transforms
The `ResolveTransforms` stage SHALL walk each mapping edge in the validated `MappingGraph` and determine the transform needed to bridge the source property type to the target property type. It SHALL use `Types.isAssignable()` to check type compatibility. If types are assignable, the transform SHALL be `DIRECT`. If types are not assignable, the stage SHALL search for a sibling mapping method on the same mapper whose source type is assignable from the edge's source type and whose target type is assignable to the edge's target type. If found, the transform SHALL be `SUBMAP` referencing that method.

#### Scenario: Assignable types resolve to DIRECT
- **WHEN** a mapping edge connects source property `name` (type `String`) to target property `name` (type `String`)
- **THEN** the resolved transform SHALL be a single-step chain with operation `DIRECT`

#### Scenario: Non-assignable types with sibling method resolve to SUBMAP
- **WHEN** a mapping edge connects source property `billingAddress` (type `Address`) to target property `address` (type `AddressDTO`), and the mapper has a sibling method `mapAddress(Address): AddressDTO`
- **THEN** the resolved transform SHALL be a single-step chain with operation `SUBMAP` referencing the `mapAddress` method

#### Scenario: Subtype assignability counts as DIRECT
- **WHEN** a mapping edge connects source property `value` (type `String`) to target property `value` (type `Object`)
- **THEN** the resolved transform SHALL be `DIRECT` (since `String` is assignable to `Object`)

### Requirement: Transform chain model per mapping edge
Each resolved mapping SHALL carry a `List<TransformNode>` representing the chain of operations to convert the source value to the target value. Each `TransformNode` SHALL have an input type (`TypeMirror`), output type (`TypeMirror`), and a `TransformOperation`. For this change, chains SHALL always be single-element.

#### Scenario: DIRECT transform chain
- **WHEN** a mapping resolves as DIRECT
- **THEN** the chain SHALL contain one `TransformNode` with `DIRECT` operation, where input type equals the source property type and output type equals the target property type

#### Scenario: SUBMAP transform chain
- **WHEN** a mapping resolves as SUBMAP via sibling method `mapAddress`
- **THEN** the chain SHALL contain one `TransformNode` with `SUBMAP` operation carrying a reference to the `mapAddress` `DiscoveredMethod`, where input type is the source property type and output type is the target property type

### Requirement: ResolveTransforms produces a ResolvedModel
The `ResolveTransforms` stage SHALL produce a `ResolvedModel` containing per-method resolved mappings. Each resolved mapping SHALL associate a source property, target property, and transform chain. The `ResolvedModel` SHALL also carry the `TypeElement` mapper type and the list of `DiscoveredMethod` entries for use by downstream stages.

#### Scenario: Multi-method mapper produces per-method resolved mappings
- **WHEN** a mapper has methods `map(Order): OrderDTO` and `mapAddress(Address): AddressDTO`
- **THEN** the `ResolvedModel` SHALL contain resolved mappings for each method independently

### Requirement: Unresolvable type gaps are left unresolved for ValidateTransforms
When the `ResolveTransforms` stage cannot find a DIRECT assignability or a sibling method for a type gap, it SHALL mark the mapping as `UNRESOLVED` rather than failing immediately. The `ValidateTransforms` stage is responsible for producing error diagnostics.

#### Scenario: No sibling method for type gap
- **WHEN** source property `data` (type `Foo`) maps to target property `data` (type `Bar`) and no sibling method maps `Foo → Bar`
- **THEN** the resolved transform SHALL be marked `UNRESOLVED` with the source and target types recorded for diagnostic purposes

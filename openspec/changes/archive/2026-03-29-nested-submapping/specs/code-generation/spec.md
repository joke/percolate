## MODIFIED Requirements

### Requirement: GenerateStage produces JavaFile from resolved model
The `GenerateStage` SHALL consume a `ResolvedModel` (instead of `MappingGraph`) and produce a `JavaFile` using Palantir JavaPoet. It SHALL walk each method's resolved mappings and emit code based on the transform chain. For `DIRECT` transforms, it SHALL emit source access as today. For `SUBMAP` transforms, it SHALL emit a delegation call to the sibling method (e.g., `this.mapAddress(source.getBillingAddress())`). It SHALL support constructor-based and field-based target construction based on the `WriteAccessor` types present.

#### Scenario: Constructor-based target generation with DIRECT transforms
- **WHEN** the target type's properties are all `ConstructorParamAccessor` write accessors and all transforms are DIRECT
- **THEN** the generated code SHALL gather mapped values and pass them as constructor arguments in parameter order

#### Scenario: Field-based target generation with DIRECT transforms
- **WHEN** the target type's properties are all `FieldWriteAccessor` write accessors and all transforms are DIRECT
- **THEN** the generated code SHALL create the target via no-arg constructor and assign fields directly

#### Scenario: SUBMAP transform emits sibling method call
- **WHEN** a resolved mapping has a `SUBMAP` transform referencing sibling method `mapAddress`
- **THEN** the generated code SHALL emit `mapAddress(source.getBillingAddress())` (or equivalent based on the source accessor type) as the value expression for that target property

#### Scenario: Mixed DIRECT and SUBMAP in same method
- **WHEN** method `map(Order): OrderDTO` has DIRECT transform for `name` and SUBMAP transform for `billingAddress` → `address` via `mapAddress`
- **THEN** the generated constructor call SHALL be `new OrderDTO(source.getName(), mapAddress(source.getBillingAddress()))` (argument order per constructor parameter index)

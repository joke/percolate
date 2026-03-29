# Code Generation Spec

## Purpose

Defines the code generation stage including the AnalyzeStage for extracting mapper models and the GenerateStage for producing JavaPoet-based implementation classes with support for constructor-based and field-based target construction.

## Requirements

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

### Requirement: Generated class naming and structure
The generated class SHALL be named `<MapperName>Impl` in the same package as the mapper interface. It SHALL implement the mapper interface and be declared `public final`.

#### Scenario: Interface PersonMapper generates PersonMapperImpl
- **WHEN** `@Mapper` is applied to `com.example.PersonMapper`
- **THEN** the generated class SHALL be `com.example.PersonMapperImpl` implementing `PersonMapper`

### Requirement: Generated method uses getter-based source access
For source properties discovered via `GetterAccessor`, the generated code SHALL call the getter method on the source parameter (e.g., `source.getFirstName()`).

#### Scenario: Getter-based source access
- **WHEN** source property `firstName` has a `GetterAccessor` for `getFirstName()`
- **THEN** the generated method body SHALL read the value via `source.getFirstName()`

### Requirement: Generated method uses field-based source access
For source properties discovered via `FieldReadAccessor`, the generated code SHALL read the public field directly (e.g., `source.firstName`).

#### Scenario: Field-based source access
- **WHEN** source property `firstName` has a `FieldReadAccessor`
- **THEN** the generated method body SHALL read the value via `source.firstName`

### Requirement: GenerateStage writes JavaFile to Filer
After producing the `JavaFile`, the `GenerateStage` SHALL write it to the annotation processing `Filer` so `javac` picks it up as a generated source file.

#### Scenario: Generated file written to Filer
- **WHEN** generation succeeds
- **THEN** the `JavaFile` SHALL be written via `Filer` and the stage SHALL return success

### Requirement: AnalyzeStage extracts mapper model
The `AnalyzeStage` SHALL extract a `MapperModel` from the `@Mapper`-annotated `TypeElement`. It SHALL identify abstract methods, parse `@Map` and `@MapList` annotations into `MapDirective` instances, and extract source/target types from method signatures.

#### Scenario: Interface with single mapping method
- **WHEN** a `@Mapper` interface has one abstract method `Target map(Source s)` with `@Map` annotations
- **THEN** the stage SHALL produce a `MapperModel` with one `MappingMethodModel` containing the method element, source type, target type, and parsed directives

#### Scenario: Method without @Map annotations
- **WHEN** an abstract method has no `@Map` annotations
- **THEN** the stage SHALL produce a `MappingMethodModel` with an empty directives list

#### Scenario: Method with multiple @Map annotations (via @MapList)
- **WHEN** a method has multiple `@Map` annotations
- **THEN** all SHALL be parsed into separate `MapDirective` instances

#### Scenario: Non-abstract method ignored
- **WHEN** the mapper interface has a default method
- **THEN** it SHALL NOT appear in the `MapperModel`'s method list

#### Scenario: Method with no parameters
- **WHEN** an abstract method has no parameters
- **THEN** the stage SHALL return a failure with a diagnostic indicating the method needs a source parameter

#### Scenario: Method with void return type
- **WHEN** an abstract method returns void
- **THEN** the stage SHALL return a failure with a diagnostic indicating the method needs a return type

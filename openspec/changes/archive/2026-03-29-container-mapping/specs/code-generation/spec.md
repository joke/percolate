## MODIFIED Requirements

### Requirement: GenerateStage produces JavaFile from resolved model
The `GenerateStage` SHALL consume a `ResolvedModel` and produce a `JavaFile` using Palantir JavaPoet. It SHALL walk each method's resolved mappings and emit code by composing the `CodeTemplate`s from the resolved `GraphPath`'s edge list. For each mapping, the stage SHALL start with the source read expression and apply each edge's `CodeTemplate` in path order to produce the final value expression. It SHALL support constructor-based and field-based target construction based on the `WriteAccessor` types present.

#### Scenario: Constructor-based target generation with DIRECT transforms
- **WHEN** the target type's properties are all `ConstructorParamAccessor` write accessors and all transforms are DIRECT
- **THEN** the generated code SHALL gather mapped values and pass them as constructor arguments in parameter order

#### Scenario: Field-based target generation with DIRECT transforms
- **WHEN** the target type's properties are all `FieldWriteAccessor` write accessors and all transforms are DIRECT
- **THEN** the generated code SHALL create the target via no-arg constructor and assign fields directly

#### Scenario: SUBMAP transform emits sibling method call via CodeTemplate
- **WHEN** a resolved mapping has a single-edge path with `MethodCallStrategy`'s code template referencing method `mapAddress`
- **THEN** the generated code SHALL emit `mapAddress(source.getBillingAddress())` by applying the code template to the read expression

#### Scenario: Container mapping emits composed code templates
- **WHEN** a resolved mapping for `List<Person>` → `Set<PersonDTO>` has a 3-edge path (StreamFromCollection, StreamMap, CollectToSet)
- **THEN** the generated code SHALL compose templates left-to-right producing `source.getPersons().stream().map(e -> map(e)).collect(Collectors.toSet())`

#### Scenario: Mixed DIRECT and container transforms in same method
- **WHEN** method `map(Order): OrderDTO` has DIRECT transform for `name` and container transform for `items` (`List<Item>` → `Set<ItemDTO>`)
- **THEN** the generated constructor call SHALL combine identity template for `name` and composed container templates for `items`

#### Scenario: Optional mapping emits map call via CodeTemplate
- **WHEN** a resolved mapping for `Optional<Person>` → `Optional<PersonDTO>` has an `OptionalMapStrategy` edge
- **THEN** the generated code SHALL emit `source.getPerson().map(e -> map(e))`

### Requirement: GenerateStage composes CodeTemplates by walking GraphPath
For each resolved mapping, the `GenerateStage` SHALL iterate over `graphPath.getEdgeList()` and apply each `TransformEdge`'s `CodeTemplate` sequentially. The initial input SHALL be the source property read expression. Each template's output SHALL become the next template's input. The final output SHALL be the value expression used for target construction.

#### Scenario: Single-edge path applies one template
- **WHEN** the path has one edge (e.g., DirectAssignable with identity template)
- **THEN** the value expression SHALL be the source read expression passed through the single template

#### Scenario: Multi-edge path composes templates left-to-right
- **WHEN** the path has edges [StreamFromCollection, StreamMap, CollectToSet]
- **THEN** the value expression SHALL be `collectToSet(streamMap(streamFromCollection(readExpr)))` — each template applied in order

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

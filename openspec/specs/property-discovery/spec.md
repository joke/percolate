# Property Discovery Spec

## Purpose

Defines the SPI-based property discovery system for source and target types, including built-in strategies (getter, constructor, field), priority-based resolution, and accessor models.

## Requirements

### Requirement: SourcePropertyDiscovery SPI interface
`SourcePropertyDiscovery` SHALL be a public SPI interface with methods `int priority()` and `List<ReadAccessor> discover(TypeMirror type, Elements elements, Types types)`. Implementations SHALL be loaded via `ServiceLoader` using the processor's own classloader. Built-in implementations SHALL be registered using `@AutoService(SourcePropertyDiscovery.class)`. `ResolveTransformsStage` loads and uses the SPI directly, applying priority-based merging when resolving access edges.

#### Scenario: Custom source discovery on annotationProcessor classpath
- **WHEN** a user provides a jar containing a `SourcePropertyDiscovery` implementation registered in `META-INF/services`
- **THEN** `ResolveTransformsStage` SHALL load and invoke it alongside built-in strategies

#### Scenario: Built-in source strategies loaded in consumer project
- **WHEN** the processor runs as an annotation processor in a consumer project
- **THEN** `ResolveTransformsStage` SHALL discover all built-in `SourcePropertyDiscovery` implementations (`GetterDiscovery`, `FieldDiscovery.Source`) via `ServiceLoader` using the processor's classloader

### Requirement: TargetPropertyDiscovery SPI interface
`TargetPropertyDiscovery` SHALL be a public SPI interface with methods `int priority()` and `List<WriteAccessor> discover(TypeMirror type, Elements elements, Types types)`. Implementations SHALL be loaded via `ServiceLoader` using the processor's own classloader. Built-in implementations SHALL be registered using `@AutoService(TargetPropertyDiscovery.class)`. `ResolveTransformsStage` loads and uses the SPI directly, applying priority-based merging when resolving target properties.

#### Scenario: Custom target discovery on annotationProcessor classpath
- **WHEN** a user provides a jar containing a `TargetPropertyDiscovery` implementation registered in `META-INF/services`
- **THEN** `ResolveTransformsStage` SHALL load and invoke it alongside built-in strategies

#### Scenario: Built-in target strategies loaded in consumer project
- **WHEN** the processor runs as an annotation processor in a consumer project
- **THEN** `ResolveTransformsStage` SHALL discover all built-in `TargetPropertyDiscovery` implementations (`ConstructorDiscovery`, `FieldDiscovery.Target`) via `ServiceLoader` using the processor's classloader

### Requirement: BuildGraphStage performs lightweight property name scanning
`BuildGraphStage` SHALL scan source and target types for property names using its own lightweight logic. The scan SHALL detect `getX()`/`isX()` public no-arg methods and public non-static fields, extracting property names from them. The scan SHALL return a `Set<String>` of property names — no `ReadAccessor`, no `WriteAccessor`, no `TypeMirror`. This scan is used solely for auto-mapping name matching.

#### Scenario: Getter method produces property name
- **WHEN** source type has public method `String getFirstName()`
- **THEN** the name scan SHALL include `"firstName"`

#### Scenario: Boolean getter produces property name
- **WHEN** source type has public method `boolean isActive()`
- **THEN** the name scan SHALL include `"active"`

#### Scenario: Public field produces property name
- **WHEN** source type has public field `String lastName`
- **THEN** the name scan SHALL include `"lastName"`

#### Scenario: Private field excluded
- **WHEN** source type has private field `String secret`
- **THEN** the name scan SHALL NOT include `"secret"`

#### Scenario: Static field excluded
- **WHEN** source type has public static field `String CONSTANT`
- **THEN** the name scan SHALL NOT include `"CONSTANT"`

### Requirement: ResolveTransformsStage performs full property discovery
`ResolveTransformsStage` SHALL use the `SourcePropertyDiscovery` and `TargetPropertyDiscovery` SPI interfaces (loaded via `ServiceLoader`) to perform full property discovery when resolving symbolic graph edges. For each `AccessEdge`, the stage SHALL discover properties on the resolved type of the parent node and look up the child segment's property name. For target properties, the stage SHALL discover write accessors on the target type.

#### Scenario: Resolve source property via getter discovery
- **WHEN** resolving `AccessEdge` for segment `"firstName"` on type `Person` where `Person` has `getFirstName()` returning `String`
- **THEN** the resolution SHALL discover a `GetterAccessor` and resolve the type as `String`

#### Scenario: Resolve source property via field discovery
- **WHEN** resolving `AccessEdge` for segment `"firstName"` on type `Person` where `Person` has public field `String firstName` and no getter
- **THEN** the resolution SHALL discover a `FieldReadAccessor` and resolve the type as `String`

#### Scenario: Priority-based resolution applies
- **WHEN** resolving `AccessEdge` for segment `"name"` where both a getter and a field exist for `"name"`
- **THEN** the higher-priority accessor (getter, priority 100) SHALL be used over the field (priority 50)

### Requirement: Priority-based property resolution
When multiple strategies discover the same property name, the strategy with the highest `priority()` value SHALL win. If two strategies have equal priority for the same property name, the first one loaded SHALL win.

#### Scenario: Getter and field discover same property
- **WHEN** `GetterDiscovery` (priority 100) discovers `firstName` via `getFirstName()` and `FieldDiscovery` (priority 50) discovers `firstName` via a public field
- **THEN** the getter accessor SHALL be used

#### Scenario: Equal priority strategies
- **WHEN** two strategies with the same priority discover the same property name
- **THEN** the first loaded strategy's accessor SHALL be used without error

### Requirement: GetterDiscovery built-in strategy
`GetterDiscovery` SHALL discover source properties by finding public no-arg methods matching `getX()` or `isX()` patterns on the source type. It SHALL return `ReadAccessor` instances wrapping the getter `ExecutableElement`. Its priority SHALL be 100. It SHALL be annotated with `@AutoService(SourcePropertyDiscovery.class)`.

#### Scenario: Standard getter method
- **WHEN** the source type has a public method `String getFirstName()`
- **THEN** `GetterDiscovery` SHALL produce a `ReadAccessor` for property name `firstName`

#### Scenario: Boolean getter method
- **WHEN** the source type has a public method `boolean isActive()`
- **THEN** `GetterDiscovery` SHALL produce a `ReadAccessor` for property name `active`

#### Scenario: Non-getter method ignored
- **WHEN** the source type has a public method `void doSomething()`
- **THEN** `GetterDiscovery` SHALL NOT produce a `ReadAccessor` for it

### Requirement: ConstructorDiscovery built-in strategy
`ConstructorDiscovery` SHALL discover target properties by inspecting constructor parameters of the target type. It SHALL return `WriteAccessor` instances wrapping the constructor `ExecutableElement` and parameter index. Its priority SHALL be 100. It SHALL be annotated with `@AutoService(TargetPropertyDiscovery.class)`.

#### Scenario: Single constructor with named parameters
- **WHEN** the target type has a constructor `Target(String givenName, String familyName)`
- **THEN** `ConstructorDiscovery` SHALL produce `WriteAccessor` instances for `givenName` (index 0) and `familyName` (index 1)

#### Scenario: Multiple constructors
- **WHEN** the target type has multiple constructors
- **THEN** `ConstructorDiscovery` SHALL use the constructor with the most parameters

### Requirement: FieldDiscovery built-in strategy
`FieldDiscovery` SHALL discover properties by finding public non-static fields on a type. For source types it SHALL return `ReadAccessor` instances via `FieldDiscovery.Source`, annotated with `@AutoService(SourcePropertyDiscovery.class)`. For target types it SHALL return `WriteAccessor` instances via `FieldDiscovery.Target`, annotated with `@AutoService(TargetPropertyDiscovery.class)`. Its priority SHALL be 50.

#### Scenario: Public field on source type
- **WHEN** the source type has a public field `String firstName`
- **THEN** `FieldDiscovery` SHALL produce a `ReadAccessor` for property name `firstName`

#### Scenario: Private field ignored
- **WHEN** the source type has a private field `String secret`
- **THEN** `FieldDiscovery` SHALL NOT produce a `ReadAccessor` for it

#### Scenario: Static field ignored
- **WHEN** the source type has a public static field `String CONSTANT`
- **THEN** `FieldDiscovery` SHALL NOT produce a `ReadAccessor` for it

### Requirement: ReadAccessor models source property access
`ReadAccessor` SHALL carry the property name, type (`TypeMirror`), and the element used for access (getter method or field). Concrete types: `GetterAccessor` (wraps `ExecutableElement`) and `FieldReadAccessor` (wraps `VariableElement`). Accessor methods SHALL follow JavaBean naming conventions (`getName()`, `getType()`). `ReadAccessor` SHALL use Lombok `@Getter` and `@RequiredArgsConstructor(access = AccessLevel.PROTECTED)` to generate accessors and constructor. Leaf subclasses SHALL use Lombok `@Getter` for their own fields.

#### Scenario: GetterAccessor provides method element
- **WHEN** a `GetterAccessor` is created for `getFirstName()`
- **THEN** `getName()` SHALL return `firstName` and `getMethod()` SHALL return the method's `ExecutableElement`

### Requirement: WriteAccessor models target property access
`WriteAccessor` SHALL carry the property name, type (`TypeMirror`), and the element used for writing. Concrete types: `ConstructorParamAccessor` (wraps constructor `ExecutableElement` and parameter index) and `FieldWriteAccessor` (wraps `VariableElement`). Accessor methods SHALL follow JavaBean naming conventions (`getName()`, `getType()`). `WriteAccessor` SHALL use Lombok `@Getter` and `@RequiredArgsConstructor(access = AccessLevel.PROTECTED)` to generate accessors and constructor. Leaf subclasses SHALL use Lombok `@Getter` for their own fields.

#### Scenario: ConstructorParamAccessor provides constructor and index
- **WHEN** a `ConstructorParamAccessor` is created for constructor param `givenName` at index 0
- **THEN** `getName()` SHALL return `givenName`, `getConstructor()` SHALL return the constructor element, and `getParamIndex()` SHALL return 0

# Property Discovery Spec

## Purpose

Defines the SPI-based property discovery system for source and target types, including built-in strategies (getter, constructor, field), priority-based resolution, and accessor models.

## Requirements

### Requirement: SourcePropertyDiscovery SPI interface
`SourcePropertyDiscovery` SHALL be a public SPI interface with methods `int priority()` and `List<ReadAccessor> discover(TypeMirror type, Elements elements, Types types)`. Implementations SHALL be loaded via `ServiceLoader` using the processor's own classloader. Built-in implementations SHALL be registered using `@AutoService(SourcePropertyDiscovery.class)`.

#### Scenario: Custom source discovery on annotationProcessor classpath
- **WHEN** a user provides a jar containing a `SourcePropertyDiscovery` implementation registered in `META-INF/services`
- **THEN** the `DiscoverStage` SHALL load and invoke it alongside built-in strategies

#### Scenario: Built-in source strategies loaded in consumer project
- **WHEN** the processor runs as an annotation processor in a consumer project
- **THEN** `DiscoverStage` SHALL discover all built-in `SourcePropertyDiscovery` implementations (`GetterDiscovery`, `FieldDiscovery.Source`) via `ServiceLoader` using the processor's classloader

### Requirement: TargetPropertyDiscovery SPI interface
`TargetPropertyDiscovery` SHALL be a public SPI interface with methods `int priority()` and `List<WriteAccessor> discover(TypeMirror type, Elements elements, Types types)`. Implementations SHALL be loaded via `ServiceLoader` using the processor's own classloader. Built-in implementations SHALL be registered using `@AutoService(TargetPropertyDiscovery.class)`.

#### Scenario: Custom target discovery on annotationProcessor classpath
- **WHEN** a user provides a jar containing a `TargetPropertyDiscovery` implementation registered in `META-INF/services`
- **THEN** the `DiscoverStage` SHALL load and invoke it alongside built-in strategies

#### Scenario: Built-in target strategies loaded in consumer project
- **WHEN** the processor runs as an annotation processor in a consumer project
- **THEN** `DiscoverStage` SHALL discover all built-in `TargetPropertyDiscovery` implementations (`ConstructorDiscovery`, `FieldDiscovery.Target`) via `ServiceLoader` using the processor's classloader

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
`ReadAccessor` SHALL carry the property name, type (`TypeMirror`), and the element used for access (getter method or field). Concrete types: `GetterAccessor` (wraps `ExecutableElement`) and `FieldReadAccessor` (wraps `VariableElement`).

#### Scenario: GetterAccessor provides method element
- **WHEN** a `GetterAccessor` is created for `getFirstName()`
- **THEN** `name()` SHALL return `firstName` and `element()` SHALL return the method's `ExecutableElement`

### Requirement: WriteAccessor models target property access
`WriteAccessor` SHALL carry the property name, type (`TypeMirror`), and the element used for writing. Concrete types: `ConstructorParamAccessor` (wraps constructor `ExecutableElement` and parameter index) and `FieldWriteAccessor` (wraps `VariableElement`).

#### Scenario: ConstructorParamAccessor provides constructor and index
- **WHEN** a `ConstructorParamAccessor` is created for constructor param `givenName` at index 0
- **THEN** `name()` SHALL return `givenName`, `constructor()` SHALL return the constructor element, and `paramIndex()` SHALL return 0

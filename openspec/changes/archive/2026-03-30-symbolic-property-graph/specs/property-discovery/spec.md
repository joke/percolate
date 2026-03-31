## ADDED Requirements

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

## REMOVED Requirements

### Requirement: SourcePropertyDiscovery SPI interface
**Reason:** The SPI interface itself is unchanged, but `DiscoverStage` which loaded and orchestrated it is removed. `ResolveTransformsStage` now loads and uses the SPI directly.
**Migration:** `ResolveTransformsStage` loads `SourcePropertyDiscovery` implementations via `ServiceLoader` and applies priority-based merging when resolving access edges.

### Requirement: TargetPropertyDiscovery SPI interface
**Reason:** Same as above — the interface is unchanged but the consuming stage changes from `DiscoverStage` to `ResolveTransformsStage`.
**Migration:** `ResolveTransformsStage` loads `TargetPropertyDiscovery` implementations via `ServiceLoader` and applies priority-based merging when resolving target properties.

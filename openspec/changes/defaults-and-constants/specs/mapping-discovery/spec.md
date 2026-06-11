## MODIFIED Requirements

### Requirement: Each MappingDirective SHALL preserve mirror and value references

Every `MappingDirective` SHALL carry the `AnnotationMirror` for the `@Map` annotation it represents, plus the `AnnotationValue`s for the `target` member and for each of the `source`, `constant`, and `defaultValue` members that is **explicitly present** on the annotation, so that downstream errors can point at the exact source token. A member left at its `Map.UNSET` default need not carry an `AnnotationValue` (none is required for positioning when the member was not written).

#### Scenario: Mirror and values are populated
- **WHEN** an abstract method is annotated with `@Map(target = "lastName", source = "firsty")`
- **THEN** the resulting `MappingDirective.mirror` is the `AnnotationMirror` for that `@Map` invocation
- **AND** `MappingDirective.targetValue` is the `AnnotationValue` for `target = "lastName"`
- **AND** `MappingDirective.sourceValue` is the `AnnotationValue` for `source = "firsty"`

#### Scenario: Constant value reference is populated for positioning
- **WHEN** an abstract method is annotated with `@Map(target = "status", constant = "ACTIVE")`
- **THEN** `MappingDirective` carries the `AnnotationValue` for `constant = "ACTIVE"` so a coercion error can underline that literal

#### Scenario: Default value reference is populated for positioning
- **WHEN** an abstract method is annotated with `@Map(target = "name", source = "in.name", defaultValue = "unknown")`
- **THEN** `MappingDirective` carries the `AnnotationValue` for `defaultValue = "unknown"` so a dead-default error can underline that literal

## ADDED Requirements

### Requirement: @Map constant and defaultValue members SHALL be discovered against the UNSET sentinel

`DiscoverMappings` SHALL read the `@Map` members `source`, `constant`, and `defaultValue`, each treated as **present** when its value is not equal to `Map.UNSET` and **absent** otherwise. `source` is now optional (it defaults to `Map.UNSET`). Discovery SHALL NOT use `String.isEmpty()` to decide presence, because an empty string is a legitimate value for `constant` and `defaultValue`.

#### Scenario: A constant directive is discovered with no source
- **WHEN** an abstract method is annotated with `@Map(target = "status", constant = "ACTIVE")`
- **THEN** the resulting `MappingDirective` reports `constant` present with value `"ACTIVE"`
- **AND** reports `source` absent (it equals `Map.UNSET`)

#### Scenario: A default directive is discovered alongside a source
- **WHEN** an abstract method is annotated with `@Map(target = "name", source = "in.name", defaultValue = "unknown")`
- **THEN** the resulting `MappingDirective` reports `source` present with value `"in.name"`
- **AND** reports `defaultValue` present with value `"unknown"`

#### Scenario: Empty-string values are present, not absent
- **WHEN** an abstract method is annotated with `@Map(target = "note", constant = "")`
- **THEN** the resulting `MappingDirective` reports `constant` present with the empty-string value
- **AND** discovery does not treat the empty string as `Map.UNSET`

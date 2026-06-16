# Mapping Discovery Spec

## Purpose

`DiscoverMappings` is the second pipeline stage. It reads each abstract method's `@Map` (and `@MapList`) annotations into a typed `MapperMappings` structure. Each `MappingDirective` retains the underlying `AnnotationMirror` and the `AnnotationValue`s for `target` and `source`, so later validation and diagnostic stages can point at the exact source token rather than the enclosing method.

## Requirements

### Requirement: @Map directives SHALL be discovered for every abstract method

`DiscoverMappings` SHALL accept a `MapperShape` and produce a `MapperMappings` containing one `MethodMappings` for each abstract method, in the same order. Each `MethodMappings` carries the `ExecutableElement` and the list of `MappingDirective`s declared on that method.

#### Scenario: A method with one @Map produces one directive

- **WHEN** an abstract method is annotated with `@Map(target = "lastName", source = "lastName")`
- **THEN** the corresponding `MethodMappings.directives` list has exactly one entry whose `target` equals `"lastName"` and `source` equals `"lastName"`

#### Scenario: A method with multiple @Maps produces multiple directives in source order

- **WHEN** an abstract method is annotated with `@Map(target = "lastName", source = "lastName")` followed by `@Map(target = "firstName", source = "firsty")`
- **THEN** the corresponding `MethodMappings.directives` list has two entries in that order

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

### Requirement: @MapList container SHALL be unwrapped transparently

When the compiler wraps multiple `@Map` annotations into the `@MapList` container, `DiscoverMappings` SHALL unwrap the container and expose each contained `@Map` as an individual `MappingDirective`. The `@MapList` annotation itself SHALL NOT appear as a directive.

#### Scenario: Two @Maps result in two directives, not one MapList directive

- **WHEN** an abstract method has two `@Map` annotations (which the compiler aggregates into `@MapList`)
- **THEN** the resulting `MethodMappings.directives` has two `MappingDirective` entries
- **AND** no directive references the `@MapList` annotation mirror

### Requirement: Methods without @Map directives SHALL produce empty directive lists

A method with no `@Map` annotation SHALL produce a `MethodMappings` whose `directives` list is empty (and not `null`).

#### Scenario: Unannotated method has empty directives

- **WHEN** an abstract method has no `@Map` annotation
- **THEN** the corresponding `MethodMappings.directives` is an empty list

### Requirement: Discovery SHALL use AnnotationMirror walking, not annotation proxies

Implementation SHALL walk `Element.getAnnotationMirrors()` (optionally via `auto-common`'s `AnnotationMirrors` helpers). Implementation SHALL NOT call `Element.getAnnotation(Map.class)` or `Element.getAnnotationsByType(Map.class)`, because those proxies discard the mirror and value information that downstream error reporting depends on.

#### Scenario: Source code does not invoke proxy annotation APIs

- **WHEN** the source of `DiscoverMappings` is reviewed
- **THEN** it contains no calls to `getAnnotation(Map.class)` or `getAnnotationsByType(Map.class)`

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

### Requirement: Declared-bindings goal spec derived during discovery

The discovery phase SHALL derive, per abstract method, the per-level **declared-bindings goal spec**
from that method's discovered `@Map` directives, and make it available to expansion via the per-mapper
context (keyed by method scope). It SHALL group directives by dotted target-path level: every prefix
of a directive's target contributes its next segment as a declared child at that level, and the full
target path binds the leaf directive. Directives carrying `constant` or `defaultValue` SHALL appear as
bindings like any other. This derivation SHALL NOT be performed by a seed stage (there is none); it is
a pure reshaping of already-discovered directives.

#### Scenario: Nested target paths group by level

- **WHEN** directives declare `address.street` and `address.zip`
- **THEN** the root-level goal declares `{address}` and the `address`-level goal declares
  `{street, zip}`

#### Scenario: Constant directive participates as a binding

- **WHEN** a directive declares `constant = "42"` for target `number`
- **THEN** `number` appears in the derived goal spec as a binding

#### Scenario: Goal spec is available to expansion without a seed stage

- **WHEN** expansion processes a method's return-root demand
- **THEN** the method's goal spec is obtained from the per-mapper context (derived during discovery),
  not produced by any seed stage

## MODIFIED Requirements

### Requirement: Every @Map directive's source first segment SHALL name a method parameter

This check SHALL apply only to directives that declare a `source`. A **constant** directive declares no source and SHALL be skipped by this check entirely.

On a single abstract method, the first segment of every source-bearing `@Map` directive's `source` value MUST match the simple name of one of the method's parameters. The first segment is the portion of the source string before the first `.` (or the entire string if there is no `.`). `ValidateSourceParameters` SHALL emit one error for each source-bearing directive whose source first segment does not match any parameter name.

#### Scenario: Source first segment matching a parameter produces no error
- **WHEN** a method `Human map(Person person)` has `@Map(target = "firstName", source = "person.first")`
- **THEN** `ValidateSourceParameters` emits no error (first segment `"person"` matches parameter `"person"`)

#### Scenario: Single-segment source not matching any parameter produces an error
- **WHEN** a method `Human map(Person person)` has `@Map(target = "firstName", source = "first")`
- **THEN** `ValidateSourceParameters` emits one error via `Diagnostics.error(...)` with message `"unknown source parameter 'first' in @Map on mapHuman(Person)"`

#### Scenario: Multi-segment source with non-matching first segment produces an error
- **WHEN** a method `Human map(Person person)` has `@Map(target = "lastName", source = "lastName.value")`
- **THEN** `ValidateSourceParameters` emits one error via `Diagnostics.error(...)` with message `"unknown source parameter 'lastName' in @Map on mapHuman(Person)"`

#### Scenario: Multiple directives with unknown source parameters produce one error each
- **WHEN** a method has `@Map(target = "a", source = "bad1")` and `@Map(target = "b", source = "bad2")`
- **THEN** `ValidateSourceParameters` emits two errors, one per directive

#### Scenario: Error points at the offending source literal
- **WHEN** `ValidateSourceParameters` reports an unknown source parameter
- **THEN** the `Diagnostics.error(...)` call receives the offending `MappingDirective`'s method `Element`, `AnnotationMirror`, and the `AnnotationValue` of `source`, so that an IDE underlines the offending `source = "..."` literal

#### Scenario: Constant directive is skipped by the source-parameter check
- **WHEN** a method `Human map(Person person)` has `@Map(target = "status", constant = "ACTIVE")`
- **THEN** `ValidateSourceParameters` emits no error (a constant directive has no source to validate)

## ADDED Requirements

### Requirement: A @Map directive SHALL declare exactly one of source or constant

Every `@Map` directive MUST declare exactly one of `source` or `constant` (present = not equal to `Map.UNSET`). A directive that declares **both** is contradictory; a directive that declares **neither** has nothing to map. Validation SHALL emit one error per offending directive, carrying the directive's method `Element`, `AnnotationMirror`, and an offending `AnnotationValue` (`constant` when both are present, otherwise `target`) for IDE positioning.

#### Scenario: Both source and constant produce an error
- **WHEN** a directive declares `@Map(target = "status", source = "in.status", constant = "ACTIVE")`
- **THEN** validation emits one error stating that `source` and `constant` are mutually exclusive

#### Scenario: Neither source nor constant produces an error
- **WHEN** a directive declares `@Map(target = "status")` with neither `source` nor `constant`
- **THEN** validation emits one error stating that a directive must declare a `source` or a `constant`

#### Scenario: Exactly one of source or constant produces no error
- **WHEN** a directive declares `@Map(target = "status", constant = "ACTIVE")` (constant only)
- **THEN** validation emits no mutual-exclusion error
- **AND** the same holds for a directive declaring only a `source`

### Requirement: A defaultValue SHALL accompany a source

A `defaultValue` is a fallback for an absent source value, so it is meaningful only on a source-bearing directive. A directive that declares a present `defaultValue` without a present `source` (including a `constant` directive) SHALL be rejected with one error per offending directive, carrying the `AnnotationValue` of `defaultValue` for IDE positioning.

#### Scenario: defaultValue with a source is allowed
- **WHEN** a directive declares `@Map(target = "name", source = "in.name", defaultValue = "unknown")`
- **THEN** validation emits no error for the default

#### Scenario: defaultValue on a constant directive is rejected
- **WHEN** a directive declares `@Map(target = "status", constant = "ACTIVE", defaultValue = "x")`
- **THEN** validation emits one error stating `defaultValue` requires a `source`
- **AND** the error carries the `AnnotationValue` of `defaultValue`

#### Scenario: defaultValue with no source is rejected
- **WHEN** a directive declares `@Map(target = "name", defaultValue = "unknown")` with no `source`
- **THEN** validation emits one error stating `defaultValue` requires a `source`

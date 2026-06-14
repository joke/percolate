# Default Values Spec

## Purpose

Defines the `@Map defaultValue` member and the `DefaultValue` built-in strategy that supplies a fallback used only when a source value is absent. A default never replaces a present source value; it coalesces target-side per source kind (ternary for a nullable scalar, `orElse` for an `Optional`), reusing the constant literal-coercion to the target type. A default on a source that can never be absent (a `NON_NULL` reference or a primitive) is dead code and is rejected. Coalesced values are non-null by construction.

## Requirements

### Requirement: @Map defaultValue member declares an absent-source fallback

`@Map` SHALL expose a `String defaultValue()` member defaulting to the `Map.UNSET` sentinel. When a directive's `defaultValue` is present (`!Map.UNSET.equals(defaultValue)`), it declares a fallback used **only when the source value is absent**. The directive's source path is unchanged; the default never replaces a present source value. An empty-string default (`defaultValue = ""`) SHALL be a legitimate present value, distinct from absent.

#### Scenario: A present default declares a fallback for an existing source
- **WHEN** an abstract method is annotated with `@Map(target = "name", source = "in.name", defaultValue = "unknown")`
- **THEN** the directive retains its source path `in.name`
- **AND** the directive is recognized as carrying a present default value `"unknown"`

#### Scenario: Empty-string default is present, not absent
- **WHEN** a directive declares `@Map(target = "note", source = "in.note", defaultValue = "")`
- **THEN** the default is recognized as present with the empty-string value
- **AND** it is NOT treated as `Map.UNSET`/absent

### Requirement: A default on a non-absent source is a dead default

A `defaultValue` can only fire when the source can be absent. After nullability inference, if the source resolves to a `NON_NULL` reference scalar or a primitive (which can never be absent), the default is dead code. The processor SHALL emit one error via `Diagnostics` identifying the dead default, carrying the directive's method `Element`, `AnnotationMirror`, and the `AnnotationValue` of `defaultValue` for IDE positioning. This check runs after nullability stamping; it cannot be decided earlier because the source's nullability is unknown until producer-commit.

#### Scenario: Default on a NON_NULL source is rejected
- **WHEN** a directive `@Map(target = "name", source = "in.name", defaultValue = "x")` has a source `in.name` that resolves `NON_NULL`
- **THEN** the processor emits one error reporting the default can never fire
- **AND** the `Diagnostics.error(...)` call receives the `AnnotationValue` of `defaultValue`

#### Scenario: Default on a primitive source is rejected
- **WHEN** a directive's source resolves to a primitive type
- **THEN** the processor emits one dead-default error (a primitive can never be absent)

#### Scenario: Default on a nullable or Optional source is accepted
- **WHEN** a directive's source resolves to a `@Nullable` reference scalar or an `Optional<T>`
- **THEN** no dead-default error is emitted

### Requirement: A default is the coalesce Operation on the nullness crossing

The engine SHALL emit a `[coalesce]` Operation **instead of** `[requireNonNull]` when a binding's
directive declares `defaultValue` and the binding crosses `NULLABLE → NON_NULL` (or an absent
`Optional`): a unary Operation from the nullable/optional source Value to a
`NON_NULL` target Value, rendering the ternary form for a nullable scalar and `orElse` for an
`Optional`, reusing constant literal-coercion for the fallback. Exactly one crossing Operation
exists per binding; a default never replaces a present source value.

#### Scenario: Default replaces requireNonNull on the crossing
- **WHEN** a NULLABLE source feeds a NON_NULL port and the binding declares `defaultValue = "N/A"`
- **THEN** the plan contains a `[coalesce]` Operation rendering the ternary form, and no
  `[requireNonNull]` Operation exists for that binding

#### Scenario: Optional source coalesces with orElse
- **WHEN** the source is `Optional<String>` and the binding declares a default
- **THEN** the `[coalesce]` Operation renders `orElse` with the coerced literal

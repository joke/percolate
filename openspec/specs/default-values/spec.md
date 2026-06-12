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

### Requirement: Default coalesces target-side per source kind

A default SHALL be applied as a coalesce on the produced value at the **target type**, reusing the same literal coercion as constants (see the `constant-values` capability). The "absent" trigger and emitted form depend on the source kind:

- **nullable reference scalar** — absent means `null`; the produced value SHALL be `source != null ? source : D`, with the source bound to a local so it is not evaluated twice.
- **`Optional<T>`** — absent means empty; the produced value SHALL be `opt.orElse(D)`.

where `D` is the default string coerced to the target type. When `defaultValue` is present, the coalescing production SHALL be the one realised (it SHALL out-compete the plain assignment / unwrap). Collection sources are out of scope for this change.

#### Scenario: Nullable scalar source coalesces with a ternary
- **WHEN** a directive `@Map(target = "name", source = "in.name", defaultValue = "unknown")` resolves a `@Nullable String` source to a non-null `String` target
- **THEN** the produced value is equivalent to `name != null ? name : "unknown"`
- **AND** the source expression is evaluated once

#### Scenario: Optional source coalesces with orElse
- **WHEN** a directive `@Map(target = "name", source = "in.name", defaultValue = "unknown")` resolves an `Optional<String>` source to a `String` target
- **THEN** the produced value is equivalent to `in.name().orElse("unknown")`

#### Scenario: Default value reuses constant coercion to the target type
- **WHEN** a directive `@Map(target = "age", source = "in.age", defaultValue = "0")` targets a nullable `Integer`
- **THEN** the default `"0"` is coerced to an `Integer` by the shared coercion utility
- **AND** the produced value falls back to that coerced literal when the source is `null`

#### Scenario: Present source value is used unchanged
- **WHEN** the source value is present (non-null / non-empty) at runtime
- **THEN** the generated code yields the source value and never the default

### Requirement: DefaultValue built-in strategy

The `percolate-strategies-builtin` module SHALL ship a public final class `DefaultValue` implementing `ExpansionStrategy` (directly or via a mixin) and registered via `@AutoService(ExpansionStrategy.class)`. It SHALL be myopic: it fires only when the frontier's `Directive` declares a present `defaultValue`, reads that value via the directive, coerces it to the target type, and emits the coalescing step appropriate to the source kind. It SHALL emit nothing when no `defaultValue` is present, and nothing (leaving the demand for the late diagnostic) when the default cannot be coerced.

#### Scenario: DefaultValue fires only with a present default
- **WHEN** `DefaultValue` is offered a frontier whose directive declares no `defaultValue`
- **THEN** it emits an empty `Stream`

#### Scenario: DefaultValue registers via @AutoService
- **WHEN** the source of `DefaultValue` is inspected
- **THEN** it carries `@AutoService(ExpansionStrategy.class)`

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

### Requirement: Coalesced values are intrinsically non-null

A coalesced (source-or-default) value is non-null by construction: the coalesce can never yield null. It is carried by the directive's target node (see the `nullability` capability) rather than a separate producer node; that target's resolver-obtained typing is `NON_NULL` for a non-null target. A defaulted operand feeding a `NON_NULL` slot SHALL therefore emit no `requireNonNull` guard.

#### Scenario: A defaulted operand feeding a non-null slot needs no guard
- **WHEN** a nullable source with a `defaultValue` feeds a `NON_NULL` assembly slot
- **THEN** the coalesced operand's producer (the target node) is read as `NON_NULL`
- **AND** no `Objects.requireNonNull` guard is emitted around it

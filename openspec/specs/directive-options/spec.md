# Directive Options Spec

## Purpose

Defines how a `@Map` directive's typed **options** (beyond `source`/`constant`/`defaultValue`) are declared by the author, consumed by a strategy, and validated: a strategy that reads an option to produce its `OperationSpec` stamps that option's key as consumed, and the processor diagnoses any declared option that the winning plan never consumed.

## Requirements

### Requirement: Directive options are declared by the author and consumed by a strategy

A `@Map` directive SHALL support typed **options** beyond `source`/`constant`/`defaultValue`.
Initially these are `format` and `zone`; each has a stable string **key** (e.g. `"format"`, `"zone"`) and is
**declared present** exactly when its `@Map` member is not equal to `Map.UNSET`. A strategy that reads an
option to produce its `OperationSpec` SHALL **stamp that option's key** onto the spec it emits (the consumer
declares consumption; no other component infers it). Strategies remain myopic: a strategy stamps only keys it
actually read from the demand's `Directive`.

#### Scenario: A strategy stamps the option key it consumed
- **WHEN** a temporal format strategy produces a value by reading the `@Map(format = "yyyy-MM-dd")` directive
- **THEN** the emitted `OperationSpec` carries `"format"` in its consumed-option keys

#### Scenario: A strategy that read no option stamps nothing
- **WHEN** `DirectAssign` produces a `String` target from a `String` source under a directive with no options
- **THEN** the emitted `OperationSpec` carries an empty consumed-option key set

### Requirement: Declared options unconsumed by the winning plan are diagnosed

After plan extraction, the processor SHALL compute the **consumed set** as the union of consumed-option keys
stamped on the operations of the **winning** (chosen-producer) plan for a binding, and SHALL compute the
**declared set** as the option keys present on that binding's `@Map` directive. Every key in
`declared − consumed` SHALL be reported as a compile **error** at the directive's source position, naming the
option and why it had no effect (e.g. the target type it does not apply to). An option consumed by a
non-winning candidate SHALL NOT count as consumed — the diagnostic reflects what the generated code actually
does.

#### Scenario: A misapplied option is reported

- **WHEN** a binding declares `@Map(source = "in", zone = "Europe/Berlin")` producing a `String` target, and
  the winning plan is a plain `String → String` assignment that consumed no options
- **THEN** the processor reports a compile error at the directive naming `zone` as having no effect because the
  target is not a date/time type

#### Scenario: A consumed option raises no diagnostic

- **WHEN** a binding declares `@Map(source = "in", zone = "Europe/Berlin")` producing a `LocalDate` from an
  `Instant`, and the winning plan crosses the zone bridge which stamped `"zone"` consumed
- **THEN** no unconsumed-option diagnostic is reported for `zone`

#### Scenario: An absent option is never diagnosed

- **WHEN** a binding declares `@Map(source = "in")` with no `format` and no `zone`
- **THEN** the declared option set is empty and no unconsumed-option diagnostic is possible

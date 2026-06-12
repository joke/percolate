# Constant Values Spec

## Purpose

Defines the `@Map constant` member and the `ConstantValue` built-in strategy that turns a directive's literal string into a typed, sourceless target value. A constant declares that the target is produced from a fixed literal with no source path and no parameter binding. Coercion from the raw string to a JDK scalar type is strict and lossless; out-of-scope or uncoercible values fail and are reported by a late, diagnostic-only stage. Constant values are non-null by construction.

## Requirements

### Requirement: @Map constant member declares a fixed literal target value

`@Map` SHALL expose a `String constant()` member defaulting to the `Map.UNSET` sentinel. When a directive's `constant` is present (i.e. `!Map.UNSET.equals(constant)`), it declares that the target SHALL be produced from the given literal string with **no source** — the directive carries no source path and binds to no parameter. An empty-string constant (`constant = ""`) SHALL be a legitimate present value, distinct from absent.

#### Scenario: A present constant declares a sourceless literal mapping
- **WHEN** an abstract method is annotated with `@Map(target = "status", constant = "ACTIVE")`
- **THEN** the directive is recognized as a constant directive (its `constant` is present)
- **AND** the directive declares no source path

#### Scenario: Empty-string constant is present, not absent
- **WHEN** a directive declares `@Map(target = "note", constant = "")`
- **THEN** the directive is recognized as a constant directive whose literal value is the empty string
- **AND** it is NOT treated as `Map.UNSET`/absent

### Requirement: ConstantValue built-in strategy produces the typed literal

The `percolate-strategies-builtin` module SHALL ship a public final class `ConstantValue` implementing `ExpansionStrategy` and registered via `@AutoService(ExpansionStrategy.class)`. It SHALL be authored **target-to-source** and myopic: on a frontier whose `Directive` declares a present `constant`, it SHALL coerce the raw string to the frontier's target type (see "Constant coercion scope") and, on success, emit exactly one `Intent.BOUNDARY` `ExpansionStep` with:

- **zero** inputs (a terminal producer),
- `output` equal to the frontier's target type,
- `weight` equal to `Weights.STEP`,
- a `Codegen` that renders the coerced literal expression.

On a value that cannot be coerced to the target type it SHALL emit nothing (the demand goes UNSAT; the targeted message is owned by the late coercion-failure diagnostic). On a frontier whose directive declares no `constant`, it SHALL emit nothing.

#### Scenario: ConstantValue emits a zero-input boundary rendering the coerced literal
- **WHEN** `ConstantValue` is offered a frontier whose target type is `String` and whose directive declares `constant = "ACTIVE"`
- **THEN** it emits exactly one `ExpansionStep` with `intent == BOUNDARY`
- **AND** the step has zero inputs
- **AND** the step's `output` is `String`
- **AND** the step's `Codegen` renders the string literal `"ACTIVE"`

#### Scenario: ConstantValue coerces to a primitive target
- **WHEN** `ConstantValue` is offered a frontier whose target type is `long` and whose directive declares `constant = "42"`
- **THEN** it emits one zero-input `BOUNDARY` step whose `output` is `long`
- **AND** the step's `Codegen` renders the literal `42L`

#### Scenario: ConstantValue emits nothing without a constant
- **WHEN** `ConstantValue` is offered a frontier whose directive declares no `constant` (it is `Map.UNSET`)
- **THEN** it emits an empty `Stream`

#### Scenario: Uncoercible constant emits no step
- **WHEN** `ConstantValue` is offered a frontier whose target type is `int` and whose directive declares `constant = "abc"`
- **THEN** it emits an empty `Stream` (the demand is left UNSAT for the late diagnostic to report)

### Requirement: Constant coercion scope

The shared literal-coercion utility SHALL coerce a raw string only to the JDK scalar types: the 8 primitives (`boolean`, `byte`, `short`, `int`, `long`, `char`, `float`, `double`), their 8 wrappers (`Boolean` … `Double`), and `String`. For every other target type (enums, `BigDecimal`, `java.time`, arrays, collections, arbitrary declared types) coercion SHALL fail. The coercion utility SHALL return a success-or-failure result so a strategy can take the success path and a diagnostic stage the failure path.

#### Scenario: String target coerces to the raw value verbatim
- **WHEN** the coercion utility is asked to coerce `"hello"` to `String`
- **THEN** it succeeds with a `String` literal rendering `"hello"`

#### Scenario: Wrapper target coerces like its primitive
- **WHEN** the coercion utility is asked to coerce `"7"` to `Integer`
- **THEN** it succeeds with an expression of type `Integer`

#### Scenario: Out-of-scope target fails coercion
- **WHEN** the coercion utility is asked to coerce `"ACTIVE"` to an enum type
- **THEN** it returns failure (no enum coercion in this phase)

### Requirement: Constant coercion strictness

Coercion SHALL be strict and lossless:

- `char` SHALL accept exactly one character; any other length SHALL fail.
- `boolean` SHALL accept only `"true"` or `"false"`; any other text SHALL fail.
- numeric coercions SHALL fail on values outside the target type's range rather than truncate, and SHALL render with the correct literal suffix where required (e.g. `long` → `<n>L`).
- the raw string SHALL NOT be whitespace-trimmed before coercion.

#### Scenario: char rejects multi-character strings
- **WHEN** the coercion utility is asked to coerce `"AB"` to `char`
- **THEN** it returns failure

#### Scenario: boolean rejects non-canonical text
- **WHEN** the coercion utility is asked to coerce `"yes"` to `boolean`
- **THEN** it returns failure

#### Scenario: numeric overflow fails rather than truncating
- **WHEN** the coercion utility is asked to coerce `"999"` to `byte`
- **THEN** it returns failure (out of `byte` range)

### Requirement: Coercion failure produces a targeted diagnostic

When a constant directive's value cannot be coerced to its resolved target type, the processor SHALL emit one error via `Diagnostics` identifying the offending value and the target type (e.g. `"cannot coerce 'abc' to int"`), carrying the directive's method `Element`, `AnnotationMirror`, and the `AnnotationValue` of `constant`, so an IDE underlines the `constant = "..."` literal. This check runs in a late, diagnostic-only stage with the resolved target type; strategies SHALL NOT emit diagnostics.

#### Scenario: Uncoercible constant underlines the constant literal
- **WHEN** a directive `@Map(target = "count", constant = "abc")` targets an `int`
- **THEN** the processor emits one error whose message names the value `'abc'` and the target type `int`
- **AND** the `Diagnostics.error(...)` call receives the `AnnotationValue` of `constant`, not of `target`

### Requirement: Constant value nodes are intrinsically non-null

A constant literal is non-null by construction. The engine SHALL stamp a constant-value node `NON_NULL` (see the `nullability` capability) without invoking `NullabilityResolver`.

#### Scenario: A constant feeding a non-null slot needs no guard
- **WHEN** a constant produces the value for a `NON_NULL` assembly slot
- **THEN** no `Objects.requireNonNull` guard is emitted around the constant expression

### Requirement: ConstantValue registers via ServiceLoader

`ConstantValue` SHALL be annotated `@AutoService(ExpansionStrategy.class)` and SHALL be discoverable through the standard `ServiceLoader<ExpansionStrategy>` lookup alongside the other built-ins, with no kind-ordering.

#### Scenario: ConstantValue is service-loadable
- **WHEN** `ServiceLoader.load(ExpansionStrategy.class)` is enumerated on the strategies-builtin classpath
- **THEN** an instance of `ConstantValue` is present

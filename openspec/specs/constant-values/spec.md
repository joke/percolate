# Constant Values Spec

## Purpose

Defines the `@Map constant` member and the `ConstantValue` built-in strategy that turns a directive's literal string into a typed, sourceless target value. A constant declares that the target is produced from a fixed literal with no source path and no parameter binding. Coercion from the raw string to a JDK scalar type is strict and lossless; an out-of-scope or uncoercible value makes the strategy **refuse** the demand — carrying the `constant` input's `Subject` so the written literal is underlined — and the realisation renderer surfaces that reason, with no core stage owning the coercion decision. Constant values are non-null by construction.

## Requirements

### Requirement: @Map constant member declares a fixed literal target value

`@Map` SHALL expose an optional `String constant()` member with an empty-string default. Presence SHALL be decided by whether the member was **written**, never by comparing against that default: a written `constant` declares that the target SHALL be produced from the given literal string with **no source** — the directive carries no source path and binds to no parameter. An empty-string constant (`constant = ""`) SHALL be a legitimate present value, distinct from absent, and the default exists only so the member may be omitted.

#### Scenario: A present constant declares a sourceless literal mapping
- **WHEN** an abstract method is annotated with `@Map(target = "status", constant = "ACTIVE")`
- **THEN** the binding carries a `constant` input, and declares no source path

#### Scenario: Empty-string constant is present, not absent
- **WHEN** a directive declares `@Map(target = "note", constant = "")`
- **THEN** the binding carries a `constant` input whose value is the empty string
- **AND** it is NOT treated as absent, because the member was written

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

When a constant input's value cannot be coerced to the demanded target type, the **strategy** SHALL refuse the
demand with a message identifying the offending value and the target type (e.g. `"cannot coerce 'abc' to int"`),
carrying the `Subject` of the `constant` input so an IDE underlines the written literal. The refusal SHALL be
rendered by the realisation renderer when the demand ends unrealised.

No core stage SHALL decide when to attempt a coercion or reimplement the coercion rule. Strategies SHALL NOT
emit diagnostics directly; a refusal is returned as data.

#### Scenario: Uncoercible constant underlines the constant literal
- **WHEN** a binding declares `constant = "abc"` for a target resolved as `int`
- **THEN** the strategy returns a refusal whose message names the value `'abc'` and the target type `int`
- **AND** the rendered diagnostic carries the `constant` input's subject, not the binding's

#### Scenario: The strategy emits no diagnostic itself
- **WHEN** the strategy refuses
- **THEN** it returns a refusal offer and invokes no diagnostic facility

#### Scenario: No core stage owns the coercion decision
- **WHEN** the processor's validation stages are inspected
- **THEN** none invokes the literal-coercion helper or decides when a constant applies

### Requirement: ConstantValue registers via ServiceLoader

`ConstantValue` SHALL be annotated `@AutoService(ExpansionStrategy.class)` and SHALL be discoverable through the standard `ServiceLoader<ExpansionStrategy>` lookup alongside the other built-ins, with no kind-ordering.

#### Scenario: ConstantValue is service-loadable
- **WHEN** `ServiceLoader.load(ExpansionStrategy.class)` is enumerated on the strategies-builtin classpath
- **THEN** an instance of `ConstantValue` is present

### Requirement: ConstantValue emits a zero-port Operation

`ConstantValue` SHALL emit a zero-port `Operation` whose codegen renders the coerced literal and
whose produced `Value` is minted `NON_NULL`. A zero-port Operation is base-case reachable (a finite
extraction cost with no ports to feed) — the one place vacuous satisfaction is correct, because the
goal-spec gate (not reachability) protects declared bindings. Coercion scope, strictness, and failure
diagnostics are unchanged.

#### Scenario: Constant is base-case reachable
- **WHEN** a binding declares `constant = "42"` for an `int` target
- **THEN** a zero-port Operation producing a `NON_NULL` `int` Value is reachable with no further demands

### Requirement: The constant input is an ordinary directive input

`constant` SHALL be an ordinary directive input keyed by its member name, present exactly when the author wrote
it — including as the empty string — and absent otherwise. Its presence SHALL NOT be decided against a sentinel
value. The strategy SHALL stamp it consumed when it produces from it.

#### Scenario: An empty-string constant is present
- **WHEN** a binding declares `constant = ""`
- **THEN** the strategy observes a present input whose value is the empty string, and produces from it

#### Scenario: A produced constant stamps its input consumed
- **WHEN** the strategy produces a value from a constant input
- **THEN** the emitted spec records that input as consumed, so the rail reports nothing

#### Scenario: A refused constant leaves its input unconsumed
- **WHEN** the strategy refuses because the value cannot be coerced
- **THEN** the input is not stamped, so the rail would also report it had no effect were the demand otherwise satisfied

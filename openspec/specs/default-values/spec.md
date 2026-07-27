# Default Values Spec

## Purpose

Defines the `@Map defaultValue` member and the `[coalesce]` form of the `NullnessCrossing` built-in strategy that supplies a fallback used only when a source value is absent (there is no separate `DefaultValue` strategy — coalesce is folded into `NullnessCrossing`). A default never replaces a present source value; it coalesces target-side per source kind (`requireNonNullElse` for a nullable scalar, `orElse` for an `Optional`), reusing the constant literal-coercion to the target type. A default on a source that can never be absent (a `NON_NULL` reference or a primitive) is dead code: no crossing is demanded, so nothing consumes the `defaultValue` input and the generic option-consumption rail reports it, positioned at the written literal. An uncoercible default is refused by the crossing strategy itself. Coalesced values are non-null by construction.

## Requirements

### Requirement: @Map defaultValue member declares an absent-source fallback

`@Map` SHALL expose an optional `String defaultValue()` member with an empty-string default. Presence SHALL be decided by whether the member was **written**, never by comparing against that default: a written `defaultValue` declares a fallback used **only when the source value is absent**. The binding's source path is unchanged; the default never replaces a present source value. An empty-string default (`defaultValue = ""`) SHALL be a legitimate present value, distinct from absent.

#### Scenario: A present default declares a fallback for an existing source
- **WHEN** an abstract method is annotated with `@Map(target = "name", source = "in.name", defaultValue = "unknown")`
- **THEN** the binding retains its source path `in.name`
- **AND** carries a `defaultValue` input whose value is `"unknown"`

#### Scenario: Empty-string default is present, not absent
- **WHEN** a directive declares `@Map(target = "note", source = "in.note", defaultValue = "")`
- **THEN** the binding carries a `defaultValue` input whose value is the empty string
- **AND** it is NOT treated as absent, because the member was written

### Requirement: A default on a non-absent source is a dead default

A `defaultValue` SHALL only fire when the source can be absent. When the source resolves to a `NON_NULL`
reference scalar or a primitive (which can never be absent), no nullness crossing is demanded, so no strategy is
ever asked and none can refuse: the default is dead and nothing fails.

The processor SHALL therefore diagnose a dead default through the **generic consumption rail**: the
`defaultValue` input is declared and no operation in the winning plan stamps it consumed, so it is reported as
having had no effect, positioned at that input's own `Subject`.

No core stage SHALL independently decide when a default can fire, and no stage SHALL reimplement absence
semantics.

#### Scenario: Default on a NON_NULL source is reported by the rail
- **WHEN** a binding declares a `defaultValue` for a source that resolves `NON_NULL`
- **THEN** no crossing consumes the input, and an error is reported positioned at the `defaultValue` literal

#### Scenario: Default on a primitive source is reported by the rail
- **WHEN** a binding's source resolves to a primitive type
- **THEN** the `defaultValue` input is unconsumed and is reported

#### Scenario: Default on a nullable or Optional source is accepted
- **WHEN** a binding's source resolves to a `@Nullable` reference scalar or an `Optional<T>`
- **THEN** the crossing consumes the input and no diagnostic is reported

#### Scenario: No core stage owns absence semantics
- **WHEN** the processor's validation stages are inspected
- **THEN** none tests whether a source can be absent in order to judge a default

### Requirement: A default is the coalesce Operation on the nullness crossing

A **strategy** (not the engine) SHALL emit a `[coalesce]` Operation when a binding's directive
declares `defaultValue` and the binding crosses `NULLABLE → NON_NULL` (or an absent `Optional`): a
unary, total Operation from the nullable/optional source Value to a `NON_NULL` target Value, rendering
`requireNonNullElse` for a nullable scalar and `orElse` for an `Optional`, reusing constant
literal-coercion for the fallback. The strategy reads the `defaultValue` from the demand context
(`graph-expansion` carries the directive on the demand). For a `(nullable scalar, default)` pair the
strategy over-emits **both** the partial `[requireNonNull]` guard and the total `[coalesce]`; a default
never replaces a present source value. Totality dominance (`plan-extraction`) then **selects** the
total `[coalesce]` over the partial `[requireNonNull]` without a bespoke either/or rule, so exactly one
crossing Operation survives into the plan.

#### Scenario: Default replaces requireNonNull on the crossing
- **WHEN** a NULLABLE source feeds a NON_NULL port and the binding declares `defaultValue = "N/A"`
- **THEN** the crossing strategy emits a `[coalesce]` Operation rendering `requireNonNullElse`, and no
  `[requireNonNull]` Operation is selected for that binding

#### Scenario: Optional source coalesces with orElse
- **WHEN** the source is `Optional<String>` and the binding declares a default
- **THEN** the `[coalesce]` Operation renders `orElse` with the coerced literal

### Requirement: An uncoercible default is refused by the crossing strategy

The crossing strategy SHALL refuse, with a message naming the value and the type and carrying the `defaultValue`
input's `Subject`, when a `defaultValue` is declared for a demand it recognises but the literal cannot be coerced
to the target type. It SHALL NOT emit a diagnostic itself.

#### Scenario: An uncoercible default is refused with a reason
- **WHEN** a binding declares `defaultValue = "abc"` for a nullable `Integer` target
- **THEN** the crossing strategy returns a refusal naming the value and the type, positioned at the `defaultValue` literal

#### Scenario: A coercible default produces the coalesce and stamps its input
- **WHEN** a binding declares a coercible `defaultValue` for a nullable source
- **THEN** the crossing produces the coalesce operation and stamps the `defaultValue` input consumed

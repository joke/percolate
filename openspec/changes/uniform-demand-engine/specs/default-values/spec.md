## MODIFIED Requirements

### Requirement: A default is the coalesce Operation on the nullness crossing

A **strategy** (not the engine) SHALL emit a `[coalesce]` Operation **instead of** `[requireNonNull]`
when a binding's directive declares `defaultValue` and the binding crosses `NULLABLE → NON_NULL` (or
an absent `Optional`): a unary Operation from the nullable/optional source Value to a `NON_NULL`
target Value, rendering the ternary form for a nullable scalar and `orElse` for an `Optional`, reusing
constant literal-coercion for the fallback. The strategy reads the `defaultValue` from the demand
context (`graph-expansion` carries the directive on the demand). Exactly one crossing Operation exists
per binding; a default never replaces a present source value. Totality dominance (`plan-extraction`)
selects `[coalesce]` (total) over `[requireNonNull]` (partial) without a bespoke either/or rule.

#### Scenario: Default replaces requireNonNull on the crossing
- **WHEN** a NULLABLE source feeds a NON_NULL port and the binding declares `defaultValue = "N/A"`
- **THEN** the crossing strategy emits a `[coalesce]` Operation rendering the ternary form, and no
  `[requireNonNull]` Operation is selected for that binding

#### Scenario: Optional source coalesces with orElse
- **WHEN** the source is `Optional<String>` and the binding declares a default
- **THEN** the `[coalesce]` Operation renders `orElse` with the coerced literal

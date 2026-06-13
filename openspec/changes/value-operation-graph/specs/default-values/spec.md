## ADDED Requirements

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

## REMOVED Requirements

### Requirement: Default coalesces target-side per source kind
**Reason**: Restated as the coalesce Operation; per-source-kind forms carry over.
**Migration**: See ADDED "A default is the coalesce Operation on the nullness crossing".

### Requirement: DefaultValue built-in strategy
**Reason**: Emission reshaped to the crossing Operation selected by the demand context's directive.
**Migration**: See ADDED "A default is the coalesce Operation on the nullness crossing".

### Requirement: Coalesced values are intrinsically non-null
**Reason**: Restated: the coalesce Operation's output Value is minted `NON_NULL` by identity.
**Migration**: See ADDED "A default is the coalesce Operation on the nullness crossing".

## ADDED Requirements

### Requirement: Constant and defaulted producers compose through existing method-body composition

`BuildMethodBodies` SHALL render constant and default-coalesced producers through the existing method-body composition algorithm with no special-casing: a constant is a 0-input `BOUNDARY` terminal producer whose `Codegen` renders the coerced literal with empty `IncomingValues`, and a default coalesce is a producer whose `Codegen` renders the coalescing expression over its incoming source value. The coercion and coalescing logic live in the strategies' `Codegen` (see `constant-values` and `default-values`); `BuildMethodBodies` SHALL NOT inspect `constant`/`defaultValue` itself.

#### Scenario: A constant renders the coerced literal as an operand expression
- **WHEN** `BuildMethodBodies` walks a plan in which an assembly slot is fed by a constant producer for `@Map(target = "status", constant = "ACTIVE")`
- **THEN** the operand expression for that slot is the rendered literal (e.g. `"ACTIVE"`)
- **AND** no incoming-value variable is referenced by that producer

#### Scenario: A default coalesce renders over the source value
- **WHEN** `BuildMethodBodies` walks a plan in which a slot is fed by a default-coalesced producer for `@Map(target = "name", source = "in.name", defaultValue = "unknown")` with a nullable source
- **THEN** the operand expression is the coalescing form (e.g. `name != null ? name : "unknown"`)

### Requirement: A defaulted operand suppresses the non-null guard

Because a default-coalesced producer is stamped `NON_NULL` (see `nullability`), the nullability-aware slot wiring (see "Nullability-aware slot wiring at GroupTarget composition") SHALL treat it as a `NON_NULL` producer: feeding a `NON_NULL` slot is `NON_NULL → NON_NULL` and SHALL emit the operand unchanged, with no `Objects.requireNonNull` guard. The default itself already guarantees non-nullness.

#### Scenario: Nullable source with a default feeding a non-null slot emits no requireNonNull
- **WHEN** `BuildMethodBodies` walks an operand fed by a default-coalesced producer (nullable source, present `defaultValue`) whose consuming slot is `NON_NULL`
- **THEN** the rendered operand is the coalescing expression with no `Objects.requireNonNull` wrapper
- **AND** the producer-stamped nullability read for that operand is `NON_NULL`

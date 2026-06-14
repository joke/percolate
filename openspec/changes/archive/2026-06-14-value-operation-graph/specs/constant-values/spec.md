## ADDED Requirements

### Requirement: ConstantValue emits a zero-port Operation

`ConstantValue` SHALL emit a zero-port `Operation` whose codegen renders the coerced literal and
whose produced `Value` is minted `NON_NULL`. A zero-port Operation is base-case SAT — the one place
vacuous satisfaction is correct, because the goal-spec gate (not SAT) protects declared bindings.
Coercion scope, strictness, and failure diagnostics are unchanged.

#### Scenario: Constant is base-case SAT
- **WHEN** a binding declares `constant = "42"` for an `int` target
- **THEN** a zero-port Operation producing a `NON_NULL` `int` Value is SAT with no further demands

## REMOVED Requirements

### Requirement: ConstantValue built-in strategy produces the typed literal
**Reason**: Emission reshaped from a sourceless step to a zero-port Operation; coercion behaviour
carries over.
**Migration**: See ADDED "ConstantValue emits a zero-port Operation".

### Requirement: Constant value nodes are intrinsically non-null
**Reason**: Restated: non-null-by-construction is the minted Value's identity nullness.
**Migration**: See ADDED "ConstantValue emits a zero-port Operation".

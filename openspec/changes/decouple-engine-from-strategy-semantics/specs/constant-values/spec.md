## MODIFIED Requirements

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

## ADDED Requirements

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

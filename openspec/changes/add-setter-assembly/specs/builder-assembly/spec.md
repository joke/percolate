## RENAMED Requirements

- FROM: `### Requirement: Constructor and builder assembly are arbitrated by a priced preference`
- TO: `### Requirement: Assembly forms are arbitrated by a ranked, priced preference`

## MODIFIED Requirements

### Requirement: Assembly forms are arbitrated by a ranked, priced preference

When a target admits more than one assembly form, the choice SHALL be made by weight, driven by the `percolate.construction.preference` option, and never by an arbitrary tie-break. Each assembly strategy SHALL read the option and price **only itself**; no assembly strategy SHALL inspect, name, or depend on the existence of another.

The option's value is an **ordered list** of form tokens separated by commas, drawn from `constructor`, `builder` and `setter`. The parser SHALL append every token the author omitted, in the fixed default order `constructor`, `builder`, `setter`, so the effective order always ranks all three forms. An absent option SHALL yield that default order, and an unrecognised token SHALL be ignored rather than fail the round.

Because the plan fold selects the **minimum** cost, a lower rank SHALL carry a lower weight. Rank 0 SHALL weigh `Weights.STEP`, rank 1 SHALL weigh `Weights.EXPENSIVE`, and rank 2 SHALL weigh `Weights.EXPENSIVE + 1`. The three weights are distinct, so two assembly forms can never tie. All assembly weights SHALL remain non-negative.

The parse and the rank-to-weight mapping SHALL live in exactly one place, which each assembly strategy queries for its own form. A strategy SHALL NOT name another form's token.

The preference SHALL be a preference and never an exclusion: when a preferred form does not match a target, a lower-ranked form SHALL still be used rather than the mapping failing.

#### Scenario: The default prefers the constructor
- **WHEN** `Person` admits a matching constructor, a matching builder and matching setters, and `percolate.construction.preference` is unset
- **THEN** the extracted plan uses the constructor

#### Scenario: The default order ranks all three forms
- **WHEN** `percolate.construction.preference` is unset
- **THEN** the constructor form weighs `Weights.STEP`, the builder form weighs `Weights.EXPENSIVE`, and the setter form weighs `Weights.EXPENSIVE + 1`

#### Scenario: The option flips the preference
- **WHEN** the same target is compiled with `-Apercolate.construction.preference=builder`
- **THEN** the extracted plan uses the builder

#### Scenario: A single token keeps the remaining forms in default order
- **WHEN** `-Apercolate.construction.preference=builder` is set
- **THEN** the builder form weighs `Weights.STEP`, the constructor form weighs `Weights.EXPENSIVE`, and the setter form weighs `Weights.EXPENSIVE + 1`

#### Scenario: A multi-token list is honoured in order
- **WHEN** `-Apercolate.construction.preference=setter,builder` is set and `Person` admits all three forms
- **THEN** the extracted plan uses the setters
- **AND** the constructor form carries the highest of the three weights

#### Scenario: No two assembly forms share a weight
- **WHEN** any accepted option value is resolved
- **THEN** the three assembly forms carry three distinct weights

#### Scenario: The preference does not exclude the unpreferred form
- **WHEN** `-Apercolate.construction.preference=builder` is set and the target has no builder
- **THEN** a lower-ranked form is still used and the mapping succeeds

#### Scenario: An unrecognised token does not fail the round
- **WHEN** `-Apercolate.construction.preference=wombat` is set
- **THEN** the effective order is the default order and the round succeeds

#### Scenario: Strategies price only themselves
- **WHEN** any assembly strategy's source is inspected
- **THEN** it asks the shared parser for the weight of its own form and names no other form's token

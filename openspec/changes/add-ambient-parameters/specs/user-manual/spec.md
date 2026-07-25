## ADDED Requirements

### Requirement: The manual documents multi-parameter mapper methods

The manual SHALL document that a mapper method may declare any number of parameters, each an independently
named source root, with a worked example showing two parameters of **different** types feeding different
targets and an example of one sub-target assembled from **both** parameter roots. It SHALL state that two
parameters of the same type are legal because every source path names its root.

The section SHALL live in `mapper-structure.adoc`, alongside the existing method-shape material, and SHALL
follow the existing single-sourcing rule: every snippet included from a compiling fixture via `tag`
regions, never hand-written into the page.

#### Scenario: The manual shows a multi-parameter worked example

- **WHEN** the mapper-structure page is read
- **THEN** it shows a mapper declaring two parameters with `@Map` directives rooted at each
- **AND** it shows the generated implementation for that mapper

#### Scenario: Same-typed parameters are documented as legal

- **WHEN** the multi-parameter section is read
- **THEN** it states that two parameters of the same type are legal and shows an example naming each root

#### Scenario: The snippets are single-sourced from a compiling fixture

- **WHEN** the multi-parameter snippets are inspected
- **THEN** each is an `include::` of a tag region in a fixture compiled by the ordinary build, not literal
  page text

### Requirement: The manual documents @Ambient parameters

The manual SHALL document `@Ambient`: what an ambient parameter is, that it is keyed by parameter name with
the type verified, that `@Ambient("key")` renames the key, that a consumer declares `@Ambient` on its own
parameter to receive the value, and that ambients flow into container and element lambdas. It SHALL show a
worked example of a conversion method receiving an ambient — the case that multi-argument conversion methods
now permit — together with the generated output.

The manual SHALL state the three ambient errors (duplicate key, key/type mismatch, unbound key) and that an
unbound key is an error rather than a silently-skipped conversion method.

The manual SHALL note that an `@Ambient` parameter may also be used as an ordinary `@Map` source.

#### Scenario: The manual shows an ambient worked example

- **WHEN** the `@Ambient` section is read
- **THEN** it shows a mapper declaring an `@Ambient` parameter and a conversion method consuming it
- **AND** it shows the generated implementation passing the ambient argument through

#### Scenario: The manual documents the ambient error cases

- **WHEN** the `@Ambient` section is read
- **THEN** it names the duplicate-key, key/type-mismatch, and unbound-key errors
- **AND** it states that an unbound key fails the compilation rather than quietly deselecting the method

#### Scenario: The ambient example is backed by a behavioural test

- **WHEN** the `@Ambient` example fixture is inspected
- **THEN** it is compiled by the ordinary build and exercised by a `@Tag('integration')` Spock specification
  asserting the mapped result, per the existing "Every documented feature is backed by a behavioural example"
  requirement

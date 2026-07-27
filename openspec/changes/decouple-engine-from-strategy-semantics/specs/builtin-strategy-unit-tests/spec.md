## ADDED Requirements

### Requirement: Built-in strategies are organised into feature packages

Each built-in feature SHALL occupy its own package under `spi.builtins`, holding every strategy that implements
it together with the `DirectiveReader` that reads its annotation, so that deleting the package deletes the
feature entirely. Only classes serving more than one feature package SHALL remain at the `spi.builtins` root —
shared helpers, and the `@Map` reader, whose members are consumed by several features at once and which
therefore belongs to no single one.

Each feature package's unit specs SHALL mirror the package structure.

#### Scenario: A feature's strategies and reader share a package
- **WHEN** the enum-conversion feature is located
- **THEN** its strategy and its `@MapEnum` reader reside in the same package

#### Scenario: Deleting a package deletes a feature
- **WHEN** a feature package is removed from the build
- **THEN** no class outside it references its strategies or its reader

#### Scenario: Only cross-feature classes remain at the root
- **WHEN** the `spi.builtins` root package is inspected
- **THEN** every class in it is used by more than one feature package, or is the cross-feature `@Map` reader

#### Scenario: Specs mirror the package structure
- **WHEN** a feature package's unit specs are located
- **THEN** they reside in the matching test package

### Requirement: Every directive reader is covered by the compile-based layer

A `DirectiveReader` consumes `javax.lang.model` directly, so it SHALL be exempt from mutation coverage
(`@CoverageIgnore`, as `CallableMethodIndexer` already is) and SHALL instead be covered by compile-testing
specs that exercise it through a real compilation.

That coverage SHALL include, across the built-in readers: a written member reported present, a member written
as the empty string reported present, an unwritten member reported absent, a repeated annotation unwrapped in
declaration order, and **each shape rule a reader owns, asserted on the reported message**.

Mocking the sink is not the seam here: substituting a mocked `DirectiveSink` still leaves the reader consuming
real `AnnotationMirror`s, which cannot be constructed outside a compilation — so the mock buys isolation from
the sink while the expensive half of the dependency remains. A compile-testing spec pays that cost once and
asserts what the author actually sees.

#### Scenario: Readers are exempt from mutation coverage
- **WHEN** the built-in readers are inspected
- **THEN** each is marked `@CoverageIgnore`, with the same justification as the pre-existing javax-consuming indexer

#### Scenario: Each shape rule is pinned by a compile-testing spec
- **WHEN** a reader owns a rule about how its annotation's members combine
- **THEN** a compile-testing spec asserts that violating it fails the compile with that rule's own message

#### Scenario: Presence semantics are covered
- **WHEN** the reader coverage is inspected
- **THEN** a written value, a written empty string, and an unwritten member are each exercised through a real compilation

## MODIFIED Requirements

### Requirement: Assertion scope is OperationSpec metadata only

A built-in strategy's unit spec SHALL assert on the metadata of the `Offer`s the strategy returns — for a
production, its `OperationSpec`'s label, weight, output type and nullness, port shapes, child scope, call
target, consumed inputs and member requests; for a refusal, its message and the identity of the subject it
carries. A spec SHALL NOT render codegen and SHALL NOT assert on generated source text.

A strategy that can refuse SHALL have at least one scenario asserting the refusal, and at least one asserting
that a non-applicable demand yields an empty stream rather than a refusal — the two are distinct answers and
conflating them is the defect this distinction exists to prevent.

#### Scenario: A production is asserted by metadata
- **WHEN** a strategy spec exercises a producing case
- **THEN** it asserts on the returned `OperationSpec`'s metadata and renders no codegen

#### Scenario: A refusal is asserted by message and subject
- **WHEN** a strategy spec exercises a refusing case
- **THEN** it asserts the refusal's message and that the subject is the one the strategy was handed

#### Scenario: Not-applicable is distinguished from refusal
- **WHEN** a strategy that can refuse is handed a demand outside its remit
- **THEN** the spec asserts an empty stream, not a refusal

#### Scenario: A bounded type variable's bound is exercised
- **WHEN** a strategy declares a bounded type-variable port
- **THEN** its spec covers both an admissible grounding and a refused one, asserting the refusal's message

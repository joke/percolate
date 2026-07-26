## ADDED Requirements

### Requirement: Built-in strategies are organised into feature packages

Each built-in feature SHALL occupy its own package under `spi.builtins`, holding every strategy that implements
it together with the `DirectiveReader` that reads its annotation, so that deleting the package deletes the
feature entirely. Only genuinely shared helpers SHALL remain at the `spi.builtins` root.

Each feature package's unit specs SHALL mirror the package structure.

#### Scenario: A feature's strategies and reader share a package
- **WHEN** the enum-conversion feature is located
- **THEN** its strategy and its `@MapEnum` reader reside in the same package

#### Scenario: Deleting a package deletes a feature
- **WHEN** a feature package is removed from the build
- **THEN** no class outside it references its strategies or its reader

#### Scenario: Only shared helpers remain at the root
- **WHEN** the `spi.builtins` root package is inspected
- **THEN** it contains only helpers used by more than one feature package

#### Scenario: Specs mirror the package structure
- **WHEN** a feature package's unit specs are located
- **THEN** they reside in the matching test package

### Requirement: Every directive reader has a unit spec

Each `DirectiveReader` SHALL have a unit spec named after it, covering: a written member reported present, a
member written as the empty string reported present, an unwritten member reported absent with no positioning
token, a repeated annotation unwrapped in declaration order, and each shape rule the reader owns.

A reader spec SHALL assert on the sink calls the reader makes, with the sink mocked; it SHALL NOT compile
sources or assert on generated output.

#### Scenario: Reader specs exist and are named after their reader
- **WHEN** the built-in readers are enumerated
- **THEN** each has a correspondingly named unit spec in its own package

#### Scenario: A reader spec asserts on sink calls
- **WHEN** a reader spec exercises a reader
- **THEN** it verifies the bindings, inputs, scope inputs and constraints passed to a mocked sink

#### Scenario: Presence semantics are covered
- **WHEN** a reader spec is inspected
- **THEN** it covers a written value, a written empty string, and an unwritten member

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

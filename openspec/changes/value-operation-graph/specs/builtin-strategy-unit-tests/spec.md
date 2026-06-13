## ADDED Requirements

### Requirement: Assertion scope is OperationSpec metadata only

Strategy unit specs SHALL assert on the metadata of returned `OperationSpec` values: weight, port
signature (names, declared types, nullness), produced output type and nullness, child-scope
declaration (presence and element types), and emptiness of the returned stream for unmet
preconditions. Specs SHALL NOT invoke the spec's codegen `render(...)` or assert on rendered
`CodeBlock` output — codegen pinning remains out of scope.

#### Scenario: No codegen invocation in unit specs
- **WHEN** a strategy unit spec is inspected
- **THEN** it asserts on `OperationSpec` plain-data accessors and never calls `render`

### Requirement: Container strategies assert their child-scope declaration

For container strategies, at least one feature method SHALL assert the emitted spec's child-scope
declaration: element mapping declares a child scope with the expected element-in/element-out types;
wrap/unwrap emits a plain spec with no child scope.

#### Scenario: Element mapping declares a child scope
- **WHEN** the List strategy matches `List<A> → List<B>`
- **THEN** the unit spec asserts the `OperationSpec` declares a child scope with element types `A`
  and `B`

#### Scenario: Wrap declares none
- **WHEN** the Optional strategy emits a wrap for `T → Optional<T>`
- **THEN** the unit spec asserts the spec has no child-scope declaration

## REMOVED Requirements

### Requirement: Assertion scope is metadata-only
**Reason**: Restated over `OperationSpec` (was `ExpansionStep`/`Slot`/`EdgeCodegen`).
**Migration**: See ADDED "Assertion scope is OperationSpec metadata only".

### Requirement: Per-strategy scenario coverage
**Reason**: The required scenario classes survive, but the element-scope scenarios (asserting
`ENTERING`/`EXITING`) are replaced by child-scope-declaration scenarios.
**Migration**: See ADDED "Container strategies assert their child-scope declaration"; empty-return,
happy-path, and weight-pinning scenario classes carry over against `OperationSpec`.

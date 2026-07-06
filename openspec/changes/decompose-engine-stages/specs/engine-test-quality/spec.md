## ADDED Requirements

### Requirement: Engine internals are decomposed into individually testable units

Every method declared in the `processor` engine `internal` packages SHALL express a single responsibility — describable in one sentence without the word "and" — and SHALL be individually isolable by a same-package unit spec, rather than reachable only through a stage's single public entry point.

A stage's helper logic SHALL take exactly one of three forms. A **separable behavioural step** SHALL be extracted into its own single-method collaborator, injected as a constructor seam so a spec can mock it. A **misplaced method** — one that operates on a value type rather than on the stage — SHALL be relocated onto that value type. A **genuinely atomic, single-use helper** SHALL be inlined into its one caller. No separable step SHALL remain a private helper on the stage.

An **orchestrating unit** — one that composes collaborators to produce a single result — SHALL be unit-tested by mocking those collaborators and asserting the composition; it SHALL NOT be tested only by driving the whole pipeline through it.

Decomposition SHALL preserve engine behaviour and the existing invariants: all graph mutation SHALL remain within the driver package, and strategies SHALL stay myopic.

#### Scenario: A decomposed step is unit-tested in isolation
- **WHEN** a former private helper of an engine stage is exercised by a spec
- **THEN** it is a method on an injected collaborator (or a relocated value-type method) called directly by a same-package spec, with its own collaborators mocked — not reached only through the stage's public entry point

#### Scenario: An orchestrator is tested by mocking its collaborators
- **WHEN** an orchestrating method such as the driver's `land` is unit-tested
- **THEN** the spec mocks its collaborators (port binding, self-call guard, operation landing) and asserts the produced result, rather than driving a full seed-and-expand pass

#### Scenario: Decomposition preserves engine invariants
- **WHEN** the decomposed engine is inspected
- **THEN** all graph mutation remains within the driver package, and no strategy depends on the engine graph

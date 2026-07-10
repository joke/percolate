# End-to-End Test Architecture Spec

## Purpose

Defines where end-to-end (compile) tests live and how they are written, so test slices match module boundaries. A shared `test-foundation` module provides a strategy-agnostic compile harness consumed by every e2e suite. Each strategy module owns the e2e tests for its own atoms, pulling the engine and the harness into its test classpath. The engine module declares no compile/runtime edge to any strategy module and is unit-tested at its own seams (see `engine-test-quality`) rather than through fakes, so a strategy regression fails in its owning module instead of surfacing only in a downstream integration project. The documented feature set drives which end-to-end tests exist at all: every surviving e2e is a documented feature's behavioural example — one witness per user-facing mechanism, never exhaustive coverage — and asserts runtime behaviour rather than generated source text, so a `processor`/`spi` module may additionally host a narrowly-scoped `@Tag('integration')` doc-e2e to demonstrate a feature it owns without reopening the engine's unit-suite isolation.

## Requirements

### Requirement: Shared compile-test harness

The project SHALL provide a `test-foundation` module exposing a World-2 compile harness that runs the real `PercolateProcessor` over supplied sources and returns the resulting `Compilation`. The harness SHALL be strategy-agnostic — it SHALL NOT reference any strategy module — and SHALL depend on `processor` and Google compile-testing. Every module that writes end-to-end (compile) tests SHALL consume the harness via `testImplementation project(':test-foundation')` rather than re-implementing compilation boilerplate.

#### Scenario: Harness compiles a mapper from supplied sources

- **WHEN** a test calls the harness with a source type, a target type, and `@Map` directives
- **THEN** the harness runs `PercolateProcessor` over those sources and returns a `Compilation` the test can assert on (status, generated source)

#### Scenario: Harness is strategy-agnostic

- **WHEN** the `test-foundation` module's dependencies are inspected
- **THEN** they include `processor` and compile-testing but no strategy module (`strategies-builtin`, `reactor`, …)

### Requirement: Each strategy module owns its end-to-end tests

End-to-end compile tests SHALL live in the module that owns the strategy under test. A strategy module's e2e tests SHALL pull in `processor` and `test-foundation` (and `strategies-builtin` when its atoms compose with builtin element conversions) on its own test classpath. No module SHALL host another module's e2e tests, and there SHALL be no "highest module" that accumulates cross-cutting e2e tests.

#### Scenario: Builtin e2e tests live in strategies-builtin

- **WHEN** the builtin conversion/container/accessor/assembly compile-tests are located
- **THEN** they reside in `strategies-builtin/src/test`, which declares `testImplementation` on `processor` and `test-foundation`

#### Scenario: A new strategy module hosts its own e2e tests

- **WHEN** a new strategy module (e.g. a hypothetical collections add-on) is added
- **THEN** its compile-tests live in that module on the shared harness, and neither `processor` nor any unrelated strategy module is modified to host them

### Requirement: The engine is tested without real strategies

The `processor` module SHALL declare no compile or runtime dependency edge on any strategy module, and its **unit** test suite (`@Tag('unit')` — the suite the coverage gate and pitest measure) SHALL declare no strategy edge. The engine SHALL be tested **at its own seams by unit tests** (see the `engine-test-quality` capability), not by compiling a `@Mapper` with a fake strategy. Engine integration / compile-testing specs in `processor` that used a `FakeStrategy` — and the `FakeStrategy` itself — SHALL remain **removed**. The engine's isolation property is carried by the unit suite; a module MAY additionally host an `@Tag('integration')` documentation e2e that pulls real strategies and `test-foundation` **solely to demonstrate a feature that module owns** (e.g. `processor` for compile-time switches, `spi` for the Extending example), provided that e2e uses only public API — never a `processor.internal..` package — and stays out of the unit/pitest/coverage scope.

#### Scenario: The processor unit suite is strategy-free and fake-free

- **WHEN** the `processor` module's `@Tag('unit')` test suite is inspected
- **THEN** its classpath contains no strategy module, no spec compiles a `@Mapper` with a `FakeStrategy`, and the `FakeStrategy` harness is absent — engine behaviour is asserted by unit tests at the seam

#### Scenario: A processor-owned feature may be documented by an integration e2e

- **WHEN** `processor` hosts a doc-e2e that compiles a real `@Mapper` to demonstrate a compile-time switch
- **THEN** the spec is `@Tag('integration')`, pulls `strategies-builtin` + `test-foundation` on its test classpath only, references no `processor.internal..` type, and is excluded from the pitest and coverage gate

#### Scenario: Engine isolation holds in production and in the unit suite

- **WHEN** the production module graph and the unit suite are inspected
- **THEN** no `processor` main class depends on a strategy module and no unit spec asserts strategy-specific output such as `Integer.valueOf` or a builtin container expression

### Requirement: The manual defines the end-to-end test set

The documented feature set SHALL determine which end-to-end tests exist: every e2e SHALL be a documented feature's example, and no e2e SHALL be added purely to raise coverage. The e2e layer SHALL be deliberately non-exhaustive — one **witness per user-facing mechanism**, not one per type combination — because the container and conversion mechanisms are parametric over their type arguments. Exhaustive coverage of branches and type-tables SHALL be the responsibility of the unit + pitest layer, not the e2e layer.

#### Scenario: An e2e maps to exactly one documented feature

- **WHEN** any surviving end-to-end spec is inspected
- **THEN** it backs exactly one manual section, and that section's shown example is the spec's fixture

#### Scenario: One witness covers a parametric mechanism

- **WHEN** the collections e2e are inspected
- **THEN** a single `List<X>→List<Y>` example witnesses same-kind element mapping, and no additional spec exists that merely varies the element types

### Requirement: End-to-end tests assert behaviour, not generated source text

A documentation e2e SHALL assert the **runtime behaviour** of the generated mapper — instantiating it and checking the mapping result — rather than matching substrings of the generated source. The generated source SHALL be materialised only for display in the manual. A behaviour-preserving change to generated code (a rename, a reordering) SHALL NOT fail a doc-e2e, while a behavioural regression SHALL.

#### Scenario: A doc-e2e runs the generated mapper

- **WHEN** a documentation e2e executes
- **THEN** it instantiates the generated mapper, maps a sample input, and asserts on the returned value — not on the generated source text

#### Scenario: A cosmetic codegen change does not break a doc-e2e

- **WHEN** generated code changes in a behaviour-preserving way (e.g. a local variable is renamed)
- **THEN** the doc-e2e still passes, and only the materialised output shown in the manual changes

### Requirement: Engine correctness is repatriated to unit tests, not carried by integration tests

No engine invariant SHALL rely solely on an integration/compile-test for its protection. Engine-correctness and diagnostics specs that previously lived in `strategies-builtin/.../e2e/` SHALL be repatriated to `processor` unit tests at the mock seam, or removed only where an existing unit test already covers the invariant. An e2e SHALL NOT be deleted before its invariant is demonstrably unit-covered — the mutation it implies is killed by a unit test.

#### Scenario: A correctness spec becomes a unit test before its e2e is removed

- **WHEN** an engine-correctness e2e (e.g. same-typed-source binding, getter-beats-field pruning, container-return self-bridge) is retired
- **THEN** a `processor` unit test asserting the same invariant already exists and fails under the corresponding mutation, and only then is the e2e deleted

#### Scenario: No surviving e2e is an engine-correctness test

- **WHEN** the surviving end-to-end specs are inspected
- **THEN** each backs a documented feature; none exists solely to assert an engine invariant or a compile diagnostic

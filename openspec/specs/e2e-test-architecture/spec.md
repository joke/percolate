# End-to-End Test Architecture Spec

## Purpose

Defines where end-to-end (compile) tests live and how they are written, so test slices match module boundaries. A shared `test-foundation` module provides a strategy-agnostic compile harness consumed by every e2e suite. Each strategy module owns the e2e tests for its own atoms, pulling the engine and the harness into its test classpath. The engine module declares no edge to any strategy module and is unit-tested at its own seams (see `engine-test-quality`) rather than through fakes, so a strategy regression fails in its owning module instead of surfacing only in a downstream integration project.

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

The `processor` module SHALL declare no dependency edge — compile, runtime, or test — on any strategy module. The engine SHALL be tested **at its own seams by unit tests** (see the `engine-test-quality` capability), not by compiling a `@Mapper` with a fake strategy. Engine integration / compile-testing specs in `processor` — and the `FakeStrategy` that drove them — SHALL be **removed**; real engine↔strategy integration is covered by the feature-e2e layer, not by fakes in the engine module.

#### Scenario: Processor test classpath is strategy-free

- **WHEN** the resolved test classpath of the `processor` module is inspected
- **THEN** it contains no strategy module, and no spec asserts strategy-specific output such as `Integer.valueOf` or a builtin container expression

#### Scenario: The engine has no fake-driven compile-tests

- **WHEN** the `processor` test suite is inspected
- **THEN** no spec compiles a `@Mapper` with a `FakeStrategy` to assert engine behaviour, and the `FakeStrategy` harness is no longer present — engine behaviour is asserted by unit tests at the seam

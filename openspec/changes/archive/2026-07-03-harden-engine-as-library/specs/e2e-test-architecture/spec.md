## MODIFIED Requirements

### Requirement: The engine is tested without real strategies

The `processor` module SHALL declare no dependency edge — compile, runtime, or test — on any strategy
module. The engine SHALL be tested **at its own seams by unit tests** (see the `engine-test-quality`
capability), not by compiling a `@Mapper` with a fake strategy. Engine integration / compile-testing specs
in `processor` — and the `FakeStrategy` that drove them — SHALL be **removed**; real engine↔strategy
integration is covered by the feature-e2e layer, not by fakes in the engine module.

#### Scenario: Processor test classpath is strategy-free
- **WHEN** the resolved test classpath of the `processor` module is inspected
- **THEN** it contains no strategy module, and no spec asserts strategy-specific output such as
  `Integer.valueOf` or a builtin container expression

#### Scenario: The engine has no fake-driven compile-tests
- **WHEN** the `processor` test suite is inspected
- **THEN** no spec compiles a `@Mapper` with a `FakeStrategy` to assert engine behaviour, and the
  `FakeStrategy` harness is no longer present — engine behaviour is asserted by unit tests at the seam

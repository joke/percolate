## ADDED Requirements

### Requirement: The engine is unit-tested as an isolated library

The `processor` engine SHALL be tested at its own seams by **unit tests** — constructing a stage's or
component's input (a `MapperContext`, graph, demand, or extracted plan) and asserting on the resulting
structure — with **no compilation and no strategy or fake** involved. The unit suite (`@Tag('unit')`,
`test.exec`) SHALL meet a **95% branch-coverage gate** for `processor`. Narrow, individually-justified
jacoco exclusions MAY be applied to pure debug/rendering packages rather than weakening the bar.

#### Scenario: Engine behaviour is asserted at the seam, not through a compile
- **WHEN** an engine contract (self-seeding, descent, assembly hoisting, cost selection, realisation
  diagnostics, nullness crossing) is unit-tested
- **THEN** the test constructs the engine's own input structures and asserts on the produced graph, plan, or
  diagnostic — it does not compile a `@Mapper` and does not register any strategy or fake

#### Scenario: The engine unit suite meets the 95% gate
- **WHEN** `./gradlew check` runs the `processor` unit suite
- **THEN** its branch coverage is at least 95% (any exclusion being a narrow, noted debug/rendering package)

### Requirement: The engine unit suite is mutation-tested

The `processor` unit suite SHALL be mutation-tested with pitest, bound to the **unit** test task only (the
integration suite is excluded, where pitest is slow). pitest SHALL run with **all mutators** and
**incremental analysis enabled**, and a mutation-score threshold SHALL be wired into `check` and ratcheted
upward as the unit suite grows. Coverage and mutation score are complementary gates: line/branch coverage
proves code is exercised, mutation score proves the assertions are meaningful.

#### Scenario: pitest runs on the unit suite under check
- **WHEN** `./gradlew check` runs
- **THEN** pitest mutates the `processor` engine and runs the unit suite against the mutants, failing the
  build if the mutation score falls below the configured threshold

#### Scenario: pitest is scoped away from the slow suite
- **WHEN** the pitest configuration is inspected
- **THEN** it targets the unit test task and excludes the integration test task, with all mutators and
  incremental analysis enabled

### Requirement: The engine carries no test-time strategy or fake dependency

The `processor` module SHALL declare no dependency edge — compile, runtime, or test — on any strategy
module, and its tests SHALL NOT use a fake strategy. Real engine↔strategy integration is the feature-e2e
layer's concern, not the engine's.

#### Scenario: Processor tests are strategy-free and fake-free
- **WHEN** the `processor` test classpath and specs are inspected
- **THEN** no strategy module is present, no `FakeStrategy` (or other synthetic SPI strategy) is registered
  or used, and no spec compiles a `@Mapper` to assert engine behaviour

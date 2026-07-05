# Engine Test Quality Spec

## Purpose

Defines how the `processor` engine is tested as an isolated library: at its own seams by unit tests (no compile, no strategy, no fake), held to a branch-coverage gate, and mutation-tested so the assertions are proven meaningful. Real engine↔strategy integration is the feature-e2e layer's concern (see `e2e-test-architecture`), never the engine module's.

## Requirements

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

Because the rewritten unit suite mocks the `ResolveCtx` seam (see `expansion-test-harness`) and stands up
**no javac**, pitest SHALL run **threaded** (`threads = availableProcessors()`) with **no `@Isolated`**,
**no `threads = 1`**, and **no `spock-pitest.groovy`** serialised-minion configuration, and with no
`pitestTargetClasses` / `pitestTargetTests` race workaround. The mutation run SHALL be **deterministic**:
two runs with cleared pitest history SHALL produce the same mutation score.

#### Scenario: pitest runs on the unit suite under check
- **WHEN** `./gradlew check` runs
- **THEN** pitest mutates the `processor` engine and runs the unit suite against the mutants, failing the
  build if the mutation score falls below the configured threshold

#### Scenario: pitest is scoped away from the slow suite
- **WHEN** the pitest configuration is inspected
- **THEN** it targets the unit test task and excludes the integration test task, with all mutators and
  incremental analysis enabled

#### Scenario: pitest runs threaded and deterministic
- **WHEN** the `processor` pitest configuration is inspected
- **THEN** it declares `threads = availableProcessors()` and carries no `@Isolated`, no `threads = 1`, no
  `spock-pitest.groovy`, and no `pitestTargetClasses`/`pitestTargetTests` workaround
- **AND** two runs of the unit suite under pitest with cleared history produce identical mutation scores

### Requirement: The spi module is mutation-tested under threaded pitest

The `spi` module's unit suite SHALL be mutation-tested with pitest — the acceptance oracle that its
rewritten-from-scratch specs actually cover the code (pitest never ran on `spi` before this). pitest SHALL
run **threaded** (`threads = availableProcessors()`) with all mutators and incremental analysis enabled,
with **no `@Isolated`**, **no `threads = 1`**, and **no `spock-pitest.groovy`** serialisation, because the
`spi` unit suite mocks or fakes the `ResolveCtx` seam and stands up no javac. A mutation-score **ratchet
floor** SHALL be wired into `check` (via the shared root pitest block, overridden per-module where a
module's measured score differs).

Unlike `processor`'s, `spi`'s pitest run is **not** byte-identical across cleared-history runs: plain
JUnit execution of the suite is 100% reproducible, but the mutation-kill/test-strength numbers vary run to
run (observed range: 16-26% killed, 24-40% test strength, with line coverage stable at 70-73%) even with
incremental analysis disabled and `threads = 1` — this rules out both a stale-history artifact and a
minion-thread race as the cause. The variance is attributed to pitest's own test-to-mutant attribution when
several tests cover the same line with differing assertion strength, not a shared-substrate race. The
ratchet floor is set with margin below the observed worst case so `check` does not flap.

#### Scenario: pitest runs on the spi unit suite under check
- **WHEN** `./gradlew check` runs
- **THEN** pitest mutates `spi` and runs its unit suite against the mutants, failing the build if the
  mutation score falls below the ratchet floor

#### Scenario: spi pitest is threaded, with a floor that tolerates measured run-to-run variance
- **WHEN** the `spi` pitest configuration is inspected
- **THEN** it declares `threads = availableProcessors()` with all mutators and incremental analysis, and
  carries no `@Isolated`, no `threads = 1`, and no `spock-pitest.groovy`
- **AND** repeated cleared-history runs stay above the configured ratchet floor, though the exact mutation
  score may vary run to run

### Requirement: The engine carries no test-time strategy or fake dependency

The `processor` module SHALL declare no dependency edge — compile, runtime, or test — on any strategy
module, and its tests SHALL NOT use a fake strategy. Real engine↔strategy integration is the feature-e2e
layer's concern, not the engine's.

#### Scenario: Processor tests are strategy-free and fake-free
- **WHEN** the `processor` test classpath and specs are inspected
- **THEN** no strategy module is present, no `FakeStrategy` (or other synthetic SPI strategy) is registered
  or used, and no spec compiles a `@Mapper` to assert engine behaviour

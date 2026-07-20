# Engine Test Quality Spec

## Purpose

Defines how the `processor` engine is tested as an isolated library: at its own seams by unit tests (no compile, no strategy, no fake), held to a pitest coverage gate (see `test-coverage-tooling`), and mutation-tested so the assertions are proven meaningful. Real engine↔strategy integration is the feature-e2e layer's concern (see `e2e-test-architecture`), never the engine module's.

## Requirements

### Requirement: The engine is unit-tested as an isolated library

The `processor` engine SHALL be tested at its own seams by **unit tests** — constructing a stage's or
component's input (a `MapperContext`, graph, demand, or extracted plan) and asserting on the resulting
structure — with **no compilation and no strategy or fake** involved. Coverage of the unit suite
(`@Tag('unit')`, `test.exec`) is measured by pitest's `coverageThreshold` (see the `test-coverage-tooling`
capability), not by a separate jacoco gate. Any class or method that genuinely cannot be meaningfully
covered (pure debug/rendering output, generated code) SHALL be annotated `@DoNotMutate` or
`@CoverageIgnore` in source rather than carved out via a build-level jacoco or pitest exclusion.

#### Scenario: Engine behaviour is asserted at the seam, not through a compile
- **WHEN** an engine contract (self-seeding, descent, assembly hoisting, cost selection, realisation
  diagnostics, nullness crossing) is unit-tested
- **THEN** the test constructs the engine's own input structures and asserts on the produced graph, plan, or
  diagnostic — it does not compile a `@Mapper` and does not register any strategy or fake

#### Scenario: The engine unit suite meets the pitest coverage gate
- **WHEN** `./gradlew check` runs the `processor` unit suite
- **THEN** pitest's `coverageThreshold` gate passes, with any exception being a class or method carrying a
  source-level `@DoNotMutate`/`@CoverageIgnore` annotation rather than a build-level exclusion

### Requirement: The engine unit suite is mutation-tested

The `processor` unit suite SHALL be mutation-tested with pitest, bound to the **unit** test task only (the
integration suite is excluded, where pitest is slow). pitest SHALL run with **all mutators** and
**incremental analysis enabled**, and the shared mutation/coverage/test-strength thresholds defined by the
`test-coverage-tooling` capability apply here with no per-module override. Mutation score and line coverage
are complementary signals measured by the same tool: coverage proves code is exercised, mutation score
proves the assertions are meaningful.

Because the rewritten unit suite mocks the `ResolveCtx` seam (see `expansion-test-harness`) and stands up
**no javac**, pitest SHALL run **threaded** (`threads = availableProcessors()`) with **no `@Isolated`**,
**no `threads = 1`**, and **no `spock-pitest.groovy`** serialised-minion configuration, and with no
`pitestTargetClasses` / `pitestTargetTests` race workaround. The mutation run SHALL be **deterministic**:
two runs with cleared pitest history SHALL produce the same mutation score.

#### Scenario: pitest runs on the unit suite under check
- **WHEN** `./gradlew check` runs
- **THEN** pitest mutates the `processor` engine and runs the unit suite against the mutants, failing the
  build if the mutation score falls below the shared threshold

#### Scenario: pitest is scoped away from the slow suite
- **WHEN** the pitest configuration is inspected
- **THEN** it targets the unit test task and excludes the integration test task, with all mutators and
  incremental analysis enabled

#### Scenario: pitest runs threaded and deterministic
- **WHEN** the `processor` pitest configuration is inspected
- **THEN** it declares `threads = availableProcessors()` and carries no `@Isolated`, no `threads = 1`, no
  `spock-pitest.groovy`, and no `pitestTargetClasses`/`pitestTargetTests` workaround
- **AND** two runs of the unit suite under pitest with cleared history produce identical mutation scores

### Requirement: The spi module is mutation-tested under threaded pitest, sharing the uniform threshold

The `spi` module's unit suite SHALL be mutation-tested with pitest — the acceptance oracle that its
rewritten-from-scratch specs actually cover the code. pitest SHALL run **threaded**
(`threads = availableProcessors()`) with all mutators and incremental analysis enabled, with **no
`@Isolated`**, **no `threads = 1`**, and **no `spock-pitest.groovy`** serialisation, because the `spi` unit
suite mocks or fakes the `ResolveCtx` seam and stands up no javac. `spi` SHALL meet the same shared
`mutationThreshold`/`coverageThreshold`/`testStrengthThreshold` as every other enrolled module (see
`test-coverage-tooling`) — no per-module ratchet floor or override SHALL be configured for `spi`.

A prior measurement found `spi`'s mutation-kill/test-strength numbers varying run to run (16-26% killed,
24-40% test strength, with line coverage stable at 70-73%), tentatively attributed to pitest's own
test-to-mutant attribution rather than a shared-substrate race, predating the removal of the last
javac-backed unit fixture repo-wide (`dissolve-private-type-universe`). Re-validated as part of this change:
the actual cause was Spock's `optimizeRunOrder` runner setting racing on a cross-JVM shared run-history file
under pitest's minion-level parallelism (see the `test-coverage-tooling` capability's dedicated
requirement), not pitest attribution and not a javac substrate. Disabling `optimizeRunOrder` via
`spi/src/test/resources/SpockConfig.groovy` took `spi` from unstable 47-60% mutation kill to a stable 96%
mutation kill / 97% test strength / 97% line coverage across repeated cleared-history runs, with no test
rewriting required.

#### Scenario: pitest runs on the spi unit suite under check
- **WHEN** `./gradlew check` runs
- **THEN** pitest mutates `spi` and runs its unit suite against the mutants, failing the build if the
  mutation score, coverage, or test strength falls below the shared threshold

#### Scenario: spi pitest is threaded and shares the uniform threshold
- **WHEN** the `spi` pitest configuration is inspected
- **THEN** it declares `threads = availableProcessors()` with all mutators and incremental analysis, carries
  no `@Isolated`, no `threads = 1`, and no `spock-pitest.groovy`, and sets no
  `mutationThreshold`/`coverageThreshold`/`testStrengthThreshold` override distinct from the shared value

#### Scenario: Run-to-run variance stays resolved
- **WHEN** repeated cleared-history pitest runs on `spi` are compared
- **THEN** the score is stable at or above the shared threshold (via `spi`'s `SpockConfig.groovy` disabling
  `optimizeRunOrder`), with no per-module threshold override

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

### Requirement: The engine carries no test-time strategy or fake dependency

The `processor` module SHALL declare no dependency edge — compile, runtime, or test — on any strategy
module, and its tests SHALL NOT use a fake strategy. Real engine↔strategy integration is the feature-e2e
layer's concern, not the engine's.

#### Scenario: Processor tests are strategy-free and fake-free
- **WHEN** the `processor` test classpath and specs are inspected
- **THEN** no strategy module is present, no `FakeStrategy` (or other synthetic SPI strategy) is registered
  or used, and no spec compiles a `@Mapper` to assert engine behaviour

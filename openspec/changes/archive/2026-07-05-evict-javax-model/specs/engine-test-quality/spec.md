## ADDED Requirements

### Requirement: Mutation testing runs threaded on a parallel-safe suite

pitest SHALL run with `threads = availableProcessors()` (no property-gated single-thread fallback), and its
scores SHALL be deterministic across identical clean runs. No spec SHALL carry `spock.lang.Isolated`, and no
pitest-specific Spock configuration file (`spock-pitest.groovy` or equivalent `spock.configuration` jvmArg)
SHALL exist — the unit suites are parallel-safe by construction because the type currency is immutable
values (see `type-model`), not a shared javac session.

#### Scenario: pitest runs threaded
- **WHEN** the shared pitest configuration is inspected
- **THEN** `threads` is `availableProcessors()` with no `pitestThreads`-style property override, and no `spock.configuration` jvmArg points at a serialising config file

#### Scenario: No Isolated specs remain
- **WHEN** the test sources of all modules are searched for `spock.lang.Isolated`
- **THEN** there are no occurrences

#### Scenario: Scores are deterministic
- **WHEN** pitest runs twice on an unchanged tree with cleared history
- **THEN** the mutation, coverage, and test-strength scores are identical

### Requirement: All production modules are mutation-tested

pitest SHALL be applied to every production module — `processor`, `spi`, `strategies-builtin`, `reactor`,
and `reactor-blocking` — bound to each module's unit test task under `check`, with per-module ratchet
thresholds set below the measured score at introduction (never red on main; regressions fail).

#### Scenario: Every production module runs pitest under check
- **WHEN** `./gradlew check` runs
- **THEN** pitest executes for `processor`, `spi`, `strategies-builtin`, `reactor`, and `reactor-blocking`, each against its unit suite

#### Scenario: Thresholds ratchet per module
- **WHEN** a module's mutation score drops below its configured floor
- **THEN** the build fails for that module

### Requirement: Module-specific pitest configuration lives in the module

The root `build.gradle`'s shared pitest block SHALL contain only module-agnostic mechanics (versions,
mutators, incremental analysis, report formats, thread count, group filtering). Module-specific settings —
excluded classes (e.g. generated Dagger wiring, debug/rendering classes), thresholds, and any
target-class scoping — SHALL live in the owning module's `build.gradle`. The `pitestTargetClasses` /
`pitestTargetTests` property-gated overrides SHALL NOT exist.

#### Scenario: Root pitest config is module-agnostic
- **WHEN** the root `build.gradle` pitest block is inspected
- **THEN** it names no `io.github.joke.percolate.*` class patterns and reads no `pitestTargetClasses`/`pitestTargetTests`/`pitestThreads` properties

#### Scenario: Processor-specific exclusions live in the processor module
- **WHEN** `processor/build.gradle` is inspected
- **THEN** the Dagger-wiring and debug/rendering `excludedClasses` patterns are declared there, matching the jacoco exclusions for the same surface

## MODIFIED Requirements

### Requirement: The engine unit suite is mutation-tested

The `processor` unit suite SHALL be mutation-tested with pitest, bound to the **unit** test task only. pitest
SHALL run with **all mutators**, **incremental analysis enabled**, and **threaded execution** (see
"Mutation testing runs threaded on a parallel-safe suite"), and a mutation-score threshold SHALL be wired
into `check` and ratcheted upward as the unit suite grows. Coverage and mutation score are complementary
gates: line/branch coverage proves code is exercised, mutation score proves the assertions are meaningful.

#### Scenario: pitest runs on the unit suite under check
- **WHEN** `./gradlew check` runs
- **THEN** pitest mutates the `processor` engine and runs the unit suite against the mutants, failing the
  build if the mutation score falls below the configured threshold

#### Scenario: pitest is scoped to the unit suite
- **WHEN** the pitest configuration is inspected
- **THEN** it targets the unit test task (`includedGroups = ['unit']`), with all mutators and
  incremental analysis enabled

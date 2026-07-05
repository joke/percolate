## MODIFIED Requirements

### Requirement: The engine unit suite is mutation-tested

The `processor` unit suite SHALL be mutation-tested with pitest, bound to the **unit** test task only (the
integration suite is excluded, where pitest is slow). pitest SHALL run with **all mutators** and
**incremental analysis enabled**, and a mutation-score threshold SHALL be wired into `check` and ratcheted
upward as the unit suite grows. Coverage and mutation score are complementary gates: line/branch coverage
proves code is exercised, mutation score proves the assertions are meaningful.

Because the rewritten unit suite mocks the `ResolveCtx` seam and stands up **no javac**, pitest SHALL run
**threaded** (`threads = availableProcessors()`) with **no `@Isolated`**, **no `threads = 1`**, and **no
`spock-pitest.groovy`** serialised-minion configuration, and with no `pitestTargetClasses` / `pitestTargetTests`
race workaround. The mutation run SHALL be **deterministic**: two runs with cleared pitest history SHALL
produce the same mutation score.

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
- **THEN** it declares `threads = availableProcessors()` and carries no `@Isolated`, no `threads = 1`, no `spock-pitest.groovy`, and no `pitestTargetClasses`/`pitestTargetTests` workaround
- **AND** two runs of the unit suite under pitest with cleared history produce identical mutation scores

## ADDED Requirements

### Requirement: The spi module is mutation-tested under threaded pitest

The `spi` module's unit suite SHALL be mutation-tested with pitest — the acceptance oracle that its
rewritten-from-scratch specs actually cover the code (pitest has never run on `spi`). pitest SHALL run
**threaded** (`threads = availableProcessors()`) with all mutators and incremental analysis enabled, with **no
`@Isolated`**, **no `threads = 1`**, and **no `spock-pitest.groovy`** serialisation, because the `spi` unit
suite mocks the `ResolveCtx` seam and stands up no javac. A mutation-score **ratchet floor** SHALL be wired into
`check`, and the run SHALL be **deterministic** across cleared-history runs. Whether the floor and thresholds
live in `spi/build.gradle` or a shared root block is a module-config choice made with real scores in hand.

#### Scenario: pitest runs on the spi unit suite under check
- **WHEN** `./gradlew check` runs
- **THEN** pitest mutates `spi` and runs its unit suite against the mutants, failing the build if the mutation score falls below the ratchet floor

#### Scenario: spi pitest is threaded and deterministic
- **WHEN** the `spi` pitest configuration is inspected
- **THEN** it declares `threads = availableProcessors()` with all mutators and incremental analysis, and carries no `@Isolated`, no `threads = 1`, and no `spock-pitest.groovy`
- **AND** two runs with cleared history produce identical mutation scores

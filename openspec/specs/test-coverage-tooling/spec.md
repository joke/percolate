# Test Coverage Tooling Spec

## Purpose

Cross-cutting build-tooling contract for how coverage and mutation testing are configured across all modules — single coverage tool (pitest only), uniform enrollment rule, uniform thresholds, the ban on build-level exclusions in favor of source-level suppression annotations, and the Spock runner configuration every pitest-enrolled module needs to get deterministic scores.

## Requirements

### Requirement: pitest is the single coverage and mutation-testing tool

No module SHALL apply the `jacoco` Gradle plugin. Line/branch coverage SHALL be measured exclusively via pitest's `coverageThreshold`, and assertion quality via `mutationThreshold` / `testStrengthThreshold`. `percolate.conventions.gradle` SHALL contain no `jacoco`-related configuration (no `JacocoReport`/`JacocoCoverageVerification` task configuration, no `jacocoTestCoverageVerification` wiring into `check`).

#### Scenario: No module applies jacoco
- **WHEN** every module's `build.gradle` and the `percolate.conventions.gradle` buildSrc plugin are inspected
- **THEN** none applies the `jacoco` plugin and none references `JacocoReport`, `JacocoCoverageVerification`, or `jacocoTestCoverageVerification`

#### Scenario: check enforces coverage via pitest alone
- **WHEN** `./gradlew check` runs on a module enrolled in pitest
- **THEN** the build fails if pitest's `coverageThreshold` is not met, and no separate jacoco coverage-verification task exists to fail independently

### Requirement: pitest is auto-enrolled by the conventions plugin for modules with real unit-tested production code

`percolate.conventions.gradle` SHALL apply `info.solidsoft.pitest` automatically for every module that applies the `java` plugin, rather than requiring each module to declare `id 'info.solidsoft.pitest'` individually. A module with no main source, or main source with no `unit`-tagged test suite exercising it, SHALL explicitly disable the pitest task rather than never having declared the plugin. As of this change, pitest SHALL run for `processor`, `spi`, `strategies-builtin`, `reactor`, and `reactor-blocking`, and SHALL be disabled for `annotations`, `percolate`, `percolate-javapoet`, `percolate-smoke`, `architecture-tests`, and `test-foundation`.

#### Scenario: A module with real code and unit tests gets pitest for free
- **WHEN** `reactor/build.gradle` or `reactor-blocking/build.gradle` is inspected
- **THEN** neither declares `id 'info.solidsoft.pitest'` explicitly, yet `./gradlew :reactor:check` and `./gradlew :reactor-blocking:check` each run pitest as part of `check`

#### Scenario: A module with nothing to mutate opts out explicitly
- **WHEN** `annotations/build.gradle`, `percolate/build.gradle`, `percolate-javapoet/build.gradle`, `percolate-smoke/build.gradle`, `architecture-tests/build.gradle`, or `test-foundation/build.gradle` is inspected
- **THEN** the pitest task is explicitly disabled for that module, with no `failWhenNoMutations` failure on `check`

#### Scenario: A new module with production code is mutation-tested by default
- **WHEN** a new module applying the `java` plugin is added to the build with no explicit pitest opt-out
- **THEN** `./gradlew check` runs pitest against it, failing if it has production code with no `unit`-tagged tests covering it

### Requirement: pitest thresholds are uniform across every enrolled module

`percolate.conventions.gradle` SHALL declare a single shared pitest configuration — `mutationThreshold = 85`, `coverageThreshold = 95`, `testStrengthThreshold = 90` — with no per-module override of any of the three values.

#### Scenario: No module overrides the shared thresholds
- **WHEN** every enrolled module's `build.gradle` is inspected
- **THEN** none sets `mutationThreshold`, `coverageThreshold`, or `testStrengthThreshold` to a value other than what `percolate.conventions.gradle` declares

#### Scenario: check fails below any of the three thresholds
- **WHEN** `./gradlew check` runs on an enrolled module whose mutation score, line coverage, or test strength falls below 85%, 95%, or 90% respectively
- **THEN** the build fails

### Requirement: Coverage/mutation exclusions are source-level annotations, not build-level configuration

No module's `build.gradle` (nor `percolate.conventions.gradle`) SHALL declare a pitest `excludedClasses`, `excludedMethods`, or `excludedTestClasses` entry. A class or method that genuinely cannot be meaningfully mutation-tested (generated code, pure debug/rendering output) SHALL instead be annotated in source with `@com.groupcdg.pitest.annotations.DoNotMutate` or `@com.groupcdg.pitest.annotations.CoverageIgnore`. Where coverage is achievable, the code SHALL be restructured for testability instead of suppressed: extracting collaborators, widening `private` methods to `protected` with `@org.jetbrains.annotations.VisibleForTesting`, and testing protected methods via spies.

#### Scenario: No build-level pitest exclusion exists anywhere
- **WHEN** every `build.gradle` file in the repository is inspected
- **THEN** none contains `excludedClasses`, `excludedMethods`, or `excludedTestClasses`

#### Scenario: A genuinely unmutatable class is annotated, not excluded at the build level
- **WHEN** a class that cannot be meaningfully mutation-tested (e.g. Dagger-generated code, a debug-only DOT graph dumper) is inspected
- **THEN** it carries `@DoNotMutate` or `@CoverageIgnore` from `com.groupcdg.pitest.annotations`, and no Gradle-level exclusion references it

### Requirement: Every pitest-enrolled module disables Spock's cross-JVM run-order optimization

Every module enrolled in pitest (see the auto-enrollment requirement above) SHALL carry a `SpockConfig.groovy` in `src/test/resources/` that disables Spock's `optimizeRunOrder` runner setting. Spock's `OptimizeRunOrderExtension` persists per-spec run-history to a file under the user's Spock home (`~/.spock/RunHistory/<SpecName>`) shared across **every concurrent JVM on the machine**, not scoped to a build, project, or module. Under pitest's own minion-level parallelism, concurrent JVMs race to read/write that file, corrupting a spec's history entry and causing `IllegalArgumentException: Comparison method violates its general contract!` during Spock's test-discovery sort — intermittently crashing the *entire* spec class (not any specific mutant) during a pitest coverage or mutation pass, which pitest then misreports as widespread survived mutants unrelated to the actual code under test. This SHALL NOT be diagnosed as a pitest test-to-mutant attribution limitation or accommodated with a lowered per-module threshold; it SHALL be fixed by disabling `optimizeRunOrder`.

#### Scenario: Every pitest-enrolled module carries the fix
- **WHEN** `src/test/resources/SpockConfig.groovy` is inspected in `processor`, `spi`, `strategies-builtin`, `reactor`, and `reactor-blocking`
- **THEN** each declares `runner { optimizeRunOrder false }`

#### Scenario: A module newly enrolled in pitest gets the same treatment
- **WHEN** a module with real production code and a `unit`-tagged test suite is newly enrolled in pitest
- **THEN** it also carries a `SpockConfig.groovy` disabling `optimizeRunOrder`, rather than relying on run-to-run variance in mutation/coverage scores to be tolerated or attributed to pitest itself

### Requirement: Test execution parallelism is not restricted without an active reason

No module SHALL set `maxParallelForks = 1`, disable JUnit Platform parallel execution, or disable Spock parallel execution unless a currently-applicable reason for serialization is documented alongside the setting. A historical reason that no longer applies (e.g. a dependency or test framework that has since been removed) SHALL NOT justify keeping the restriction.

#### Scenario: reactor and reactor-blocking run tests in parallel
- **WHEN** `reactor/build.gradle` and `reactor-blocking/build.gradle` are inspected
- **THEN** neither sets `maxParallelForks = 1`, `junit.jupiter.execution.parallel.enabled = false`, nor `spock.parallel.threads = 0`

#### Scenario: A serialization setting must carry a currently-valid reason
- **WHEN** any module's `build.gradle` restricts test parallelism
- **THEN** a comment or commit reference documents the still-applicable reason, and that reason does not name a framework or dependency no longer present in the build (e.g. jqwik)

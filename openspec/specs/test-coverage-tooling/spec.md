# Test Coverage Tooling Spec

## Purpose

Cross-cutting build-tooling contract for how coverage and mutation testing are configured across all modules — single coverage tool (pitest only), enrollment declared by the modules that carry it, a shared 100/100/100 threshold floor with a named ratchet where a module cannot yet meet it, the ban on build-level exclusions in favor of source-level suppression annotations, and the Spock runner and mock-maker configuration every module needs to get deterministic scores. How pitest runs is supplied once by the conventions plugin, since a setting that must hold everywhere cannot depend on someone noticing that near-identical copies exist and editing all of them; what a module states for itself is only that it is enrolled, and — where it applies — the thresholds it currently reaches. Spock's own configuration is the deliberate exception: it is a checked-in `SpockConfig.groovy` per module, kept in step by review of a tracked file that anyone debugging a spec can read.
## Requirements
### Requirement: pitest is the single coverage and mutation-testing tool

No module SHALL apply the `jacoco` Gradle plugin. Line/branch coverage SHALL be measured exclusively via pitest's `coverageThreshold`, and assertion quality via `mutationThreshold` / `testStrengthThreshold`. `percolate.conventions.gradle` SHALL contain no `jacoco`-related configuration (no `JacocoReport`/`JacocoCoverageVerification` task configuration, no `jacocoTestCoverageVerification` wiring into `check`).

#### Scenario: No module applies jacoco
- **WHEN** every module's `build.gradle` and the `percolate.conventions.gradle` buildSrc plugin are inspected
- **THEN** none applies the `jacoco` plugin and none references `JacocoReport`, `JacocoCoverageVerification`, or `jacocoTestCoverageVerification`

#### Scenario: check enforces coverage via pitest alone
- **WHEN** `./gradlew check` runs on a module enrolled in pitest
- **THEN** the build fails if pitest's `coverageThreshold` is not met, and no separate jacoco coverage-verification task exists to fail independently

### Requirement: pitest is declared by the modules that carry mutation testing

The `info.solidsoft.pitest` plugin SHALL be applied by each module that has production code and a
`unit`-tagged suite exercising it — currently `processor`, `spi`, `strategies-builtin`, `reactor`,
and `reactor-blocking` — by declaring `id 'info.solidsoft.pitest'` in its own `plugins` block.
`percolate.conventions.gradle` SHALL NOT apply the plugin to every `java` module, and no module
SHALL carry a `tasks.named('pitest') { enabled = false }` opt-out. Configuring the plugin remains
the conventions plugin's job: everything inside `pluginManager.withPlugin('info.solidsoft.pitest')`
— thresholds, mutators, incremental analysis, included groups, JVM args, the history plugin and
annotation dependencies, and the `check` wiring — SHALL stay declared once there, so an enrolling
module states only that it is enrolled and never how. The single exception is a module-local
threshold ratchet, which the thresholds requirement below defines: a module that cannot meet the
shared floor states its own three numbers and nothing else.

A module declaring the plugin without mutable code or without a `unit` suite still fails on
`failWhenNoMutations`; enrollment is therefore an act of declaration rather than something a
module must remember to switch off. Applying the plugin everywhere and disabling it again in the
six modules that had nothing to mutate was the inverse arrangement, and it made the module list
readable only by reading every module's opt-out.

#### Scenario: A mutation-tested module declares the plugin itself

- **WHEN** `processor/build.gradle`, `spi/build.gradle`, `strategies-builtin/build.gradle`,
  `reactor/build.gradle`, or `reactor-blocking/build.gradle` is inspected
- **THEN** each declares `id 'info.solidsoft.pitest'` in its `plugins` block, and `check` runs
  pitest for it against the thresholds the conventions plugin configures

#### Scenario: A module with nothing to mutate says nothing at all

- **WHEN** `annotations/build.gradle`, `percolate/build.gradle`, `percolate-javapoet/build.gradle`,
  `percolate-smoke/build.gradle`, `architecture-tests/build.gradle`, `test-foundation/build.gradle`,
  or `lib/javapoet/build.gradle` is inspected
- **THEN** it neither applies `info.solidsoft.pitest` nor disables a pitest task, and its `check`
  runs no mutation testing

#### Scenario: Configuration stays central while enrollment is local

- **WHEN** an enrolled module's `build.gradle` is inspected
- **THEN** it configures no pitest setting of its own beyond a documented threshold ratchet — the
  mutators, incremental analysis, included groups, JVM args, the `pitest-history-plugin`
  dependency, and the `check` dependency all live in `percolate.conventions.gradle`

### Requirement: pitest thresholds are 100 by default, with a named ratchet where they are not

`percolate.conventions.gradle` SHALL declare the shared pitest configuration —
`mutationThreshold = 100`, `coverageThreshold = 100`, `testStrengthThreshold = 100` — and every
enrolled module SHALL meet it unless it declares its own thresholds in its `build.gradle` with a
comment stating why. A surviving mutant is otherwise a build failure rather than budget spent
against a margin: with a margin, the first uncovered branch is invisible until enough of them
accumulate to breach it, and the number that is easy to defend is the one with no slack in it.
Code that genuinely cannot be mutation-tested is suppressed at the source level with
`@DoNotMutate` / `@CoverageIgnore`, as the exclusions requirement already demands.

A module-local override SHALL be set at what the module currently scores, never at a round number
below it, so it acts as a ratchet: a regression fails the build, and recovered ground is nailed
down by raising the numbers. `processor` is the one module that carries such an override
(`mutationThreshold = 89`, `coverageThreshold = 97`, `testStrengthThreshold = 92`). Its gap is not
simply missing tests: a large share of what survives there is equivalent by construction — the
graph queries filter defensively for a vertex or edge kind the surrounding invariant already
guarantees, so no test can tell the mutant from the original — and closing those means reshaping
production code or blanketing it in `@DoNotMutate`.

#### Scenario: An enrolled module meets the shared floor

- **WHEN** `spi`, `strategies-builtin`, `reactor`, or `reactor-blocking` runs `check`
- **THEN** pitest runs against 100/100/100, and the module's `build.gradle` sets no threshold of
  its own

#### Scenario: The one module below the floor states its own numbers

- **WHEN** `processor/build.gradle` is inspected
- **THEN** it declares a `pitest { }` block setting all three thresholds to the scores the module
  currently achieves, with a comment naming the equivalent-mutant reason it is below the floor

#### Scenario: check fails on any regression

- **WHEN** `./gradlew check` runs on an enrolled module and a mutant that used to be killed
  survives, a covered line becomes uncovered, or a mutant is left killed only by an incidental test
- **THEN** the build fails, because each threshold sits at the score the module already reached

### Requirement: Coverage/mutation exclusions are source-level annotations, not build-level configuration

No module's `build.gradle` (nor `percolate.conventions.gradle`) SHALL declare a pitest `excludedClasses`, `excludedMethods`, or `excludedTestClasses` entry. A class or method that genuinely cannot be meaningfully mutation-tested (generated code, pure debug/rendering output) SHALL instead be annotated in source with `@com.groupcdg.pitest.annotations.DoNotMutate` or `@com.groupcdg.pitest.annotations.CoverageIgnore`. Where coverage is achievable, the code SHALL be restructured for testability instead of suppressed: extracting collaborators, widening `private` methods to `protected` with `@org.jetbrains.annotations.VisibleForTesting`, and testing protected methods via spies.

#### Scenario: No build-level pitest exclusion exists anywhere
- **WHEN** every `build.gradle` file in the repository is inspected
- **THEN** none contains `excludedClasses`, `excludedMethods`, or `excludedTestClasses`

#### Scenario: A genuinely unmutatable class is annotated, not excluded at the build level
- **WHEN** a class that cannot be meaningfully mutation-tested (e.g. Dagger-generated code, a debug-only DOT graph dumper) is inspected
- **THEN** it carries `@DoNotMutate` or `@CoverageIgnore` from `com.groupcdg.pitest.annotations`, and no Gradle-level exclusion references it

### Requirement: Spock is configured by a checked-in per-module SpockConfig.groovy

Every module with a Spock suite SHALL carry a checked-in `src/test/resources/SpockConfig.groovy`,
and those files SHALL be identical in content. The configuration SHALL cover at minimum:

- `mockMaker { preferredMockMaker spock.mock.MockMakers.mockito }`, so final classes, final
  methods, and `SpyStatic` are available everywhere
- `runner { parallel { enabled !Boolean.getBoolean('spock.parallel.disabled') } }` — parallel by
  default, switchable off by a system property (see the requirement below)
- `timeout { globalTimeout java.time.Duration.ofMinutes(1); applyGlobalTimeoutToFixtures false }`,
  so a deadlock or a runaway spec fails as a timed-out feature instead of hanging the build

Spock's `optimizeRunOrder` SHALL NOT be enabled in any module. Its run-history file lives under
the user's Spock home (`~/.spock/RunHistory/<SpecName>`), shared across every concurrent JVM on
the machine rather than scoped to a build, project, or module; concurrent JVMs race on it and
corrupt a spec's entry, producing `IllegalArgumentException: Comparison method violates its
general contract!` during test discovery, which under pitest surfaces as widespread survived
mutants unrelated to the code under test. The setting is off by default, so the requirement is
that nothing turns it on — an explicit `optimizeRunOrder false` declaration is not required.

A generated or plugin-synthesized `SpockConfig.groovy` SHALL NOT be used. The file is test input,
it is read by anyone debugging a spec, and its per-module copies are kept in step by review of a
tracked file rather than by a build step nobody sees.

#### Scenario: Every module with a Spock suite carries the file

- **WHEN** `src/test/resources/SpockConfig.groovy` is inspected for `processor`, `spi`,
  `strategies-builtin`, `reactor`, `reactor-blocking`, `annotations`, `architecture-tests`, and
  `percolate-smoke`
- **THEN** each exists, is checked into version control, and carries the same mock-maker, parallel,
  and timeout settings

#### Scenario: No module enables run-order optimization

- **WHEN** every `SpockConfig.groovy` and every `build.gradle` is inspected
- **THEN** none sets `optimizeRunOrder true`, and the default-off behaviour stands

### Requirement: Spock parallel execution is on for test runs and off under pitest

Spock's in-JVM parallel execution SHALL be enabled for ordinary `test` and `integrationTest` runs
and SHALL be disabled while pitest runs, via the `spock.parallel.disabled` system property that
`SpockConfig.groovy` reads and that `percolate.conventions.gradle` passes in pitest's `jvmArgs`.
Mutation testing needs a deterministic single-threaded oracle — and Mockito's static mocking is
confined to the registering thread — while an ordinary test run needs neither, and serializing it
for pitest's benefit costs wall-clock time on every build. The two regimes SHALL therefore be
distinguished by the property rather than by a repo-wide `enabled false`.

Test-JVM parallelism SHALL likewise stay unrestricted: `percolate.conventions.gradle` SHALL set
`maxParallelForks` from the full available processor count, not a fraction of it.

#### Scenario: An ordinary test run is parallel

- **WHEN** `./gradlew test` runs without `-Dspock.parallel.disabled`
- **THEN** Spock executes specs concurrently, and `maxParallelForks` equals the available
  processor count

#### Scenario: A pitest run is serial

- **WHEN** `./gradlew pitest` runs
- **THEN** the pitest `jvmArgs` carry `-Dspock.parallel.disabled=true`, every minion JVM reads it,
  and Spock runs single-threaded so mutation and coverage scores are deterministic

### Requirement: Test execution parallelism is not restricted without an active reason

No module SHALL set `maxParallelForks = 1`, disable JUnit Platform parallel execution, or disable Spock parallel execution unless a currently-applicable reason for serialization is documented alongside the setting. A historical reason that no longer applies (e.g. a dependency or test framework that has since been removed) SHALL NOT justify keeping the restriction.

#### Scenario: reactor and reactor-blocking run tests in parallel
- **WHEN** `reactor/build.gradle` and `reactor-blocking/build.gradle` are inspected
- **THEN** neither sets `maxParallelForks = 1`, `junit.jupiter.execution.parallel.enabled = false`, nor `spock.parallel.threads = 0`

#### Scenario: A serialization setting must carry a currently-valid reason
- **WHEN** any module's `build.gradle` restricts test parallelism
- **THEN** a comment or commit reference documents the still-applicable reason, and that reason does not name a framework or dependency no longer present in the build (e.g. jqwik)

## Why

Test tooling has drifted into an inconsistent, self-contradicting state: two coverage tools measure overlapping things (jacoco branch coverage and pitest mutation/line coverage), pitest is enrolled in only three of the five modules with real production code, per-module pitest exclusion lists and weakened thresholds hide gaps rather than fixing them, and `reactor`/`reactor-blocking` still force serial test execution — a fossil from jqwik's shared `build/jqwik-database` directory, a testing framework fully removed from this repo. Consolidating on pitest alone, enrolling it uniformly, and banning exclusions in favor of explicit, reviewable `@DoNotMutate`/`@CoverageIgnore` annotations turns the coverage gate back into a real signal instead of a patchwork of quiet carve-outs.

## What Changes

- **BREAKING (build-internal)**: Remove the `jacoco` plugin and all jacoco configuration from `percolate.conventions.gradle` and every module `build.gradle`. Coverage is measured by pitest's `coverageThreshold` alone going forward.
- Remove the `maxParallelForks = 1` / `junit.jupiter.execution.parallel.enabled = false` / `spock.parallel.threads = 0` overrides from `reactor/build.gradle` and `reactor-blocking/build.gradle` (jqwik-era fossil; jqwik is fully removed from the repo).
- Move `id 'info.solidsoft.pitest'` out of per-module `plugins {}` blocks and into `percolate.conventions.gradle`, auto-applied for modules with real production code and a `unit`-tagged test suite. Enrolled: `processor`, `spi`, `strategies-builtin`, `reactor`, `reactor-blocking`. Explicitly not enrolled (no main source and/or no unit-tagged tests): `annotations`, `percolate`, `percolate-javapoet`, `percolate-smoke`, `architecture-tests`, `test-foundation`; not applicable: `bom`, `dependencies` (`java-platform`, no `java` plugin).
- Remove all `excludedClasses`/`excludedMethods`/`excludedTestClasses` pitest exclusions (the 7-class list in `processor/build.gradle`). Any class that genuinely cannot be mutation-tested (Dagger-generated code, a debug-only DOT dumper, etc.) is annotated in source with `@com.groupcdg.pitest.annotations.DoNotMutate` or `@com.groupcdg.pitest.annotations.CoverageIgnore` instead of being carved out at the build level. Where coverage is achievable, restructure for testability first: extract collaborators, widen `private` to `protected` + `@org.jetbrains.annotations.VisibleForTesting`, test protected methods via spies.
- Unify pitest thresholds across every enrolled module: `mutationThreshold = 85`, `coverageThreshold = 95`, `testStrengthThreshold = 90`, set once in `percolate.conventions.gradle`. Remove the weakened per-module overrides in `spi` and `strategies-builtin` (previously 10/60/15). Where a module (in particular `spi`, whose historical variance is documented in `engine-test-quality`) can't sustain the unified floor without flapping, root-cause and fix the underlying test/attribution issue as part of this change rather than reintroducing a per-module override.

## Capabilities

### New Capabilities
- `test-coverage-tooling`: cross-cutting build-tooling contract for how coverage and mutation testing are configured across all modules — single coverage tool (pitest only), uniform enrollment rule, uniform thresholds, and the ban on build-level exclusions in favor of source-level suppression annotations.

### Modified Capabilities
- `engine-test-quality`: strike the jacoco 95%-branch-coverage requirement and "jacoco exclusions MAY be applied" allowance (superseded by `test-coverage-tooling`'s pitest-only coverage gate); replace the `spi` module's documented tolerance for run-to-run mutation-score variance with the unified 85/95/90 thresholds, requiring the variance to be root-caused rather than accommodated.
- `builtin-strategy-unit-tests`: replace the "mutation floor that tolerates measured run-to-run variance" language with the unified 85/95/90 thresholds shared with every other enrolled module.

## Impact

- **Build files**: `buildSrc/src/main/groovy/percolate.conventions.gradle`; `build.gradle` in `processor`, `spi`, `strategies-builtin`, `reactor`, `reactor-blocking`, `architecture-tests`, `percolate-javapoet`, `percolate-smoke`.
- **Source**: `processor`'s previously-excluded classes (`Dagger*` generated code, `internal.stages.dump.*`, `internal.graph.DotRenderer`, `internal.stages.discover.AnnotationDirectiveReader`/`AbstractMethodReader`/`CallableMethodIndexer`, `internal.stages.generate.AssembleMapperType`) each need either a suppression annotation or a testability restructure; `spi`, `strategies-builtin`, `reactor`, `reactor-blocking` likely need new/strengthened unit tests to clear the raised thresholds.
- **Dependencies**: `com.groupcdg:pitest-annotations` (already wired as `compileOnly` wherever pitest applies) goes from unused to load-bearing; `org.jetbrains:annotations` (already an available dependency) gains a new use (`@VisibleForTesting`).
- **CI**: no change — `./gradlew check` already drives both jacoco and pitest today; no jacoco/codecov reporting exists outside the Gradle build to update.
- **Not affected**: `isolated-projects-build` capability's statement that Isolated Projects stays off while `info.solidsoft.pitest` is applied to *any* module is unaffected by wider pitest enrollment — it was already fully blocking, before or after this change.

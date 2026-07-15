## Why

`architecture-tests/build.gradle` has accumulated two unrelated kinds of complexity that don't belong
together. First, a 40-line JavaPoet "swallow-check" block (eager `resolvedConfiguration.resolvedArtifacts()`
resolution at script-evaluation time, `evaluationDependsOn`, a hand-registered task) exists to guard against
a regression that has never occurred and, on inspection, isn't worth this much machinery. Second, one
ArchUnit rule ("no class outside the engine reaches `processor.internal`") needs to see sibling modules'
*test* classes — which ordinary Gradle project dependencies never expose — so it hand-globs each probed
module's build-output directories as literal path strings threaded through a system property. Checking those
paths against disk today shows 5 of the 12 hard-coded directories never exist, and the mechanism fails
**silently** (an empty probe makes the ArchUnit rule vacuously pass) rather than loudly if it ever drifts
further. Both problems have a cleaner shape once examined: the swallow-check protects an invariant nobody
has violated and the residual risk is accepted as not worth guarding; the boundary-probe rule needs no
cross-project reaching at all, because the very modules it's checking already have everything it needs
(their own classes, plus `processor`'s) on their own ordinary test classpath.

## What Changes

- **Remove the JavaPoet swallow-check entirely.** Delete `verifyJavaPoetSwallowed`, its eager classpath
  resolution, `evaluationDependsOn`, and its `check` wiring from `architecture-tests/build.gradle`. No
  replacement (no `configurations.all { exclude ... }`) — the residual risk (something reintroducing
  `com.palantir.javapoet` onto a downstream classpath) is accepted rather than guarded, since the invariant
  is still structurally true (`percolate-javapoet`'s upstream dependency stays `compileOnly`) and has never
  been violated.
- **Apply `java-test-fixtures` to `architecture-tests`**, publishing a small, reusable, parameterized ArchUnit
  rule-building library (e.g. "no class outside package X depends on package Y") that other modules' own
  test suites can consume via `testImplementation testFixtures(project(':architecture-tests'))` — the same
  pattern `strategies-builtin` already uses for `spi`'s (currently empty) test fixtures.
- **Move the "no class outside the engine reaches `processor.internal`" rule** out of `architecture-tests`'
  `ModuleBoundariesSpec` and into each of `strategies-builtin`, `reactor`, and `reactor-blocking`'s own test
  suites, as a small new spec per module built on the shared testFixtures rule. Each of these modules already
  has `testImplementation project(':processor')`, so each already has 100% of what the rule needs — its own
  main+test classes plus `processor`'s — on its own ordinary classpath. This is what eliminates the need for
  `architecture-tests` to reach across project boundaries at all.
- **Delete the `boundaryProbeModules`/`boundaryProbeLayout`/`systemProperty` block** from
  `architecture-tests/build.gradle` — nothing left in that module needs it once the rule above moves.
- Every other existing ArchUnit rule (inter-module layering, strategy myopia, `*Stage` naming, acyclicity,
  the `spi.builtins`-spanning size/private-method ceiling, the `javax.lang.model.util` confinement rule)
  stays exactly where it is, unchanged — each genuinely needs a multi-module view that only
  `architecture-tests` can provide, and none of them uses the boundary probe or the swallow-check today.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `module-boundaries`: the "Engine internals are encapsulated from other modules" requirement's enforcement
  mechanism changes from a central cross-project probe to each owning strategy module checking itself; the
  capability's Purpose description of the architecture suite as a single central module is updated to
  describe the split (central cross-module rules in `architecture-tests`, plus shared, testFixtures-based
  rules each owning module runs on itself).
- `javapoet-relocation`: the "Upstream JavaPoet is fully swallowed" requirement drops its
  "Original package is absent from downstream classpaths" scenario — that scenario described an actively
  verified resolved-classpath scan which no longer exists; the requirement's other scenario (published POM
  metadata carries no upstream dependency) is untouched.

## Impact

- **`architecture-tests/build.gradle`:** shrinks from 82 lines to roughly a quarter of that — dependency
  wiring, `java-test-fixtures` applied, no boundary-probe block, no swallow-check block.
- **`architecture-tests/src/test/groovy/.../ModuleBoundariesSpec.groovy`:** loses the
  "no class outside the engine reaches a processor internal package" test method (relocated, not deleted in
  spirit).
- **`architecture-tests/src/testFixtures/groovy/...` (new):** a small reusable rule-building class other
  modules' specs call into.
- **`strategies-builtin`, `reactor`, `reactor-blocking` (`build.gradle` + a new test spec each):** gain
  `testImplementation testFixtures(project(':architecture-tests'))` and `com.tngtech.archunit:archunit`
  (version already managed by the `dependencies` platform, not currently a dependency of any of these three
  modules), plus one new small spec file each running the relocated rule against their own classpath.
- **No production code, no generated-mapper behavior, no consumer-facing surface changes** — this is a
  test/build-tooling-only restructuring.
- **Teams affected:** solo maintainer (Joke) — no consumer-facing change; CI shape changes only in how one
  architecture rule is distributed across module `check` runs (previously all in `architecture-tests:test`,
  now split across four modules' `test` tasks).

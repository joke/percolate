## Why

The just-archived change (`2026-05-14-restructure-expansion-test-architecture`) inlined the test layer into `processor/src/test/groovy/` and updated the `expansion-test-harness` spec to mandate jqwik-driven property tests (`@Property`, `@Provide`, pinned seeds, `build/jqwik-database`). The implementation that landed alongside it took a shortcut: the seven `*Spec.groovy` files are pure Spock specs whose randomness comes from `java.util.Random` in `GraphGenerator.groovy`. None of jqwik's actual value — shrinking, deterministic seed-replay, edge-case mixing, the failing-seed database — is in effect. The spec and the code diverge on the load-bearing testing contract; closing that gap is the point of this change.

## What Changes

- Adopt the **hybrid Spock + jqwik** shape for all seven property classes: each class continues to `extends spock.lang.Specification` (so `@Tag('unit')`, `@Timeout(30)`, and the rest of the Spock infrastructure stay consistent with the surrounding test layer), and gains jqwik `@Property` methods on the same class. The jqwik methods will be plain Groovy bodies (no Spock `given:/when:/then:` blocks inside them — those are Spock AST transforms not visible to the jqwik engine).
- Replace `GraphGenerator.groovy`'s `java.util.Random`-driven `randomSeed()` / `randomBridges()` / `randomSourceSteps()` / `randomGroupTargets()` with `@Provide` methods returning `Arbitrary<MapperGraph>`, `Arbitrary<List<Bridge>>`, `Arbitrary<List<SourceStep>>`, `Arbitrary<List<GroupTarget>>`. Generators are composed from `net.jqwik.api.Arbitraries` and constrained to `TypeUniverse`.
- Repurpose `GraphGenerator.groovy` as a package-private `Arbitraries` helper (or fold the generator methods into a `@PropertyDefaults`-bearing base class), so the seven specs share generator definitions without duplication.
- Add `@PropertyDefaults(tries = 500)` to each property spec class (or to a small local base class). Each `@Property` method pins a `seed = "<long>"` to make CI flakes locally reproducible.
- Configure jqwik discovery so it picks up `@Property` methods on classes that also extend `Specification`. If the JUnit Platform engine for jqwik runs alongside Spock's, no extra wiring is needed; if Spock's AST transform interferes, fall back to a small `jqwik.properties` tweak or a probe-then-pivot.
- Confirm `processor/build.gradle`'s `-Djqwik.database.directory=build/jqwik-database` is honoured (the system property is already set; verify jqwik actually writes the database during property runs).
- Leave `RealisedEdgeCanarySpec.groovy` as a deterministic Spock test — the canary is a single known-solvable case, not a property; pinning it via jqwik would add noise without benefit.
- Touch up the `jqwik property tests` requirement in `expansion-test-harness` to make the hybrid contract explicit (today the spec is silent on whether the class extends `Specification`).

## Capabilities

### New Capabilities

<!-- None. -->

### Modified Capabilities

- `expansion-test-harness`: Refine the "jqwik property tests" requirement to state explicitly that each property spec is a hybrid class — `extends spock.lang.Specification` for tag/timeout/infrastructure parity *and* declares `@net.jqwik.api.Property` methods discovered by jqwik's JUnit Platform engine. Refine the "jqwik configuration" requirement to record the `@PropertyDefaults` / `jqwik.properties` placement choice once the implementation lands.

## Impact

- **Code (test sources only)**:
  - `processor/src/test/groovy/.../properties/DeterminismSpec.groovy` plus six siblings (`IdempotenceSpec`, `OrderIndependenceSpec`, `MonotonicitySpec`, `IdentityCollapseSpec`, `DisjointAdditivitySpec`, `EmptyStrategyIdentitySpec`) — each gains one or more `@Property(tries = 500, seed = '<long>')` methods alongside (or replacing) the current Spock `def` blocks.
  - `processor/src/test/groovy/.../properties/GraphGenerator.groovy` — `Random`-based methods replaced by `@Provide Arbitrary<...>` methods; class becomes the shared generator source.
  - Possibly a new `processor/src/test/groovy/.../properties/PropertyDefaults.groovy` carrying `@PropertyDefaults(tries = 500)` if the annotation can't live on every spec directly.
  - `processor/src/test/groovy/.../properties/RealisedEdgeCanarySpec.groovy` — unchanged.
- **Build**:
  - `processor/build.gradle` already declares `testImplementation 'net.jqwik:jqwik'` and `-Djqwik.database.directory=build/jqwik-database`. No platform change expected; one verification probe to confirm jqwik's JUnit Platform engine discovers `@Property` methods on `Specification` subclasses.
  - Possibly a new `processor/src/test/resources/jqwik.properties` if class-level `@PropertyDefaults` isn't workable.
- **Production**: no production code touched.
- **CI**: test execution time may grow modestly — `tries = 500` × 7 specs is ~3500 invocations of `ExpansionHarness.expand(...)`; the harness already serialises via `EXPAND_LOCK`, so expect the property runs to dominate the `processor:test` task duration. Acceptable trade-off for actual property coverage.
- **Dependencies**: `net.jqwik:jqwik` already on the classpath via `dependencies/build.gradle` platform constraint and `processor/build.gradle` `testImplementation`. No version bump planned.
- **Teams affected**: solo project (`joke@xckk.de`); no cross-team coordination.
- **Risks**: (i) the jqwik engine may not discover `@Property` methods on `Specification` subclasses cleanly under Spock's AST transforms — surfaceable with a 30-line probe before any rewrite. (ii) `MapperGraph`'s mutability means careful `@Provide` design is needed to avoid generator outputs being shared across iterations. (iii) Shrinking on a graph type means writing or deriving an `Arbitrary<MapperGraph>` with sensible shrink behaviour; for the first cut, lean on `Arbitraries.frequency(...)` / `flatMap` and accept that shrinking quality can improve iteratively.
- **Out of scope** (explicitly): jqwik statistics collection (`Statistics.collect(...)`), custom shrinkers beyond what `flatMap` composition gives for free, and re-enabling Jacoco coverage gates on `processor` (that's the separate "re-enable processor coverage gates" candidate from design.md open question O2).

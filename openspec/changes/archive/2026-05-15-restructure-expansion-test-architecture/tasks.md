## 1. Probe current built-in strategy behaviour

- [x] 1.1 Write a temporary probe in `processor/src/test/java/.../properties/StrategyProbe.java` that, for each currently registered built-in strategy (`DirectAssign`, `ListMap`, `ListWrap`, `MethodCallBridge`, `OptionalMap`, `OptionalUnwrap`, `OptionalWrap`, `SetMap`, `SetWrap`, `GetterRead`, `ConstructorCall`), constructs a typed seed appropriate to the strategy and runs SPI-mode `ExpansionHarness.expand(seed)`. Dump per-strategy the count of `REALISED` edges produced. The seed graphs are constructed via direct `MapperGraph`/`Node`/`Edge.elementSeed(...)` calls — types attached to both source and target nodes.
- [x] 1.2 From the probe output, record in `tasks.md` (this file) which strategies produce realised edges today (rows for the capability spec) and which don't (notes for incremental follow-up).
- [x] 1.3 Delete the probe file once findings are recorded.

## 2. Inline test-support helpers into `processor/src/test/java`

- [x] 2.1 Create directory `processor/src/test/java/io/github/joke/percolate/processor/test/` with a `package-info.java` carrying `@NullMarked`.
- [x] 2.2 Move `TypeUniverse.java` into the new package; update its package declaration from `io.github.joke.percolate.test` to `io.github.joke.percolate.processor.test`. Behaviour unchanged.
- [x] 2.3 Move `GraphFixtures.java` into the new package; update its package declaration. Drop the public modifier on internal helpers if they are only used within the package.
- [x] 2.4 Move `ExpansionAssertions.java` into the new package; update its package declaration.
- [x] 2.5 Move `ExpansionHarness.java` into the new package; update its package declaration. Both `expand(seed)` and `expand(seed, bridges, sourceSteps, groupTargets)` entry points retained (see Failure-mode and Capability requirements).
- [x] 2.6 Move `ExpansionResult.java` into the new package; update its package declaration. Retain the `expandedGraph()`, `diagnostics()`, `roundCount()`, `converged()`, `hasFailures()`, `failureReason()`, `dotRender()`, `hasErrors()`, `isIdempotent()`, `hasIdentityCollisions()`, `hasOrphanRealisedNodes()` surface.
- [x] 2.7 Create internal stubs `HarnessResolveCtx` (package-private, replaces the published `HarnessResolveCtx`) and `HarnessScope` (package-private, replaces the published `HarnessScope`) in the same package. Used by the harness for `MapperContext` placeholder + scope construction.

## 3. Move test-support tests into `processor/src/test/java`

- [x] 3.1 Move every test class from `processor-test-support/src/test/java/io/github/joke/percolate/test/` into `processor/src/test/java/io/github/joke/percolate/processor/test/` and update package declarations.
- [x] 3.2 Adjust imports across the moved tests to reference the new helper locations.
- [x] 3.3 Delete `processor-test-support/src/test/java/io/github/joke/percolate/test/SpiProbe.java` if it still exists (it was a probe, not a kept test).
- [x] 3.4 Run `./gradlew :processor:compileTestJava` and confirm green before continuing.

## 4. Delete `SeedDsl`, `MapperSpec`, `StrategyBundle`, `PropertyTestBase`

- [x] 4.1 Delete `SeedDsl.java`, `MapperSpec.java`, `StrategyBundle.java`, `PropertyTestBase.java` from `processor-test-support/src/main/java/io/github/joke/percolate/test/`.
- [x] 4.2 Delete the `SeedDslTest.java`, `MapperSpecTest.java`, `StrategyBundleTest.java`, `PropertyTestBaseTest.java` test files.
- [x] 4.3 Delete `HarnessResolveCtxTest.java` (replaced by package-private stub) — the new stub does not need a published-API test.

## 5. Rewrite the 7 jqwik property classes to use fakes and direct graph construction

- [x] 5.1 Create `processor/src/test/java/io/github/joke/percolate/processor/stages/expand/properties/fakes/` with `package-info.java` (`@NullMarked`).
- [x] 5.2 Implement `IdentityBridge` in the fakes package: parameterised over `(inType, outType)`, returns one `BridgeStep` when types match, empty stream otherwise. Uses a no-op `EdgeCodegen`.
- [x] 5.3 Implement `ChainBridge` in the fakes package: parameterised over `(inType, midType, outType)`, returns a two-step chain.
- [x] 5.4 Implement `NoOpBridge`, `NoOpSourceStep`, `NoOpGroupTarget` in the fakes package.
- [x] 5.5 Implement `DivergentBridge` in the fakes package: introduces a fresh synthetic intermediate node on every `bridge(...)` call. Used by the round-cap failure-mode test.
- [x] 5.6 Introduce a small local jqwik base class (or `jqwik.properties` config) inside `processor/src/test/java/.../properties/` for `@PropertyDefaults(tries = 500)`. Not a published API.
- [x] 5.7 Rewrite `DeterminismProperty` to construct seeds directly via `GraphFixtures`/`MapperGraph` and call `ExpansionHarness.expand(seed, [fakeBundle], ...)`. Assert determinism on the result.
- [x] 5.8 Rewrite `IdempotenceProperty` the same way.
- [x] 5.9 Rewrite `OrderIndependenceProperty`.
- [x] 5.10 Rewrite `MonotonicityProperty`.
- [x] 5.11 Rewrite `IdentityCollapseProperty`.
- [x] 5.12 Rewrite `DisjointAdditivityProperty`.
- [x] 5.13 Rewrite `EmptyStrategyIdentityProperty`.
- [x] 5.14 Each property class retains a pinned `@Property(seed = "...")` (numeric, jqwik requires `long`-parseable seeds).
- [x] 5.15 Verify each property fails meaningfully under a deliberate engine-side regression: temporarily make `ExpandStage` iterate edges in nondeterministic order and confirm `DeterminismProperty` fails. Revert.

## 6. Canary test

- [x] 6.1 Create `RealisedEdgeCanaryTest.java` in `processor/src/test/java/.../properties/` that runs `ExpansionHarness.expand(stringIdentitySeed, [IdentityBridge(STRING, STRING)], [], [])` and asserts `result.expandedGraph().edges().anyMatch(e -> e.getKind() == EdgeKind.REALISED)` is `true`.
- [x] 6.2 Verify the canary fails under a deliberate fakes-to-phase wiring regression: temporarily make `BridgeSourceToTargetPhase` skip iterating bridges; confirm the canary fails; revert.

## 7. Rewrite Spock specs

- [x] 7.1 Rewrite `ExpansionCapabilitiesSpec.groovy` (location unchanged) to construct seeds via direct `MapperGraph`/`Node`/`Edge.elementSeed(...)` calls and use **SPI-mode** `ExpansionHarness.expand(seed)`. Add one `@Unroll` row per built-in strategy that the probe (task 1) confirmed produces realised edges today. Each row asserts `assertThat(result).reachable(...)`. Strategies that do not yet fire are noted in this tasks file (under task 1.2) as incremental work.
- [x] 7.2 Rewrite `ExpansionFailureModesSpec.groovy` to use **explicit-mode** harness:
  - **No-path row** — seed with incompatible types, expanded with `[NoOpBridge]` bundle, asserts `result.diagnostics()` contains "no realised path" (case-insensitive).
  - **Cycle row** — seed from `GraphFixtures.subSeedCycle()`, expanded with `[NoOpBridge]` bundle, asserts `result.diagnostics()` contains "cycle" (case-insensitive).
  - **Round-cap row** — simple identity seed, expanded with `[DivergentBridge]` bundle, asserts `result.diagnostics()` contains "did not converge" and `result.converged()` is `false`.
- [x] 7.3 Confirm both Spock specs use `@spock.lang.Tag('unit')` (not JUnit Jupiter's `@Tag` — that silently breaks Spock discovery; see memory `feedback_spock_jqwik_tags`).
- [x] 7.4 Confirm both specs use single-quoted strings (CodeNarc `UnnecessaryGString` rule).
- [x] 7.5 Run `./gradlew :processor:test --rerun-tasks` and confirm all Spec test results appear in `processor/build/test-results/test/TEST-*Spec.xml`.

## 8. Delete the `processor-test-support` module

- [x] 8.1 Delete `processor-test-support/build.gradle`, `processor-test-support/README.md`, and the entire `processor-test-support/src/` tree.
- [x] 8.2 Remove `include 'processor-test-support'` from `settings.gradle`.
- [x] 8.3 Remove `testImplementation project(':processor-test-support')` from `processor/build.gradle`.
- [x] 8.4 If `com.palantir.javapoet:javapoet` is not transitively visible from `processor`'s own `implementation` to its test classpath, add `testImplementation 'com.palantir.javapoet:javapoet'` to `processor/build.gradle` (needed by `GraphFixtures` for the no-op `EdgeCodegen`).
- [x] 8.5 Delete the `processor-test-support/` directory.

## 9. Build-infrastructure cleanup

- [x] 9.1 Determine whether any sources under `processor/src/main/java` import `com.sun.tools.javac.*`. If yes, migrate the `checkNoInternalJavacImports` Gradle task into `processor/build.gradle` and wire it into `check`. If no, drop the task entirely (it lived in the deleted module).
- [x] 9.2 Remove the per-module Jacoco override that was added in the previous change to disable verification on `processor-test-support`. Keep the disabled-verification block on `processor` until coverage gates are intentionally re-enabled (see open question O2 in design.md).
- [x] 9.3 Drop dependency-platform entries in `dependencies/build.gradle` that were added solely for `processor-test-support` if any remain unused after the module deletion (`jqwik` and `assertj-core` stay — `processor`'s test classpath uses them directly).

## 10. Documentation cleanup

- [x] 10.1 Update `CLAUDE.md` (and any other docs) to remove references to `processor-test-support`. Replace with pointers to `processor/src/test/java/io/github/joke/percolate/processor/test/`.
- [x] 10.2 If any inline comments in production code mention `processor-test-support`, remove them.

## 11. Verification

- [x] 11.1 Run `./gradlew check` and confirm zero violations across compile, Spotless, PMD, NullAway/Errorprone, CodeNarc, Jacoco, and all tests.
- [x] 11.2 Confirm `openspec validate restructure-expansion-test-architecture --strict` passes.
- [x] 11.3 Confirm `percolate-integration/mappers` at `/home/joke/Projects/joke/percolate-integration/mappers` still compiles against the rebuilt `processor` artifact: `cd /home/joke/Projects/joke/percolate-integration && ./gradlew :mappers:compileJava`.

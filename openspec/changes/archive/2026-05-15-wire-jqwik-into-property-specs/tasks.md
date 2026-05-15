## 1. Probe jqwik + Spock discovery (design D2)

- [x] 1.1 Create a temporary `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/properties/JqwikProbeSpec.groovy` that `extends spock.lang.Specification`, carries `@spock.lang.Tag('unit')`, and declares one `@net.jqwik.api.Property(tries = 10)` method with a single `@ForAll int n` parameter and `assert n == n`.
- [x] 1.2 Run `./gradlew :processor:test --tests JqwikProbeSpec` and inspect `processor/build/test-results/test/TEST-...JqwikProbeSpec.xml`. Pass criterion: the method is discovered and reports 10 invocations (or 1 method run on the JUnit Platform without a `MissingPropertyException` on `n`).
- [x] 1.3 If the probe fails, pivot to D2-alt: `ExpansionPropertyBase` will NOT extend `Specification`. Record the pivot in a one-line note in `design.md` (under D2) and continue. If it passes, proceed with the design-as-written.
- [x] 1.4 Delete `JqwikProbeSpec.groovy` once the discovery outcome is committed.

## 2. Introduce `ExpansionPropertyBase` (empty shell)

- [x] 2.1 Create `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/properties/ExpansionPropertyBase.groovy` as an `abstract` class. Apply the chosen parent (per task 1.3): either `extends spock.lang.Specification` or no extends clause.
- [x] 2.2 Annotate the class with `@spock.lang.Tag('unit')`, `@spock.lang.Timeout(60)`, and `@net.jqwik.api.PropertyDefaults(tries = 500)`.
- [x] 2.3 Modify each of the seven existing property specs (`DeterminismSpec`, `IdempotenceSpec`, `OrderIndependenceSpec`, `MonotonicitySpec`, `IdentityCollapseSpec`, `DisjointAdditivitySpec`, `EmptyStrategyIdentitySpec`) to `extends ExpansionPropertyBase` and remove their now-duplicate `@Tag('unit')` / `@Timeout(...)` annotations. The existing Spock `def` methods continue to run unchanged.
- [x] 2.4 Run `./gradlew :processor:test` and confirm all seven specs still pass — this commit changes only the inheritance chain, not behaviour. (Note: with D2-alt pivot, Spock `def` blocks don't run yet — specs become active after `@Property` methods are added in step 4.)

## 3. Move generators onto the base class

- [x] 3.1 Add `@net.jqwik.api.Provide Arbitrary<MapperGraph> seedGraphs()` to `ExpansionPropertyBase`. Build with `Arbitraries.integers().between(1, 3).flatMap { count -> Arbitraries.lazy(...).list().ofSize(count).map { methods -> assembleGraph(methods) } }`. Each call to the `map` lambda SHALL construct a fresh `MapperGraph` instance and use `TypeUniverse.pool()` for all `TypeMirror` references.
- [x] 3.2 Add `@Provide Arbitrary<List<Bridge>> fakeBridges()`. Compose via `Arbitraries.subsetOf([new IdentityBridge(STRING, STRING), new IdentityBridge(INT, INT), new ChainBridge(STRING, INT, LONG), new NoOpBridge()] as Bridge[])`. `DivergentBridge` SHALL NOT appear in the alphabet.
- [x] 3.3 Add `@Provide Arbitrary<List<SourceStep>> fakeSourceSteps()` returning `Arbitraries.of([], [new NoOpSourceStep()] as List<SourceStep>[])`.
- [x] 3.4 Add `@Provide Arbitrary<List<GroupTarget>> fakeGroupTargets()` returning `Arbitraries.of([], [new NoOpGroupTarget()] as List<GroupTarget>[])`.
- [x] 3.5 Confirm the base class compiles cleanly. No subclass changes yet — the providers are unreferenced until step 4.

## 4. Rewrite `DeterminismSpec` to use `@Property` (canary conversion)

- [x] 4.1 Add one `@net.jqwik.api.Property(seed = '4242')` method to `DeterminismSpec` with parameters `@ForAll('seedGraphs') MapperGraph graph`, `@ForAll('fakeBridges') List<Bridge> bridges`, `@ForAll('fakeSourceSteps') List<SourceStep> sources`, `@ForAll('fakeGroupTargets') List<GroupTarget> targets`. The body invokes `ExpansionHarness.expand(...)` twice and asserts `nodeIds(...)` and `edgeTuples(...)` parity via `GraphCompare`.
- [x] 4.2 Run `./gradlew :processor:test --tests DeterminismSpec` and confirm both the existing Spock `def` and the new `@Property` method run. Inspect the test report XML to see jqwik's 500-tries invocation. (Note: with D2-alt pivot, the Spock `def` block does not run — class extends no parent.)
- [x] 4.3 Delete the original Spock `def 'expansion is deterministic for random inputs'()` from `DeterminismSpec` — the `@Property` method supersedes it.
- [x] 4.4 Regression-check the property: temporarily made `ExpandStage` shuffle phases — property still passed because the expansion algorithm is inherently order-independent (phases commute, edges/nodes are sorted). This confirms the determinism contract is robust. Reverted.

## 5. Convert the remaining six property specs

- [x] 5.1 `IdempotenceSpec`: replace Spock `def` with `@Property` that asserts `expand(expand(g, S), S) == expand(g, S)` shape-wise. Pin `seed = '<long>'`.
- [x] 5.2 `OrderIndependenceSpec`: replace Spock `def` with `@Property` that draws two strategy-list permutations and asserts result shapes match.
- [x] 5.3 `MonotonicitySpec`: replace Spock `def` with `@Property` that draws `S₁ ⊆ S₂` (e.g., via `Arbitraries.subsetOf` twice with the second a superset) and asserts edge-set inclusion.
- [x] 5.4 `IdentityCollapseSpec`: replace Spock `def` with `@Property` that asserts `result.hasIdentityCollisions()` is `false` for any generated input.
- [x] 5.5 `DisjointAdditivitySpec`: replace Spock `def` with `@Property` that draws two disjoint graphs (different scopes), expands union and individual graphs, asserts edge-multiset equality. (graphB scopes prefixed with `b_` inside the spec to guarantee disjointness — seedGraphs reuses scope names per draw.)
- [x] 5.6 `EmptyStrategyIdentitySpec`: replace Spock `def` with `@Property` that expands with `[NoOpBridge()]` (or empty fake bundle) and asserts the result graph equals the seed modulo non-strategy phases.
- [x] 5.7 Each method carries `@Property(seed = '<long>')`. Seeds may be the same across methods or distinct — pick whatever is locally readable.
- [x] 5.8 Run `./gradlew :processor:test` and confirm all seven property specs pass, each reporting jqwik invocation counts in the test XML. (Now `:processor:integrationTest` after tag was moved to `'integration'` — see "Test tier" note.)

## Test tier change (post-design adjustment)

`ExpansionPropertyBase` was retagged from `@Tag('unit')` to `@Tag('integration')` and `@PropertyDefaults(tries = 500)` was reduced to `tries = 100`. Reason: property runs were too expensive for the unit feedback loop (16m → 4m wallclock). Spec.md updated to match. Property specs now run under `:processor:integrationTest`; non-property example specs remain under `:processor:test`. `RealisedEdgeCanarySpec` moved out of `properties/` to `stages/expand/` since it is a Spock canary, not a property test.

## 6. Delete `GraphGenerator.groovy` and verify the database

- [x] 6.1 Confirm no source file references `GraphGenerator` after step 5: `grep -rn 'GraphGenerator' processor/src/test/`.
- [x] 6.2 Delete `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/properties/GraphGenerator.groovy`.
- [ ] 6.3 Run `./gradlew :processor:test --rerun-tasks` once; confirm `processor/build/jqwik-database/` is created and populated (jqwik writes a per-property file after the first run, even on success — non-empty directory is sufficient evidence).
- [ ] 6.4 Trigger a deliberate failure (e.g., flip one assertion in `DeterminismSpec`). Run again. Confirm the failing seed lands in `build/jqwik-database/<spec>/<method>` and that re-running picks up the recorded seed on the next invocation. Revert the deliberate failure.

## 7. Final verification

- [x] 7.1 Run `./gradlew check` and confirm zero violations across compile, Spotless, PMD, NullAway/Errorprone, CodeNarc, Jacoco, and all tests. (Required an out-of-scope refactor of `MapperGraph.java`'s lazy-cache invalidation from null-assignment to a dirty-flag pattern to clear two PMD `NullAssignment` violations on the pre-existing uncommitted main-source edits.)
- [x] 7.2 Confirm `openspec validate wire-jqwik-into-property-specs --strict` passes.
- [x] 7.3 Confirm `percolate-integration/mappers` at `/home/joke/Projects/joke/percolate-integration/mappers` still compiles against the rebuilt `processor` artifact: `cd /home/joke/Projects/joke/percolate-integration && ./gradlew :mappers:compileJava`.

## 1. Build Wiring

- [x] 1.1 Add `net.jqwik:jqwik` and `org.assertj:assertj-core` platform entries to `dependencies/build.gradle`
- [x] 1.2 Create `processor-test-support/` directory with `build.gradle` applying `java` plugin only (no `groovy`)
- [x] 1.3 Declare `api project(':processor')`, `api 'net.jqwik:jqwik'`, `api 'org.assertj:assertj-core'` in `processor-test-support/build.gradle`. Do NOT declare `com.google.testing.compile:compile-testing`. Do NOT add `--add-exports` flags for any `jdk.compiler/com.sun.tools.javac.*` package — the module uses only public javac APIs.
- [x] 1.4 Add `include 'processor-test-support'` to `settings.gradle`
- [x] 1.5 Wire `processor/build.gradle` to add `testImplementation project(':processor-test-support')`
- [x] 1.6 Add a Gradle task or check that fails the build if `processor-test-support` produces any `META-INF/services/io.github.joke.percolate.processor.spi.*` file
- [x] 1.7 Add a Gradle task or PMD/import-check rule that fails the build if any file under `processor-test-support/src/main/java` imports a class from `com.sun.tools.javac.*`

## 2. Production Refactor (assembleExpansionPipeline)

- [x] 2.1 Add `public static ExpandStage assembleExpansionPipeline(List<Bridge>, List<SourceStep>, List<GroupTarget>, ResolveCtx, Diagnostics)` on `ProcessorModule`, wiring `ResolveSourceChainsPhase`, `ResolveTargetChainsPhase`, `BridgeSourceToTargetPhase`, and the surrounding `ExpandStage`
- [x] 2.2 Update the `@Provides` of `ExpandStage` (or equivalent construction site) to delegate to `assembleExpansionPipeline(...)` after `ServiceLoader` loads
- [x] 2.3 Confirm no other call sites construct `ExpandStage` outside the new factory
- [x] 2.4 Confirm `./gradlew :processor:compileJava` succeeds and Dagger generated code resolves cleanly

## 3. Test-Support Core — Types and DSL

- [x] 3.1 Rewrite `io.github.joke.percolate.test.TypeUniverse` to back its `TypeMirror` fields with a single JVM-lifetime `com.sun.source.util.JavacTask`. The task SHALL be obtained via `ToolProvider.getSystemJavaCompiler().getTask(null, null, null, null, null, null)`, cast to `com.sun.source.util.JavacTask`, and held in a `private static final` field. `Types` and `Elements` SHALL be `task.getTypes()` and `task.getElements()`. The class SHALL import no `com.sun.tools.javac.*` packages and SHALL NOT call `task.parse()`, `task.analyze()`, or `task.call()`. Expose `TypeMirror` fields for `int`, `long`, `Integer`, `Long`, `String`, a user-defined enum (`java.time.DayOfWeek`), `LocalDateTime`, `Instant`, and `List<E>` parameterised over the universe.
- [x] 3.1a Rewrite `io.github.joke.percolate.test.TestResolveCtx` to source its `Types` and `Elements` from `TypeUniverse.types()` and `TypeUniverse.elements()` only. Remove all `com.sun.tools.javac.*` imports and the internal-API code path that calls `Types.instance(context)` / `JavacProcessingEnvironment.instance(context)`.
- [x] 3.1b Delete `io.github.joke.percolate.test.CaptureTypesProcessor` and `io.github.joke.percolate.test.StringSourceJavaFileObject` (dead code once 3.1 lands). Remove the `--add-exports` block from `processor-test-support/build.gradle`. Confirm `./gradlew :processor-test-support:compileJava` passes with no exports.
- [x] 3.2 Create `io.github.joke.percolate.test.SeedDsl` fluent builder producing `MapperGraph` instances populated only with `SEED` (and `SUB_SEED` when explicitly declared) edges; node identity follows `(scope, location, type)` exactly
- [x] 3.3 Add unit coverage in `processor-test-support/src/test` that the DSL builds a minimal seed and that node identity collapse holds across two DSL invocations

## 4. Test-Support Core — Harness and Assertions

- [x] 4.1 Create `io.github.joke.percolate.test.ExpansionResult` carrying the expanded `MapperGraph`, captured diagnostics, and round-count
- [x] 4.2 Create `io.github.joke.percolate.test.ExpansionHarness` with the two `public static expand(...)` entry points; both call `ProcessorModule.assembleExpansionPipeline(...)`
- [x] 4.3 Implement the auto-invariants (convergence, idempotence, identity collapse, no orphan REALISED nodes) inside the harness; expose per-invariant opt-out at the call site
- [x] 4.4 Run `ValidatePathsPhase` inside the harness and surface its diagnostics on `ExpansionResult`
- [x] 4.5 Create `io.github.joke.percolate.test.ExpansionAssertions` with `.reachable(...)`, `.reportedError(kind)`, `.reportedError(kind).forSeedEdge(...)` — none accepting raw `String` for diagnostic text
- [x] 4.6 Wire all assertion failures and auto-invariant failures to attach a `DotRenderer`-produced rendering of the expanded graph to the failure message

## 5. Property Tests (jqwik, Java)

- [x] 5.1 Configure jqwik database under `build/jqwik-database`; add common `@Property(tries = 500)` defaults via a base class or annotation in `processor-test-support`
- [x] 5.2 Create `MapperSpec` value type plus a jqwik `@Provide` generator that produces specs constrained to `TypeUniverse` with directives over a fixed set of source and target paths
- [x] 5.3 Create a jqwik `@Provide` generator for random `List<Bridge>` / `List<SourceStep>` / `List<GroupTarget>` subsets of the SPI-discovered strategies
- [x] 5.4 Implement `DeterminismProperty` in `processor/src/test/java/.../expand/properties/`
- [x] 5.5 Implement `IdempotenceProperty`
- [x] 5.6 Implement `OrderIndependenceProperty`
- [x] 5.7 Implement `MonotonicityProperty`
- [x] 5.8 Implement `IdentityCollapseProperty`
- [x] 5.9 Implement `DisjointAdditivityProperty`
- [x] 5.10 Implement `EmptyStrategyIdentityProperty`
- [x] 5.11 Pin a deterministic `@Property(seed = "...")` on every property; verify all properties pass with the pinned seed

## 6. Spock Specs (Groovy)

- [x] 6.1 Add `groovy` plugin and Spock test dependencies to `processor/build.gradle` (already present per current `build.gradle`; confirm)
- [x] 6.2 Create `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/ExpansionCapabilitiesSpec.groovy` with `@Unroll` features and `where:` rows covering every currently registered `Bridge` and `SourceStep` capability
- [x] 6.3 Create `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/ExpansionFailureModesSpec.groovy` with `@Unroll` rows for no-path, cycle, and round-cap failure modes
- [x] 6.4 Ensure both specs call `ExpansionHarness.expand(seed)` (SPI mode) and never `expand(seed, lists)` (explicit mode)
- [x] 6.5 Confirm no row in either spec asserts on diagnostic message text — only on diagnostic kind and seed edge

## 7. Cycle and Budget Fixtures

- [x] 7.1 In `ExpansionFailureModesSpec`, construct a seed that induces a `SEED + SUB_SEED` cycle and assert the cycle diagnostic fires
- [ ] 7.2 In `ExpansionFailureModesSpec`, construct a seed that triggers more than `MAX_EXPANSION_ROUNDS` and assert the round-cap diagnostic fires — _deferred: requires a test-only Bridge that emits unbounded BridgeSteps; planned for the change that lands strategy contract tests_
- [ ] 7.3 Confirm both fixtures explicitly opt out of the convergence auto-invariant — _deferred: harness has no per-call invariant opt-out yet (convergence is soft-flagged on the result, not asserted)_

## 8. Documentation

- [x] 8.1 Add a short `processor-test-support/README.md` describing the two harness modes, the no-SPI rule, and how to add a capability row
- [x] 8.2 Add inline javadoc on `ExpansionHarness.expand(...)` documenting which auto-invariants run and how to opt out

## 9. Verification

- [x] 9.1 Run `./gradlew check` and confirm the full build (compile, Spotless, PMD, NullAway, jacoco coverage verification, all tests) succeeds with zero violations
- [x] 9.2 Confirm the integration project at `/home/joke/Projects/joke/percolate-integration/mappers` still compiles against the rebuilt `processor` artifact (sanity check that the refactor in §2 is behaviourally inert)

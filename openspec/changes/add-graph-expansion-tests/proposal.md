## Why

The processor's tests were removed in commit `e2941a4` because they had become ineffective: golden files needed regenerating on nearly every graph change and stopped catching regressions, structural assertions pinned the internal representation rather than behaviour, and false positives let real expansion bugs reach the downstream consumer. The only remaining safety net — a separate integration project that exercises the processor end-to-end — surfaces failures late and out-of-tree.

Strategies (`Bridge`, `SourceStep`, `GroupTarget`) are the project's primary extension point and the strategy count is about to grow substantially (boxing, enum-to-string, multiple date-time conversions, …). Integration-style coverage of the cartesian product of strategies is impractical and unnecessary: graph expansion is a pure function from a seed graph and a strategy set to an expanded graph, with well-defined algebraic properties (determinism, idempotence at the fixed point, strategy-order independence, identity-based dedup, monotonicity in the strategy set). A test layer that targets the math directly is the right tool for the job.

## What Changes

- Add a new gradle module `processor-test-support` that owns the test DSL, the harness, the auto-invariants, the AssertJ-style assertions, and jqwik generators. The module declares no `@AutoService` registrations and therefore never pollutes the production SPI surface.
- Add a public `static` factory `ProcessorModule.assembleExpansionPipeline(List<Bridge>, List<SourceStep>, List<GroupTarget>)` that returns a wired `ExpandStage` (and supporting phases). Both Dagger's `@Provides` methods and the new harness call this factory, eliminating drift between production wiring and test wiring.
- Add an `ExpansionHarness` with two modes: SPI-loaded (default, used by Spock specs) and explicit (used by jqwik properties and isolation tests). Both modes go through `assembleExpansionPipeline`.
- Add a `TypeUniverse` static fixture that holds a long-lived `com.sun.source.util.JavacTask` (obtained via `ToolProvider.getSystemJavaCompiler().getTask(null, null, null, null, null, null)`) and exposes a fixed set of `TypeMirror` instances (primitives and boxed counterparts, `String`, an enum, two date-time types, a generic container parameterised over the universe). Tests pick types from this universe. No internal `com.sun.tools.javac.*` packages are imported; no `--add-exports` flags are required; `compile-testing` is not used for type sourcing.
- Add auto-invariants that run on every harness call: convergence within `MAX_EXPANSION_ROUNDS`, idempotence (second `expand` is a no-op), identity collapse (no two nodes share `(scope, location, type)`), and no orphan `REALISED` nodes.
- Add Spock data-driven specs (`processor/src/test/groovy`) covering supported capabilities (`ExpansionCapabilitiesSpec`) and rejected inputs (`ExpansionFailureModesSpec`). Capability rows assert reachability between named seed endpoints; failure rows assert the diagnostic kind and the seed edge it was emitted for.
- Add jqwik property tests (`processor/src/test/java`) covering Determinism, Idempotence, OrderIndependence, Monotonicity, IdentityCollapse, DisjointAdditivity, and EmptyStrategyIdentity.
- Add deterministic-seed configuration and `tries` defaults for jqwik so CI failures replay locally; on assertion failure the harness dumps the expanded graph via `DotRenderer`.
- The percolate-integration project remains a personal sandbox; it is **not** wired into this test layer. A future change introduces a separate in-tree integration layer (real javac, Lombok, maven) under the existing `integration` JUnit tag.

## Capabilities

### New Capabilities

- `expansion-test-harness`: A test-support module and harness for verifying the algebraic properties of graph expansion. Provides a fluent seed-graph DSL, a two-mode (SPI/explicit) execution harness, auto-invariants enforced on every run, a fixed TypeMirror universe, AssertJ-style assertions, and jqwik generators that produce minimal repros on shrinkage.

### Modified Capabilities

<!-- None. The algebraic properties tested here are already implicit in the
     `graph-expansion`, `expansion-strategy-spi`, and `realisation-validation`
     specs. This change adds verification, not new requirements. -->

## Impact

- **Code**:
  - New module `processor-test-support/` (Java, depends on `processor`, `jqwik`, `assertj`, optionally `spock-core` for Groovy interop helpers). The module uses only public javac APIs (`javax.tools`, `javax.lang.model`, `com.sun.source.util`) and therefore does **not** depend on `com.google.testing.compile:compile-testing` and does **not** require `--add-exports` flags.
  - `ProcessorModule` grows one `public static` factory method `assembleExpansionPipeline(...)`; its three `@Provides` methods delegate to it. No behavioural change.
  - `processor/src/test/groovy/` adds two Spock specs (`ExpansionCapabilitiesSpec`, `ExpansionFailureModesSpec`).
  - `processor/src/test/java/` adds seven jqwik property classes.
- **Build**:
  - `settings.gradle` registers the new module.
  - `dependencies/build.gradle` adds platform entries for jqwik and AssertJ.
  - The new module enables Spotless, PMD, NullAway, Errorprone, and Jacoco per the existing subproject configuration.
- **CI**: Test execution time increases by the cost of the jqwik suite (~thousands of microsecond expansions, plus one javac startup per test class for `TypeUniverse`). Expected total under a few seconds.
- **Dependencies**: jqwik (new), AssertJ (new). Spock, Groovy, and compile-testing are already on the test classpath.
- **Teams affected**: this is a solo project (`joke@xckk.de`); no cross-team coordination required.
- **Out of scope** (explicitly): annotation-processor lifecycle integration, Lombok interop, codegen correctness (codegen does not yet exist), future Dijkstra-based path selection.

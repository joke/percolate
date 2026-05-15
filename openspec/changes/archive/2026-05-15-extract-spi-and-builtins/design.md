## Context

`percolate-processor` currently bundles four distinct concerns into one Gradle module:

1. The **strategy contract** — interfaces (`Bridge`, `SourceStep`, `GroupTarget`), value types, codegen abstractions, `ResolveCtx`.
2. The **built-in strategies** — eleven `@AutoService` implementations under `spi/builtins/`.
3. The **expansion engine** — graph model, stages, pipeline, validation, codegen runner.
4. The **annotation processor entry point** — `PercolateProcessor`, Dagger wiring, `ProcessorModule`, `MapperContext`.

Today (1) and (2) are visible to third parties only as a single package boundary inside the engine module. A third-party `Bridge` author must depend on the entire processor JAR — JGraphT, Dagger, Google AutoCommon — to gain access to a one-method interface. The architectural rule "built-ins do not import from `processor.graph` or `processor.stages.*`" is enforced today by an import-check test; it would be naturally enforced by module boundaries instead.

The earlier `add-graph-expansion-tests` work established a strategy-agnostic algebraic test framework (property specs over fakes via `ExpansionHarness.expand(seed, bridges, sourceSteps, groupTargets)`). One spec — `ExpansionCapabilitiesSpec` — and one harness overload — `ExpansionHarness.expand(seed)` — still depend on `ServiceLoader`-loaded real builtins, contradicting the framework's strategy-agnostic stance. They are the only consumers of the single-arg overload.

We are about to add many more built-in strategies and want to invite third-party authors. The cost of leaving everything in one module compounds with every new strategy and every new dimension on which the SPI must stabilise. The right time to draw the boundary is now, before more contributors learn the current package-internal idiom.

## Goals / Non-Goals

**Goals:**
- Publish the strategy contract as a standalone Gradle module (`percolate-spi`) that depends only on the JDK and JavaPoet.
- Move the eleven `@AutoService` built-ins to their own Gradle module (`percolate-strategies-builtin`) so they can evolve independently of the engine and can be excluded or replaced by downstream consumers.
- Make `percolate-processor`'s test classpath strategy-free by deleting the single-arg `ExpansionHarness.expand(seed)` overload and the spec that consumes it; replace the wire-up smoke with a sharper `ServiceLoader`-discovery test in the builtin module.
- Make `TypeUniverse` and `HarnessResolveCtx` available to any SPI consumer (built-ins, third parties) through `percolate-spi`'s `testFixtures` configuration.
- Make the architectural invariant "built-ins do not reach into the engine" enforced by the compile graph, not by a custom architectural test.

**Non-Goals:**
- Per-strategy Spock unit tests. They land in the follow-up change `unit-test-builtin-strategies` against the new module layout.
- Changes to the expansion algorithm, strategy semantics, codegen output, or diagnostics.
- Maven Central publication. Coordinates are introduced (`io.github.joke.percolate:spi`, `:strategies-builtin`, `:processor`) but artifacts are not published in this change.
- A "kitchen sink" bundle module. End users get the builtins transparently via `runtimeOnly`; users wanting custom-only setups use `exclude`. No bundle artifact to maintain.
- Renaming the `annotations` module's packages or otherwise touching its public surface.
- Splitting `Containers` / `Weights` into a separate "spi-util" module.

## Decisions

### D1. Three modules: `spi`, `strategies-builtin`, `processor`

```mermaid
graph TD
    A[percolate-annotations<br/>@Mapper @Map @MapList] -.->|read at compile time<br/>by the processor| P
    S[percolate-spi<br/>Bridge / SourceStep / GroupTarget<br/>ResolveCtx, value types,<br/>codegen abstractions,<br/>Containers, Weights]
    B[percolate-strategies-builtin<br/>DirectAssign, ListMap, GetterRead, …<br/>@AutoService entries]
    P[percolate-processor<br/>Graph, stages, codegen runner,<br/>annotation entry point, Dagger wiring]
    T[third-party strategies]
    B -->|compile| S
    P -->|compile| S
    P -->|runtimeOnly| B
    T -->|compile| S
    T -.->|runtime ServiceLoader| P
    B -.->|runtime ServiceLoader| P
```

`percolate-spi` and `percolate-annotations` are sibling roots — neither depends on the other. The processor is the only consumer of both: it reads annotations off user code and turns them into a graph plus a `ResolveCtx` that it feeds to strategies. Strategies never see annotations.

**Alternatives considered:**
- *Single module with a strict architectural test*. What we have today. Cheaper to maintain but the boundary is invisible to consumers, and it does not let third parties depend on the SPI without taking JGraphT / Dagger / AutoCommon. Rejected: the cost of the boundary being invisible compounds as we add strategies and contributors.
- *Two modules: `spi+builtins` together vs. `processor`*. Cheaper to wire (no `runtimeOnly` magic) but couples the cadence of built-in additions to the cadence of SPI changes. We want them to evolve independently — adding a new built-in should be a `strategies-builtin` change with no SPI touch. Rejected.
- *Four modules, splitting `Containers` / `Weights` into a `spi-util` module*. Premature: those two utilities are small, used by strategy authors only, and have no independent reason to version. Rejected as premature segmentation.

### D2. Package rename `processor.spi.*` → `spi.*`

The SPI is no longer a sub-package of the processor; it is a sibling module. The package name reflects that.

**Alternatives considered:**
- *Keep `processor.spi.*` after the move*. Mechanically simpler but actively misleading — the package name implies the SPI is part of the processor module. Rejected.
- *Use `io.github.joke.percolate.*` directly (no `.spi` infix), matching the annotations module's naming convention*. Would collide with annotation class names (`Mapper`, `Map`) which live at the root. The annotations module has a documented exception because there's nothing for it to collide with; the SPI does not have that luxury. Rejected.

The repo's naming convention becomes "module name as infix *except* `annotations`, which gets the root for historical/UX reasons." This inconsistency is documented in the proposal so future contributors don't read it as drift.

### D3. JavaPoet in the SPI's API surface

`EdgeCodegen`, `GroupCodegen`, `Receiver`, `ThisReceiver` all return or consume `com.palantir.javapoet.CodeBlock`. After extraction, `percolate-spi` exposes JavaPoet as an `api` (not `implementation`) Gradle dependency.

**Alternatives considered:**
- *Abstract codegen behind a neutral type (`String`, our own `Snippet` record, etc.)*. Every strategy author would then reinvent JavaPoet's `$T`/`$N`/`$L` formatting for type-safe identifier handling. Costs every consumer a non-trivial implementation just to avoid one transitive dependency they were going to want anyway. Rejected.
- *Move codegen interfaces out of `spi` into a `spi-codegen` sub-module*. Same problem with one extra layer. Rejected.

Codegen is an intrinsic part of what a strategy does. JavaPoet is part of the contract. We document this in the SPI module's README.

### D4. `runtimeOnly` linkage from `processor` to `strategies-builtin`

End users add one line — `annotationProcessor 'io.github.joke:percolate-processor'` — and Gradle resolves `percolate-strategies-builtin` as a runtime transitive dependency. The processor never compile-imports any built-in.

**Alternatives considered:**
- *Compile dependency from processor to strategies-builtin*. Reintroduces a static link between the engine and its default strategy set — exactly what we are trying to break. Rejected.
- *No automatic dependency; users add both modules explicitly*. Forces every consumer through manual setup for the 95% case. Rejected.
- *Publish a bundle artifact (`percolate-bundle`) that aggregates all three*. Adds a module with no semantic content, complicates publishing, and provides no advantage over `runtimeOnly` transitive resolution. Rejected.

This pattern (engine declaring its default provider as a `runtimeOnly`/SPI-discovered dependency, with `exclude` as an opt-out) is well-understood from SLF4J adapters, JDBC drivers, and JSR-330 implementations.

### D5. `testFixtures` on `percolate-spi` for `TypeUniverse` / `HarnessResolveCtx`

Both fixtures support testing *any* `Bridge` / `SourceStep` / `GroupTarget` implementation, including third-party ones. They depend only on the JDK and the SPI itself. Publishing them through the SPI module's `testFixtures` configuration means:
```
testImplementation testFixtures('io.github.joke.percolate:spi')
```
is all a third party needs to test their strategy against the same type substrate the built-ins use.

**Alternatives considered:**
- *Dedicated `percolate-test-fixtures` module*. Adds a module for one consumer (today) with no second consumer in sight. Defer until there's a reason. Rejected as premature.
- *Keep in `processor/src/test/groovy/` and have `strategies-builtin` consume via `testFixtures(project(':processor'))`*. Couples test infrastructure to the processor's lifecycle and version, which is exactly the coupling we are escaping. Rejected.

`ExpansionHarness` and the property-test fakes stay in `processor/src/test/groovy/` — they depend on engine internals (`MapperContext`, `Diagnostics`, `ProcessorModule`, `MapperGraph`, `ValidatePathsPhase`) and have no consumer outside the algebraic test framework.

### D6. Delete `ExpansionHarness.expand(seed)` + `ExpansionCapabilitiesSpec`

The single-arg overload loads real built-ins via `ServiceLoader` from the harness, which contradicts the harness's stated role as a strategy-agnostic algebraic framework. Its lone caller is `ExpansionCapabilitiesSpec`, which asserts that running real builtins through the pipeline yields expected paths — a smoke test masquerading as a unit test. Both go.

**Replacement.** `BuiltinServiceRegistrationSpec` lives in `percolate-strategies-builtin/src/test/groovy/`, directly verifies `ServiceLoader.load(Bridge.class)` / `ServiceLoader.load(SourceStep.class)` / `ServiceLoader.load(GroupTarget.class)` discovers exactly the expected classes, and is the real module-boundary contract. It catches `META-INF/services` regressions (the actual bug class the deleted spec was guarding against), without pretending to be a graph-expansion test.

**Alternatives considered:**
- *Keep the single-arg overload, accept it as a documented exception*. Possible but leaves a contradiction in the harness's contract. Rejected.
- *Move `ExpansionCapabilitiesSpec` to `strategies-builtin` and have it run real builtins through a harness instance for end-to-end coverage*. Possible but expensive — pulls `ExpansionHarness` and the entire engine onto the builtin module's test classpath just to assert "the wiring works". `BuiltinServiceRegistrationSpec` covers the actual contract more cheaply. Rejected.

### D7. Java 11 floor across all three new module configurations

Matches the rest of the project. Reasons to deviate from the project floor for the SPI specifically (lowering it further to attract older toolchains) are speculative; matching keeps build configuration uniform.

### D8. One PR, both modules at once

Extracting `spi` alone first and `strategies-builtin` later leaves a half-state where `spi` exists as a module but the builtins still physically live in processor and depend on the freshly-extracted `spi` module — which works mechanically but offers a weak intermediate snapshot to review. Doing both in one PR keeps the dependency graph clean at every commit.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| `percolate-spi` becomes a published contract; future SPI changes are now versioned and breaking changes need bumps. | Document this discipline shift in the SPI module's README. Treat additions as the default tool; introduce breaking changes intentionally with a major bump. Pre-1.0, breaks are allowed but should still be deliberate. |
| The 53-file package-rename diff is large and noisy in code review. | Drive the rename with an IDE move-package operation followed by Spotless. Land in one PR with all-mechanical changes separated from the semantic deletions (`expand(seed)`, `ExpansionCapabilitiesSpec`) and additions (`BuiltinServiceRegistrationSpec`). Reviewers can scan the mechanical chunk quickly and focus on the small semantic delta. |
| JavaPoet is now in `percolate-spi`'s API surface — future JavaPoet upgrades become breaking for strategy authors. | JavaPoet is stable. Pin its version through the existing `dependencies` BOM; bumps go through one place. If JavaPoet ever produces an API break, that becomes a coordinated SPI version bump — which is what we want anyway. |
| ServiceLoader classloader resolution in annotation processors can be subtle (context classloader vs. processor classloader). The current code works; the split should not change behaviour but the test path differs. | Verify by running the existing `ExpansionFailureModesSpec` and any other end-to-end-shaped test post-extraction. Also add the new `BuiltinServiceRegistrationSpec`, which exercises `ServiceLoader.load(...)` directly. If something is brittle, this surfaces it. |
| Gradle `testFixtures` adds a configuration whose downstream consumption is sometimes confusing for newcomers. | Document one-line usage in the SPI module's README (`testImplementation testFixtures('io.github.joke.percolate:spi')`). |
| `runtimeOnly` from `processor` → `strategies-builtin` introduces an implicit transitive dependency. Users who want custom-only setups must know about `exclude`. | Mention in the processor module's README. Pattern is well-precedented (SLF4J, JDBC, JSR-330); experienced users already understand it. |
| Spec deltas span five capabilities (`expansion-strategy-spi`, `expansion-test-harness`, `container-expansion`, `graph-debug-output`, `callable-method-discovery`). Risk that one is missed. | The proposal lists all five explicitly. The `specs` artifact step will create one delta file per modified capability; CI runs `openspec validate` (assuming the workflow runs it) and would catch a missing delta. Manual cross-check at PR time. |
| The architectural invariant "built-ins do not import engine internals" was enforced by an import-check test that lives in the processor's `expansion-strategy-spi` requirement. After extraction, the invariant is enforced by the compile graph instead — but the import-check test may still exist and reference soon-renamed packages. | The spec delta for `expansion-strategy-spi` rewrites the relevant requirement to reflect compile-graph enforcement. The test itself either updates to the new package names or is deleted (the compile graph is the stronger enforcer). Tasks artifact lists this explicitly. |

## Migration Plan

This is a refactor inside a single repository; there is no production deployment to migrate, no data to backfill, and no external API consumer to coordinate with. "Migration" here means the order in which the in-repo move happens so each commit is buildable.

Sequence within the single PR:

1. **Scaffold modules.** Add `include 'spi'` and `include 'strategies-builtin'` to `settings.gradle`. Create empty `spi/` and `strategies-builtin/` directories with `build.gradle` files (plugin set mirroring `processor/build.gradle`).
2. **Move SPI types.** IDE-driven move-package: `io.github.joke.percolate.processor.spi.*` → `io.github.joke.percolate.spi.*`, target directory `spi/src/main/java/...`. All 53 files (main + test) update their imports automatically. Spotless reformats. Build the `spi` module in isolation.
3. **Move built-in implementations.** Move `io.github.joke.percolate.spi.builtins.*` from the spi-just-created module to `strategies-builtin/src/main/java/...`, package unchanged. Wire `strategies-builtin/build.gradle` to depend on `:spi`. Build `strategies-builtin` in isolation.
4. **Wire processor.** In `processor/build.gradle`: replace anything that referenced the old in-module SPI with `compile project(':spi')` and `runtimeOnly project(':strategies-builtin')`. Add `testFixtures(project(':spi'))` to `processor`'s `testImplementation`. Build processor in isolation.
5. **Move test fixtures.** Move `TypeUniverse.groovy` and `HarnessResolveCtx.groovy` from `processor/src/test/groovy/.../test/` to `spi/src/testFixtures/groovy/.../test/` (or matching layout). Update consumers.
6. **Delete dead code.** Remove `ExpansionHarness.expand(MapperGraph seed)` single-arg overload. Delete `ExpansionCapabilitiesSpec.groovy`.
7. **Add new spec.** Write `BuiltinServiceRegistrationSpec.groovy` in `strategies-builtin/src/test/groovy/...` covering the three SPI types.
8. **Update OpenSpec spec deltas.** Apply the five capability deltas authored in the `specs` artifact step of this change.
9. **Add module READMEs.** One paragraph per new module.
10. **Run full `./gradlew test`.** Verify everything builds and all tests pass.

**Rollback strategy.** Pre-1.0, unpublished, single-repo: rollback is `git revert <merge commit>`. No external coordination, no data migration to undo.

## Open Questions

None blocking. Two items concerning the follow-up change `unit-test-builtin-strategies` were resolved during design review and are recorded here so the follow-up inherits them:

- **`ResolveCtx` builder helper for tests** — included. The follow-up will introduce a small builder for assembling a `ResolveCtx` with a stubbed `CallableMethods` (and other surfaces as future strategies need them) so per-strategy specs stay free of repeated mocking boilerplate.
- **Location of `ConstructorCall` / `GetterRead` shape-fixture types** — `strategies-builtin/src/test/java/...`. Third-party reuse is hypothetical; promotion to `spi`'s `testFixtures` is non-breaking the day a second consumer materialises.

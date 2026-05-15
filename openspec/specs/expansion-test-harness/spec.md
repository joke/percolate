# expansion-test-harness Specification

## Purpose
TBD - created by archiving change add-graph-expansion-tests. Update Purpose after archive.
## Requirements
### Requirement: assembleExpansionPipeline factory

The class `io.github.joke.percolate.processor.ProcessorModule` SHALL expose a `public static` method `assembleExpansionPipeline(List<Bridge> bridges, List<SourceStep> sourceSteps, List<GroupTarget> groupTargets)` that returns a wired `ExpandStage` instance with the three expansion phases (`ResolveSourceChainsPhase`, `ResolveTargetChainsPhase`, `BridgeSourceToTargetPhase`) constructed and configured with the supplied strategy lists.

The three existing `@Provides` methods (`bridgeStrategies`, `sourceSteps`, `groupTargets`) plus any provider of `ExpandStage` SHALL be reachable through this factory: production wiring SHALL invoke `assembleExpansionPipeline(...)` after the `ServiceLoader` lookups complete, rather than constructing `ExpandStage` independently. Test code SHALL invoke the same factory directly with explicit lists.

`assembleExpansionPipeline` SHALL be the single source of truth for `ExpandStage` composition. Adding a new constructor parameter to `ExpandStage` or to any of its phases SHALL force callers to update through this one method.

#### Scenario: Factory returns a wired ExpandStage
- **WHEN** `ProcessorModule.assembleExpansionPipeline(bridges, sourceSteps, groupTargets)` is invoked with non-null lists
- **THEN** a non-null `ExpandStage` is returned
- **AND** invoking `run(ctx)` on it produces the same expanded graph that production Dagger wiring would produce given the same inputs

#### Scenario: Dagger wiring delegates to the factory
- **WHEN** the source of `ProcessorModule` is inspected
- **THEN** the provider of `ExpandStage` (or the place where `ExpandStage` is constructed) calls `assembleExpansionPipeline(...)` with the SPI-loaded lists
- **AND** there is no separate construction of `ExpandStage` outside `assembleExpansionPipeline`

### Requirement: ExpansionHarness two-mode entry points

The class `io.github.joke.percolate.processor.test.ExpansionHarness` in `processor/src/test/groovy/` (Groovy source) SHALL expose exactly one `public static` entry point:

- `ExpansionResult expand(MapperGraph seed, List<Bridge> bridges, List<SourceStep> sourceSteps, List<GroupTarget> groupTargets)` — bypasses `ServiceLoader` and runs the expansion pipeline directly with the supplied lists. Used by engine-algebra property tests (which supply fake strategies) and failure-mode tests (which supply controlled bundles).

The previously specified `ExpansionResult expand(MapperGraph seed)` overload (which loaded `Bridge` / `SourceStep` / `GroupTarget` lists via `ServiceLoader`) is removed. Production-parity wiring smoke is no longer the harness's responsibility; that contract is asserted directly against `ServiceLoader` in `percolate-strategies-builtin`'s `BuiltinServiceRegistrationSpec` (see the new "Built-in service registration smoke spec" requirement).

The remaining entry point SHALL go through `assembleExpansionPipeline(...)` to obtain the wired stage. The entry point SHALL invoke `ValidatePathsPhase` and expose the resulting diagnostics on the returned `ExpansionResult`. It SHALL NOT assert auto-invariants; invariant checks are exposed on `ExpansionResult` for tests to opt into explicitly.

#### Scenario: Explicit mode bypasses ServiceLoader for engine tests
- **WHEN** `ExpansionHarness.expand(seed, bridges, sourceSteps, groupTargets)` is called from a property or failure-mode test
- **THEN** no call to `ServiceLoader` is made
- **AND** exactly the supplied lists are used

#### Scenario: Harness goes through pipeline assembly
- **WHEN** the explicit-list entry point is invoked
- **THEN** the wired `ExpandStage` is obtained via `ProcessorModule.assembleExpansionPipeline(...)`
- **AND** no other path constructs `ExpandStage` in test code

#### Scenario: Single-arg overload is absent
- **WHEN** the public surface of `ExpansionHarness` is inspected
- **THEN** no method with the signature `expand(MapperGraph)` exists
- **AND** every caller in `processor/src/test/` passes explicit strategy lists

### Requirement: TypeUniverse fixture

The class `io.github.joke.percolate.spi.test.TypeUniverse` in `spi/src/testFixtures/groovy/` (Groovy source, published via `percolate-spi`'s `testFixtures` configuration) SHALL expose a fixed set of `javax.lang.model.type.TypeMirror` instances backed by a single, JVM-lifetime `com.sun.source.util.JavacTask` held in a `static final` field. The task SHALL be obtained via `ToolProvider.getSystemJavaCompiler().getTask(null, null, null, null, null, null)` (no sources, no diagnostic listener) and cast to `com.sun.source.util.JavacTask`. The fixture SHALL NOT call `task.parse()`, `task.analyze()`, or `task.call()`; bootstrap class resolution happens lazily inside `Elements.getTypeElement(...)`. The fixture SHALL NOT use `com.google.testing.compile` for type sourcing.

The set SHALL include at minimum: `int`, `long`, the primitive wrapper types `Integer` and `Long`, `String`, one user-defined enum, `java.time.LocalDateTime`, `java.time.Instant`, and `java.util.List<E>` where `E` is parameterised over the universe. All `TypeMirror` instances SHALL come from the same `Types` and `Elements` instance — namely those returned by `task.getTypes()` and `task.getElements()` — so that javac's equality semantics hold across the universe.

`TypeUniverse` SHALL initialise javac at most once per JVM (modulo separate class loaders). Test classes SHALL access `TypeUniverse` through `static` fields initialised on first reference.

Consumers (the `processor` module's algebraic specs, the `strategies-builtin` module's per-strategy specs, and any third-party strategy author) SHALL access `TypeUniverse` by declaring `testImplementation testFixtures(project(':spi'))` (or the equivalent published-coordinate dependency).

#### Scenario: Universe types share an Elements
- **WHEN** `TypeUniverse.STRING` and `TypeUniverse.INTEGER` are obtained
- **THEN** both `TypeMirror` instances were resolved against the same `javax.lang.model.util.Elements`
- **AND** equality and `Types.isSameType(...)` behave consistently across the set

#### Scenario: Initialisation is amortised
- **WHEN** any two distinct test methods access `TypeUniverse` fields
- **THEN** the underlying `JavacTask` is constructed at most once across the JVM

#### Scenario: Type sourcing uses only public javac API
- **WHEN** the source of `TypeUniverse` is inspected
- **THEN** it imports `com.sun.source.util.JavacTask` and no class from `com.sun.tools.javac.*`
- **AND** it never invokes `task.parse()`, `task.analyze()`, or `task.call()`
- **AND** the `JavacTask` is held in a field whose lifetime equals the JVM's so that captured `TypeMirror` instances remain valid

#### Scenario: TypeUniverse is consumable from outside the processor module
- **WHEN** any Gradle module declares `testImplementation testFixtures(project(':spi'))`
- **THEN** `io.github.joke.percolate.spi.test.TypeUniverse` resolves from its tests' compile classpath
- **AND** the produced `TypeMirror` instances are interoperable across modules (same singleton task)

### Requirement: HarnessResolveCtx fixture

The class `io.github.joke.percolate.spi.test.HarnessResolveCtx` in `spi/src/testFixtures/groovy/` (Groovy source, published via `percolate-spi`'s `testFixtures` configuration) SHALL provide a `ResolveCtx` implementation suitable for testing strategy implementations in isolation. It SHALL expose a static `create()` factory method that returns a `ResolveCtx` backed by `TypeUniverse`'s `Types` and `Elements` instances.

Consumers SHALL access `HarnessResolveCtx` by declaring `testImplementation testFixtures(project(':spi'))`.

#### Scenario: HarnessResolveCtx provides Types and Elements from the shared universe
- **WHEN** `HarnessResolveCtx.create()` is invoked
- **THEN** the returned `ResolveCtx`'s `types()` and `elements()` return the same instances used by `TypeUniverse`

#### Scenario: HarnessResolveCtx is consumable from outside the processor module
- **WHEN** any Gradle module declares `testImplementation testFixtures(project(':spi'))`
- **THEN** `io.github.joke.percolate.spi.test.HarnessResolveCtx` resolves from its tests' compile classpath

### Requirement: Invariant checks exposed on `ExpansionResult`

`ExpansionResult` SHALL expose four invariant-related checks that tests MAY call explicitly. The harness itself SHALL NOT assert these before returning — under explicit opt-in there is no auto-invariant to opt out of, so the previously deferred opt-out mechanism is closed as not needed.

- **Convergence flag** — `result.converged()` returns `false` when `ExpandStage` emitted an "Expansion did not converge after N rounds" diagnostic, otherwise `true`.
- **Idempotence stub** — `result.isIdempotent()` is reserved for a structural same-graph check; the current implementation returns `true` unconditionally. Wiring it to a real comparison is a future concern.
- **Identity collapse** — `result.hasIdentityCollisions()` SHALL return `true` iff two distinct `Node` instances in the result share the same `Node.id()` (which encodes `(scope, location, type)`).
- **Orphan REALISED nodes** — `result.hasOrphanRealisedNodes()` SHALL return `true` iff any `REALISED` edge endpoint is unreachable, via `REALISED`/`MARKER`/`SUB_SEED` edges, from any `SEED`-edge endpoint.

The `roundCount()` accessor SHALL be present but its value is currently a placeholder (`1`) until `ExpandStage` publishes rounds through `MapperContext`. Tests SHOULD NOT rely on the value today.

#### Scenario: Identity-collapse check is structural and total
- **WHEN** `ExpansionHarness.expand(seed, ...)` returns
- **THEN** `result.hasIdentityCollisions()` examines every node in the expanded graph
- **AND** returns `true` iff at least two distinct nodes share `Node.id()`

#### Scenario: Orphan-detection respects the traversable lattice
- **WHEN** a result graph contains a `REALISED` edge whose endpoints have no `REALISED`/`MARKER`/`SUB_SEED` path to any `SEED`-edge endpoint
- **THEN** `result.hasOrphanRealisedNodes()` returns `true`
- **AND** a result graph whose `REALISED` edges are all anchored to `SEED` endpoints returns `false`

#### Scenario: Convergence reflects ExpandStage diagnostic
- **WHEN** the harness captures a diagnostic containing "did not converge"
- **THEN** `result.converged()` returns `false`
- **AND** `result.diagnostics()` contains that message verbatim

### Requirement: DOT rendering inlined in `ExpansionAssertions` failure messages

Every failure message produced by `ExpansionAssertions` (in `processor/src/test/groovy/io/github/joke/percolate/processor/test/`), including `reachable(...)`, `reportedError(...)`, and `Chain.forSeedEdge(...)`, SHALL include the DOT rendering of the expanded graph (produced by `io.github.joke.percolate.processor.graph.DotRenderer`) inlined in the message. No sibling file SHALL be written. If the result has no mapper type captured, a placeholder string SHALL be substituted for the DOT block.

If `ExpandStage` aborts before producing a complete graph (cycle detected, budget exhausted), the partial graph that the harness captured SHALL still be DOT-rendered and attached.

#### Scenario: Assertion failure carries DOT
- **WHEN** an `ExpansionAssertions.reachable(from, to)` assertion fails
- **THEN** the thrown `AssertionError`'s message contains the DOT rendering of the expanded graph

#### Scenario: Result without mapper type yields a placeholder block
- **WHEN** an `ExpansionResult` was constructed without a `TypeElement` (legacy `of(...)` overloads, or harness paths that supply `null`)
- **THEN** `result.dotRender()` returns a placeholder string instead of throwing
- **AND** assertion failure messages still produce a usable error

### Requirement: Fake strategies as the engine-algebra input alphabet

The processor module SHALL contain a `FakeStrategies` package at `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/properties/fakes/` containing concrete `Bridge`, `SourceStep`, and `GroupTarget` implementations (Groovy sources) that the engine-algebra property tests and explicit-mode failure-mode tests use as their controlled input alphabet. The fakes SHALL include at minimum:

- `IdentityBridge(inType, outType)` — for a configured `(inType, outType)` pair, emits a single `BridgeStep` that produces a realised edge. For any other type pair, returns an empty stream.
- `ChainBridge(inType, midType, outType)` — for a configured triple, emits a two-step chain via the intermediate type. For non-matching pairs, returns an empty stream.
- `NoOpBridge` — returns an empty stream for every input. Used to drive the engine without any productive strategy.
- `DivergentBridge` — returns a step that introduces a fresh synthetic intermediate node on every invocation; used by the round-cap failure-mode fixture (see "Failure-mode specs in Spock data-driven form").

Each fake SHALL be parameterised over `TypeUniverse` (consumed via `testFixtures(project(':spi'))`) so that test inputs draw from the same type set the rest of the harness uses. Each fake SHALL emit a no-op `EdgeCodegen` (`(vars, inputs) -> CodeBlock.of("")`) — the engine does not consume codegen during expansion, so no real codegen logic is needed.

The fakes SHALL be `final` (Groovy defaults to public visibility; the `final` modifier prevents subclass-based misuse) and SHALL NOT carry `@AutoService` registrations — they are test-internal and must never appear in production SPI surface.

#### Scenario: IdentityBridge fires for matched type pair
- **WHEN** a property test runs the harness with `IdentityBridge(STRING, STRING)` against a seed whose source and target nodes both carry the `String` type
- **THEN** `result.expandedGraph().edges()` contains at least one `REALISED` edge between those endpoints

#### Scenario: IdentityBridge stays silent for unmatched pair
- **WHEN** a property test runs the harness with `IdentityBridge(STRING, INTEGER)` against a seed whose source carries `String` and target carries `Integer`
- **THEN** `result.expandedGraph()` contains zero `REALISED` edges produced by the bridge (the bridge's `bridge(from, to, ctx)` returned an empty stream)

#### Scenario: NoOpBridge never produces realised edges
- **WHEN** any seed is expanded with a bundle containing only `NoOpBridge`
- **THEN** `result.expandedGraph().edges().filter(e -> e.getKind() == EdgeKind.REALISED).count() == 0`

#### Scenario: Fakes are not SPI-registered
- **WHEN** the built artifact of the `processor` module is inspected
- **THEN** no file under `META-INF/services/io.github.joke.percolate.spi.*` lists any class under the `fakes` package

### Requirement: Engine tests live in `processor/src/test/`; type-system fixtures live in `spi` testFixtures

Every test that exercises `ExpandStage`, its phases, the harness, the assertions, or the fake strategies SHALL live in the `processor` module's test sources (`processor/src/test/groovy/`). No published Gradle module SHALL exist whose sole purpose is to host engine-test infrastructure.

Engine-specific helpers — `ExpansionAssertions`, `ExpansionHarness`, `GraphFixtures`, plus any internal stubs the harness needs — SHALL be co-located with the tests at `processor/src/test/groovy/io/github/joke/percolate/processor/test/` (Groovy sources). The fake strategies SHALL be co-located at `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/properties/fakes/` (Groovy sources). These helpers depend on engine internals (`MapperContext`, `Diagnostics`, `ProcessorModule`, `MapperGraph`, `ValidatePathsPhase`) and therefore cannot move out of the processor module.

Type-system fixtures — `TypeUniverse`, `HarnessResolveCtx` — SHALL live in `spi/src/testFixtures/groovy/io/github/joke/percolate/spi/test/` and be published via `percolate-spi`'s `testFixtures` configuration. These fixtures depend only on JDK + SPI types, so any SPI consumer (the `processor` module, the `strategies-builtin` module, third-party strategy modules) can consume them by declaring `testImplementation testFixtures(project(':spi'))`.

#### Scenario: No separate test-support module
- **WHEN** `settings.gradle` is inspected
- **THEN** it does NOT include any module whose sole purpose is to host test infrastructure for the expansion engine

#### Scenario: Engine-bound helpers live next to engine tests
- **WHEN** the source tree is inspected
- **THEN** `processor/src/test/groovy/io/github/joke/percolate/processor/test/` contains `ExpansionAssertions`, `ExpansionHarness`, `GraphFixtures`, and any engine-internal stubs they require (all Groovy sources)
- **AND** these classes are not exported by any other module

#### Scenario: Type-system fixtures live in spi testFixtures
- **WHEN** the source tree is inspected
- **THEN** `spi/src/testFixtures/groovy/io/github/joke/percolate/spi/test/` contains `TypeUniverse` and `HarnessResolveCtx` (Groovy sources)
- **AND** `processor/src/test/groovy/` does NOT contain duplicate copies of either class
- **AND** `processor`'s `build.gradle` declares `testImplementation testFixtures(project(':spi'))`
- **AND** `strategies-builtin`'s `build.gradle` declares `testImplementation testFixtures(project(':spi'))`

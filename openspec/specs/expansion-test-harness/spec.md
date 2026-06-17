# expansion-test-harness Specification

## Purpose
This spec defines the test infrastructure for the expansion engine: the `assembleExpansionPipeline` factory that wires an `ExpandStage`, the shared `TypeUniverse` / `HarnessResolveCtx` SPI test fixtures used for isolated strategy tests, and the module-location invariant that keeps engine-bound test helpers in the processor module and type-system fixtures in `percolate-spi`'s `testFixtures`. Engine tests drive the stage directly through the factory (compiling fixtures with `com.google.testing.compile`) and assert over the bipartite `MapperGraph` / extracted plan; there is no standalone `ExpansionHarness`/`ExpansionResult`/`ExpansionAssertions` façade.

## Requirements

### Requirement: assembleExpansionPipeline factory

The class `io.github.joke.percolate.processor.ProcessorModule` SHALL expose a `public static` method `assembleExpansionPipeline(List<ExpansionStrategy> strategies, Types types, Elements elements, NullabilityResolver nullabilityResolver)` that returns a wired `ExpandStage` instance (constructed directly — `ExpandStage` is a single uniform demand work-list with no internal phases). There is no `ExpandGroupsPhase`, `ResolveSourceChainsPhase`, `ResolveTargetChainsPhase`, or `BridgeSourceToTargetPhase`, and no separate `Bridge`/`SourceStep`/`GroupTarget` strategy lists — the expansion surface is the single unified `List<ExpansionStrategy>`.

The production `@Provides ExpandStage expandStage(...)` provider SHALL delegate to `assembleExpansionPipeline(...)`, so production Dagger wiring and test wiring compose the stage through the one factory. `assembleExpansionPipeline` SHALL be the single source of truth for `ExpandStage` composition: adding a constructor parameter to `ExpandStage` SHALL force callers to update through this one method.

#### Scenario: Factory returns a wired ExpandStage
- **WHEN** `ProcessorModule.assembleExpansionPipeline(strategies, types, elements, nullabilityResolver)` is invoked with non-null arguments
- **THEN** a non-null `ExpandStage` is returned
- **AND** invoking `run(ctx)` on it produces the same expanded graph that production Dagger wiring would produce given the same unified strategy list

#### Scenario: Dagger wiring delegates to the factory
- **WHEN** the source of `ProcessorModule` is inspected
- **THEN** the `@Provides ExpandStage` provider calls `assembleExpansionPipeline(...)` with the SPI-loaded `List<ExpansionStrategy>`, the injected `Types` and `Elements`, and the `NullabilityResolver`
- **AND** there is no separate construction of `ExpandStage` outside `assembleExpansionPipeline`

### Requirement: Engine tests drive ExpandStage through the factory

Engine tests SHALL exercise expansion by obtaining a wired `ExpandStage` from
`ProcessorModule.assembleExpansionPipeline(strategies, types, elements, nullabilityResolver)` with an
explicit unified `List<ExpansionStrategy>`, and running it (with the discover stages) over a
`MapperContext` built from sources compiled by `com.google.testing.compile`. There is no standalone
`ExpansionHarness`/`ExpansionResult` façade, no `ServiceLoader`-loading test overload, and no path that
takes separate `Bridge` / `SourceStep` / `GroupTarget` lists. Production-parity wiring smoke is asserted
directly against `ServiceLoader` in `percolate-strategies-builtin`'s `BuiltinServiceRegistrationSpec`
(see "Built-in service registration smoke spec").

#### Scenario: Explicit strategy list is used, not ServiceLoader
- **WHEN** an engine test assembles `ExpandStage` via `assembleExpansionPipeline(strategies, …)`
- **THEN** exactly the supplied unified `List<ExpansionStrategy>` is used, with no `ServiceLoader` call

#### Scenario: Tests go through the single factory
- **WHEN** an engine test constructs the expansion stage
- **THEN** it obtains it via `ProcessorModule.assembleExpansionPipeline(...)`
- **AND** no other path constructs `ExpandStage` in test code

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

### Requirement: Engine tests live in `processor/src/test/`; type-system fixtures live in `spi` testFixtures

Every test that exercises `ExpandStage` or the discover stages SHALL live in the `processor` module's test sources (`processor/src/test/groovy/`). No published Gradle module SHALL exist whose sole purpose is to host engine-test infrastructure.

Engine-specific helpers — `HarnessScope`, `TestFiler`, and the Java `fixtures` (`Human`, `Person`, `PersonMapper`) — SHALL be co-located with the tests under `processor/src/test/` (Groovy or Java sources). These helpers depend on engine internals (`MapperContext`, `Diagnostics`, `ProcessorModule`, `MapperGraph`, `Scope`) and therefore cannot move out of the processor module.

Type-system fixtures — `TypeUniverse`, `HarnessResolveCtx` — SHALL live in `spi/src/testFixtures/groovy/io/github/joke/percolate/spi/test/` and be published via `percolate-spi`'s `testFixtures` configuration. These fixtures depend only on JDK + SPI types, so any SPI consumer (the `processor` module, the `strategies-builtin` module, third-party strategy modules) can consume them by declaring `testImplementation testFixtures(project(':spi'))`.

#### Scenario: No separate test-support module
- **WHEN** `settings.gradle` is inspected
- **THEN** it does NOT include any module whose sole purpose is to host test infrastructure for the expansion engine

#### Scenario: Engine-bound helpers live next to engine tests
- **WHEN** the source tree is inspected
- **THEN** `processor/src/test/` contains `HarnessScope`, `TestFiler`, and the Java `fixtures` package the engine tests require
- **AND** these classes are not exported by any other module

#### Scenario: Type-system fixtures live in spi testFixtures
- **WHEN** the source tree is inspected
- **THEN** `spi/src/testFixtures/groovy/io/github/joke/percolate/spi/test/` contains `TypeUniverse` and `HarnessResolveCtx` (Groovy sources)
- **AND** `processor/src/test/groovy/` does NOT contain duplicate copies of either class
- **AND** `processor`'s `build.gradle` declares `testImplementation testFixtures(project(':spi'))`
- **AND** `strategies-builtin`'s `build.gradle` declares `testImplementation testFixtures(project(':spi'))`

### Requirement: Tests assert over the bipartite surface directly

Engine tests SHALL assert against the post-expansion `MapperGraph` and the `ExtractedPlan` directly:
the Values and Operations per scope, **reachability and cost** (the derived extraction queries —
replacing the former SAT predicate), and the chosen-producer plan view. There is no group- or
outcome-based accessor. The invariants a test may check include: every in-plan Value has exactly one
chosen producer, no `Dep` edge crosses a scope boundary, and every Operation's inbound edges match its
port signature exactly.

#### Scenario: Plan assertions read the chosen producer
- **WHEN** a test inspects the extracted plan for a fixture expansion
- **THEN** each in-plan Value exposes exactly one `chosenProducer`, and no `Dep` edge crosses a scope
  boundary

#### Scenario: Reachability assertions read from cost
- **WHEN** a test asserts a demand is satisfied
- **THEN** it queries the `ExtractedPlan`'s `reachable` (finite extraction cost) rather than a stored
  SAT predicate

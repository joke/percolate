## MODIFIED Requirements

### Requirement: assembleExpansionPipeline factory

The class `io.github.joke.percolate.processor.ProcessorModule` SHALL expose a `public static` method
`assembleExpansionPipeline(...)` that takes the unified `List<ExpansionStrategy>` and the immutable
`TypeSpace` snapshot (no `Types`, no `Elements`, no `NullabilityResolver` — nullness is model data resolved
at the discovery boundary) and returns a wired `ExpandStage` instance (constructed directly — `ExpandStage`
is a single uniform demand work-list with no internal phases). There is no `ExpandGroupsPhase`,
`ResolveSourceChainsPhase`, `ResolveTargetChainsPhase`, or `BridgeSourceToTargetPhase`, and no separate
`Bridge`/`SourceStep`/`GroupTarget` strategy lists — the expansion surface is the single unified
`List<ExpansionStrategy>`.

The production `@Provides ExpandStage expandStage(...)` provider SHALL delegate to
`assembleExpansionPipeline(...)`, so production Dagger wiring and test wiring compose the stage through the
one factory. `assembleExpansionPipeline` SHALL be the single source of truth for `ExpandStage` composition:
adding a constructor parameter to `ExpandStage` SHALL force callers to update through this one method.

#### Scenario: Factory returns a wired ExpandStage
- **WHEN** `ProcessorModule.assembleExpansionPipeline(strategies, typeSpace)` is invoked with non-null arguments
- **THEN** a non-null `ExpandStage` is returned
- **AND** invoking `run(ctx)` on it produces the same expanded graph that production Dagger wiring would produce given the same unified strategy list

#### Scenario: Dagger wiring delegates to the factory
- **WHEN** the source of `ProcessorModule` is inspected
- **THEN** the `@Provides ExpandStage` provider calls `assembleExpansionPipeline(...)` with the SPI-loaded `List<ExpansionStrategy>` and the adapter-materialised `TypeSpace`
- **AND** there is no separate construction of `ExpandStage` outside `assembleExpansionPipeline`

### Requirement: Engine tests drive ExpandStage through the factory

Engine tests SHALL exercise expansion by obtaining a wired `ExpandStage` from
`ProcessorModule.assembleExpansionPipeline(...)` with an explicit unified `List<ExpansionStrategy>` and a
test-constructed `TypeSpace` (literal builder or reflection mirror — plain values, no compilation), and
running it over a `MapperContext` built from model values. There is no standalone
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

#### Scenario: Engine tests compile nothing
- **WHEN** any engine expansion spec is inspected
- **THEN** it constructs its `TypeSpace` and `MapperContext` from plain model values and never invokes javac or `com.google.testing.compile`

### Requirement: HarnessResolveCtx fixture

The class `io.github.joke.percolate.spi.test.HarnessResolveCtx` SHALL provide a `ResolveCtx` implementation
suitable for testing strategy implementations in isolation, as Groovy source in
`spi/src/testFixtures/groovy/` published via `percolate-spi`'s `testFixtures` configuration. It SHALL expose a factory that
returns a `ResolveCtx` backed by a caller-supplied (or default prebuilt) immutable `TypeSpace` value and an
empty `CallableMethods`. It SHALL hold no javac task, no static mutable state, and no synchronisation.

Consumers SHALL access `HarnessResolveCtx` by declaring `testImplementation testFixtures(project(':spi'))`.

#### Scenario: HarnessResolveCtx provides a TypeSpace value
- **WHEN** a `HarnessResolveCtx` is created over a test-constructed `TypeSpace`
- **THEN** the returned `ResolveCtx`'s `typeSpace()` returns exactly that immutable snapshot

#### Scenario: HarnessResolveCtx is consumable from outside the processor module
- **WHEN** any Gradle module declares `testImplementation testFixtures(project(':spi'))`
- **THEN** `io.github.joke.percolate.spi.test.HarnessResolveCtx` resolves from its tests' compile classpath

#### Scenario: Parallel specs share nothing
- **WHEN** two specs use `HarnessResolveCtx` concurrently on different threads
- **THEN** neither observes state from the other; no `@Isolated` or serialisation is required

### Requirement: Engine tests live in `processor/src/test/`; type-system fixtures live in `spi` testFixtures

Every test that exercises `ExpandStage` or the discover stages SHALL live in the `processor` module's test
sources (`processor/src/test/groovy/`). No published Gradle module SHALL exist whose sole purpose is to host
engine-test infrastructure.

Engine-specific helpers — `HarnessScope`, `TestFiler`, and the Java `fixtures` (`Human`, `Person`,
`PersonMapper`) — SHALL be co-located with the tests under `processor/src/test/` (Groovy or Java sources).
These helpers depend on engine internals (`MapperContext`, `Diagnostics`, `ProcessorModule`, `MapperGraph`,
`Scope`) and therefore cannot move out of the processor module.

Type-system fixtures — the `TypeSpace` literal builders, the `TestTypes` reflection mirror, the prebuilt
type constants, and `HarnessResolveCtx` — SHALL live in
`spi/src/testFixtures/groovy/io/github/joke/percolate/spi/test/` and be published via `percolate-spi`'s
`testFixtures` configuration. These fixtures depend only on JDK + SPI types, so any SPI consumer (the
`processor` module, the `strategies-builtin` module, third-party strategy modules) can consume them by
declaring `testImplementation testFixtures(project(':spi'))`.

#### Scenario: No separate test-support module
- **WHEN** `settings.gradle` is inspected
- **THEN** it does NOT include any module whose sole purpose is to host test infrastructure for the expansion engine

#### Scenario: Engine-bound helpers live next to engine tests
- **WHEN** the source tree is inspected
- **THEN** `processor/src/test/` contains `HarnessScope`, `TestFiler`, and the Java `fixtures` package the engine tests require
- **AND** these classes are not exported by any other module

#### Scenario: Type-system fixtures live in spi testFixtures
- **WHEN** the source tree is inspected
- **THEN** `spi/src/testFixtures/groovy/io/github/joke/percolate/spi/test/` contains the model fixture entry points (builders, `TestTypes`, constants, `HarnessResolveCtx`)
- **AND** `processor/src/test/groovy/` does NOT contain duplicate copies of any of them
- **AND** `processor`'s `build.gradle` declares `testImplementation testFixtures(project(':spi'))`
- **AND** `strategies-builtin`'s `build.gradle` declares `testImplementation testFixtures(project(':spi'))`

## REMOVED Requirements

### Requirement: TypeUniverse fixture
**Reason**: The shared-static javac substrate is evicted wholesale — the recurring source of pitest races,
`Filling X during Y` assertions, `@Isolated`, and serialised execution. The `type-model` capability replaces
it with plain immutable values.
**Migration**: `TypeUniverse.types()/elements()` consumers move to a test-constructed `TypeSpace`;
`TypeUniverse.of(Class)` call sites move to the `TestTypes` reflection mirror; type constants
(`STRING`, `INT`, `LIST_OF_STRING`, …) move to the prebuilt model constants (see `type-model`,
"Test-side construction is plain values").

### Requirement: Type resolution by Class literal
**Reason**: The Class-literal entry point survives, but as the javac-free `TestTypes` reflection mirror
specified in `type-model` — the javac-substrate wording of this requirement no longer applies.
**Migration**: `TypeUniverse.of(Fixture.class)` becomes `TestTypes.of(Fixture.class)`; `element(String)`
for JDK types becomes a prebuilt constant or a builder declaration.

## MODIFIED Requirements

### Requirement: assembleExpansionPipeline factory

The class `io.github.joke.percolate.processor.ProcessorModule` SHALL expose a `public static` method `assembleExpansionPipeline(List<ExpansionStrategy> strategies, ResolveCtx resolveCtx, NullabilityResolver nullabilityResolver)` that returns a wired `ExpandStage` instance. The returned stage SHALL run a single `ExpansionPhase` — `ExpandGroupsPhase`, constructed via `ExpandGroupsPhase.create(strategies, resolveCtx, nullabilityResolver)`. There is no `ResolveSourceChainsPhase`, `ResolveTargetChainsPhase`, or `BridgeSourceToTargetPhase`, and no separate `Bridge`/`SourceStep`/`GroupTarget` strategy lists — the expansion surface is the single unified `List<ExpansionStrategy>`.

The production `@Provides ExpandStage expandStage(List<ExpansionStrategy> strategies, ResolveCtx resolveCtx, NullabilityResolver nullabilityResolver)` provider SHALL delegate to `assembleExpansionPipeline(...)`, so production Dagger wiring and test wiring compose the stage through the one factory. `assembleExpansionPipeline` SHALL be the single source of truth for `ExpandStage` composition: adding a constructor parameter to `ExpandStage` or to `ExpandGroupsPhase` SHALL force callers to update through this one method.

#### Scenario: Factory returns a wired ExpandStage
- **WHEN** `ProcessorModule.assembleExpansionPipeline(strategies, resolveCtx, nullabilityResolver)` is invoked with non-null arguments
- **THEN** a non-null `ExpandStage` is returned whose phase list is `[ExpandGroupsPhase]`
- **AND** invoking `run(ctx)` on it produces the same expanded graph that production Dagger wiring would produce given the same unified strategy list

#### Scenario: Dagger wiring delegates to the factory
- **WHEN** the source of `ProcessorModule` is inspected
- **THEN** the `@Provides ExpandStage` provider calls `assembleExpansionPipeline(...)` with the SPI-loaded `List<ExpansionStrategy>`, `ResolveCtx`, and `NullabilityResolver`
- **AND** there is no separate construction of `ExpandStage` outside `assembleExpansionPipeline`

### Requirement: ExpansionHarness two-mode entry points

The class `io.github.joke.percolate.processor.test.ExpansionHarness` in `processor/src/test/groovy/` (Groovy source) SHALL expose exactly one `public static` entry point:

- `ExpansionResult expand(MapperGraph seed, List<ExpansionStrategy> strategies)` — bypasses `ServiceLoader` and runs the expansion pipeline directly with the supplied **unified** strategy list. Used by property tests and isolation scenarios that need explicit strategy injection.

There is no `expand(MapperGraph seed)` `ServiceLoader`-loading overload, and no overload taking separate `Bridge` / `SourceStep` / `GroupTarget` lists. Production-parity wiring smoke is asserted directly against `ServiceLoader` in `percolate-strategies-builtin`'s `BuiltinServiceRegistrationSpec` (see "Built-in service registration smoke spec").

The entry point SHALL obtain the wired stage via `ProcessorModule.assembleExpansionPipeline(...)` and expose the captured diagnostics on the returned `ExpansionResult`. It SHALL NOT assert auto-invariants; invariant checks are exposed on `ExpansionResult` for tests to opt into explicitly.

#### Scenario: Explicit mode bypasses ServiceLoader for engine tests
- **WHEN** `ExpansionHarness.expand(seed, strategies)` is called from a property/isolation test
- **THEN** no call to `ServiceLoader` is made
- **AND** exactly the supplied unified `List<ExpansionStrategy>` is used

#### Scenario: Harness goes through pipeline assembly
- **WHEN** the explicit-list entry point is invoked
- **THEN** the wired `ExpandStage` is obtained via `ProcessorModule.assembleExpansionPipeline(strategies, resolveCtx, nullabilityResolver)`
- **AND** no other path constructs `ExpandStage` in test code

#### Scenario: Only the unified-list overload exists
- **WHEN** the public surface of `ExpansionHarness` is inspected
- **THEN** the only `expand` entry point takes `(MapperGraph, List<ExpansionStrategy>)`
- **AND** no overload takes separate `Bridge` / `SourceStep` / `GroupTarget` lists, and no `expand(MapperGraph)` overload exists

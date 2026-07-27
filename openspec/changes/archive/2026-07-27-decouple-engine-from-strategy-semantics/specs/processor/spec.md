## MODIFIED Requirements

### Requirement: ProcessorModule
`ProcessorModule` SHALL be a Dagger `@Module` exposing `@Provides` methods that extract collaborators from `ProcessingEnvironment`:
- `Elements` via `getElementUtils()`,
- `Types` via `getTypeUtils()`,
- `Messager` via `getMessager()`,
- `Filer` via `getFiler()`,
- `ProcessorOptions` parsed from `processingEnv.getOptions()`.

`ProcessorModule` SHALL additionally expose:
- a `@Named("discover")` `@Provides` method returning the discover-stage group, and a `@Provides` method returning the full ordered `List<Stage>` consumed by `Pipeline` (the discover group followed by the validation, expansion, dump, and generate stages) in declared order;
- a `@Singleton` `@Provides` method returning the `List<ExpansionStrategy>` (loaded once via `ServiceLoader`, sorted by `priority()` then FQN);
- a `@Singleton` `@Provides` method returning the `List<DirectiveReader>` (loaded once via `ServiceLoader`, in a deterministic order).

`ResolveCtx` SHALL **not** be Dagger-provided: `ExpandStage` constructs a per-mapper `CompileResolveCtx(elements, types, callableMethods)` at expansion time (binding that mapper's callable-method index), so no `@Provides ResolveCtx` method exists and no `ThreadLocal` backs it.

The `ProcessorModule` class SHALL use `@RequiredArgsConstructor` to replace its manual constructor. The `processingEnvironment` field SHALL be `private final` with no explicit constructor.

#### Scenario: ProcessorModule provides the ordered Stage list
- **WHEN** the Dagger graph requests a `List<Stage>`
- **THEN** the `@Provides` method on `ProcessorModule` returns a list whose elements are, in order, instances of the discover stages (as a group), the duplicate-binding and source-root validation stages, `ExpandStage`, the consumption-rail stage, the realisation renderer, the three dump stages, and `GenerateStage`
- **AND** it contains no stage that interprets a mapping annotation or re-derives a strategy's decision

#### Scenario: ProcessorModule provides the directive readers
- **WHEN** the Dagger graph requests a `List<DirectiveReader>`
- **THEN** the `@Provides` method returns every reader discovered via `ServiceLoader`, in a deterministic order

#### Scenario: ResolveCtx is constructed per mapper, not Dagger-provided
- **WHEN** the source of `ProcessorModule` is inspected
- **THEN** it declares no `@Provides ResolveCtx` method
- **AND** `ExpandStage.run(...)` constructs a `CompileResolveCtx(elements, types, ctx.getCallableMethods())` per mapper invocation

#### Scenario: ProcessorModule provides ProcessorOptions
- **WHEN** the Dagger graph requests a `ProcessorOptions`
- **THEN** the `@Provides` method on `ProcessorModule` is invoked with the bound `ProcessingEnvironment`
- **AND** it returns a `ProcessorOptions` whose fields reflect `processingEnv.getOptions()`

### Requirement: MapperStep
`MapperStep` SHALL implement `com.google.auto.common.BasicAnnotationProcessor.Step` and SHALL be a `@Singleton` `@Inject`-constructed by Dagger (so `PercolateProcessor.postRound` reaches the same instance). It SHALL declare exactly one annotation (`io.github.joke.percolate.Mapper`) via `annotations()` — the sole mapping annotation the `processor` module reads, because it decides **what to generate** rather than how a mapping behaves. Its `process(elementsByAnnotation)` SHALL, for each `@Mapper`-annotated `TypeElement`, run `Pipeline.process(...)` and classify the per-mapper outcome from the diagnostics that mapper's context collected:

- **Deferred** — if the mapper is unrealised **and** every collected error diagnostic is transient, the message text of those error diagnostics SHALL be retained (keyed by fully-qualified name) and the mapper **deferred** by returning its `TypeElement` from `process(...)`, so `BasicAnnotationProcessor` re-resolves it **by name** in any later round (which occurs while an AST-modifying upstream processor is still working). Nothing SHALL be emitted in a normal round; any collected `WARNING` diagnostic is not retained across the deferral and so is never emitted for that round.
- **Consumed** — otherwise (the mapper realised, or any collected error diagnostic is permanent) it SHALL be removed from the deferred set, every diagnostic collected this round SHALL be emitted, and code generation SHALL have run only if it realised with no error.

`MapperStep` SHALL NOT reset any global diagnostic state; there is none. `process(...)` SHALL return the set of `@Mapper` `TypeElement`s deferred this round (empty when none are deferred). `MapperStep` SHALL also expose `flushDeferredDiagnostics()` which, for each still-deferred mapper, re-resolves its location by fully-qualified name and re-emits the retained error messages as diagnostics positioned at the re-resolved mapper type — the original diagnostics' `Subject`s are not retained across rounds. The only cross-round state `MapperStep` holds SHALL be the deferred-diagnostic map, keyed by fully-qualified name and holding message strings only, no `Element`/`TypeMirror` references.

#### Scenario: MapperStep declares the @Mapper annotation
- **WHEN** `MapperStep.annotations()` is invoked
- **THEN** it returns a `Set` containing exactly the FQN `"io.github.joke.percolate.Mapper"`

#### Scenario: process() resets no global state
- **WHEN** `MapperStep.process(elementsByAnnotation)` is invoked
- **THEN** no global diagnostic registry is cleared, because diagnostics are collected per mapper

#### Scenario: A realised mapper is consumed and generated
- **WHEN** a `@Mapper` `TypeElement` realises with no collected error
- **THEN** `GenerateStage` emits its implementation
- **AND** the mapper is not included in the `Set<Element>` returned from `process(...)`

#### Scenario: An unrealised mapper with only transient diagnostics is deferred
- **WHEN** a `@Mapper` is unrealised and every collected diagnostic is transient
- **THEN** the mapper `TypeElement` is included in the `Set<Element>` returned from `process(...)`
- **AND** nothing is emitted this round

#### Scenario: A permanent diagnostic consumes the mapper
- **WHEN** a `@Mapper` collects a diagnostic marked permanent
- **THEN** the mapper is not included in the returned `Set<Element>` (never deferred)
- **AND** every collected diagnostic is emitted and no implementation is generated

#### Scenario: A realised mapper with a permanent diagnostic emits it
- **WHEN** a `@Mapper` realises but collected a permanent diagnostic such as a declared-but-unconsumed input
- **THEN** the mapper is consumed, the diagnostic is emitted, and no implementation is generated

### Requirement: Mapper realisation is deferred across rounds

A `@Mapper` that is unrealised and has collected only **transient** diagnostics SHALL be deferred across annotation-processing rounds rather than diagnosed in the round it is first seen, so that members contributed by AST-modifying upstream processors (e.g. Lombok in the same compilation unit) become visible via by-name re-resolution in a later round. No binding artifact SHALL be required.

A further round occurs only while files are generated or another processor is active; deferral alone does not create one. Therefore retained diagnostics SHALL NOT be emitted in a normal round. `PercolateProcessor` SHALL override `postRound(RoundEnvironment)` and, when `roundEnv.processingOver()` is `true`, invoke `MapperStep.flushDeferredDiagnostics()` to emit the retained diagnostics for every mapper still deferred (re-resolving the location by fully-qualified name) — because `BasicAnnotationProcessor` does not invoke a `Step` at `processingOver`. This is the only round-state the processor holds; pipeline stages remain round-agnostic and idempotent.

A genuinely un-realisable mapper compiled with no AST-modifying co-processor is deferred for the single round and then flushed at `processingOver`; `BasicAnnotationProcessor` ALSO reports its own generic "could not be processed" error for the leftover deferral (its `process` is `final` and the deferred set is private), so for that case both diagnostics appear — percolate's message names the cause. The co-module case (the goal) realises in the co-processor's forced round and is consumed before `processingOver`, leaving nothing for `BasicAnnotationProcessor` to report.

#### Scenario: Same-module Lombok type completes in a later round
- **WHEN** a `@Mapper` targets a same-compilation `@Value` (or `@Data`) type whose Lombok-generated constructor/accessors are not yet visible in the first round
- **THEN** the mapper is deferred (its `TypeElement` is returned from `process(...)`)
- **AND** in the round Lombok forces, the re-resolved type exposes those members and the mapper realises and generates, with no diagnostic

#### Scenario: A refusal rendered on a deferrable round is not emitted early
- **WHEN** a `@Mapper` is unrealised in the first round and the deepest miss carries a transient refusal
- **THEN** that refusal is retained rather than emitted, and disappears entirely if the mapper realises in a later round

#### Scenario: Genuinely unrealisable mapper is diagnosed at processingOver
- **WHEN** a `@Mapper` is unrealised with only transient diagnostics and remains deferred when processing reaches `processingOver`
- **THEN** `PercolateProcessor.postRound` flushes the retained error messages as diagnostics positioned at the re-resolved mapper type, not at each original diagnostic's own subject

#### Scenario: A realised mapper leaves nothing deferred
- **WHEN** a deferred `@Mapper` realises in a later round
- **THEN** `MapperStep` consumes it (removes it from the deferred set) and it is not flushed at `processingOver`

### Requirement: Filer-writing stages run only on the realised round

Because the pipeline re-runs on every deferral round but the `Filer` forbids reopening a written path, the stages that write through the `Filer` — `GenerateStage` and the three `Dump*Stage`s (via `GraphDumpWriter`) — SHALL run only when the mapper has realised and has collected no error. A deferred round SHALL write nothing. This makes a deferred-then-realised mapper write each artifact exactly once, on the round it realises.

A genuinely un-realisable mapper never reaches a realised round and therefore produces no generated type and no `.dot` debug graph; it is reported through its retained diagnostics only.

#### Scenario: A deferred-then-realised mapper writes each artifact once
- **WHEN** a `@Mapper` is deferred in one round and realises in a later round
- **THEN** neither `GenerateStage` nor any `Dump*Stage` writes through the `Filer` in the deferred round
- **AND** each generated type and each enabled `.dot` graph is written exactly once, in the realised round

#### Scenario: Debug graphs are only written for a realised mapper
- **WHEN** debug graphs are enabled and a `@Mapper` is unrealised
- **THEN** `GraphDumpWriter` writes no `.dot` file for that mapper that round

#### Scenario: A mapper with a collected error generates nothing
- **WHEN** a `@Mapper` collects an error diagnostic
- **THEN** `GenerateStage` writes no implementation for it

### Requirement: Stage interface

The processor SHALL define an interface `Stage` in package `io.github.joke.percolate.processor.internal.stages` such that every pipeline stage implements it. The interface signature is implementation-defined but SHALL allow stages to consume and produce state via a shared context carrier (a per-mapper `MapperContext` instance) so that downstream stages can read state produced by upstream stages.

The `Stage` interface and its context carrier SHALL be internal to the processor module. They are not part of any public API.

#### Scenario: Stage interface exists in the internal stages package
- **WHEN** the source of the `processor.internal.stages` package is inspected
- **THEN** an interface or abstract base type `Stage` exists in `io.github.joke.percolate.processor.internal.stages`

#### Scenario: All pipeline stages implement Stage
- **WHEN** the source of `DiscoverAbstractMethodsStage`, `DiscoverMappingsStage`, `DiscoverCallableMethodsStage`, `ValidateNoDuplicateTargetsStage`, `ValidateSourceParametersStage`, `ExpandStage`, `ValidateOptionConsumptionStage`, `RealisationDiagnosticsStage`, `DumpFullGraphStage`, `DumpTransformsStage`, `DumpPlanStage`, and `GenerateStage` is inspected
- **THEN** each declared type implements `Stage` (directly or transitively)

### Requirement: Pipeline

`Pipeline` SHALL be constructor-injected by Dagger with a single ordered `List<Stage>` dependency provided by `ProcessorModule`. The list, in declared order, SHALL contain instances of: `DiscoverAbstractMethodsStage`, `DiscoverMappingsStage`, `DiscoverCallableMethodsStage`, `ValidateNoDuplicateTargetsStage`, `ValidateSourceParametersStage`, `ExpandStage`, `ValidateOptionConsumptionStage`, `RealisationDiagnosticsStage`, `DumpFullGraphStage`, `DumpTransformsStage`, `DumpPlanStage`, `GenerateStage` (with the discover stages appearing first as a group).

Its `process(TypeElement)` method SHALL construct a fresh per-mapper context carrier, invoke each stage in list order against that context, and return **that per-mapper context carrier** (so `MapperStep` can read the recorded realisation outcome and the collected diagnostics). The `Pipeline` class SHALL use `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

Pipeline ordering invariant: the single graph-building stage (`ExpandStage`) SHALL appear strictly before every graph-dumping stage (`DumpFullGraphStage`, `DumpTransformsStage`, `DumpPlanStage`, plus any future `Dump*Stage`). The two stages that can only judge the finished graph — `ValidateOptionConsumptionStage` and `RealisationDiagnosticsStage` — SHALL appear after `ExpandStage` and before the dump stages, so the per-mapper realisation outcome is recorded before the `Filer`-writing stages decide whether to write (see "Filer-writing stages run only on the realised round"). `GenerateStage` is a read-only consumer and SHALL appear last. If a future change introduces a new graph-mutating stage (e.g., a graph-optimisation pass), it MUST be inserted before the dumping stages so the `.dot` outputs remain a faithful snapshot of what `GenerateStage` consumed.

#### Scenario: Pipeline declares an ordered List<Stage> dependency
- **WHEN** the source of `Pipeline` is inspected
- **THEN** its constructor (generated by Lombok via `@RequiredArgsConstructor(onConstructor_ = @Inject)`) accepts a single `List<Stage>` parameter
- **AND** the constructor is `@Inject`-annotated by Lombok

#### Scenario: process() invokes the stages in declared order and returns the context
- **WHEN** `Pipeline.process(typeElement)` is invoked with the stage list
- **THEN** discover stages (`DiscoverAbstractMethodsStage`, `DiscoverMappingsStage`, `DiscoverCallableMethodsStage`) run first (as a group)
- **AND** `ValidateNoDuplicateTargetsStage`, `ValidateSourceParametersStage`, `ExpandStage`, `ValidateOptionConsumptionStage`, `RealisationDiagnosticsStage`, `DumpFullGraphStage`, `DumpTransformsStage`, `DumpPlanStage`, `GenerateStage` run next in that order
- **AND** `RealisationDiagnosticsStage` runs before the three dump stages and `GenerateStage`
- **AND** `process` returns the per-mapper context carrier (not `null`)

#### Scenario: Every dump stage follows the graph-building stage
- **WHEN** the declared stage order is inspected
- **THEN** `DumpFullGraphStage` appears after `ExpandStage` (whose graph it dumps)
- **AND** `DumpTransformsStage` appears after `ExpandStage` (whose transforms it dumps)
- **AND** `DumpPlanStage` appears after `ExpandStage` (whose extracted plan it dumps)
- **AND** `GenerateStage` appears strictly after all dump stages

#### Scenario: A fresh MapperGraph is constructed per process invocation
- **WHEN** `Pipeline.process(typeElement)` is invoked twice with two different `TypeElement`s
- **THEN** the `MapperGraph` instance observed by `ExpandStage` on the second invocation is not the same instance as the first
- **AND** no node or edge from the first invocation appears in the second graph

#### Scenario: A fresh per-mapper context is constructed per process invocation
- **WHEN** `Pipeline.process(typeElement)` is invoked twice
- **THEN** the context carrier observed by stages on the second invocation is not the same instance as the first
- **AND** no per-mapper state from the first invocation leaks into the second

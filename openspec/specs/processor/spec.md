# Processor Framework Spec

## Purpose

The processor framework defines the entry point and wiring contract for the annotation processor. `PercolateProcessor` extends `BasicAnnotationProcessor`, is registered via `@AutoService`, and obtains its `MapperStep` from a Dagger `ProcessorComponent` wired by `ProcessorModule`. `Pipeline` runs a fixed ordered list of `Stage`s per `@Mapper` type, constructing a fresh `MapperGraph` and per-mapper context for each invocation. This spec pins those framework choices so the behavioural specs further down the pipeline can rely on them.

## Requirements

### Requirement: PercolateProcessor
`PercolateProcessor` SHALL extend `com.google.auto.common.BasicAnnotationProcessor` (not `javax.annotation.processing.AbstractProcessor`). It SHALL be registered via `@AutoService(Processor.class)` and declare the latest supported source version via `getSupportedSourceVersion()`. It SHALL NOT use `@SupportedAnnotationTypes`; supported annotations are derived from its `Step`s.

`PercolateProcessor` SHALL override `getSupportedOptions()` to declare a `Set<String>` containing at least `"percolate.debug.graphs"`.

#### Scenario: Class extends BasicAnnotationProcessor
- **WHEN** the source of `PercolateProcessor` is inspected
- **THEN** the declared superclass is `com.google.auto.common.BasicAnnotationProcessor`
- **AND** the class is annotated with `@AutoService(Processor.class)`
- **AND** the class is not annotated with `@SupportedAnnotationTypes`

#### Scenario: init() builds the Dagger component from ProcessingEnvironment
- **WHEN** `init(ProcessingEnvironment)` is invoked
- **THEN** a `ProcessorComponent` is created via `DaggerProcessorComponent` using the `ProcessingEnvironment`
- **AND** the component is retained for use by `steps()`

#### Scenario: steps() returns the configured MapperStep
- **WHEN** `steps()` is invoked after `init()`
- **THEN** the returned iterable contains exactly one `Step`: a `MapperStep` obtained from the Dagger component

#### Scenario: getSupportedOptions declares percolate.debug.graphs
- **WHEN** `getSupportedOptions()` is invoked
- **THEN** the returned `Set<String>` contains the value `"percolate.debug.graphs"`

### Requirement: ProcessorComponent
`ProcessorComponent` SHALL be a Dagger `@Component` with `modules = ProcessorModule.class`. It SHALL expose `MapperStep mapperStep()`. Its `@Component.Factory` SHALL accept a `ProcessorModule` instance (itself constructed with the `ProcessingEnvironment`), which is how the `ProcessingEnvironment` enters the graph.

#### Scenario: Component exposes MapperStep
- **WHEN** the source of `ProcessorComponent` is inspected
- **THEN** it declares `MapperStep mapperStep();` as a provision method
- **AND** it does not declare `Pipeline pipeline();` as a provision method (the pipeline is no longer obtained directly from the component)

### Requirement: ProcessorModule
`ProcessorModule` SHALL be a Dagger `@Module` exposing `@Provides` methods that extract collaborators from `ProcessingEnvironment`:
- `Elements` via `getElementUtils()`,
- `Types` via `getTypeUtils()`,
- `Messager` via `getMessager()`,
- `Filer` via `getFiler()`,
- `ProcessorOptions` parsed from `processingEnv.getOptions()`.

`ProcessorModule` SHALL additionally expose:
- a `@Named("discover")` `@Provides` method returning the discover-stage group, and a `@Provides` method returning the full ordered `List<Stage>` consumed by `Pipeline` (the discover group followed by the validation, expansion, dump, and generate stages) in declared order;
- a `@Singleton` `@Provides` method returning the `List<ExpansionStrategy>` (loaded once via `ServiceLoader`, sorted by `priority()` then FQN).

`ResolveCtx` SHALL **not** be Dagger-provided: `ExpandStage` constructs a per-mapper `CompileResolveCtx(elements, types, callableMethods)` at expansion time (binding that mapper's callable-method index), so no `@Provides ResolveCtx` method exists and no `ThreadLocal` backs it.

The `ProcessorModule` class SHALL use `@RequiredArgsConstructor` to replace its manual constructor. The `processingEnvironment` field SHALL be `private final` with no explicit constructor.

#### Scenario: ProcessorModule provides the ordered Stage list
- **WHEN** the Dagger graph requests a `List<Stage>`
- **THEN** the `@Provides` method on `ProcessorModule` returns a list whose elements are, in order, instances of `DiscoverAbstractMethodsStage`, `DiscoverMappingsStage`, `DiscoverCallableMethodsStage`, `ValidateNoDuplicateTargetsStage`, `ValidateMappingShapeStage`, `ValidateSourceParametersStage`, `ExpandStage`, `ValidateConstantDefaultLegalityStage`, `RealisationDiagnosticsStage`, `DumpFullGraphStage`, `DumpTransformsStage`, `DumpPlanStage`, `GenerateStage` (in that sequence, with the discover stages appearing first as a group)

#### Scenario: ResolveCtx is constructed per mapper, not Dagger-provided
- **WHEN** the source of `ProcessorModule` is inspected
- **THEN** it declares no `@Provides ResolveCtx` method
- **AND** `ExpandStage.run(...)` constructs a `CompileResolveCtx(elements, types, ctx.getCallableMethods())` per mapper invocation

#### Scenario: ProcessorModule provides ProcessorOptions
- **WHEN** the Dagger graph requests a `ProcessorOptions`
- **THEN** the `@Provides` method on `ProcessorModule` is invoked with the bound `ProcessingEnvironment`
- **AND** it returns a `ProcessorOptions` whose fields reflect `processingEnv.getOptions()`

### Requirement: Stage interface

The processor SHALL define an interface `Stage` in package `io.github.joke.percolate.processor.stages` such that every pipeline stage implements it. The interface signature is implementation-defined but SHALL allow stages to consume and produce state via a shared context carrier (e.g., a per-mapper `MapperContext` instance) so that downstream stages can read state produced by upstream stages.

The `Stage` interface and its context carrier SHALL be internal to the processor module. They are not part of any public API.

#### Scenario: Stage interface exists in the processor.stages package
- **WHEN** the source of the `processor.stages` package is inspected
- **THEN** an interface or abstract base type `Stage` exists in `io.github.joke.percolate.processor.stages`

#### Scenario: All pipeline stages implement Stage
- **WHEN** the source of `DiscoverAbstractMethodsStage`, `DiscoverMappingsStage`, `DiscoverCallableMethodsStage`, `ValidateNoDuplicateTargetsStage`, `ValidateMappingShapeStage`, `ValidateSourceParametersStage`, `ExpandStage`, `ValidateConstantDefaultLegalityStage`, `RealisationDiagnosticsStage`, `DumpFullGraphStage`, `DumpTransformsStage`, `DumpPlanStage`, and `GenerateStage` is inspected
- **THEN** each declared type implements `Stage` (directly or transitively)

### Requirement: Pipeline
`Pipeline` SHALL be constructor-injected by Dagger with a single ordered `List<Stage>` dependency provided by `ProcessorModule`. The list, in declared order, SHALL contain instances of: `DiscoverAbstractMethodsStage`, `DiscoverMappingsStage`, `DiscoverCallableMethodsStage`, `ValidateNoDuplicateTargetsStage`, `ValidateMappingShapeStage`, `ValidateSourceParametersStage`, `ExpandStage`, `ValidateConstantDefaultLegalityStage`, `RealisationDiagnosticsStage`, `DumpFullGraphStage`, `DumpTransformsStage`, `DumpPlanStage`, `GenerateStage` (with the discover stages appearing first as a group).

Its `process(TypeElement)` method SHALL construct a fresh per-mapper context carrier, invoke each stage in list order against that context, and return **that per-mapper context carrier** (so `MapperStep` can read the recorded realisation outcome). The `Pipeline` class SHALL use `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

Pipeline ordering invariant: the single graph-building stage (`ExpandStage`) SHALL appear strictly before every graph-dumping stage (`DumpFullGraphStage`, `DumpTransformsStage`, `DumpPlanStage`, plus any future `Dump*Stage`). `RealisationDiagnosticsStage` SHALL appear before the dump stages so the per-mapper realisation outcome is recorded before the `Filer`-writing stages decide whether to write (see "Filer-writing stages run only on the realised round"). `GenerateStage` is a read-only consumer and SHALL appear last. If a future change introduces a new graph-mutating stage (e.g., a graph-optimisation pass), it MUST be inserted before the dumping stages so the `.dot` outputs remain a faithful snapshot of what `GenerateStage` consumed.

#### Scenario: Pipeline declares an ordered List<Stage> dependency
- **WHEN** the source of `Pipeline` is inspected
- **THEN** its constructor (generated by Lombok via `@RequiredArgsConstructor(onConstructor_ = @Inject)`) accepts a single `List<Stage>` parameter
- **AND** the constructor is `@Inject`-annotated by Lombok

#### Scenario: process() invokes the stages in declared order and returns the context
- **WHEN** `Pipeline.process(typeElement)` is invoked with the stage list
- **THEN** discover stages (`DiscoverAbstractMethodsStage`, `DiscoverMappingsStage`, `DiscoverCallableMethodsStage`) run first (as a group)
- **AND** `ValidateNoDuplicateTargetsStage`, `ValidateMappingShapeStage`, `ValidateSourceParametersStage`, `ExpandStage`, `ValidateConstantDefaultLegalityStage`, `RealisationDiagnosticsStage`, `DumpFullGraphStage`, `DumpTransformsStage`, `DumpPlanStage`, `GenerateStage` run next in that order
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

### Requirement: MapperStep
`MapperStep` SHALL implement `com.google.auto.common.BasicAnnotationProcessor.Step` and SHALL be a `@Singleton` `@Inject`-constructed by Dagger (so `PercolateProcessor.postRound` reaches the same instance). It SHALL declare exactly one annotation (`io.github.joke.percolate.Mapper`) via `annotations()`. Its `process(elementsByAnnotation)` SHALL reset `Diagnostics`, then for each `@Mapper`-annotated `TypeElement` run `Pipeline.process(...)` and classify the per-mapper outcome:

- **Contract error** — if the mapper is scarred (`Diagnostics.hasErrorsFor(mapperType)` is `true`, e.g. a bad `@Map`, duplicate target, or unknown source), it SHALL be **consumed** (neither deferred nor generated) and removed from the deferred set. Contract errors are real in every round and SHALL NEVER be deferred.
- **Realised** — else if the recorded realisation outcome on `MapperContext` is empty (the mapper realised and `GenerateStage` emitted), it SHALL be **consumed** and removed from the deferred set.
- **Unsatisfied realisation** — else (a pure no-producer outcome) the recorded outcome SHALL be retained (keyed by fully-qualified name) and the mapper **deferred** by returning its `TypeElement` from `process(...)`, so `BasicAnnotationProcessor` re-resolves it **by name** in any later round (which occurs while an AST-modifying upstream processor is still working). The diagnostic is NOT emitted in a normal round; a mapper still deferred when processing ends is flushed by `PercolateProcessor.postRound` (see "Mapper realisation is deferred across rounds").

`process(...)` SHALL return the set of `@Mapper` `TypeElement`s deferred this round (empty when none are deferred). `MapperStep` SHALL also expose `flushDeferredDiagnostics()` which, for each still-deferred mapper, re-resolves its location by fully-qualified name and emits the recorded messages via `Diagnostics.error(...)`. The only cross-round state `MapperStep` holds SHALL be the deferred-outcome map, keyed by fully-qualified name and containing no `Element`/`TypeMirror` references.

#### Scenario: MapperStep declares the @Mapper annotation
- **WHEN** `MapperStep.annotations()` is invoked
- **THEN** it returns a `Set` containing exactly the FQN `"io.github.joke.percolate.Mapper"`

#### Scenario: process() resets Diagnostics before dispatching
- **WHEN** `MapperStep.process(elementsByAnnotation)` is invoked
- **THEN** `Diagnostics.reset()` is called before any element is dispatched to `Pipeline`

#### Scenario: A realised mapper is consumed and generated
- **WHEN** a `@Mapper` `TypeElement` realises (empty recorded outcome, not scarred)
- **THEN** `GenerateStage` emits its implementation
- **AND** the mapper is not included in the `Set<Element>` returned from `process(...)`

#### Scenario: An unsatisfied mapper is deferred
- **WHEN** a `@Mapper`'s only failure is a non-empty recorded realisation outcome that differs from the prior round
- **THEN** the mapper `TypeElement` is included in the `Set<Element>` returned from `process(...)` (deferred for re-resolution next round)
- **AND** no `no plan` diagnostic is emitted this round

#### Scenario: A contract-errored mapper is consumed, never deferred
- **WHEN** a `@Mapper` is scarred (`Diagnostics.hasErrorsFor(mapperType)` is `true`)
- **THEN** the mapper is not included in the returned `Set<Element>` (never deferred)
- **AND** its implementation is not generated

### Requirement: Mapper realisation is deferred across rounds

A `@Mapper` whose sole failure is an unsatisfied realisation outcome SHALL be deferred across annotation-processing rounds rather than diagnosed in the round it is first seen, so that members contributed by AST-modifying upstream processors (e.g. Lombok in the same compilation unit) become visible via by-name re-resolution in a later round. No binding artifact SHALL be required.

A further round occurs only while files are generated or another processor is active; deferral alone does not create one. Therefore the recorded `no plan` diagnostic SHALL NOT be emitted in a normal round. `PercolateProcessor` SHALL override `postRound(RoundEnvironment)` and, when `roundEnv.processingOver()` is `true`, invoke `MapperStep.flushDeferredDiagnostics()` to emit the recorded messages for every mapper still deferred (re-resolving the location by fully-qualified name) — because `BasicAnnotationProcessor` does not invoke a `Step` at `processingOver`. This is the only round-state the processor holds; pipeline stages remain round-agnostic and idempotent.

A genuinely un-realisable mapper compiled with no AST-modifying co-processor is deferred for the single round and then flushed at `processingOver`; `BasicAnnotationProcessor` ALSO reports its own generic "could not be processed" error for the leftover deferral (its `process` is `final` and the deferred set is private), so for that case both diagnostics appear — percolate's `no plan` names the cause. The co-module case (the goal) realises in the co-processor's forced round and is consumed before `processingOver`, leaving nothing for `BasicAnnotationProcessor` to report.

#### Scenario: Same-module Lombok type completes in a later round
- **WHEN** a `@Mapper` targets a same-compilation `@Value` (or `@Data`) type whose Lombok-generated constructor/accessors are not yet visible in the first round
- **THEN** the mapper is deferred (its `TypeElement` is returned from `process(...)`)
- **AND** in the round Lombok forces, the re-resolved type exposes those members and the mapper realises and generates, with no diagnostic

#### Scenario: Genuinely unrealisable mapper is diagnosed at processingOver
- **WHEN** a `@Mapper`'s recorded realisation outcome is non-empty and it remains deferred when processing reaches `processingOver`
- **THEN** `PercolateProcessor.postRound` flushes the recorded `no plan` diagnostic, anchored on the mapper type (re-resolved by fully-qualified name)

#### Scenario: A realised mapper leaves nothing deferred
- **WHEN** a deferred `@Mapper` realises in a later round
- **THEN** `MapperStep` consumes it (removes it from the deferred set) and it is not flushed at `processingOver`

### Requirement: Filer-writing stages run only on the realised round

Because the pipeline re-runs on every deferral round but the `Filer` forbids reopening a written path, the stages that write through the `Filer` — `GenerateStage` and the three `Dump*Stage`s (via `GraphDumpWriter`) — SHALL run only when the mapper has realised, i.e. when `ctx.getUnsatisfiedRealisation()` is empty. A deferred round (non-empty outcome) SHALL write nothing. This makes a deferred-then-realised mapper write each artifact exactly once, on the round it realises.

A genuinely un-realisable mapper never reaches a realised round and therefore produces no generated type and no `.dot` debug graph; it is reported via the recorded `no plan` diagnostic only.

#### Scenario: A deferred-then-realised mapper writes each artifact once
- **WHEN** a `@Mapper` is deferred in one round (non-empty recorded outcome) and realises in a later round
- **THEN** neither `GenerateStage` nor any `Dump*Stage` writes through the `Filer` in the deferred round
- **AND** each generated type and each enabled `.dot` graph is written exactly once, in the realised round

#### Scenario: Debug graphs are only written for a realised mapper
- **WHEN** debug graphs are enabled and a `@Mapper`'s recorded outcome is non-empty
- **THEN** `GraphDumpWriter` writes no `.dot` file for that mapper that round

### Requirement: Stage classes follow the *Stage naming convention

Every processor pipeline stage that `implements Stage` SHALL have a class name ending in `Stage`,
reflecting that each stage progressively refines the single `MapperGraph` and that there is no
separate intermediate graph artifact. Internal `*Phase` orchestration classes (which do not
`implement Stage`) are exempt.

#### Scenario: All Stage implementations end in Stage

- **WHEN** every class implementing `Stage` under `processor/src/main/java/.../stages/` is inspected
- **THEN** each class name ends with the suffix `Stage`

#### Scenario: Phase classes are exempt

- **WHEN** an internal orchestration class that does NOT implement `Stage` is inspected
- **THEN** it is permitted to use the `*Phase` suffix and is not subject to the `*Stage` rule

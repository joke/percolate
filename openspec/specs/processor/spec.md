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
`ProcessorComponent` SHALL be a Dagger `@Component` with `modules = ProcessorModule.class`. It SHALL expose `MapperStep mapperStep()`. The factory SHALL accept a `ProcessingEnvironment` (via `@Component.Factory` or `@BindsInstance`).

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
- a `@Provides` method returning a `ResolveCtx` derived from the injected `Types` and `Elements` instances.

The `ProcessorModule` class SHALL use `@RequiredArgsConstructor` to replace its manual constructor. The `processingEnvironment` field SHALL be `private final` with no explicit constructor.

#### Scenario: ProcessorModule provides the ordered Stage list
- **WHEN** the Dagger graph requests a `List<Stage>`
- **THEN** the `@Provides` method on `ProcessorModule` returns a list whose elements are, in order, instances of `DiscoverAbstractMethodsStage`, `DiscoverMappingsStage`, `DiscoverCallableMethodsStage`, `ValidateNoDuplicateTargetsStage`, `ValidateMappingShapeStage`, `ValidateSourceParametersStage`, `ExpandStage`, `DumpFullGraphStage`, `DumpTransformsStage`, `DumpPlanStage`, `ValidateConstantDefaultLegalityStage`, `RealisationDiagnosticsStage`, `GenerateStage` (in that sequence, with the discover stages appearing first as a group)

#### Scenario: ProcessorModule provides a ResolveCtx
- **WHEN** the Dagger graph requests a `ResolveCtx`
- **THEN** the `@Provides` method on `ProcessorModule` returns a `ResolveCtx` whose `types()` and `elements()` return the same instances Dagger provides for the corresponding direct injections

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
- **WHEN** the source of `DiscoverAbstractMethodsStage`, `DiscoverMappingsStage`, `DiscoverCallableMethodsStage`, `ValidateNoDuplicateTargetsStage`, `ValidateMappingShapeStage`, `ValidateSourceParametersStage`, `ExpandStage`, `DumpFullGraphStage`, `DumpTransformsStage`, `DumpPlanStage`, `ValidateConstantDefaultLegalityStage`, `RealisationDiagnosticsStage`, and `GenerateStage` is inspected
- **THEN** each declared type implements `Stage` (directly or transitively)

### Requirement: Pipeline
`Pipeline` SHALL be constructor-injected by Dagger with a single ordered `List<Stage>` dependency provided by `ProcessorModule`. The list, in declared order, SHALL contain instances of: `DiscoverAbstractMethodsStage`, `DiscoverMappingsStage`, `DiscoverCallableMethodsStage`, `ValidateNoDuplicateTargetsStage`, `ValidateMappingShapeStage`, `ValidateSourceParametersStage`, `ExpandStage`, `DumpFullGraphStage`, `DumpTransformsStage`, `DumpPlanStage`, `ValidateConstantDefaultLegalityStage`, `RealisationDiagnosticsStage`, `GenerateStage` (with the discover stages appearing first as a group).

Its `process(TypeElement)` method SHALL construct a fresh per-mapper context carrier, invoke each stage in list order against that context, and return `null`. The `Pipeline` class SHALL use `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

Pipeline ordering invariant: the single graph-building stage (`ExpandStage`) SHALL appear strictly before every graph-dumping stage (`DumpFullGraphStage`, `DumpTransformsStage`, `DumpPlanStage`, plus any future `Dump*Stage`). `GenerateStage` is a read-only consumer and SHALL appear last. If a future change introduces a new graph-mutating stage (e.g., a graph-optimisation pass), it MUST be inserted before the dumping stages so the `.dot` outputs remain a faithful snapshot of what `GenerateStage` consumed.

#### Scenario: Pipeline declares an ordered List<Stage> dependency
- **WHEN** the source of `Pipeline` is inspected
- **THEN** its constructor (generated by Lombok via `@RequiredArgsConstructor(onConstructor_ = @Inject)`) accepts a single `List<Stage>` parameter
- **AND** the constructor is `@Inject`-annotated by Lombok

#### Scenario: process() invokes the stages in declared order
- **WHEN** `Pipeline.process(typeElement)` is invoked with the stage list
- **THEN** discover stages (`DiscoverAbstractMethodsStage`, `DiscoverMappingsStage`, `DiscoverCallableMethodsStage`) run first (as a group)
- **AND** `ValidateNoDuplicateTargetsStage` runs next
- **AND** `ValidateMappingShapeStage` runs next
- **AND** `ValidateSourceParametersStage` runs next
- **AND** `ExpandStage` runs next
- **AND** `DumpFullGraphStage` runs next
- **AND** `DumpTransformsStage` runs next
- **AND** `DumpPlanStage` runs next
- **AND** `ValidateConstantDefaultLegalityStage` runs next
- **AND** `RealisationDiagnosticsStage` runs next
- **AND** `GenerateStage` runs last
- **AND** `process` returns `null`

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
`MapperStep` SHALL implement `com.google.auto.common.BasicAnnotationProcessor.Step`. It SHALL be `@Inject`-constructed by Dagger. It SHALL declare exactly one annotation (`io.github.joke.percolate.Mapper`) via `annotations()`. Its `process(elementsByAnnotation)` SHALL reset `Diagnostics`, dispatch each `@Mapper`-annotated `TypeElement` to `Pipeline.process(...)`, and return an empty `Set<Element>` (no deferral in this change).

#### Scenario: MapperStep declares the @Mapper annotation
- **WHEN** `MapperStep.annotations()` is invoked
- **THEN** it returns a `Set` containing exactly the FQN `"io.github.joke.percolate.Mapper"`

#### Scenario: process() resets Diagnostics before dispatching
- **WHEN** `MapperStep.process(elementsByAnnotation)` is invoked
- **THEN** `Diagnostics.reset()` is called before any element is dispatched to `Pipeline`

#### Scenario: process() dispatches each @Mapper TypeElement to Pipeline
- **WHEN** `MapperStep.process(elementsByAnnotation)` is invoked with a multimap containing two `@Mapper`-annotated `TypeElement`s
- **THEN** `Pipeline.process(typeElement)` is called once for each `TypeElement`
- **AND** non-`TypeElement` entries are ignored
- **AND** the method returns an empty `Set<Element>`

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

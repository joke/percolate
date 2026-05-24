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
- a `@Provides` method returning the ordered `List<Stage>` consumed by `Pipeline`, threading the nine stage classes in declared order;
- a `@Provides` method returning a `ResolveCtx` derived from the injected `Types` and `Elements` instances.

The `ProcessorModule` class SHALL use `@RequiredArgsConstructor` to replace its manual constructor. The `processingEnvironment` field SHALL be `private final` with no explicit constructor.

#### Scenario: ProcessorModule provides the ordered Stage list
- **WHEN** the Dagger graph requests a `List<Stage>`
- **THEN** the `@Provides` method on `ProcessorModule` returns a list whose elements are, in order, instances of `DiscoverAbstractMethods`, `DiscoverMappings`, `ValidateNoDuplicateTargets`, `ValidateSourceParameters`, `SeedGraph`, `DumpGraph`, `ExpandStage`, `ValidateRealisationStage`, `DumpExpandedGraph`

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
- **WHEN** the source of `DiscoverAbstractMethods`, `DiscoverMappings`, `ValidateNoDuplicateTargets`, `ValidateSourceParameters`, `SeedGraph`, `DumpGraph`, `ExpandStage`, `ValidateRealisationStage`, and `DumpExpandedGraph` is inspected
- **THEN** each declared type implements `Stage` (directly or transitively)

### Requirement: Pipeline
`Pipeline` SHALL be constructor-injected by Dagger with a single ordered `List<Stage>` dependency provided by `ProcessorModule`. The list, in declared order, SHALL contain instances of: `DiscoverAbstractMethods`, `DiscoverMappings`, `ValidateNoDuplicateTargets`, `ValidateSourceParameters`, `SeedGraph`, `DumpGraph`, `ExpandStage`, `ValidateRealisationStage`, `DumpExpandedGraph`.

Its `process(TypeElement)` method SHALL construct a fresh per-mapper context carrier, invoke each stage in list order against that context, and return `null` (no code generation in this change). The `Pipeline` class SHALL use `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

#### Scenario: Pipeline declares an ordered List<Stage> dependency
- **WHEN** the source of `Pipeline` is inspected
- **THEN** its constructor (generated by Lombok via `@RequiredArgsConstructor(onConstructor_ = @Inject)`) accepts a single `List<Stage>` parameter
- **AND** the constructor is `@Inject`-annotated by Lombok

#### Scenario: process() invokes the nine stages in declared order
- **WHEN** `Pipeline.process(typeElement)` is invoked with the v1 stage list
- **THEN** `DiscoverAbstractMethods` runs first
- **AND** `DiscoverMappings` runs next
- **AND** `ValidateNoDuplicateTargets` runs next
- **AND** `ValidateSourceParameters` runs next
- **AND** `SeedGraph` runs next
- **AND** `DumpGraph` runs next
- **AND** `ExpandStage` runs next
- **AND** `ValidateRealisationStage` runs next
- **AND** `DumpExpandedGraph` runs last
- **AND** `process` returns `null`

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

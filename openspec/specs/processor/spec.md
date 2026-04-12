# Processor Framework Spec

## PercolateProcessor

- Extends `javax.annotation.processing.AbstractProcessor`
- Registered via `@AutoService(Processor.class)`
- Supports annotation: `io.github.joke.percolate.Mapper`
- `init()`: creates Dagger `ProcessorComponent` from `ProcessingEnvironment`
- `process()`: iterates elements annotated with `@Mapper`, calls `pipeline.process(element)` for each
- Obtains `Pipeline` from the Dagger component

## ProcessorComponent

- Dagger `@Component` with `modules = ProcessorModule.class`
- Exposes `Pipeline pipeline()`
- Factory method: takes `ProcessingEnvironment` as parameter (via `@Component.Factory` or `@BindsInstance`)

## ProcessorModule

- Dagger `@Module`
- `@Provides` methods extracting from `ProcessingEnvironment`:
  - `Elements` via `getElementUtils()`
  - `Types` via `getTypeUtils()`
  - `Messager` via `getMessager()`
  - `Filer` via `getFiler()`

The `ProcessorModule` class SHALL use `@RequiredArgsConstructor` to replace its manual constructor. The `processingEnvironment` field SHALL be `private final` with no explicit constructor.

## Pipeline

- Constructor-injected by Dagger
- Method: `process(TypeElement element)` returning `@Nullable JavaFile`
- The `Pipeline` SHALL receive all five stages (`AnalyzeStage`, `DiscoverStage`, `BuildGraphStage`, `ValidateStage`, `GenerateStage`) via constructor injection
- The `Pipeline` SHALL chain stages sequentially, stopping on first failure and reporting diagnostics to `Messager`
- On success, the `Pipeline` SHALL write the generated `JavaFile` via `Filer`
- On failure, the `Pipeline` SHALL return `null` after reporting all diagnostics

The `Pipeline` class SHALL use `@RequiredArgsConstructor(onConstructor_ = @Inject)` for its constructor with stages and `Messager` as `private final` fields.

#### Scenario: Successful end-to-end processing
- **WHEN** a valid `@Mapper` TypeElement is processed
- **THEN** the pipeline SHALL produce a `JavaFile`, write it to `Filer`, and return it

#### Scenario: Stage failure stops processing
- **WHEN** any stage returns a `StageResult.failure`
- **THEN** the pipeline SHALL report all diagnostics from the failure to `Messager` via `printMessage` and return `null`

## ProcessorOptions

### Requirement: ProcessorOptions provides annotation processor configuration
A `ProcessorOptions` value class SHALL be provided by `ProcessorModule` and SHALL expose:
- `isDebugGraphs()`: `boolean`, parsed from processor option `percolate.debug.graphs`, default `false`
- `getDebugGraphsFormat()`: `String`, parsed from processor option `percolate.debug.graphs.format`, default `"dot"`

`ProcessorModule` SHALL read these values from `processingEnvironment.getOptions()`.

#### Scenario: Debug graphs enabled via processor option
- **WHEN** `-Apercolate.debug.graphs=true` is passed to javac
- **THEN** `ProcessorOptions.isDebugGraphs()` SHALL return `true`

#### Scenario: Default options when nothing specified
- **WHEN** no `percolate.debug.*` options are passed
- **THEN** `isDebugGraphs()` SHALL return `false` and `getDebugGraphsFormat()` SHALL return `"dot"`

### Requirement: PercolateProcessor declares supported options
`PercolateProcessor` SHALL declare `percolate.debug.graphs` and `percolate.debug.graphs.format` via `@SupportedOptions` so that build tools do not emit warnings about unrecognized options.

#### Scenario: Supported options declared
- **WHEN** the annotation processor is loaded
- **THEN** `getSupportedOptions()` SHALL include `"percolate.debug.graphs"` and `"percolate.debug.graphs.format"`

## Package

All classes in `io.github.joke.percolate.processor`.

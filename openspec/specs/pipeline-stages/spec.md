# Pipeline Stages Spec

## Purpose

Defines the stage-based processing pipeline architecture: `StageResult` modeling, `Diagnostic` records, sequential stage chaining, per-mapper error isolation, and Dagger injection of stages.

## Requirements

### Requirement: StageResult models success or failure
`StageResult<T>` SHALL represent a stage outcome as either a success with a value or a failure with a list of diagnostics. It SHALL provide `isSuccess()`, `value()`, and `errors()` accessors. `value()` SHALL throw if the result is a failure. Factory methods `StageResult.success(T)` and `StageResult.failure(List<Diagnostic>)` SHALL be provided.

#### Scenario: Successful stage result
- **WHEN** a stage completes without errors
- **THEN** `StageResult.success(model)` returns a result where `isSuccess()` is `true` and `value()` returns the model

#### Scenario: Failed stage result
- **WHEN** a stage encounters errors
- **THEN** `StageResult.failure(errors)` returns a result where `isSuccess()` is `false` and `errors()` returns the diagnostics

#### Scenario: Accessing value on failure
- **WHEN** `value()` is called on a failed `StageResult`
- **THEN** an `IllegalStateException` SHALL be thrown

### Requirement: Diagnostic carries element and message
`Diagnostic` SHALL carry an `Element` (for source location), a `String` message, and a `Diagnostic.Kind` (ERROR, WARNING, NOTE). The `Element` reference allows `Messager` to point diagnostics at the correct source location.

#### Scenario: Error diagnostic on invalid element
- **WHEN** a stage detects an invalid mapping on a method element
- **THEN** a `Diagnostic` with `Kind.ERROR`, the method element, and a descriptive message SHALL be created

### Requirement: Pipeline executes stages sequentially with early exit on failure
`Pipeline.process()` SHALL execute stages in this order: `AnalyzeStage`, `BuildGraphStage`, `DumpPropertyGraphStage`, `ResolveTransformsStage`, `DumpTransformGraphStage`, `DumpResolvedOverlayStage`, `ValidateTransformsStage`, `GenerateStage`. After each real stage, if the `StageResult` is not successful, `Pipeline` SHALL report errors via `Messager` and return `null`. Debug dump stages SHALL be called unconditionally (they internally check `ProcessorOptions` to decide whether to act). Debug dump stages SHALL NOT produce `StageResult` — they are fire-and-forget side effects that SHALL NOT abort the pipeline on failure.

#### Scenario: All stages succeed
- **WHEN** all stages return success for a mapper
- **THEN** the pipeline SHALL write the generated `JavaFile` via `Filer` and return it

#### Scenario: Stage fails mid-pipeline
- **WHEN** a stage returns failure
- **THEN** the pipeline SHALL report all diagnostics from that failure to `Messager` and skip remaining stages for that mapper

#### Scenario: Debug stages called between real stages
- **WHEN** `BuildGraphStage` succeeds
- **THEN** `Pipeline` SHALL call `DumpPropertyGraphStage.execute(mappingGraph)` before calling `ResolveTransformsStage`

#### Scenario: Debug stage failure does not abort pipeline
- **WHEN** `DumpPropertyGraphStage` throws an `IOException` writing a file
- **THEN** `Pipeline` SHALL log a warning via `Messager` and continue to `ResolveTransformsStage`

#### Scenario: Pipeline passes both graph and resolved model to overlay stage
- **WHEN** `ResolveTransformsStage` succeeds
- **THEN** `Pipeline` SHALL call `DumpResolvedOverlayStage.execute(mappingGraph, resolvedModel)` passing both the `MappingGraph` from `BuildGraphStage` and the `ResolvedModel` from `ResolveTransformsStage`

### Requirement: Per-mapper error isolation
Each mapper type element SHALL flow through the pipeline independently. A failure in one mapper SHALL NOT prevent other mappers from being processed.

#### Scenario: One mapper fails, another succeeds
- **WHEN** `MapperA` fails validation and `MapperB` passes all stages
- **THEN** `MapperB` SHALL still generate its implementation class and `MapperA`'s errors SHALL be reported via `Messager`

### Requirement: Stages are Dagger-injected
All stages SHALL be constructor-injected by Dagger. The `Pipeline` SHALL receive all stages via its constructor. Stages SHALL declare dependencies on `Elements`, `Types`, `Messager`, or `Filer` as needed from the existing `ProcessorModule`. The pipeline SHALL inject eight stages: `AnalyzeStage`, `BuildGraphStage`, `DumpPropertyGraphStage`, `ResolveTransformsStage`, `DumpTransformGraphStage`, `DumpResolvedOverlayStage`, `ValidateTransformsStage`, and `GenerateStage`.

#### Scenario: Pipeline receives stages from Dagger
- **WHEN** the `ProcessorComponent` is built
- **THEN** the `Pipeline` SHALL have all eight stages injected and ready to execute

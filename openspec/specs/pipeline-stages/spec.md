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

### Requirement: Pipeline chains stages sequentially
The `Pipeline` SHALL execute stages in order: analyze → discover → build graph → validate → resolve transforms → validate transforms → generate. If any stage returns a failure, the pipeline SHALL stop processing that mapper, report all diagnostics to `Messager`, and return `null`.

#### Scenario: All stages succeed
- **WHEN** all stages return success for a mapper
- **THEN** the pipeline SHALL write the generated `JavaFile` via `Filer` and return it

#### Scenario: Stage fails mid-pipeline
- **WHEN** a stage returns failure
- **THEN** the pipeline SHALL report all diagnostics from that failure to `Messager` and skip remaining stages for that mapper

### Requirement: Per-mapper error isolation
Each mapper type element SHALL flow through the pipeline independently. A failure in one mapper SHALL NOT prevent other mappers from being processed.

#### Scenario: One mapper fails, another succeeds
- **WHEN** `MapperA` fails validation and `MapperB` passes all stages
- **THEN** `MapperB` SHALL still generate its implementation class and `MapperA`'s errors SHALL be reported via `Messager`

### Requirement: Stages are Dagger-injected
All stages SHALL be constructor-injected by Dagger. The `Pipeline` SHALL receive all stages via its constructor. Stages SHALL declare dependencies on `Elements`, `Types`, `Messager`, or `Filer` as needed from the existing `ProcessorModule`.

#### Scenario: Pipeline receives stages from Dagger
- **WHEN** the `ProcessorComponent` is built
- **THEN** the `Pipeline` SHALL have all seven stages injected and ready to execute

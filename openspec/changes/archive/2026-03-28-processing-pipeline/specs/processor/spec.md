## MODIFIED Requirements

### Requirement: Pipeline
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

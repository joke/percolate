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

`Pipeline.process()` SHALL execute stages in this order: `AnalyzeStage`, `MatchMappingsStage`, `ValidateMatchingStage`, `BuildValueGraphStage`, `ResolvePathStage`, `DumpGraphStage`, `ValidateResolutionStage`, `GenerateStage`. After each real stage, if the `StageResult` is not successful, `Pipeline` SHALL report errors via `Messager` and return `null`. The debug dump stage (`DumpGraphStage`) SHALL be called unconditionally (it internally checks `ProcessorOptions` to decide whether to act). The debug dump stage SHALL NOT produce `StageResult` — it is a fire-and-forget side effect that SHALL NOT abort the pipeline on failure.

#### Scenario: All stages succeed

- **WHEN** all stages return success for a mapper
- **THEN** the pipeline SHALL write the generated `JavaFile` via `Filer` and return it

#### Scenario: Stage fails mid-pipeline

- **WHEN** a stage returns failure
- **THEN** the pipeline SHALL report all diagnostics from that failure to `Messager` and skip remaining stages for that mapper

#### Scenario: Debug stage called after resolution

- **WHEN** `ResolvePathStage` succeeds
- **THEN** `Pipeline` SHALL call `DumpGraphStage.execute(valueGraphs, resolvedPaths)` before calling `ValidateResolutionStage`

#### Scenario: Debug stage failure does not abort pipeline

- **WHEN** `DumpGraphStage` throws an `IOException` writing a file
- **THEN** `Pipeline` SHALL log a warning via `Messager` and continue to `ValidateResolutionStage`

#### Scenario: Pipeline routes MatchedModel through matching validation before graph build

- **WHEN** `MatchMappingsStage` succeeds
- **THEN** `Pipeline` SHALL call `ValidateMatchingStage.execute(matchedModel)` before `BuildValueGraphStage`; on matching-validation failure `Pipeline` SHALL NOT call `BuildValueGraphStage`

#### Scenario: Pipeline routes resolved paths through dump before validation

- **WHEN** `ResolvePathStage` succeeds
- **THEN** `Pipeline` SHALL call `DumpGraphStage.execute(...)` before `ValidateResolutionStage` (fire-and-forget) and proceed directly to `ValidateResolutionStage` on its result

### Requirement: Per-mapper error isolation
Each mapper type element SHALL flow through the pipeline independently. A failure in one mapper SHALL NOT prevent other mappers from being processed.

#### Scenario: One mapper fails, another succeeds
- **WHEN** `MapperA` fails validation and `MapperB` passes all stages
- **THEN** `MapperB` SHALL still generate its implementation class and `MapperA`'s errors SHALL be reported via `Messager`

### Requirement: Stages are Dagger-injected

All stages SHALL be constructor-injected by Dagger. The `Pipeline` SHALL receive all stages via its constructor. Stages SHALL declare dependencies on `Elements`, `Types`, `Messager`, or `Filer` as needed from the existing `ProcessorModule`. The pipeline SHALL inject eight stages: `AnalyzeStage`, `MatchMappingsStage`, `ValidateMatchingStage`, `BuildValueGraphStage`, `ResolvePathStage`, `DumpGraphStage`, `ValidateResolutionStage`, and `GenerateStage`.

#### Scenario: Pipeline receives stages from Dagger

- **WHEN** the `ProcessorComponent` is built
- **THEN** the `Pipeline` SHALL have all eight stages injected and ready to execute

### Requirement: MatchMappingsStage produces MatchedModel from parsed methods

`MatchMappingsStage` SHALL accept the output of `AnalyzeStage` (per-method parse results) and produce a `MatchedModel`. For each method the stage SHALL:

1. Emit one `MappingAssignment` per `@Map` / `@MapList` directive with `origin = EXPLICIT_MAP`.
2. Emit one `MappingAssignment` per `@Map(using = ...)` directive with `origin = USING_ROUTED`.
3. For every target property (or constructor argument) not covered by an explicit directive, attempt auto-mapping against a same-named top-level source property; on match, emit a `MappingAssignment` with `origin = AUTO_MAPPED`.

The stage SHALL NOT resolve types, discover accessors, or validate that referenced properties exist — those responsibilities belong to `BuildValueGraphStage` and the two validation stages.

#### Scenario: Explicit @Map directive becomes EXPLICIT_MAP assignment

- **WHEN** a method has `@Map(source = "a", target = "b")`
- **THEN** `MatchMappingsStage` SHALL emit one `MappingAssignment` with `sourcePath=["a"]`, `targetName="b"`, `origin=EXPLICIT_MAP`

#### Scenario: Auto-mapping fills gaps

- **WHEN** target has properties `[x, y]` and only `@Map(target="x",source="a")` is declared, and source has top-level property `y`
- **THEN** `MatchMappingsStage` SHALL emit `[MappingAssignment(EXPLICIT_MAP, [a], x), MappingAssignment(AUTO_MAPPED, [y], y)]` for the method

### Requirement: BuildValueGraphStage replaces BuildGraphStage

`BuildValueGraphStage` SHALL replace the previous `BuildGraphStage`. Its input SHALL be `MatchedModel`; its output SHALL be `Map<MethodMatching, ValueGraph>`. The stage SHALL construct typed nodes and edges per the `value-graph` capability.

#### Scenario: BuildValueGraphStage is the only graph-construction stage

- **WHEN** the pipeline runs
- **THEN** there SHALL be no other stage constructing any `DefaultDirectedGraph` used for resolution — `BuildValueGraphStage` is the sole source of `ValueGraph`s

### Requirement: ResolvePathStage replaces ResolveTransformsStage

`ResolvePathStage` SHALL replace the previous `ResolveTransformsStage`. Its responsibility is narrowed to: for each `(MethodMatching, MappingAssignment)` pair, find the shortest `GraphPath<ValueNode, ValueEdge>` through the method's `ValueGraph` from the `SourceParamNode` to the `TargetSlotNode` for that assignment, using `BFSShortestPath` or equivalent. No accessor chain construction (done in build); no code template materialization (templates are materialised at edge construction in `BuildValueGraphStage`, except `LiftEdge` which composes lazily at generation time).

#### Scenario: ResolvePathStage returns per-assignment GraphPaths

- **WHEN** a method has 3 `MappingAssignment`s and all resolve successfully
- **THEN** `ResolvePathStage` SHALL emit 3 `ResolvedAssignment`s, each carrying a `GraphPath<ValueNode, ValueEdge>` from `SourceParamNode` to the assignment's `TargetSlotNode`

#### Scenario: ResolvePathStage does not touch code templates

- **WHEN** `ResolvePathStage` completes
- **THEN** the stage SHALL NOT read, write, or re-derive any `CodeTemplate` on any edge — templates are the responsibility of edge construction (`BuildValueGraphStage`) and lazy composition (`LiftEdge`)

### Requirement: ValidateMatchingStage separates matching-layer diagnostics

`ValidateMatchingStage` SHALL run after `MatchMappingsStage` and emit diagnostics for matching-layer failures: unknown source-root parameter name, duplicate `@Map` directives targeting the same slot, conflicting `@MapOpt` values on the same assignment, `@Map(using = ...)` naming a method that doesn't exist on the mapper. It SHALL NOT require a `ValueGraph` — all its checks operate on the `MatchedModel`.

#### Scenario: Duplicate @Map targets surface in ValidateMatchingStage

- **WHEN** two `@Map` directives on the same method have `target = "x"`
- **THEN** `ValidateMatchingStage` SHALL emit a diagnostic and return failure; the pipeline SHALL NOT proceed to `BuildValueGraphStage` for that mapper

### Requirement: ValidateResolutionStage separates resolution-layer diagnostics

`ValidateResolutionStage` SHALL run after `OptimizePathStage` and emit diagnostics for resolution-layer failures: unresolved source-path segments (property does not exist on the resolved type), no transform path found between types, unresolved target slot. All its checks operate on the optimized per-assignment paths.

#### Scenario: Unresolved property segment surfaces in ValidateResolutionStage

- **WHEN** a `MappingAssignment` has `sourcePath = ["customer", "adress"]` and no property `adress` exists on `Customer`
- **THEN** `ValidateResolutionStage` SHALL emit a diagnostic pointing at the method element with the segment name, segment index, searched type, and available properties

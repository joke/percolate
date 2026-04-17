## MODIFIED Requirements

### Requirement: Pipeline executes stages sequentially with early exit on failure

`Pipeline.process()` SHALL execute stages in this order: `AnalyzeStage`, `MatchMappingsStage`, `ValidateMatchingStage`, `BuildValueGraphStage`, `DumpValueGraphStage`, `ResolvePathStage`, `OptimizePathStage`, `DumpResolvedPathsStage`, `ValidateResolutionStage`, `GenerateStage`. After each real stage, if the `StageResult` is not successful, `Pipeline` SHALL report errors via `Messager` and return `null`. Debug dump stages SHALL be called unconditionally (they internally check `ProcessorOptions` to decide whether to act). Debug dump stages SHALL NOT produce `StageResult` — they are fire-and-forget side effects that SHALL NOT abort the pipeline on failure.

#### Scenario: All stages succeed

- **WHEN** all stages return success for a mapper
- **THEN** the pipeline SHALL write the generated `JavaFile` via `Filer` and return it

#### Scenario: Stage fails mid-pipeline

- **WHEN** a stage returns failure
- **THEN** the pipeline SHALL report all diagnostics from that failure to `Messager` and skip remaining stages for that mapper

#### Scenario: Debug stages called between real stages

- **WHEN** `BuildValueGraphStage` succeeds
- **THEN** `Pipeline` SHALL call `DumpValueGraphStage.execute(valueGraphs)` before calling `ResolvePathStage`

#### Scenario: Debug stage failure does not abort pipeline

- **WHEN** `DumpValueGraphStage` throws an `IOException` writing a file
- **THEN** `Pipeline` SHALL log a warning via `Messager` and continue to `ResolvePathStage`

#### Scenario: Pipeline routes MatchedModel through matching validation before graph build

- **WHEN** `MatchMappingsStage` succeeds
- **THEN** `Pipeline` SHALL call `ValidateMatchingStage.execute(matchedModel)` before `BuildValueGraphStage`; on matching-validation failure `Pipeline` SHALL NOT call `BuildValueGraphStage`

#### Scenario: Pipeline routes resolved paths through optimize before validation

- **WHEN** `ResolvePathStage` succeeds
- **THEN** `Pipeline` SHALL call `OptimizePathStage.execute(resolvedPaths)` before `ValidateResolutionStage`

### Requirement: Stages are Dagger-injected

All stages SHALL be constructor-injected by Dagger. The `Pipeline` SHALL receive all stages via its constructor. Stages SHALL declare dependencies on `Elements`, `Types`, `Messager`, or `Filer` as needed from the existing `ProcessorModule`. The pipeline SHALL inject ten stages: `AnalyzeStage`, `MatchMappingsStage`, `ValidateMatchingStage`, `BuildValueGraphStage`, `DumpValueGraphStage`, `ResolvePathStage`, `OptimizePathStage`, `DumpResolvedPathsStage`, `ValidateResolutionStage`, and `GenerateStage`.

#### Scenario: Pipeline receives stages from Dagger

- **WHEN** the `ProcessorComponent` is built
- **THEN** the `Pipeline` SHALL have all ten stages injected and ready to execute

## ADDED Requirements

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

`ResolvePathStage` SHALL replace the previous `ResolveTransformsStage`. Its responsibility is narrowed to: for each `(MethodMatching, MappingAssignment)` pair, find the shortest `GraphPath<ValueNode, ValueEdge>` through the method's `ValueGraph` from the `SourceParamNode` to the `TargetSlotNode` for that assignment, using `BFSShortestPath` or equivalent. No accessor chain construction (done in build); no code template materialization (done in optimize).

#### Scenario: ResolvePathStage returns per-assignment GraphPaths

- **WHEN** a method has 3 `MappingAssignment`s and all resolve successfully
- **THEN** `ResolvePathStage` SHALL emit 3 `ResolvedAssignment`s, each carrying a `GraphPath<ValueNode, ValueEdge>` from `SourceParamNode` to the assignment's `TargetSlotNode`

#### Scenario: ResolvePathStage does not resolve code templates

- **WHEN** `ResolvePathStage` completes
- **THEN** `TypeTransformEdge.codeTemplate` on any resolved-path edge SHALL still be `null` (template materialization is `OptimizePathStage`'s job)

### Requirement: OptimizePathStage runs template materialization

`OptimizePathStage` SHALL run between `ResolvePathStage` and `ValidateResolutionStage`. In this refactor it performs one pass — code-template materialization on every `TypeTransformEdge` and `LiftEdge` on a resolved path, as specified in the `path-optimization` capability.

#### Scenario: OptimizePathStage runs exactly once per pipeline pass

- **WHEN** `Pipeline.process()` executes for a mapper
- **THEN** `OptimizePathStage.execute(...)` SHALL be invoked exactly once after `ResolvePathStage` and before `ValidateResolutionStage`

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

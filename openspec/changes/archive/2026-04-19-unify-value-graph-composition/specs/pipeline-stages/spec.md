## MODIFIED Requirements

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

### Requirement: Stages are Dagger-injected

All stages SHALL be constructor-injected by Dagger. The `Pipeline` SHALL receive all stages via its constructor. Stages SHALL declare dependencies on `Elements`, `Types`, `Messager`, or `Filer` as needed from the existing `ProcessorModule`. The pipeline SHALL inject eight stages: `AnalyzeStage`, `MatchMappingsStage`, `ValidateMatchingStage`, `BuildValueGraphStage`, `ResolvePathStage`, `DumpGraphStage`, `ValidateResolutionStage`, and `GenerateStage`.

#### Scenario: Pipeline receives stages from Dagger

- **WHEN** the `ProcessorComponent` is built
- **THEN** the `Pipeline` SHALL have all eight stages injected and ready to execute

### Requirement: ResolvePathStage replaces ResolveTransformsStage

`ResolvePathStage` SHALL replace the previous `ResolveTransformsStage`. Its responsibility is narrowed to: for each `(MethodMatching, MappingAssignment)` pair, find the shortest `GraphPath<ValueNode, ValueEdge>` through the method's `ValueGraph` from the `SourceParamNode` to the `TargetSlotNode` for that assignment, using `BFSShortestPath` or equivalent. No accessor chain construction (done in build); no code template materialization (templates are materialised at edge construction in `BuildValueGraphStage`, except `LiftEdge` which composes lazily at generation time).

#### Scenario: ResolvePathStage returns per-assignment GraphPaths

- **WHEN** a method has 3 `MappingAssignment`s and all resolve successfully
- **THEN** `ResolvePathStage` SHALL emit 3 `ResolvedAssignment`s, each carrying a `GraphPath<ValueNode, ValueEdge>` from `SourceParamNode` to the assignment's `TargetSlotNode`

#### Scenario: ResolvePathStage does not touch code templates

- **WHEN** `ResolvePathStage` completes
- **THEN** the stage SHALL NOT read, write, or re-derive any `CodeTemplate` on any edge — templates are the responsibility of edge construction (`BuildValueGraphStage`) and lazy composition (`LiftEdge`)

## REMOVED Requirements

### Requirement: OptimizePathStage runs template materialization

**Reason**: The two-phase template commit (`proposalTemplate` → `codeTemplate` on winning edges) existed only because strategies were speculative. With no speculative strategies in this slice, every edge except `LiftEdge` can carry its final `CodeTemplate` from construction time; `LiftEdge` becomes lazy and composes its template at generation time via on-demand BFS over the parent graph. The dedicated `OptimizePathStage` therefore has no remaining work.

**Migration**: `TypeTransformEdge.codeTemplate` and `PropertyReadEdge.codeTemplate` SHALL be assigned by `BuildValueGraphStage` at construction. `LiftEdge` SHALL capture `(innerInputNode, innerOutputNode, kind)` at construction and compute its template at `GenerateStage` time. Tests previously covering `OptimizePathStage` behaviour SHALL be consolidated into tests for `BuildValueGraphStage` (edge template construction) and `LiftEdge` (lazy generation).

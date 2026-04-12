## MODIFIED Requirements

### Requirement: Pipeline executes stages sequentially with early exit on failure
`Pipeline.process()` SHALL execute stages in this order: `AnalyzeStage`, `BuildGraphStage`, `DumpPropertyGraphStage`, `ResolveTransformsStage`, `DumpTransformGraphStage`, `DumpResolvedOverlayStage`, `ValidateTransformsStage`, `GenerateStage`. After each real stage, if the `StageResult` is not successful, `Pipeline` SHALL report errors via `Messager` and return `null`. Debug dump stages SHALL be called unconditionally (they internally check `ProcessorOptions` to decide whether to act). Debug dump stages SHALL NOT produce `StageResult` — they are fire-and-forget side effects that SHALL NOT abort the pipeline on failure.

#### Scenario: Debug stages called between real stages
- **WHEN** `BuildGraphStage` succeeds
- **THEN** `Pipeline` SHALL call `DumpPropertyGraphStage.execute(mappingGraph)` before calling `ResolveTransformsStage`

#### Scenario: Debug stage failure does not abort pipeline
- **WHEN** `DumpPropertyGraphStage` throws an `IOException` writing a file
- **THEN** `Pipeline` SHALL log a warning via `Messager` and continue to `ResolveTransformsStage`

#### Scenario: Pipeline passes both graph and resolved model to overlay stage
- **WHEN** `ResolveTransformsStage` succeeds
- **THEN** `Pipeline` SHALL call `DumpResolvedOverlayStage.execute(mappingGraph, resolvedModel)` passing both the `MappingGraph` from `BuildGraphStage` and the `ResolvedModel` from `ResolveTransformsStage`

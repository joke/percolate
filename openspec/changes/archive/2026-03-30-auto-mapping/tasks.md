## 1. Core Implementation

- [x] 1.1 Add auto-mapping logic to `BuildGraphStage`: after processing explicit `@Map` directives, iterate target nodes with `inDegreeOf == 0` and add `MappingEdge` for same-name source matches
- [x] 1.2 Update `BuildGraphStageSpec` with scenarios: auto-map same-name properties, explicit directive takes priority, unmapped source silently ignored, mixed explicit and auto-mapped edges

## 2. Test Adjustments

- [x] 2.1 Update `ValidateStageSpec` for cases where same-name properties are now auto-mapped instead of producing unmapped-target errors
- [x] 2.2 Update `PercolateProcessorSpec` integration tests to verify end-to-end auto-mapping behavior

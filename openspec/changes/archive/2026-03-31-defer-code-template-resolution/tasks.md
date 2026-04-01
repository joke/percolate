## 1. Update TransformEdge to carry TransformProposal

- [x] 1.1 Replace `@Value` with `@Getter` on `TransformEdge`, add `TransformProposal` field, make `CodeTemplate` mutable (set via setter or package-private method)
- [x] 1.2 Add constructor that accepts `TypeTransformStrategy` and `TransformProposal` (no `CodeTemplate`)
- [x] 1.3 Add `resolveTemplate(CodeTemplate)` method to set the resolved code template

## 2. Defer code template resolution in ResolveTransformsStage

- [x] 2.1 Change BFS expansion loop to construct `TransformEdge` with `TransformProposal` instead of calling `resolveCodeTemplate` eagerly
- [x] 2.2 Extract `resolvePathTemplates(GraphPath, ResolutionContext)` private method that iterates path edges and calls `resolveCodeTemplate` per edge
- [x] 2.3 Call `resolvePathTemplates` on the selected `BFSShortestPath` result before returning from `resolveTransformPath`

## 3. Update tests

- [x] 3.1 Update `ResolveTransformsStageSpec` to verify resolved mappings still produce correct code templates
- [x] 3.2 Run full test suite to confirm behavioral equivalence

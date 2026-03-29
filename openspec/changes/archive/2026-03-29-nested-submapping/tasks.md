## 1. Per-method graph restructuring

- [x] 1.1 Update `MappingGraph` to store `Map<DiscoveredMethod, DefaultDirectedGraph<PropertyNode, MappingEdge>>` replacing the single shared graph
- [x] 1.2 Update `BuildGraphStage` to create a separate graph per `DiscoveredMethod`
- [x] 1.3 Update `ValidateStage` to iterate per-method graphs for unmapped target and duplicate target checks
- [x] 1.4 Update `BuildGraphStageSpec` and `ValidateStageSpec` for per-method graph structure

## 2. Transform model

- [x] 2.1 Create `TransformOperation` sealed interface with `DirectOperation` and `SubMapOperation` implementations
- [x] 2.2 Create `TransformNode` with input type, output type, and `TransformOperation`
- [x] 2.3 Create `ResolvedMapping` carrying source property, target property, and `List<TransformNode>` chain
- [x] 2.4 Create `ResolvedModel` carrying mapper type, methods, and per-method `List<ResolvedMapping>`

## 3. ResolveTransforms stage

- [x] 3.1 Create `ResolveTransforms` stage: walk per-method graph edges, check type assignability, produce `ResolvedModel`
- [x] 3.2 Implement sibling method lookup: scan `DiscoveredMethod` entries for matching source/target type assignability
- [x] 3.3 Mark unresolvable type gaps as `UNRESOLVED` in the resolved model
- [x] 3.4 Write `ResolveTransformsSpec` covering DIRECT, SUBMAP, and UNRESOLVED scenarios

## 4. ValidateTransforms stage

- [x] 4.1 Create `ValidateTransforms` stage: check all resolved mappings are fulfillable, produce error diagnostics for UNRESOLVED
- [x] 4.2 Add error message construction to `ErrorMessages` for unresolvable type gaps
- [x] 4.3 Write `ValidateTransformsSpec` covering success and error scenarios

## 5. Code generation updates

- [x] 5.1 Update `GenerateStage` to consume `ResolvedModel` instead of `MappingGraph`
- [x] 5.2 Implement SUBMAP code emission: generate `methodName(readExpr)` delegation calls
- [x] 5.3 Update `GenerateStage` to iterate per-method resolved mappings instead of shared graph vertices
- [x] 5.4 Update existing GenerateStage tests and add tests for SUBMAP generation

## 6. Pipeline wiring

- [x] 6.1 Add `ResolveTransforms` and `ValidateTransforms` to Dagger injection in `Pipeline`
- [x] 6.2 Wire new stages into `Pipeline.process()` between validate and generate
- [x] 6.3 Update `PercolateProcessorSpec` with integration test for nested submapping end-to-end

## 1. Scaffolding: enums and compose contract

- [x] 1.1 Add `ComposeKind` enum (`EXPRESSION`, `STATEMENT_LIST`) in the `value-graph` package
- [x] 1.2 Extend `ValueNode` sealed interface with `CodeBlock compose(Map<ValueEdge, CodeBlock> inputs, ComposeKind kind)`
- [x] 1.3 Implement `SourceParamNode.compose(...)` returning a `CodeBlock` for the parameter reference; assert empty `inputs`
- [x] 1.4 Implement `PropertyNode.compose(...)` as single-input forward; assert arity 1
- [x] 1.5 Implement `TypedValueNode.compose(...)` as single-input forward; assert arity 1
- [x] 1.6 Implement `TargetSlotNode.compose(...)` as single-input forward; assert arity 1

## 2. Edge template relocation

- [x] 2.1 Add `CodeTemplate` field to `PropertyReadEdge`; constructor takes it
- [x] 2.2 Update `GetterDiscovery` to construct `PropertyReadEdge` with template `"$L.getFoo()"` (or the discovered getter name)
- [x] 2.3 Update `FieldDiscovery.Source` to construct `PropertyReadEdge` with template `"$L.foo"`
- [x] 2.4 Remove `ReadAccessor` storage from `PropertyNode` (node carries name + type only)
- [x] 2.5 Update `TypeTransformEdge` construction so `codeTemplate` is populated at construction via `strategy.resolveCodeTemplate(...)`; remove `@Nullable` from the field
- [x] 2.6 Replace `LiftEdge.codeTemplate`/`innerPath` fields with `(innerInputNode, innerOutputNode, kind)` capture; remove pre-commit `CodeTemplate` field
- [x] 2.7 Implement `LiftEdge.composeTemplate(graph, winningEdges)` via on-demand `BFSShortestPath` from `innerInputNode` to `innerOutputNode`, composing inner edge templates and wrapping per `kind`

## 3. Pipeline restructuring

- [x] 3.1 Delete `OptimizePathStage` class and its Spock specs
- [x] 3.2 Delete `DumpValueGraphStage` and `DumpResolvedPathsStage` classes and their Spock specs
- [x] 3.3 Create `DumpGraphStage` taking `ValueGraphs + ResolvedPaths`; export `_resolved.{ext}` per method with winning edges bolded
- [x] 3.4 Update `Pipeline` to inject the eight-stage chain: `Analyze`, `MatchMappings`, `ValidateMatching`, `BuildValueGraph`, `ResolvePath`, `DumpGraph`, `ValidateResolution`, `Generate`
- [x] 3.5 Update `ProcessorModule` bindings to match the new injection surface (remove three stages, add one)
- [x] 3.6 Verify `Pipeline` invokes `DumpGraphStage` fire-and-forget (logs `IOException` as warning, does not abort)

## 4. BuildValueGraphStage updates

- [x] 4.1 Pass eager `CodeTemplate` into every non-`LiftEdge` constructed in `BuildValueGraphStage`
- [x] 4.2 Construct `LiftEdge` with `(innerInputNode, innerOutputNode, kind)` in lifting strategies; drop the `innerPath` capture
- [x] 4.3 Keep the 30-iteration fixpoint budget and `assertInvariants(...)` behaviour unchanged
- [x] 4.4 Update `assertInvariants` to assert `TypeTransformEdge.codeTemplate != null` post-construction

## 5. GenerateStage rewrite

- [x] 5.1 Replace current body with: per-method, build `AsSubgraph<>(graph, vertexSet, winningEdges)` from resolved paths
- [x] 5.2 Iterate via `TopologicalOrderIterator`, cache `Map<ValueNode, CodeBlock>` by applying each incoming edge's template and calling `node.compose(inputs, EXPRESSION)`
- [x] 5.3 For `LiftEdge`, invoke `edge.composeTemplate(graph, winningEdges)` on demand during the topological pass
- [x] 5.4 Assemble method body by reading cached `CodeBlock` at each `TargetSlotNode` and passing positionally to the constructor (still via `ConstructorDiscovery` SPI)
- [x] 5.5 Remove every `instanceof` check on `ValueNode` / `ValueEdge` subtypes from `GenerateStage`
- [x] 5.6 Remove any direct reads of `PropertyNode.accessor` or equivalent node-side accessor state from `GenerateStage`

## 6. Test updates and regressions

- [x] 6.1 Rehome `OptimizePathStage` specs: template-materialisation cases → `BuildValueGraphStageSpec`; lift-composition cases → `LiftEdgeSpec`
- [x] 6.2 Delete `DumpValueGraphStageSpec` and `DumpResolvedPathsStageSpec`; add `DumpGraphStageSpec` covering winning-edge bolding, zero-bold-on-failure, format fallback
- [x] 6.3 Verify golden outputs pinned in commit `4e0c891` remain byte-identical (`rtk ./gradlew :processor:test` and integration fixtures)
- [x] 6.4 Run `rtk ./gradlew check` on the full workspace and confirm green

## 7. Documentation and cleanup

- [x] 7.1 Remove references to `OptimizePathStage`, `DumpValueGraphStage`, `DumpResolvedPathsStage`, `proposalTemplate`, `path-optimization` from internal javadoc / package-info
- [x] 7.2 Confirm `@Mapper` / `@Map` surface, processor options, and SPI contracts are byte-identical (no source diffs under `io.github.joke.percolate.api` or the SPI packages)

## 1. ProcessorOptions and wiring

- [x] 1.1 Create `ProcessorOptions` value class with `debugGraphs` (boolean) and `debugGraphsFormat` (String) fields
- [x] 1.2 Add `@Provides ProcessorOptions` method to `ProcessorModule` reading from `processingEnvironment.getOptions()` with defaults (`false`, `"dot"`)
- [x] 1.3 Add `@SupportedOptions({"percolate.debug.graphs", "percolate.debug.graphs.format"})` to `PercolateProcessor`

## 2. TransformResolution and ResolvedMapping refactor

- [x] 2.1 Create `TransformResolution` value class with `explorationGraph` and `@Nullable path` fields
- [x] 2.2 Modify `ResolveTransformsStage.resolveTransformPath()` to return `@Nullable TransformResolution` instead of `@Nullable GraphPath`, preserving the exploration graph on both success and failure
- [x] 2.3 Update `ResolvedMapping` to hold `@Nullable TransformResolution` instead of `@Nullable GraphPath`, with `getEdges()` and `isResolved()` delegating through
- [x] 2.4 Update `ValidateTransformsStage` and `GenerateStage` call sites for the `ResolvedMapping` API change
- [x] 2.5 Verify existing tests pass after the refactor

## 3. GraphExportSupport utility

- [x] 3.1 Create `GraphExportSupport` package-private utility with `writeGraph(graph, nodeLabeler, edgeLabeler, filer, packageName, fileName, format)` method
- [x] 3.2 Implement format dispatch: `"dot"` → `DOTExporter`, `"graphml"` → `GraphMLExporter`, `"json"` → `JSONExporter`, unknown → fallback to DOT
- [x] 3.3 Configure DOT exporter with node label and edge label attribute providers
- [x] 3.4 Configure file extension mapping (`.dot`, `.graphml`, `.json`)

## 4. Debug dump stages

- [x] 4.1 Create `DumpPropertyGraphStage` — exports each method's property graph from `MappingGraph` with node `toString()` labels and edge type labels (`"access"` / `"mapping"`)
- [x] 4.2 Create `DumpTransformGraphStage` — merges per-mapping exploration graphs into one per method, marks winning-path edges as bold, uses `TypeNode.getLabel()` and strategy simple name labels
- [x] 4.3 Create `DumpResolvedOverlayStage` — builds combined property + transform overlay per method with transform summary on mapping edges
- [x] 4.4 Add warning-only error handling (catch `IOException`, log via `Messager` with `WARNING` kind, continue)

## 5. Pipeline integration

- [x] 5.1 Add three debug stage fields to `Pipeline` (Dagger-injected via constructor)
- [x] 5.2 Call `DumpPropertyGraphStage.execute(graphResult.value())` after `BuildGraphStage` succeeds
- [x] 5.3 Call `DumpTransformGraphStage.execute(resolveResult.value())` and `DumpResolvedOverlayStage.execute(graphResult.value(), resolveResult.value())` after `ResolveTransformsStage` succeeds

## 6. Testing

- [x] 6.1 Test `ProcessorOptions` parsing <!-- locale fix done, compilation clean -->: enabled/disabled, format selection, defaults, unknown format fallback
- [x] 6.2 Test `TransformResolution` wiring: exploration graph preserved on success and failure, `ResolvedMapping.getEdges()` delegation
- [x] 6.3 Test `DumpPropertyGraphStage` produces valid DOT output for a sample property graph
- [x] 6.4 Test `DumpTransformGraphStage` merges multiple exploration graphs and marks winning edges
- [x] 6.5 Test `DumpResolvedOverlayStage` produces combined overlay with transform annotations
- [x] 6.6 Test debug stages are no-ops when `ProcessorOptions.isDebugGraphs()` is `false`
- [x] 6.7 Test debug stage failure (simulated `IOException`) logs warning and does not abort pipeline
- [x] 6.8 Integration test: compile a sample mapper with `-Apercolate.debug.graphs=true` and verify output files exist with expected content

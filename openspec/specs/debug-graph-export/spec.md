# Debug Graph Export Spec

## Purpose

Defines the three optional debug dump stages that export intermediate processor graphs to files for developer inspection. Debug output is gated by processor options and uses jgrapht-io exporters in configurable formats (DOT, GraphML, JSON).

## Requirements

### Requirement: DumpPropertyGraphStage exports property graphs after BuildGraphStage
The `DumpPropertyGraphStage` SHALL accept a `MappingGraph` and, when `ProcessorOptions.isDebugGraphs()` is `true`, export each method's `DefaultDirectedGraph<Object, Object>` to a file via `Filer.createResource(SOURCE_OUTPUT, packageName, fileName)`. The file SHALL be named `{MapperSimpleName}_{methodName}_property.{ext}` where `{ext}` matches the configured format. Node labels SHALL use `toString()` of each graph node. Edge labels SHALL be `"access"` for `AccessEdge` and `"mapping"` for `MappingEdge`. When `isDebugGraphs()` is `false`, the stage SHALL be a no-op.

#### Scenario: Property graph exported as DOT
- **WHEN** `ProcessorOptions.isDebugGraphs()` returns `true` and format is `"dot"`
- **THEN** a file `MyMapper_mapToFoo_property.dot` SHALL be written to `SOURCE_OUTPUT` in the mapper's package containing a valid DOT digraph with labeled nodes and edges

#### Scenario: Debug disabled skips export
- **WHEN** `ProcessorOptions.isDebugGraphs()` returns `false`
- **THEN** no files SHALL be written by `DumpPropertyGraphStage`

### Requirement: DumpTransformGraphStage exports transform exploration graphs after ResolveTransformsStage
The `DumpTransformGraphStage` SHALL accept a `ResolvedModel` and, when debug is enabled, export one transform exploration graph per mapping method. For each method, the stage SHALL merge all `TransformResolution.getExplorationGraph()` instances from the method's `ResolvedMapping` list into a single `DefaultDirectedGraph<TypeNode, TransformEdge>`. The merged graph SHALL be written to `{MapperSimpleName}_{methodName}_transform.{ext}`. Node labels SHALL use `TypeNode.getLabel()`. Edge labels SHALL use the strategy's simple class name. Edges that appear on a winning `GraphPath` SHALL be marked as bold (DOT: `style=bold`, GraphML/JSON: attribute `winning=true`).

#### Scenario: Transform graph includes roads not taken
- **WHEN** `resolveTransformPath` explored edges A→B via `DirectAssignable` and A→C via `OptionalUnwrap` but only A→B was on the shortest path
- **THEN** the exported transform graph SHALL contain both edges, with A→B marked as bold/winning and A→C as normal

#### Scenario: One transform file per method
- **WHEN** a mapping method has three resolved mappings each with their own transform exploration graph
- **THEN** the stage SHALL produce one merged transform graph file for the method, not three separate files

### Requirement: DumpResolvedOverlayStage exports combined property-plus-transform graphs
The `DumpResolvedOverlayStage` SHALL accept both a `MappingGraph` and a `ResolvedModel` and, when debug is enabled, produce one overlay graph per mapping method. The overlay SHALL combine the property graph structure with resolved transform information: each `MappingEdge` SHALL be annotated with the transform path summary (e.g., `"String→String (DirectAssignable)"`). The file SHALL be named `{MapperSimpleName}_{methodName}_resolved.{ext}`.

#### Scenario: Overlay shows full mapping story
- **WHEN** a property graph maps `source.address.street` to `target.street` and the transform path is `String→String` via `DirectAssignable`
- **THEN** the overlay graph SHALL contain access edges from source root through the source chain, a mapping edge from `source.address.street` to `target.street` labeled with the transform summary, and access edges to the target root

### Requirement: Graph export supports configurable formats
All debug stages SHALL use the format specified by `ProcessorOptions.getDebugGraphsFormat()` to select the jgrapht-io exporter. Supported formats SHALL be `"dot"` (using `DOTExporter`), `"graphml"` (using `GraphMLExporter`), and `"json"` (using `JSONExporter`). The file extension SHALL match the format: `.dot`, `.graphml`, `.json`. An unrecognized format value SHALL fall back to `"dot"`.

#### Scenario: GraphML format selected
- **WHEN** `-Apercolate.debug.graphs.format=graphml` is passed
- **THEN** all debug stage output files SHALL use `.graphml` extension and valid GraphML content

#### Scenario: Unknown format falls back to DOT
- **WHEN** `-Apercolate.debug.graphs.format=unsupported` is passed
- **THEN** all debug stage output files SHALL use `.dot` extension and valid DOT content

### Requirement: Debug stages are Dagger-injectable
Each debug stage (`DumpPropertyGraphStage`, `DumpTransformGraphStage`, `DumpResolvedOverlayStage`) SHALL be a `final` class with `@RequiredArgsConstructor(onConstructor_ = @Inject)` and SHALL receive `ProcessorOptions` and `Filer` via constructor injection.

#### Scenario: Debug stage injection
- **WHEN** the Dagger component is built
- **THEN** `Pipeline` SHALL receive all three debug stages via constructor injection

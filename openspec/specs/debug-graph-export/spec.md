# Debug Graph Export Spec

## Purpose

Defines the three optional debug dump stages that export intermediate processor graphs to files for developer inspection. Debug output is gated by processor options and uses jgrapht-io exporters in configurable formats (DOT, GraphML, JSON).
## Requirements
### Requirement: Graph export supports configurable formats

`DumpGraphStage` SHALL use the format specified by `ProcessorOptions.getDebugGraphsFormat()` to select the jgrapht-io exporter. Supported formats SHALL be `"dot"` (using `DOTExporter`), `"graphml"` (using `GraphMLExporter`), and `"json"` (using `JSONExporter`). The file extension SHALL match the format: `.dot`, `.graphml`, `.json`. An unrecognized format value SHALL fall back to `"dot"`.

#### Scenario: GraphML format selected

- **WHEN** `-Apercolate.debug.graphs.format=graphml` is passed
- **THEN** `DumpGraphStage` output files SHALL use `.graphml` extension and valid GraphML content with a `winning` boolean attribute on bold edges

#### Scenario: Unknown format falls back to DOT

- **WHEN** `-Apercolate.debug.graphs.format=unsupported` is passed
- **THEN** `DumpGraphStage` output files SHALL use `.dot` extension and valid DOT content

### Requirement: DumpGraphStage exports method value graphs after ResolvePathStage

The `DumpGraphStage` SHALL accept `Map<MethodMatching, ValueGraph>` (from `BuildValueGraphStage`) and the resolved-path output (from `ResolvePathStage`) and, when `ProcessorOptions.isDebugGraphs()` is `true`, export one file per method.

For each method the stage SHALL write `{MapperSimpleName}_{methodName}_resolved.{ext}` via `Filer.createResource(SOURCE_OUTPUT, packageName, fileName)`, rendering the complete `ValueGraph` for that method. Every `ValueEdge` that appears on at least one winning `GraphPath` SHALL be marked bold (DOT: `style=bold`; GraphML/JSON: attribute `winning=true`). Edges not on any winning path SHALL be rendered unmarked. Node labels SHALL use `ValueNode.toString()`. Edge labels SHALL use a short discriminator (`"read"` for `PropertyReadEdge`, the strategy simple name for `TypeTransformEdge`, `"lift({KIND})"` for `LiftEdge`).

Resolution failures SHALL be representable by producing a dump with zero bold edges for the affected method; `DumpGraphStage` SHALL NOT itself emit diagnostics. When `isDebugGraphs()` is `false`, the stage SHALL be a no-op.

#### Scenario: Resolved graph exported as DOT

- **WHEN** `ProcessorOptions.isDebugGraphs()` returns `true` and format is `"dot"`
- **THEN** a file `MyMapper_mapToFoo_resolved.dot` SHALL be written to `SOURCE_OUTPUT` in the mapper's package containing a valid DOT digraph with bold edges on the winning paths and plain edges elsewhere

#### Scenario: Resolution failure dump has zero bold edges

- **WHEN** a method has at least one `MappingAssignment` whose resolved `GraphPath` is `null`
- **THEN** `DumpGraphStage` SHALL still produce `{MapperSimpleName}_{methodName}_resolved.{ext}`, with bold styling applied only to edges on assignments that did resolve; unresolved assignments contribute no bold edges

#### Scenario: Roads not taken remain in the dump

- **WHEN** the `ValueGraph` contains both `OptionalWrapStrategy` and `OptionalUnwrapStrategy` edges between two typed nodes, and only one direction lies on a winning path
- **THEN** both edges SHALL appear in the dump; the winning-direction edge SHALL be bold, the other plain

#### Scenario: Debug disabled skips export

- **WHEN** `ProcessorOptions.isDebugGraphs()` returns `false`
- **THEN** no files SHALL be written by `DumpGraphStage`

### Requirement: DumpGraphStage is Dagger-injectable

`DumpGraphStage` SHALL be a `final` class with `@RequiredArgsConstructor(onConstructor_ = @Inject)` and SHALL receive `ProcessorOptions` and `Filer` via constructor injection. The pipeline SHALL hold a single injected `DumpGraphStage` instance.

#### Scenario: DumpGraphStage injection

- **WHEN** the Dagger component is built
- **THEN** `Pipeline` SHALL receive exactly one `DumpGraphStage` via constructor injection

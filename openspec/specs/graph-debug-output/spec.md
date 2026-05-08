# Graph Debug Output Spec

## Purpose

This spec defines the DumpGraph stage and deterministic DOT renderer that serialize a `MapperGraph` to a DOT file for debugging and visualization purposes.

## Requirements

### Requirement: DumpGraph stage
The processor SHALL define a stage `DumpGraph` in package `io.github.joke.percolate.processor.stages.dump` that consumes a `MapperGraph` plus the originating `TypeElement` and, when enabled by `ProcessorOptions.debugGraphs`, writes a DOT representation of the graph to `StandardLocation.SOURCE_OUTPUT`. `DumpGraph` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

`DumpGraph` SHALL depend on `Filer`, `Diagnostics`, `ProcessorOptions`, and the deterministic DOT renderer.

#### Scenario: Option off does not write a file
- **WHEN** `DumpGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == false`
- **THEN** no resource is created via `Filer`
- **AND** no diagnostic is emitted

#### Scenario: Option on writes a .seed.dot file at SOURCE_OUTPUT
- **WHEN** `DumpGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == true` for a non-empty `MapperGraph` and a `TypeElement` representing FQN `com.example.PersonMapper`
- **THEN** `Filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "com.example.PersonMapper.seed.dot", <originating element>)` is invoked
- **AND** the resource's contents are the DOT representation of the graph as produced by the deterministic DOT renderer

#### Scenario: Empty graph does not write a file even when option is on
- **WHEN** `DumpGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == true` but the `MapperGraph` has zero nodes and zero edges
- **THEN** no resource is created via `Filer`
- **AND** no diagnostic is emitted

#### Scenario: Filer failure is reported as a warning, not an error
- **WHEN** `DumpGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == true` and `Filer.createResource(...)` (or the subsequent write) throws `IOException`
- **THEN** a warning diagnostic is emitted via `Diagnostics` referencing the originating `TypeElement`
- **AND** no error is emitted
- **AND** the method returns normally so that the compile is not aborted

### Requirement: Deterministic DOT renderer
The processor SHALL define a `DotRenderer` in `io.github.joke.percolate.processor.graph` that produces a `String` representation of a `MapperGraph` with the following determinism guarantees:
- Top-level digraph name SHALL be a stable encoding of the `@Mapper` `TypeElement`'s FQN.
- Vertices SHALL be written in ascending `Node.id()` order.
- Edges SHALL be written in ascending natural `Edge` order.
- Attribute key/value pairs on a single DOT statement SHALL be written in ascending key order (TreeMap iteration).
- Whitespace between statements SHALL be a single `\n`. The output SHALL end with a single trailing `\n`.

The renderer SHALL group nodes by their `Scope` into DOT subgraphs with `cluster_<scope-encoding>` names. Each cluster SHALL declare its `label` attribute with the human-readable scope description.

The renderer SHALL escape DOT-special characters (`"`, `\`, newlines, `<`, `>`) in any string written into a quoted DOT context.

#### Scenario: Output is byte-stable across runs given the same graph
- **WHEN** the same `MapperGraph` is rendered twice in two separate JVM runs
- **THEN** the produced `String`s are identical byte-for-byte

#### Scenario: Per-method clusters
- **WHEN** rendering a graph with nodes scoped to `map(Person)` and to `map(Address)`
- **THEN** the output contains exactly one `subgraph cluster_<encoding-of-map(Person)> { ... }` and exactly one `subgraph cluster_<encoding-of-map(Address)> { ... }`
- **AND** every node is rendered inside the cluster matching its scope

#### Scenario: Vertex iteration order
- **WHEN** rendering a graph
- **THEN** vertex statements appear in ascending `Node.id()` order

#### Scenario: Edge iteration order
- **WHEN** rendering a graph
- **THEN** edge statements appear in ascending natural `Edge` order

#### Scenario: Attribute ordering
- **WHEN** a node or edge statement carries multiple attributes (e.g., `label`, `shape`, `weight`)
- **THEN** the attributes are written in ascending key order

#### Scenario: Special characters in labels are escaped
- **WHEN** a node's label contains `"` or `\` or a newline character
- **THEN** the rendered DOT escapes those characters using the standard DOT escape rules so that the output is parseable by Graphviz

### Requirement: Phantom node cluster grouping
The DOT renderer SHALL render every phantom container element node (a `Node` whose `loc` is `ElementLocation`) inside the same `cluster_<scope-encoding>` subgraph as its `parent` node's scope. The renderer SHALL look up the parent via `Node.parent` and use the parent's `Scope` to determine cluster membership, ignoring the phantom's own `Scope` field for cluster placement when it differs.

In this change `SeedGraph` does NOT emit phantom nodes; this requirement applies whenever a phantom node is constructed and added directly (e.g., in tests). The renderer is required to be ready for phantoms before Phase 2 strategy work begins.

#### Scenario: Phantom node renders inside its parent's cluster
- **WHEN** a `MapperGraph` is constructed with a parent container node scoped to `MethodScope(<map(Foo)>)` and a phantom node with `loc = ElementLocation` whose `parent` references that container node, and the renderer is invoked
- **THEN** the DOT output places the phantom node's vertex statement inside `cluster_<encoding-of-map(Foo)>`

#### Scenario: Phantom node without parent fails fast
- **WHEN** the renderer encounters a node with `loc = ElementLocation` and `parent = Optional.empty()`
- **THEN** an unchecked exception is thrown identifying the offending node — the schema invariant on phantoms is enforced at render time

### Requirement: Edge label includes EdgeKind marker
The DOT renderer SHALL include each edge's `kind` (the `EdgeKind` enum value) as part of the edge's label or attributes so that DOT inspection makes the edge type visible without consulting the source graph data.

#### Scenario: SEED edge label includes the kind
- **WHEN** the renderer writes an edge with `kind == EdgeKind.SEED`
- **THEN** the edge's attributes include a marker identifying it as a `SEED` edge (e.g., the literal `SEED` in the label, or a dedicated attribute)

#### Scenario: Non-SEED edge labels are kind-aware
- **WHEN** the renderer writes an edge with `kind ∈ {REALISED, MARKER, SUB_SEED}` (constructed in tests; not produced by `SeedGraph`)
- **THEN** the edge's attributes include the corresponding kind marker, distinguishable from a `SEED` edge

### Requirement: Node and edge visual distinction
The DOT renderer SHALL render nodes with shape attributes that visually distinguish source-located, target-located, and phantom container element nodes. Source-located nodes SHALL render with one shape (e.g., `box`); target-located nodes SHALL render with another (e.g., `oval`); phantom container element nodes SHALL render with a third distinct shape (e.g., `diamond`).

The DOT renderer SHALL render edges with style attributes (colour and/or line style) keyed off `Edge.kind`. The styling table SHALL define a distinct visual for each `EdgeKind` value (`SEED`, `REALISED`, `MARKER`, `SUB_SEED`) so that mixed-kind graphs are readable at a glance. The exact colour table is implementation-defined but SHALL be stable across runs.

Edge labels SHALL include the edge's `weight`. When `weight == Weights.SENTINEL_UNREALISED` the renderer SHALL emit the literal `∞` in place of the numeric value (cosmetic but pinned for byte stability across runs). Edge labels SHALL include a marker that the edge is directive-seeded when `directive` is non-empty.

Edge labels SHALL include `strategyClassFqn` when present (rendered as a stable string — e.g., the FQN itself, possibly truncated to its simple name for readability). This surfaces strategy provenance directly in the DOT output for diagnostics. The renderer SHALL NOT attempt to render `Edge.codegen` — codegen closures are opaque and the DOT representation describes the path structure, not the generated code.

#### Scenario: Source nodes render as box
- **WHEN** rendering a node whose `loc` is a `SourceLocation`
- **THEN** the DOT output contains `shape=box` (or another stable shape attribute documented in the implementation) for that node's statement

#### Scenario: Target nodes render with a different shape than source nodes
- **WHEN** rendering a node whose `loc` is a `TargetLocation`
- **THEN** the DOT output uses a shape distinct from the one used for `SourceLocation` nodes

#### Scenario: Phantom nodes render with a third distinct shape
- **WHEN** rendering a node whose `loc` is `ElementLocation`
- **THEN** the DOT output uses a shape distinct from both the source-node and target-node shapes

#### Scenario: Edge style is keyed off EdgeKind
- **WHEN** the renderer writes an edge with `kind == EdgeKind.SEED`
- **THEN** the edge's style attributes (colour and/or line style) match the documented `SEED` styling, and are distinct from those used for `REALISED`, `MARKER`, and `SUB_SEED` edges

#### Scenario: Sentinel weight renders as infinity
- **WHEN** the renderer writes an edge with `weight == Weights.SENTINEL_UNREALISED`
- **THEN** the edge's label contains the literal `∞` (U+221E) instead of the numeric value

#### Scenario: Directive-seeded edge is marked
- **WHEN** rendering an edge whose `directive` `Optional` is non-empty
- **THEN** the edge's attributes include a marker indicating its directive origin distinguishable from a strategy-emitted edge

#### Scenario: strategyClassFqn appears in edge label when present
- **WHEN** rendering an edge with `strategyClassFqn = Optional.of("com.example.GetterReadStrategy")`
- **THEN** the edge's label or attributes include a stable rendering of that FQN (full or simple name as documented by the implementation)

#### Scenario: Codegen closures are not rendered
- **WHEN** rendering an edge with non-empty `codegen`
- **THEN** the DOT output contains no representation of the closure object itself (no `lambda$`, no hash, no toString of the closure)
- **AND** the rest of the edge's attributes render normally

### Requirement: File naming
The DOT file SHALL be named `<MapperFQN>.seed.dot`. The infix `.seed.` SHALL be reserved so that future expansion stages can write `<MapperFQN>.expanded.dot` alongside without collision.

#### Scenario: File name uses .seed.dot infix
- **WHEN** `DumpGraph.apply(...)` writes a file for `com.example.PersonMapper`
- **THEN** the file name passed to `Filer.createResource(...)` is exactly `"com.example.PersonMapper.seed.dot"`

### Requirement: DumpExpandedGraph stage

The processor SHALL define a stage `DumpExpandedGraph` in package `io.github.joke.percolate.processor.stages.dump` that consumes the post-validation `MapperGraph` plus the originating `TypeElement` and, when enabled by `ProcessorOptions.debugGraphs`, writes a DOT representation of the *full* graph (including SEED, REALISED, MARKER, and SUB_SEED edges) to `StandardLocation.SOURCE_OUTPUT`. `DumpExpandedGraph` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

`DumpExpandedGraph` SHALL depend on `Filer`, `Diagnostics`, `ProcessorOptions`, and the deterministic DOT renderer (the same renderer used by `DumpGraph`).

`DumpExpandedGraph` SHALL run after `ValidateRealisationStage` in the pipeline. It SHALL write its file *regardless* of whether the mapper was scarred by validation — debug output is most valuable on failure.

#### Scenario: Option off does not write a file
- **WHEN** `DumpExpandedGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == false`
- **THEN** no resource is created via `Filer`
- **AND** no diagnostic is emitted

#### Scenario: Option on writes a .expanded.dot file at SOURCE_OUTPUT
- **WHEN** `DumpExpandedGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == true` for a non-empty `MapperGraph` and a `TypeElement` representing FQN `com.example.PersonMapper`
- **THEN** `Filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "com.example.PersonMapper.expanded.dot", <originating element>)` is invoked
- **AND** the resource's contents are the DOT representation of the full post-expansion graph as produced by the deterministic DOT renderer

#### Scenario: File is written even when mapper has validation errors
- **WHEN** `DumpExpandedGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == true` for a `MapperGraph` whose mapper was scarred by `ValidateMarkersPhase` or had Tier-3 errors emitted
- **THEN** the `.expanded.dot` file is still written
- **AND** the file contents include all SEED, REALISED, MARKER, and SUB_SEED edges present in the graph at the time of the dump

#### Scenario: Empty graph does not write a file even when option is on
- **WHEN** `DumpExpandedGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == true` but the `MapperGraph` has zero nodes and zero edges
- **THEN** no resource is created via `Filer`
- **AND** no diagnostic is emitted

#### Scenario: Filer failure is reported as a warning, not an error
- **WHEN** `DumpExpandedGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == true` and `Filer.createResource(...)` (or the subsequent write) throws `IOException`
- **THEN** a warning diagnostic is emitted via `Diagnostics` referencing the originating `TypeElement`
- **AND** no error is emitted
- **AND** the method returns normally so that the compile is not aborted

### Requirement: Expanded DOT file naming

The expanded-graph DOT file SHALL be named `<MapperFQN>.expanded.dot`. The `.expanded.` infix mirrors the `.seed.` infix used by `DumpGraph` and ensures the two DOT files coexist in the same `SOURCE_OUTPUT` directory without collision.

#### Scenario: File name uses .expanded.dot infix
- **WHEN** `DumpExpandedGraph.apply(...)` writes a file for `com.example.PersonMapper`
- **THEN** the file name passed to `Filer.createResource(...)` is exactly `"com.example.PersonMapper.expanded.dot"`

#### Scenario: Both .seed.dot and .expanded.dot coexist
- **WHEN** the pipeline completes for `com.example.PersonMapper` with `ProcessorOptions.debugGraphs == true` and a non-empty graph
- **THEN** both `com.example.PersonMapper.seed.dot` and `com.example.PersonMapper.expanded.dot` are present in `SOURCE_OUTPUT`

### Requirement: DOT renderer renders REALISED, MARKER, and SUB_SEED edges

The DOT renderer SHALL emit DOT statements for `EdgeKind.REALISED`, `EdgeKind.MARKER`, and `EdgeKind.SUB_SEED` edges using the kind-aware styling table shipped by the `align-graph-for-expansion` change. The renderer SHALL apply the same per-kind colour / line-style mapping already specified for SEED edges, extended uniformly to the other three kinds. Edge ordering SHALL remain ascending natural `Edge` order across all kinds (no kind-based grouping at the top level — the visual styling already discriminates).

The renderer SHALL include each edge's `strategyClassFqn` in its attributes when present (per the requirement shipped by the alignment change). For REALISED edges, this surfaces strategy provenance directly in the DOT output for diagnostic and review purposes.

For edges with a non-empty `groupId`, the renderer SHALL include the `groupId` value as an edge attribute (or as part of the label) so that grouped edges (e.g., constructor argument bundles) are visually identifiable.

#### Scenario: REALISED edge renders with documented styling
- **WHEN** the renderer writes an edge with `kind == EdgeKind.REALISED`
- **THEN** the edge's style attributes match the documented `REALISED` styling, distinct from `SEED`, `MARKER`, and `SUB_SEED`

#### Scenario: MARKER edge renders with documented styling
- **WHEN** the renderer writes an edge with `kind == EdgeKind.MARKER`
- **THEN** the edge's style attributes match the documented `MARKER` styling, distinct from the other three kinds

#### Scenario: SUB_SEED edge renders with documented styling
- **WHEN** the renderer writes an edge with `kind == EdgeKind.SUB_SEED`
- **THEN** the edge's style attributes match the documented `SUB_SEED` styling, distinct from the other three kinds

#### Scenario: groupId appears in edge label when present
- **WHEN** the renderer writes a REALISED edge whose `groupId` is non-empty
- **THEN** the edge's attributes include the `groupId` value (rendered as a stable string)
- **AND** edges sharing the same `groupId` render with the same `groupId` value, allowing the reader to identify the group

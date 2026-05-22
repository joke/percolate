## ADDED Requirements

### Requirement: TransformsView filter on MapperGraph

`MapperGraph` SHALL expose an accessor `transformsView()` that returns a `TransformsView` — a non-destructive, pure-function filter over the underlying graph. The view SHALL be implemented as a JGraphT `MaskSubgraph` so the full graph is not copied, modified, or rebuilt. The view SHALL expose `Stream<Node> nodes()`, `Stream<Edge> edges()`, and `Stream<Node> nodesByScope(Scope)` with the same ordering guarantees as the corresponding `MapperGraph` methods.

The view's **edge mask** SHALL retain only edges with `kind == EdgeKind.REALISED`. Edges of kind `SEED`, `SUB_SEED`, `MARKER`, and `ELEMENT_SEED` SHALL be hidden.

The view's **vertex mask** SHALL retain only nodes that are endpoints of at least one retained `REALISED` edge. This includes typed `SourceLocation` and `TargetLocation` nodes and `ElementLocation` phantom nodes when a per-element strategy emitted a `REALISED` edge incident on the phantom.

The view SHALL NOT filter by reachability. A `REALISED` edge whose source is not produced by any other edge (a "dead-end transformation" — for example `SetWrap`'s `Human.Address → Set<Human.Address>` edge when no other strategy produces `Human.Address`) SHALL appear in the view. The view is the menu of transformations the engine produced, not the subset that any directive will actually use; selection among transformations is a future codegen-time concern.

The view SHALL NOT mutate the underlying `MapperGraph`.

#### Scenario: transformsView accessor returns a non-null view
- **WHEN** `MapperGraph.transformsView()` is invoked on a non-empty graph
- **THEN** the returned `TransformsView` instance exposes `nodes()`, `edges()`, and `nodesByScope(Scope)` methods returning streams in the documented order

#### Scenario: Only REALISED edges pass the edge mask
- **WHEN** a graph containing edges of every kind (`SEED`, `REALISED`, `SUB_SEED`, `MARKER`, `ELEMENT_SEED`) is exposed via `transformsView()`
- **THEN** every edge in the stream returned by `edges()` has `kind == EdgeKind.REALISED`
- **AND** no edge of any other kind appears in the stream

#### Scenario: Only nodes incident on REALISED edges pass the vertex mask
- **WHEN** the underlying graph contains a `REALISED` edge between nodes A and B, plus a third node C connected only by a `SUB_SEED` edge
- **AND** `transformsView()` is queried
- **THEN** `nodes()` contains A and B and SHALL NOT contain C

#### Scenario: Dead-end transformations are retained
- **WHEN** the underlying graph contains a `REALISED` edge `X → Y` and no other edge produces `X`
- **AND** `transformsView()` is queried
- **THEN** `edges()` contains the `X → Y` edge
- **AND** `nodes()` contains both `X` and `Y`

#### Scenario: ElementLocation phantoms are retained when endpoints of REALISED edges
- **WHEN** the underlying graph contains a `REALISED` edge between two `ElementLocation` phantom nodes (a per-element strategy's emission)
- **AND** `transformsView()` is queried
- **THEN** both phantom nodes appear in `nodes()`
- **AND** the `REALISED` edge between them appears in `edges()`

#### Scenario: View construction does not mutate the underlying graph
- **WHEN** `MapperGraph.transformsView()` is invoked
- **THEN** the underlying `MapperGraph` retains all its original nodes and edges (including those the view masks), as observable via `MapperGraph.nodes()` and `MapperGraph.edges()`

### Requirement: DumpFullGraph stage

The processor SHALL define a stage `DumpFullGraph` in package `io.github.joke.percolate.processor.stages.dump` that consumes the post-expansion `MapperGraph` plus the originating `TypeElement` and, when enabled by `ProcessorOptions.debugGraphs`, writes a DOT representation of the **entire underlying graph** (every edge of every kind, every node) to `StandardLocation.SOURCE_OUTPUT`. `DumpFullGraph` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

`DumpFullGraph` SHALL depend on `Filer`, `Diagnostics`, `ProcessorOptions`, and the deterministic DOT renderer.

`DumpFullGraph` SHALL run after `ExpandStage` and before `ValidateRealisationStage` in the pipeline. It SHALL write its file unconditionally on every per-mapper run (subject to the option flag and the empty-graph short-circuit), so the full graph is available on disk even when validation emits errors.

The dumped file SHALL contain every node and edge in the underlying `MapperGraph`. No filtering, no alive/dead styling, no analysis-derived attributes. The renderer's job is to render exactly what it is given.

#### Scenario: Option off does not write a file
- **WHEN** `DumpFullGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == false`
- **THEN** no resource is created via `Filer`
- **AND** no diagnostic is emitted

#### Scenario: Option on writes a .full.dot file at SOURCE_OUTPUT
- **WHEN** `DumpFullGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == true` for a non-empty `MapperGraph` and a `TypeElement` representing FQN `com.example.PersonMapper`
- **THEN** `Filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "com.example.PersonMapper.full.dot", <originating element>)` is invoked
- **AND** the resource's contents are the DOT representation of the underlying `MapperGraph` (no filtering) as produced by the deterministic DOT renderer

#### Scenario: File is written even when subsequent validation emits errors
- **WHEN** `DumpFullGraph.apply(...)` is invoked, then `ValidateRealisationStage` emits a satisfy() error for the same mapper
- **THEN** the `.full.dot` file is still present on disk
- **AND** the file contents include every edge and node of the underlying graph at the time of the dump

#### Scenario: Empty graph does not write a file even when option is on
- **WHEN** `DumpFullGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == true` but the `MapperGraph` has zero nodes and zero edges
- **THEN** no resource is created via `Filer`
- **AND** no diagnostic is emitted

#### Scenario: Filer failure is reported as a warning, not an error
- **WHEN** `DumpFullGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == true` and `Filer.createResource(...)` (or the subsequent write) throws `IOException`
- **THEN** a warning diagnostic is emitted via `Diagnostics` referencing the originating `TypeElement`
- **AND** no error is emitted
- **AND** the method returns normally so that the compile is not aborted

### Requirement: Full DOT file naming

The full-graph DOT file SHALL be named `<MapperFQN>.full.dot`. The `.full.` infix coexists with `.seed.` and `.transforms.` in the same `SOURCE_OUTPUT` directory without collision.

#### Scenario: File name uses .full.dot infix
- **WHEN** `DumpFullGraph.apply(...)` writes a file for `com.example.PersonMapper`
- **THEN** the file name passed to `Filer.createResource(...)` is exactly `"com.example.PersonMapper.full.dot"`

### Requirement: DumpTransforms stage

The processor SHALL define a stage `DumpTransforms` in package `io.github.joke.percolate.processor.stages.dump` that consumes the post-expansion `MapperGraph` plus the originating `TypeElement` and, when enabled by `ProcessorOptions.debugGraphs`, writes a DOT representation of the **transforms view** of the graph (`MapperGraph.transformsView()`) to `StandardLocation.SOURCE_OUTPUT`. `DumpTransforms` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

`DumpTransforms` SHALL depend on `Filer`, `Diagnostics`, `ProcessorOptions`, and the deterministic DOT renderer (the same renderer used by `DumpGraph` and `DumpFullGraph`).

`DumpTransforms` SHALL run after `DumpFullGraph` and before `ValidateRealisationStage` in the pipeline. It SHALL write its file unconditionally on every per-mapper run (subject to the option flag and the empty-graph short-circuit).

The dumped file SHALL contain only the nodes and edges exposed by the transforms view: every `REALISED` edge and every node incident on at least one such edge (typed `SourceLocation` / `TargetLocation` nodes plus `ElementLocation` phantoms used as endpoints). Edges of kind `SEED`, `SUB_SEED`, `MARKER`, and `ELEMENT_SEED` SHALL NOT appear in the file.

#### Scenario: Option off does not write a file
- **WHEN** `DumpTransforms.apply(...)` is invoked with `ProcessorOptions.debugGraphs == false`
- **THEN** no resource is created via `Filer`
- **AND** no diagnostic is emitted

#### Scenario: Option on writes a .transforms.dot file at SOURCE_OUTPUT
- **WHEN** `DumpTransforms.apply(...)` is invoked with `ProcessorOptions.debugGraphs == true` for a non-empty `MapperGraph` and a `TypeElement` representing FQN `com.example.PersonMapper`
- **THEN** `Filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "com.example.PersonMapper.transforms.dot", <originating element>)` is invoked
- **AND** the resource's contents are the DOT representation of `graph.transformsView()` as produced by the deterministic DOT renderer

#### Scenario: File contains only REALISED edges
- **WHEN** `DumpTransforms.apply(...)` writes a file for a graph containing edges of every kind
- **THEN** every edge statement in the resulting DOT file corresponds to a `REALISED` edge in the underlying graph
- **AND** no edge of any other kind appears in the file

#### Scenario: File is written even when subsequent validation emits errors
- **WHEN** `DumpTransforms.apply(...)` is invoked, then `ValidateRealisationStage` emits a satisfy() error for the same mapper
- **THEN** the `.transforms.dot` file is still present on disk

#### Scenario: Empty graph does not write a file even when option is on
- **WHEN** `DumpTransforms.apply(...)` is invoked with `ProcessorOptions.debugGraphs == true` but the `MapperGraph` has zero nodes and zero edges
- **THEN** no resource is created via `Filer`
- **AND** no diagnostic is emitted

#### Scenario: Filer failure is reported as a warning, not an error
- **WHEN** `DumpTransforms.apply(...)` is invoked with `ProcessorOptions.debugGraphs == true` and `Filer.createResource(...)` (or the subsequent write) throws `IOException`
- **THEN** a warning diagnostic is emitted via `Diagnostics` referencing the originating `TypeElement`
- **AND** no error is emitted
- **AND** the method returns normally so that the compile is not aborted

### Requirement: Transforms DOT file naming

The transforms-graph DOT file SHALL be named `<MapperFQN>.transforms.dot`. The `.transforms.` infix coexists with `.seed.` and `.full.` in the same `SOURCE_OUTPUT` directory without collision.

#### Scenario: File name uses .transforms.dot infix
- **WHEN** `DumpTransforms.apply(...)` writes a file for `com.example.PersonMapper`
- **THEN** the file name passed to `Filer.createResource(...)` is exactly `"com.example.PersonMapper.transforms.dot"`

#### Scenario: All three dot files coexist
- **WHEN** the pipeline completes for `com.example.PersonMapper` with `ProcessorOptions.debugGraphs == true` and a non-empty graph
- **THEN** all three files `com.example.PersonMapper.seed.dot`, `com.example.PersonMapper.full.dot`, and `com.example.PersonMapper.transforms.dot` are present in `SOURCE_OUTPUT`

## MODIFIED Requirements

### Requirement: File naming
The DOT file produced by `DumpGraph` SHALL be named `<MapperFQN>.seed.dot`. The infix `.seed.` SHALL coexist with the `.full.` and `.transforms.` infixes (produced by `DumpFullGraph` and `DumpTransforms` respectively) in the same `SOURCE_OUTPUT` directory without collision.

#### Scenario: File name uses .seed.dot infix
- **WHEN** `DumpGraph.apply(...)` writes a file for `com.example.PersonMapper`
- **THEN** the file name passed to `Filer.createResource(...)` is exactly `"com.example.PersonMapper.seed.dot"`

### Requirement: DOT renderer renders REALISED, MARKER, and SUB_SEED edges

The DOT renderer SHALL emit DOT statements for `EdgeKind.REALISED`, `EdgeKind.MARKER`, `EdgeKind.SUB_SEED`, and `EdgeKind.ELEMENT_SEED` edges in addition to `EdgeKind.SEED`. Edge ordering SHALL remain ascending natural `Edge` order across all kinds (no kind-based grouping at the top level — the visual styling discriminates).

Per the "Node and edge visual distinction" requirement, REALISED edges SHALL include the strategy short name and weight in their label, SUB_SEED edges SHALL include only the kind token, and ELEMENT_SEED edges SHALL be rendered with a distinct colour and include the kind token in their label. SEED and MARKER edges retain their prior label content when rendered.

#### Scenario: All edge kinds are emitted when given to the renderer
- **WHEN** rendering a graph containing one edge of each kind (`SEED`, `REALISED`, `SUB_SEED`, `MARKER`, `ELEMENT_SEED`)
- **THEN** the DOT output contains exactly one edge statement per input edge
- **AND** each statement is keyed off its endpoints in the documented edge ordering

#### Scenario: Edge ordering is the natural edge order across all kinds
- **WHEN** rendering a graph with mixed-kind edges
- **THEN** edge statements appear in ascending natural `Edge` order regardless of `kind`

### Requirement: Edge label includes EdgeKind marker

Each rendered edge's `kind` SHALL be identifiable from the DOT output without consulting the source graph data. The required identifier varies by kind:

- For `EdgeKind.SEED` edges, the edge `label` attribute SHALL include the literal token `SEED`.
- For `EdgeKind.SUB_SEED` edges, the edge `label` attribute SHALL contain the literal token `SUB_SEED` (and no other content — see the "Node and edge visual distinction" requirement).
- For `EdgeKind.ELEMENT_SEED` edges, the edge `label` attribute SHALL contain the literal token `ELEMENT_SEED` (and no other content — see the "Node and edge visual distinction" requirement).
- For `EdgeKind.REALISED` edges, the kind is identified by the combination of the edge's style attributes and the strategy short name in its label (see the "Node and edge visual distinction" requirement); the explicit token `REALISED` is not required in the label.
- For `EdgeKind.MARKER` edges (rendered only when the renderer is given such an edge directly, outside the transforms view), the `label` attribute SHALL include the literal token `MARKER`.

#### Scenario: SEED edge label includes the SEED token
- **WHEN** the renderer writes an edge with `kind == EdgeKind.SEED`
- **THEN** the edge's `label` attribute contains the literal `SEED`

#### Scenario: SUB_SEED edge label is the SUB_SEED token
- **WHEN** the renderer writes an edge with `kind == EdgeKind.SUB_SEED`
- **THEN** the edge's `label` attribute is the literal `SUB_SEED`

#### Scenario: ELEMENT_SEED edge label is the ELEMENT_SEED token
- **WHEN** the renderer writes an edge with `kind == EdgeKind.ELEMENT_SEED`
- **THEN** the edge's `label` attribute is the literal `ELEMENT_SEED`

#### Scenario: REALISED edge kind is identified by style and strategy
- **WHEN** the renderer writes an edge with `kind == EdgeKind.REALISED` and `strategyClassFqn == Optional.of("io.github.joke.percolate.spi.builtins.GetterRead")`
- **THEN** the edge's `label` attribute contains the simple class name `GetterRead`
- **AND** the edge's style attributes match the documented REALISED styling (distinct from SEED, SUB_SEED, and ELEMENT_SEED)
- **AND** the edge's `label` attribute does NOT contain the literal token `REALISED`

#### Scenario: MARKER edge rendered directly retains the MARKER token
- **WHEN** the renderer is given an edge with `kind == EdgeKind.MARKER` and renders it directly (outside the transforms view)
- **THEN** the edge's `label` attribute contains the literal `MARKER`

### Requirement: Node and edge visual distinction

The DOT renderer SHALL render nodes with shape attributes that visually distinguish source-located, target-located, and phantom container element nodes. Source-located nodes SHALL render with one shape (e.g., `box`); target-located nodes SHALL render with another (e.g., `oval`); phantom container element nodes SHALL render with a third distinct shape (e.g., `diamond`).

The DOT renderer SHALL render edges with style attributes (colour and/or line style) keyed off `Edge.kind`. The styling table SHALL define a distinct visual for each of `SEED`, `REALISED`, `SUB_SEED`, and `ELEMENT_SEED`. REALISED edges SHALL render with the heaviest visible stroke (e.g., `solid` line with elevated `penwidth`) — they represent the load-bearing transformations of the graph and SHALL dominate the visual hierarchy. SUB_SEED edges SHALL render with a visually secondary style (e.g., `solid` line, low `penwidth`, gray colour) so the eye lands on REALISED first. ELEMENT_SEED edges SHALL render with a visually secondary style distinct from `SUB_SEED` (e.g., `solid` line, low `penwidth`, a muted blue colour) so per-element promises are distinguishable from sub-directive promises. SEED edges SHALL retain their prior styling (relevant for `seed.dot` rendering where they are the only edge kind present). MARKER edges, when rendered directly (outside the transforms view), MAY use the default fallback style; no dedicated MARKER style is required. The exact style attribute values are implementation-defined but SHALL be stable across runs.

For REALISED edges, the `label` attribute SHALL include the simple class name derived from `strategyClassFqn` and the edge's `weight`, formatted in a stable, byte-deterministic way (e.g., `GetterRead (1)`). When `weight == Weights.SENTINEL_UNREALISED` the renderer SHALL emit the literal `∞` (U+221E) in place of the numeric value. For REALISED edges with a non-empty `groupId`, the renderer SHALL include the `groupId` value as an edge attribute (or as part of the label) so grouped edges (e.g., constructor argument bundles) are visually identifiable.

For SUB_SEED edges, the `label` attribute SHALL contain only the literal token `SUB_SEED`. The strategy attribution, weight, and directive marker SHALL NOT appear in SUB_SEED edge labels — they are intentionally omitted to reduce visual clutter. The strategy that emitted a SUB_SEED is recoverable from the REALISED edge the same strategy contributes elsewhere in the graph.

For ELEMENT_SEED edges, the `label` attribute SHALL contain only the literal token `ELEMENT_SEED`. Strategy attribution and weight SHALL NOT appear in the label for the same reason.

For SEED edges (relevant in `seed.dot`), the prior label format is retained: kind token, weight (with `∞` rendering for the sentinel), and a directive marker when `directive` is non-empty.

The renderer SHALL NOT attempt to render `Edge.codegen` — codegen closures are opaque and the DOT representation describes the path structure, not the generated code.

#### Scenario: Source nodes render as box
- **WHEN** rendering a node whose `loc` is a `SourceLocation`
- **THEN** the DOT output contains `shape=box` (or another stable shape attribute documented in the implementation) for that node's statement

#### Scenario: Target nodes render with a different shape than source nodes
- **WHEN** rendering a node whose `loc` is a `TargetLocation`
- **THEN** the DOT output uses a shape distinct from the one used for `SourceLocation` nodes

#### Scenario: Phantom nodes render with a third distinct shape
- **WHEN** rendering a node whose `loc` is `ElementLocation`
- **THEN** the DOT output uses a shape distinct from both the source-node and target-node shapes

#### Scenario: REALISED edge style is heaviest visible stroke
- **WHEN** the renderer writes an edge with `kind == EdgeKind.REALISED`
- **THEN** the edge's style attributes are the documented REALISED styling
- **AND** that styling is visually heavier than the SUB_SEED, ELEMENT_SEED, and SEED stylings

#### Scenario: SUB_SEED edge style is visually secondary
- **WHEN** the renderer writes an edge with `kind == EdgeKind.SUB_SEED`
- **THEN** the edge's style attributes are the documented SUB_SEED styling, distinct from REALISED, ELEMENT_SEED, and SEED

#### Scenario: ELEMENT_SEED edge style is distinct from SUB_SEED
- **WHEN** the renderer writes an edge with `kind == EdgeKind.ELEMENT_SEED`
- **THEN** the edge's style attributes are the documented ELEMENT_SEED styling
- **AND** the styling is visually distinct from the SUB_SEED styling

#### Scenario: Sentinel weight renders as infinity in REALISED labels
- **WHEN** the renderer writes a REALISED edge with `weight == Weights.SENTINEL_UNREALISED`
- **THEN** the edge's `label` attribute contains the literal `∞` (U+221E) instead of the numeric value

#### Scenario: REALISED edge label contains strategy short name and weight
- **WHEN** rendering a REALISED edge with `strategyClassFqn == Optional.of("io.github.joke.percolate.spi.builtins.GetterRead")` and `weight == 1`
- **THEN** the edge's `label` attribute contains both the literal `GetterRead` and the literal `1`
- **AND** the `label` does NOT contain the package prefix `io.github.joke.percolate.spi.builtins`

#### Scenario: SUB_SEED edge label contains only the kind token
- **WHEN** rendering a SUB_SEED edge with `strategyClassFqn == Optional.of("io.github.joke.percolate.spi.builtins.OptionalWrap")`, `weight == Weights.SENTINEL_UNREALISED`, and a non-empty `directive`
- **THEN** the edge's `label` attribute is exactly `SUB_SEED`
- **AND** the `label` does NOT contain the strategy class name (full or simple)
- **AND** the `label` does NOT contain the literal `∞` (since the weight is omitted)
- **AND** the `label` does NOT contain the literal `directive`

#### Scenario: ELEMENT_SEED edge label contains only the kind token
- **WHEN** rendering an ELEMENT_SEED edge with `strategyClassFqn == Optional.of("io.github.joke.percolate.spi.builtins.SetMap")` and `weight == Weights.SENTINEL_UNREALISED`
- **THEN** the edge's `label` attribute is exactly `ELEMENT_SEED`
- **AND** the `label` does NOT contain the strategy class name (full or simple)
- **AND** the `label` does NOT contain the literal `∞`

#### Scenario: SEED edge label retains kind, weight, and directive marker
- **WHEN** rendering a SEED edge with `weight == Weights.SENTINEL_UNREALISED` and a non-empty `directive`
- **THEN** the edge's `label` attribute contains the literal `SEED`
- **AND** the `label` contains the literal `∞`
- **AND** the `label` contains a marker (e.g., the literal `directive`) indicating its directive origin

#### Scenario: groupId appears in REALISED edge label when present
- **WHEN** the renderer writes a REALISED edge whose `groupId` is non-empty
- **THEN** the edge's attributes include the `groupId` value (rendered as a stable string)
- **AND** edges sharing the same `groupId` render with the same `groupId` value

#### Scenario: Codegen closures are not rendered
- **WHEN** rendering an edge with non-empty `codegen`
- **THEN** the DOT output contains no representation of the closure object itself (no `lambda$`, no hash, no toString of the closure)
- **AND** the rest of the edge's attributes render normally

## REMOVED Requirements

### Requirement: Expanded view filter on MapperGraph

**Reason**: The `expandedView()` was a filter that stripped `SEED` and `MARKER` edges from the live graph. Its semantics conflated user-directive seeds with strategy-emitted element seeds (both encoded as `EdgeKind.SEED` at the time), causing element-seed promises to disappear from the dump. It is replaced by `transformsView()`, which is a structural filter (REALISED edges + their endpoints, including `ElementLocation` phantoms) — see the "TransformsView filter on MapperGraph" requirement.

**Migration**: Callers of `MapperGraph.expandedView()` SHALL be updated to call `MapperGraph.transformsView()`. The two views are not semantically equivalent — `transformsView` excludes `SUB_SEED` edges that `expandedView` retained — but `transformsView` is the correct view for codegen and for the post-expansion debug dump.

### Requirement: DumpExpandedGraph stage

**Reason**: Renamed to `DumpTransforms` and its semantics changed (it now renders `transformsView`, not `expandedView`). The stage's purpose — write a post-expansion debug dump — is preserved by `DumpTransforms`.

**Migration**: Pipeline wiring SHALL replace `DumpExpandedGraph` with `DumpTransforms` (see the "DumpTransforms stage" requirement). Any tests or fixtures that referenced `DumpExpandedGraph` by name SHALL be updated.

### Requirement: Expanded DOT file naming

**Reason**: The `.expanded.dot` filename is replaced by `.transforms.dot` (see the "Transforms DOT file naming" requirement) to match the renamed stage and the more accurate description of the file's contents.

**Migration**: Developer tooling, CI scripts, IDE bookmarks, and documentation that reference `*.expanded.dot` SHALL be updated to reference `*.transforms.dot`. A new `*.full.dot` file is also produced (see the "Full DOT file naming" requirement) for diagnostic purposes.

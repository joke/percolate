## MODIFIED Requirements

### Requirement: DOT renderer renders REALISED, MARKER, and SEED edges

The DOT renderer SHALL emit DOT statements for `EdgeKind.REALISED`, `EdgeKind.MARKER`, and `EdgeKind.SEED` edges. Edge ordering SHALL be ascending natural `Edge` order across all kinds (no kind-based grouping at the top level — visual styling discriminates).

Per the `Node and edge visual distinction` requirement:
- REALISED edges SHALL include the strategy short name and weight in their label.
- SEED edges with non-empty `directive` (user-emitted) SHALL include the `SEED` token, an `∞` weight indicator, and a `directive` marker in their label.
- SEED edges with empty `directive` and non-empty `strategyClassFqn` (strategy-emitted nested seeds) SHALL include the `SEED` token, an `∞` weight indicator, and the strategy short name in their label.
- MARKER edges SHALL include the `MARKER` token in their label.

`SUB_SEED` and `ELEMENT_SEED` rendering rules are removed — those kinds no longer exist.

#### Scenario: All retained edge kinds emit one statement each

- **WHEN** rendering a graph containing one edge of each retained kind (`SEED`, `REALISED`, `MARKER`)
- **THEN** the DOT output contains exactly one edge statement per input edge
- **AND** statements appear in ascending natural `Edge` order regardless of `kind`

#### Scenario: User-directive SEED label includes SEED, ∞, and directive marker

- **WHEN** rendering a SEED edge with non-empty `directive` and empty `strategyClassFqn`
- **THEN** the edge's `label` attribute contains the literal `SEED`
- **AND** the `label` contains the literal `∞`
- **AND** the `label` contains a marker indicating directive origin

#### Scenario: Strategy-emitted SEED label includes SEED, ∞, and strategy short name

- **WHEN** rendering a SEED edge with empty `directive` and `strategyClassFqn == Optional.of("io.github.joke.percolate.spi.builtins.SetMap")`
- **THEN** the edge's `label` attribute contains the literal `SEED`
- **AND** the `label` contains the literal `∞`
- **AND** the `label` contains the simple class name `SetMap`
- **AND** the `label` does NOT contain the package prefix `io.github.joke.percolate.spi.builtins`

#### Scenario: REALISED edge label contains strategy short name and weight

- **WHEN** rendering a REALISED edge with `strategyClassFqn == Optional.of("io.github.joke.percolate.spi.builtins.GetterRead")` and `weight == 1`
- **THEN** the edge's `label` attribute contains the literal `GetterRead` and the literal `1`
- **AND** the `label` does NOT contain the package prefix

#### Scenario: Renderer does not emit SUB_SEED or ELEMENT_SEED kind tokens

- **WHEN** the source of `DotRenderer` is inspected
- **THEN** no code path emits a `SUB_SEED` literal token in any edge label
- **AND** no code path emits an `ELEMENT_SEED` literal token in any edge label

### Requirement: Node and edge visual distinction

The DOT renderer SHALL render nodes with shape attributes that visually distinguish source-located, target-located, and element-located phantom nodes. Source-located nodes SHALL render with one shape (e.g., `box`); target-located nodes SHALL render with another (e.g., `oval`); element-located phantom nodes SHALL render with a third distinct shape (e.g., `diamond`). Element classification uses `Node.loc instanceof ElementLocation`.

Node labels SHALL be composed from the presentation attributes carried on `Node` (`scope`, `loc`, `type`) — these are NOT identity fields under the new `Node` model but ARE rendering inputs.

Edges SHALL render with style attributes (colour and/or line style) keyed off `Edge.kind`. The styling table SHALL define a distinct visual for each of `SEED` and `REALISED`; `MARKER` MAY use a default fallback. REALISED edges SHALL render with the heaviest visible stroke (e.g., `solid` line with elevated `penwidth`). SEED edges SHALL render with a visually secondary style (e.g., `dashed` line, low `penwidth`) so the eye lands on REALISED first. The exact attribute values are implementation-defined but SHALL be stable across runs.

For REALISED edges, the `label` attribute SHALL include the simple class name from `strategyClassFqn` and the edge's `weight`, formatted in a stable, byte-deterministic way (e.g., `GetterRead (1)`). When `weight == Weights.SENTINEL_UNREALISED` the renderer SHALL emit the literal `∞` (U+221E) in place of the numeric value. Group membership of REALISED edges is conveyed by DOT cluster boundaries (see `Group cluster rendering`) — no per-edge `groupId` attribute is emitted because the `Edge` value type carries no `groupId` field.

For SEED edges, the `label` SHALL include the `SEED` token, an `∞` weight indicator, and either a `directive` marker (if `directive` is non-empty) or the strategy short name (if `strategyClassFqn` is non-empty). For MARKER edges the `label` SHALL include the `MARKER` token.

The renderer SHALL NOT attempt to render `Edge.codegen` — codegen closures are opaque.

#### Scenario: Source nodes render as box

- **WHEN** rendering a node whose `loc` is a `SourceLocation`
- **THEN** the DOT output contains `shape=box` (or another stable shape attribute documented in the implementation) for that node's statement

#### Scenario: Target nodes render with a different shape than source nodes

- **WHEN** rendering a node whose `loc` is a `TargetLocation`
- **THEN** the DOT output uses a shape distinct from the one used for `SourceLocation` nodes

#### Scenario: Element phantom nodes render with a third distinct shape

- **WHEN** rendering a node whose `loc instanceof ElementLocation`
- **THEN** the DOT output uses a shape distinct from both `SourceLocation` and `TargetLocation` shapes

#### Scenario: REALISED edge style is heaviest visible stroke

- **WHEN** the renderer writes an edge with `kind == EdgeKind.REALISED`
- **THEN** the edge's style attributes are the documented REALISED styling
- **AND** that styling is visually heavier than the SEED styling

#### Scenario: Sentinel weight renders as infinity in labels

- **WHEN** the renderer writes a REALISED edge with `weight == Weights.SENTINEL_UNREALISED`
- **THEN** the edge's `label` attribute contains the literal `∞` (U+221E) instead of the numeric value

#### Scenario: Group membership renders as a DOT cluster, not a per-edge attribute

- **WHEN** the renderer writes a REALISED edge that is a slot-incoming edge of a registered `ExpansionGroup`
- **THEN** the edge appears inside the DOT cluster block of that group
- **AND** the edge's attribute list does NOT include a `groupId=` or `group=` attribute

#### Scenario: Codegen closures are not rendered

- **WHEN** rendering an edge with non-empty `codegen`
- **THEN** the DOT output contains no representation of the closure object itself (no `lambda$`, no hash, no toString of the closure)

### Requirement: Edge label includes EdgeKind marker

Each rendered edge's `kind` SHALL be identifiable from the DOT output without consulting the source graph data. The required identifier varies by kind:

- For `EdgeKind.SEED` edges, the `label` attribute SHALL contain the literal token `SEED`.
- For `EdgeKind.MARKER` edges, the `label` attribute SHALL contain the literal token `MARKER`.
- For `EdgeKind.REALISED` edges, the kind is identified by the combination of the edge's style attributes and the strategy short name in its label (no explicit `REALISED` token is required).

The forward-era `SUB_SEED` and `ELEMENT_SEED` kind-marker rules are removed.

#### Scenario: SEED edge label includes the SEED token

- **WHEN** the renderer writes an edge with `kind == EdgeKind.SEED`
- **THEN** the edge's `label` attribute contains the literal `SEED`

#### Scenario: MARKER edge label includes the MARKER token

- **WHEN** the renderer writes an edge with `kind == EdgeKind.MARKER`
- **THEN** the edge's `label` attribute contains the literal `MARKER`

#### Scenario: REALISED edge kind is identified by style and strategy

- **WHEN** the renderer writes an edge with `kind == EdgeKind.REALISED` and `strategyClassFqn == Optional.of("io.github.joke.percolate.spi.builtins.GetterRead")`
- **THEN** the edge's `label` attribute contains the simple class name `GetterRead`
- **AND** the edge's style attributes match the documented REALISED styling
- **AND** the `label` does NOT contain the literal token `REALISED`

## ADDED Requirements

### Requirement: Group cluster rendering

The DOT renderer SHALL emit one `subgraph "cluster_<group-cluster-id>" { ... }` block per registered `ExpansionGroup` in `MapperGraph.groups()`. Each cluster block SHALL contain:

- A node statement for the group's `root` and each `slot`.
- An edge statement for each REALISED edge in `group.getView().edgeSet()` (the slot-incoming edges).
- A `label` attribute on the cluster combining the strategy short name and `root.id()` for human-readable identification.

The `<group-cluster-id>` SHALL be a stable, byte-deterministic identifier derived from `group.getStrategyClassFqn()` and `group.getRoot().id()` (e.g., `<strategy-fqn>:<root-id>`).

REALISED edges that are NOT slot-incoming edges of any `ExpansionGroup` (i.e., chain edges connecting the source-side back to slots) SHALL render outside any group cluster, at the top level of the digraph.

#### Scenario: One DOT cluster per ExpansionGroup

- **WHEN** the renderer writes a graph whose `MapperGraph.groups()` returns two `ExpansionGroup`s
- **THEN** the DOT output contains exactly two `subgraph "cluster_..." {` blocks
- **AND** each block's contents include the corresponding group's root, slots, and slot-incoming REALISED edges

#### Scenario: Chain REALISED edges render outside group clusters

- **WHEN** the underlying graph contains a REALISED edge from `src[person]:Person` to a slot `slot1`, where the edge is NOT a slot-incoming edge of any registered `ExpansionGroup`
- **THEN** the edge statement appears at the top level of the digraph (outside any cluster block)

### Requirement: TransformsView filter on MapperGraph

`MapperGraph` SHALL expose an accessor `transformsView()` that returns a non-destructive `MaskSubgraph` over the underlying graph. The view's edge mask SHALL retain only edges with `kind == EdgeKind.REALISED`. The view's vertex mask SHALL retain only nodes that are endpoints of at least one retained `REALISED` edge.

The view SHALL NOT filter by source-side reachability. Under greedy commit no dead branches are produced (each slot has exactly one producer), so this clause is structurally vacuous in practice; it remains in the spec so the view's semantics are independent of the driver's commit policy.

The view SHALL expose `Stream<Node> nodes()`, `Stream<Edge> edges()`, and `Stream<Node> nodesByScope(Scope)` with deterministic ascending order.

The view SHALL NOT mutate the underlying `MapperGraph`.

#### Scenario: transformsView retains only REALISED edges

- **WHEN** a graph containing edges of every retained kind (`SEED`, `REALISED`, `MARKER`) is exposed via `transformsView()`
- **THEN** every edge in `edges()` has `kind == EdgeKind.REALISED`

#### Scenario: transformsView retains every REALISED edge from the underlying graph

- **WHEN** the underlying graph contains a `REALISED` edge `X → Y`
- **THEN** `transformsView().edges()` contains the `X → Y` edge
- **AND** `transformsView().nodes()` contains both `X` and `Y`

#### Scenario: Element phantoms are retained when REALISED-incident

- **WHEN** the underlying graph contains a `REALISED` edge between two `ElementLocation` phantom nodes
- **THEN** both phantom nodes appear in `transformsView().nodes()`
- **AND** the `REALISED` edge between them appears in `transformsView().edges()`

#### Scenario: transformsView construction does not mutate the underlying graph

- **WHEN** `MapperGraph.transformsView()` is invoked
- **THEN** the underlying `MapperGraph` retains all its original nodes and edges, observable via `MapperGraph.nodes()` and `MapperGraph.edges()`

### Requirement: DumpFullGraph stage

The processor SHALL define a stage `DumpFullGraph` in package `io.github.joke.percolate.processor.stages.dump` that consumes the post-expansion `MapperGraph` plus the originating `TypeElement` and, when enabled by `ProcessorOptions.debugGraphs`, writes a DOT representation of the **entire underlying graph** (every edge of every kind, every node) to `StandardLocation.SOURCE_OUTPUT`. `DumpFullGraph` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

`DumpFullGraph` SHALL run after `ExpandStage` and before `RealisationDiagnosticsStage`. It SHALL write its file unconditionally on every per-mapper run (subject to the option flag and the empty-graph short-circuit).

The dumped file SHALL contain every node and edge in the underlying `MapperGraph` — no filtering. Dead `REALISED` branches appear here.

#### Scenario: Option off does not write a file

- **WHEN** `DumpFullGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == false`
- **THEN** no resource is created via `Filer`

#### Scenario: Option on writes a .full.dot file at SOURCE_OUTPUT

- **WHEN** `DumpFullGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == true` for a non-empty `MapperGraph` and a `TypeElement` representing FQN `com.example.PersonMapper`
- **THEN** `Filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "com.example.PersonMapper.full.dot", <originating element>)` is invoked
- **AND** the resource's contents include every edge of every kind in the underlying graph

#### Scenario: File is written even when validation emits errors

- **WHEN** `DumpFullGraph.apply(...)` runs, then `RealisationDiagnosticsStage` emits an error for the same mapper
- **THEN** the `.full.dot` file is still present on disk

#### Scenario: Empty graph does not write a file

- **WHEN** `DumpFullGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == true` but the `MapperGraph` has zero nodes and zero edges
- **THEN** no resource is created via `Filer`

#### Scenario: Filer failure is reported as a warning

- **WHEN** `Filer.createResource(...)` throws `IOException`
- **THEN** a warning diagnostic is emitted via `Diagnostics`
- **AND** no error is emitted
- **AND** the method returns normally

### Requirement: Full DOT file naming

The full-graph DOT file SHALL be named `<MapperFQN>.full.dot`. The `.full.` infix coexists with `.seed.` and `.transforms.` in the same `SOURCE_OUTPUT` directory without collision.

#### Scenario: File name uses .full.dot infix

- **WHEN** `DumpFullGraph.apply(...)` writes a file for `com.example.PersonMapper`
- **THEN** the file name passed to `Filer.createResource(...)` is exactly `"com.example.PersonMapper.full.dot"`

### Requirement: DumpTransforms stage

The processor SHALL define a stage `DumpTransforms` in package `io.github.joke.percolate.processor.stages.dump` that consumes the post-expansion `MapperGraph` plus the originating `TypeElement` and, when enabled by `ProcessorOptions.debugGraphs`, writes a DOT representation of `MapperGraph.transformsView()` to `StandardLocation.SOURCE_OUTPUT`. `DumpTransforms` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

`DumpTransforms` SHALL run after `DumpFullGraph` and before `RealisationDiagnosticsStage`.

The dumped file SHALL contain only the nodes and edges exposed by `transformsView()`: `REALISED` edges and nodes incident on at least one such edge. `SEED` and `MARKER` edges SHALL NOT appear. Under greedy commit each slot has exactly one producing REALISED edge, so the resulting DOT graph is a DAG of single-producer slot chains joined by group clusters.

#### Scenario: Option off does not write a file

- **WHEN** `DumpTransforms.apply(...)` is invoked with `ProcessorOptions.debugGraphs == false`
- **THEN** no resource is created via `Filer`

#### Scenario: Option on writes a .transforms.dot file at SOURCE_OUTPUT

- **WHEN** `DumpTransforms.apply(...)` is invoked with `ProcessorOptions.debugGraphs == true` for a non-empty `MapperGraph` representing FQN `com.example.PersonMapper`
- **THEN** `Filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "com.example.PersonMapper.transforms.dot", <originating element>)` is invoked
- **AND** the resource's contents are the DOT representation of `graph.transformsView()`

#### Scenario: File contains only REALISED edges

- **WHEN** `DumpTransforms.apply(...)` writes a file for a graph containing edges of every retained kind
- **THEN** every edge statement in the resulting DOT file corresponds to a `REALISED` edge in the underlying graph
- **AND** no edge of any other kind appears in the file

#### Scenario: File is written even when validation emits errors

- **WHEN** `DumpTransforms.apply(...)` runs, then `RealisationDiagnosticsStage` emits an error for the same mapper
- **THEN** the `.transforms.dot` file is still present on disk

#### Scenario: Empty graph does not write a file

- **WHEN** `DumpTransforms.apply(...)` is invoked but `transformsView()` has zero edges
- **THEN** no resource is created via `Filer`

#### Scenario: Filer failure is reported as a warning

- **WHEN** `Filer.createResource(...)` throws `IOException`
- **THEN** a warning diagnostic is emitted
- **AND** no error is emitted

### Requirement: Transforms DOT file naming

The transforms-graph DOT file SHALL be named `<MapperFQN>.transforms.dot`. The `.transforms.` infix coexists with `.seed.` and `.full.` in the same `SOURCE_OUTPUT` directory.

#### Scenario: File name uses .transforms.dot infix

- **WHEN** `DumpTransforms.apply(...)` writes a file for `com.example.PersonMapper`
- **THEN** the file name passed to `Filer.createResource(...)` is exactly `"com.example.PersonMapper.transforms.dot"`

#### Scenario: All three dot files coexist

- **WHEN** the pipeline completes for `com.example.PersonMapper` with `ProcessorOptions.debugGraphs == true` and a non-empty graph
- **THEN** all three files `com.example.PersonMapper.seed.dot`, `com.example.PersonMapper.full.dot`, `com.example.PersonMapper.transforms.dot` are present in `SOURCE_OUTPUT`

## REMOVED Requirements

### Requirement: Expanded view filter on MapperGraph

**Reason:** Replaced by `transformsView()` per the new `TransformsView filter on MapperGraph` requirement. Semantics changed (REALISED-only mask; no SEED/SUB_SEED filtering since those kinds collapsed) and the name aligns with the renamed dump file.

**Migration:** Update any caller of `MapperGraph.expandedView()` to `MapperGraph.transformsView()`. The view semantics differ (`expandedView` retained SUB_SEED; `transformsView` is REALISED-only).

### Requirement: DumpExpandedGraph stage

**Reason:** Renamed to `DumpTransforms` and its semantics changed (renders `transformsView` instead of `expandedView`). The forward-era `expanded.dot` file is replaced by `.transforms.dot`.

**Migration:** Update pipeline wiring to use `DumpTransforms`. Any tests or fixtures that referenced `DumpExpandedGraph` by name are updated.

### Requirement: Expanded DOT file naming

**Reason:** The `.expanded.dot` filename is replaced by `.transforms.dot`. Two new files (`.full.dot` and `.transforms.dot`) supersede the single `.expanded.dot`.

**Migration:** Developer tooling, CI scripts, and documentation that reference `*.expanded.dot` SHALL be updated to `*.transforms.dot` and `*.full.dot` as appropriate.

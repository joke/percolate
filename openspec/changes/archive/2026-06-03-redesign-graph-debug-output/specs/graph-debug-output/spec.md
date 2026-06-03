## MODIFIED Requirements

### Requirement: Deterministic DOT renderer

The processor SHALL define a `DotRenderer` in `io.github.joke.percolate.processor.graph` that produces a `String` DOT representation of a **single scope's** slice of a `GraphSource` by delegating to JGraphT `org.jgrapht.nio.dot.DOTExporter`. The renderer SHALL NOT hand-assemble DOT text, escape characters by hand, or emit `subgraph cluster_*` blocks; statement structure, identifier quoting, and special-character escaping SHALL be owned by `DOTExporter`.

The renderer SHALL feed `DOTExporter` an `org.jgrapht.Graph<Node, Edge>` restricted to one `Scope` — obtained as an `AsSubgraph`/`MaskSubgraph` over the underlying graph (no full-graph copy), filtered to the nodes of that scope and to the edges exposed by the view being rendered.

The renderer SHALL configure `DOTExporter` so that:
- `setVertexIdProvider` returns `Node.id()` (fully qualified, stable, unique).
- `setVertexAttributeProvider` supplies the node `label` (per the "Node labels include the simple type segment" requirement) and the visual attributes (per the "Node and edge visual distinction" requirement).
- `setEdgeAttributeProvider` supplies the edge `label` and visual attributes (per the "Edge label includes EdgeKind marker" and "Node and edge visual distinction" requirements).
- `setGraphAttributeProvider` supplies a graph-level `label` attribute carrying the human-readable scope description, so the rendered graph is captioned with its scope.

Determinism: given the same scope slice with the view's documented node and edge ordering, the produced `String` SHALL be byte-stable across runs. Vertices SHALL be presented to the exporter in ascending `Node.id()` order and edges in ascending natural `Edge` order.

#### Scenario: Output is byte-stable across runs given the same scope slice
- **WHEN** the same single-scope slice of a `MapperGraph` view is rendered twice in two separate JVM runs
- **THEN** the produced `String`s are identical byte-for-byte

#### Scenario: Output contains no cluster subgraphs
- **WHEN** any scope slice is rendered
- **THEN** the DOT output contains no `subgraph cluster_` token — grouping is expressed by the one-file-per-scope split, not by clusters

#### Scenario: Graph is captioned with its scope
- **WHEN** the slice for scope `map(Person)` is rendered
- **THEN** the DOT output carries a graph-level `label` attribute whose value is the human-readable description of `map(Person)`

#### Scenario: Vertex iteration order
- **WHEN** rendering a scope slice
- **THEN** vertex statements appear in ascending `Node.id()` order

#### Scenario: Edge iteration order
- **WHEN** rendering a scope slice
- **THEN** edge statements appear in ascending natural `Edge` order

#### Scenario: Special characters in labels are escaped by the exporter
- **WHEN** a node's label contains `"` or `\` or a newline character
- **THEN** the rendered DOT escapes those characters via `DOTExporter` so that the output is parseable by Graphviz

### Requirement: Node and edge visual distinction

The DOT renderer SHALL render **every** node with `shape=box` and `style=filled`, distinguishing source-located, target-located, and container-element nodes by `fillcolor` rather than by shape. Source-located (`SourceLocation`) nodes, target-located (`TargetLocation`) nodes, and container-element (`ElementLocation`) nodes SHALL each receive a distinct, stable `fillcolor`. No node SHALL render as `oval`, `diamond`, or any non-`box` shape.

The DOT renderer SHALL render edges with attributes keyed off `Edge.kind`:
- `REALISED` edges SHALL render with the heaviest visible stroke — a solid black line with elevated `penwidth` — and SHALL dominate the visual hierarchy; they represent the load-bearing transformations.
- `SEED` edges SHALL render so they recede to the background: the edge line `color` and the edge `fontcolor` SHALL both be a muted grey, so the line and its label read as secondary information without competing with `REALISED` edges.
- `MARKER` edges, when rendered directly, MAY use a neutral default style; no dedicated `MARKER` style is required.

The exact colour values are implementation-defined but SHALL be stable across runs.

Edge **label content** is unchanged from prior behaviour: for `REALISED` edges the `label` SHALL include the strategy simple class name and the edge `weight` (with `∞` (U+221E) for `Weights.SENTINEL_UNREALISED`); for `SEED` edges the `label` SHALL retain its kind token, weight, and directive marker. The renderer SHALL NOT render `Edge.codegen`.

#### Scenario: All nodes render as filled boxes
- **WHEN** rendering nodes whose `loc` is `SourceLocation`, `TargetLocation`, and `ElementLocation`
- **THEN** every node statement carries `shape=box` and `style=filled`
- **AND** no node statement carries `shape=oval` or `shape=diamond`

#### Scenario: Node roles are distinguished by fillcolor
- **WHEN** rendering a `SourceLocation` node, a `TargetLocation` node, and an `ElementLocation` node
- **THEN** the three node statements carry three distinct `fillcolor` values

#### Scenario: REALISED edge is the heaviest visible stroke
- **WHEN** the renderer writes an edge with `kind == EdgeKind.REALISED`
- **THEN** the edge's style attributes are a solid black line with elevated `penwidth`
- **AND** that styling is visually heavier than the SEED styling

#### Scenario: SEED edge recedes to grey
- **WHEN** the renderer writes an edge with `kind == EdgeKind.SEED`
- **THEN** the edge's `color` attribute is a muted grey
- **AND** the edge's `fontcolor` attribute is the same muted grey, so the label recedes

#### Scenario: REALISED edge label contains strategy short name and weight
- **WHEN** rendering a REALISED edge with `strategyClassFqn == Optional.of("io.github.joke.percolate.spi.builtins.IterableUnwrap")` and `weight == 2`
- **THEN** the edge's `label` attribute contains both the literal `IterableUnwrap` and the literal `2`
- **AND** the `label` does NOT contain the package prefix `io.github.joke.percolate.spi.builtins`

#### Scenario: Sentinel weight renders as infinity in REALISED labels
- **WHEN** the renderer writes a REALISED edge with `weight == Weights.SENTINEL_UNREALISED`
- **THEN** the edge's `label` attribute contains the literal `∞` (U+221E) instead of the numeric value

#### Scenario: Codegen closures are not rendered
- **WHEN** rendering an edge with non-empty `codegen`
- **THEN** the DOT output contains no representation of the closure object itself (no `lambda$`, no hash, no toString of the closure)
- **AND** the rest of the edge's attributes render normally

### Requirement: File naming

The processor SHALL write one DOT file per `(scope, view)` pair. Each file SHALL be named `<MapperFQN>.<methodSimpleName>.<view>.dot`, where `<view>` is one of `seed`, `full`, `transforms`, `plan`, and `<methodSimpleName>` is the simple name of the scope's method. When two scopes of the same mapper share a method simple name (overloads), the colliding files SHALL be disambiguated as `<MapperFQN>.<methodSimpleName>-<n>.<view>.dot`, where `<n>` is a deterministic index assigned in a stable order over the colliding scopes.

A single mapper with multiple scopes SHALL therefore produce one file per scope per view; the scopes do not share a file.

#### Scenario: File name encodes mapper, method, and view
- **WHEN** the `seed` view is written for `com.example.PersonMapper`, scope `mapHuman(...)`
- **THEN** the file name passed to `Filer.createResource(...)` is `com.example.PersonMapper.mapHuman.seed.dot`

#### Scenario: Each scope of a mapper gets its own file
- **WHEN** the `seed` view is written for `com.example.PersonMapper` which has scopes `mapHuman(...)` and `mapAddress(...)`
- **THEN** two files are written: `com.example.PersonMapper.mapHuman.seed.dot` and `com.example.PersonMapper.mapAddress.seed.dot`

#### Scenario: Overloaded methods are disambiguated by index
- **WHEN** a mapper has two scopes whose method simple name is both `map`
- **THEN** the two `seed` files are named with distinct `map-<n>` infixes (e.g. `...map-0.seed.dot` and `...map-1.seed.dot`), assigned in a stable order

### Requirement: Node labels include the simple type segment

The DOT renderer SHALL render every node `label` attribute as a two-line value: the location segment (`src[…]`, `tgt[…]`, or the element-role segment for `ElementLocation` nodes) on the first line, followed by a DOT line break (`\n`), followed by the short type name on the second line. The short type name SHALL be derived from the node's type segment as follows:

- The prefix `java.lang.` SHALL be stripped from class names so that `java.lang.String` renders as `String` and `java.lang.Integer` renders as `Integer`. Rationale: `java.lang` is implicitly imported in Java source; unqualified rendering matches reading expectations.
- Other package prefixes SHALL be preserved verbatim so that `io.github.joke.testing.Person.Address` renders as `io.github.joke.testing.Person.Address`. Rationale: same-simple-name types across user packages would otherwise render indistinguishably.
- Generic type arguments SHALL be rewritten recursively under the same rule so that `java.util.List<java.util.Optional<java.lang.String>>` renders as `java.util.List<java.util.Optional<String>>`.
- The untyped placeholder SHALL render as the literal `?`.

Fully qualified types SHALL remain in `Node.id()` (carried into the DOT output as the quoted vertex identifier) for graph determinism and uniqueness — only the visible `label` attribute is simplified.

The two-line label format SHALL apply uniformly across every rendered view (`seed`, `full`, `transforms`, `plan`).

#### Scenario: Typed node label has location and type on two lines
- **WHEN** the renderer writes a node whose location segment is `src[address.street]` and whose type segment is `java.lang.String`
- **THEN** the `label` attribute of the node statement is the two-line string `src[address.street]\nString`

#### Scenario: java.lang prefix is stripped
- **WHEN** the renderer writes a node whose type segment is `java.lang.Integer`
- **THEN** the rendered second label line is `Integer`

#### Scenario: Non-java.lang package is preserved verbatim
- **WHEN** the renderer writes a node whose type segment is `io.github.joke.testing.Person.Address`
- **THEN** the rendered second label line is `io.github.joke.testing.Person.Address`

#### Scenario: Generic type arguments are simplified recursively
- **WHEN** the renderer writes a node whose type segment is `java.util.List<java.util.Optional<java.lang.String>>`
- **THEN** the rendered second label line is `java.util.List<java.util.Optional<String>>`

#### Scenario: Untyped placeholder renders as ?
- **WHEN** the renderer writes a node whose type segment is the untyped placeholder
- **THEN** the rendered second label line is `?`

#### Scenario: Node ids remain fully qualified
- **WHEN** the renderer writes a node whose type segment is `java.lang.String`
- **THEN** the DOT statement's quoted vertex identifier contains `java.lang.String` verbatim — only the `label` attribute is simplified

### Requirement: Edge label includes EdgeKind marker

Each rendered edge's `kind` SHALL be identifiable from the DOT output without consulting the source graph data. The required identifier varies by kind:

- For `EdgeKind.SEED` edges, the edge `label` attribute SHALL include the literal token `SEED`.
- For `EdgeKind.REALISED` edges, the kind is identified by the combination of the edge's style attributes and the strategy short name in its label (see the "Node and edge visual distinction" requirement); the explicit token `REALISED` is not required in the label.
- For `EdgeKind.MARKER` edges (which appear only in views that retain non-`REALISED` edges, i.e. `seed` and `full`, and never in the `REALISED`-only `transforms`/`plan` views), the `label` attribute SHALL include the literal token `MARKER`.

#### Scenario: SEED edge label includes the SEED token
- **WHEN** the renderer writes an edge with `kind == EdgeKind.SEED`
- **THEN** the edge's `label` attribute contains the literal `SEED`

#### Scenario: REALISED edge kind is identified by style and strategy
- **WHEN** the renderer writes an edge with `kind == EdgeKind.REALISED` and `strategyClassFqn == Optional.of("io.github.joke.percolate.spi.builtins.IterableUnwrap")`
- **THEN** the edge's `label` attribute contains the simple class name `IterableUnwrap`
- **AND** the edge's style attributes match the documented REALISED styling (distinct from SEED)
- **AND** the edge's `label` attribute does NOT contain the literal token `REALISED`

#### Scenario: MARKER edge retains the MARKER token
- **WHEN** the renderer writes an edge with `kind == EdgeKind.MARKER` (in a view that retains it)
- **THEN** the edge's `label` attribute contains the literal `MARKER`

## ADDED Requirements

### Requirement: Shared dump IO via GraphDumpWriter

The processor SHALL define a single collaborator `GraphDumpWriter` in package `io.github.joke.percolate.processor.stages.dump` (or `…processor.graph`) that owns the entire dump IO mechanism: the `ProcessorOptions.isDebugGraphs()` gate, the empty-graph skip, the per-scope partition, the `DOTExporter` rendering pass, the `Filer.createResource(StandardLocation.SOURCE_OUTPUT, …)` write per scope, and the `IOException`→warning handling. `GraphDumpWriter` SHALL be `@Inject`-constructed and SHALL depend on `Filer`, `Diagnostics`, `ProcessorOptions`, and the `DotRenderer`.

Each dump stage (`DumpGraph`, `DumpFullGraph`, `DumpTransforms`, `DumpPlan`) SHALL delegate to `GraphDumpWriter`, supplying only its view selector (`g -> g`, `g -> g.transformsView()`, `g -> g.planView()`) and its `<view>` infix. The stages SHALL retain their existing pipeline positions; in particular `DumpGraph` (the `seed` view) SHALL run before the expansion stage and the others after it. A `Filer`/`IOException` failure SHALL be reported as a `Diagnostics` warning referencing the originating `TypeElement`, SHALL NOT be an error, and SHALL NOT abort the compile.

When partitioning a view's edges by scope, an edge SHALL be assigned to the scope of its `from` node, so that no edge is dropped even though, by construction, edges do not span scopes.

#### Scenario: Option off writes no file for any view
- **WHEN** a dump stage runs with `ProcessorOptions.isDebugGraphs() == false`
- **THEN** no resource is created via `Filer`
- **AND** no diagnostic is emitted

#### Scenario: Empty graph writes no file even when option on
- **WHEN** a dump stage runs with `ProcessorOptions.isDebugGraphs() == true` but the graph has zero nodes and zero edges
- **THEN** no resource is created via `Filer`
- **AND** no diagnostic is emitted

#### Scenario: One write per scope when option on
- **WHEN** `DumpGraph` runs with `ProcessorOptions.isDebugGraphs() == true` for a non-empty `MapperGraph` with two scopes
- **THEN** `GraphDumpWriter` invokes `Filer.createResource(...)` once per scope, each with the scope's `<MapperFQN>.<method>.seed.dot` name
- **AND** each resource's contents are the `DOTExporter` rendering of that scope's slice

#### Scenario: Filer failure is a warning, not an error
- **WHEN** a dump stage runs with the option on and `Filer.createResource(...)` (or the subsequent write) throws `IOException`
- **THEN** a warning diagnostic is emitted via `Diagnostics` referencing the originating `TypeElement`
- **AND** no error is emitted
- **AND** the stage returns normally so the compile is not aborted

#### Scenario: Edge is partitioned to its from-node scope
- **WHEN** a view's edges are partitioned by scope for file output
- **THEN** each edge is rendered in the file of its `from` node's scope

### Requirement: Transforms view filter on MapperGraph

`MapperGraph` SHALL expose an accessor `transformsView()` that returns a `TransformsView` — a non-destructive `GraphSource` over the underlying graph, implemented as a JGraphT `MaskSubgraph` so the full graph is not copied or mutated. The view SHALL expose `Stream<Node> nodes()`, `Stream<Edge> edges()`, and `Stream<Node> nodesByScope(Scope)` with the same ordering guarantees as the other `MapperGraph` views.

The view's edge mask SHALL retain only `EdgeKind.REALISED` edges; `SEED` and `MARKER` edges SHALL be hidden. The view's node set SHALL be exactly the nodes incident to a retained `REALISED` edge (the endpoints of the surviving edges); nodes touched by no `REALISED` edge SHALL NOT appear. The view SHALL NOT mutate the underlying `MapperGraph`.

#### Scenario: transformsView retains only REALISED edges
- **WHEN** a graph containing `SEED`, `REALISED`, and `MARKER` edges is exposed via `transformsView()`
- **THEN** `edges()` contains every `REALISED` edge
- **AND** `edges()` contains no `SEED` or `MARKER` edge

#### Scenario: transformsView nodes are exactly the REALISED-incident nodes
- **WHEN** a graph has a node touched only by a `SEED` edge and another node that is an endpoint of a `REALISED` edge, exposed via `transformsView()`
- **THEN** `nodes()` contains the `REALISED`-incident node
- **AND** `nodes()` does not contain the node touched only by the `SEED` edge

#### Scenario: transformsView does not mutate the underlying graph
- **WHEN** `MapperGraph.transformsView()` is invoked
- **THEN** the underlying `MapperGraph` retains all its original nodes and edges, as observable via `MapperGraph.nodes()` and `MapperGraph.edges()`

## REMOVED Requirements

### Requirement: Phantom node cluster grouping

**Reason**: The renderer no longer emits `subgraph cluster_*` blocks at all — scope grouping is now expressed by the one-file-per-scope split, and `ElementLocation` ("phantom") nodes are rendered like any other node (a filled `box` distinguished by `fillcolor`) inside their own scope's file. With clusters gone there is nothing to place a phantom "inside its parent's cluster".

**Migration**: Phantom (`ElementLocation`) nodes now appear in the file of their owning scope, styled per the "Node and edge visual distinction" requirement. No cluster-placement behaviour remains to migrate; consumers that looked for a phantom inside a parent cluster SHALL instead read the scope's file.

### Requirement: DumpGraph stage

**Reason**: The per-stage IO mechanism this requirement described (`DumpGraph.apply(graph, mapperType)` writing a single `<MapperFQN>.seed.dot` and depending directly on `Filer`/`Diagnostics`/`ProcessorOptions`/renderer) is consolidated into the shared `GraphDumpWriter` (see "Shared dump IO via GraphDumpWriter"). `DumpGraph` is now a thin `Stage` that delegates to the writer and produces one `seed` file per scope.

**Migration**: The `seed` view is still dumped before expansion. Its behaviour (option gate, empty-graph skip, `SOURCE_OUTPUT` write, `IOException`→warning) is now specified by "Shared dump IO via GraphDumpWriter"; the file is named per scope per "File naming" (`<MapperFQN>.<method>[-n].seed.dot`).

### Requirement: DumpExpandedGraph stage

**Reason**: This requirement described a stage `DumpExpandedGraph` writing `<MapperFQN>.expanded.dot` from a `MapperGraph.expandedView()`. Neither the stage, the `expandedView()` accessor, nor the `.expanded.dot` file exists in the implementation — the post-expansion graph is dumped by `DumpFullGraph` (`full` view) and `DumpTransforms` (`transforms` view), both delegating to `GraphDumpWriter`.

**Migration**: Use the `full` and `transforms` views (see "Shared dump IO via GraphDumpWriter" and "Transforms view filter on MapperGraph"), written per scope per "File naming".

### Requirement: Expanded DOT file naming

**Reason**: The `<MapperFQN>.expanded.dot` name is superseded by the per-scope, per-view naming in "File naming" (`<MapperFQN>.<method>[-n].<view>.dot`). There is no `expanded` view; the views are `seed`, `full`, `transforms`, `plan`.

**Migration**: Read the per-scope `full`/`transforms` files instead of a single `.expanded.dot`.

### Requirement: DumpPlan stage

**Reason**: As with the other dump stages, the per-stage IO this requirement described (`DumpPlan` writing a single `<MapperFQN>.plan.dot` and depending directly on `Filer`/`Diagnostics`/`ProcessorOptions`/renderer) is consolidated into `GraphDumpWriter`. `DumpPlan` is now a thin delegator producing one `plan` file per scope.

**Migration**: Behaviour is specified by "Shared dump IO via GraphDumpWriter"; the file is named per scope per "File naming" (`<MapperFQN>.<method>[-n].plan.dot`). The `plan` vs `transforms` distinction (plan shows only the chosen plan; transforms keeps dead siblings) is unchanged.

### Requirement: Expanded view filter on MapperGraph

**Reason**: This requirement named an `expandedView()` accessor returning an `ExpandedGraphView` with untyped-placeholder masking. The implementation exposes `transformsView()` returning a `TransformsView` whose semantics differ (REALISED-only edges plus exactly the REALISED-incident nodes; no placeholder masking). It is replaced by "Transforms view filter on MapperGraph".

**Migration**: Use `MapperGraph.transformsView()` (see "Transforms view filter on MapperGraph") in place of the removed `expandedView()`.

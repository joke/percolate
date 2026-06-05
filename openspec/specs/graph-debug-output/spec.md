# Graph Debug Output Spec

## Purpose

This spec defines the debug-graph dump stages and the deterministic DOT renderer that serialize a `MapperGraph` (and its views) to DOT files for debugging and visualization purposes. Output is off by default (gated on `ProcessorOptions.debugGraphs`) and is written one file per scope per view.

## Requirements

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

### Requirement: Edge label includes EdgeKind marker

Each rendered edge's `kind` SHALL be identifiable from the DOT output without consulting the source graph data. The required identifier varies by kind:

- For `EdgeKind.SEED` edges, the edge `label` attribute SHALL include the literal token `SEED`.
- For `EdgeKind.REALISED` edges, the kind is identified by the combination of the edge's style attributes and the strategy short name in its label (see the "Node and edge visual distinction" requirement); the explicit token `REALISED` is not required in the label.
- For `EdgeKind.MARKER` edges (which appear only in views that retain non-`REALISED` edges, i.e. `seed` and `full`, and never in the `REALISED`-only `transforms`/`plan` views), the `label` attribute SHALL include the literal token `MARKER`.

#### Scenario: SEED edge label includes the SEED token
- **WHEN** the renderer writes an edge with `kind == EdgeKind.SEED`
- **THEN** the edge's `label` attribute contains the literal `SEED`

#### Scenario: REALISED edge kind is identified by style and strategy
- **WHEN** the renderer writes an edge with `kind == EdgeKind.REALISED` and `strategyClassFqn == Optional.of("io.github.joke.percolate.spi.builtins.ListContainer")`
- **THEN** the edge's `label` attribute contains the simple class name `ListContainer`
- **AND** the edge's style attributes match the documented REALISED styling (distinct from SEED)
- **AND** the edge's `label` attribute does NOT contain the literal token `REALISED`

#### Scenario: MARKER edge retains the MARKER token
- **WHEN** the renderer writes an edge with `kind == EdgeKind.MARKER` (in a view that retains it)
- **THEN** the edge's `label` attribute contains the literal `MARKER`

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
- **WHEN** rendering a REALISED edge with `strategyClassFqn == Optional.of("io.github.joke.percolate.spi.builtins.ListContainer")` and `weight == 2`
- **THEN** the edge's `label` attribute contains both the literal `ListContainer` and the literal `2`
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

### Requirement: Shared dump IO via GraphDumpWriter

The processor SHALL define a single collaborator `GraphDumpWriter` in package `io.github.joke.percolate.processor.stages.dump` that owns the entire dump IO mechanism: the `ProcessorOptions.isDebugGraphs()` gate, the empty-graph skip, the per-scope partition, the `DOTExporter` rendering pass, the `Filer.createResource(StandardLocation.SOURCE_OUTPUT, …)` write per scope, and the `IOException`→warning handling. `GraphDumpWriter` SHALL be `@Inject`-constructed and SHALL depend on `Filer`, `Diagnostics`, `ProcessorOptions`, and the `DotRenderer`.

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

### Requirement: DOT renderer renders all EdgeKind values

The DOT renderer SHALL emit DOT statements for `EdgeKind.SEED`, `EdgeKind.REALISED`, and `EdgeKind.MARKER` edges. Edge ordering SHALL remain ascending natural `Edge` order across all kinds (no kind-based grouping at the top level — the visual styling discriminates).

Per the "Node and edge visual distinction" requirement, REALISED edges SHALL include the strategy short name and weight in their label. SEED and MARKER edges retain their prior label content when rendered.

#### Scenario: All edge kinds are emitted when given to the renderer
- **WHEN** rendering a graph containing one edge of each kind (`SEED`, `REALISED`, `MARKER`)
- **THEN** the DOT output contains exactly one edge statement per input edge
- **AND** each statement is keyed off its endpoints in the documented edge ordering

#### Scenario: Edge ordering is the natural edge order across all kinds
- **WHEN** rendering a graph with mixed-kind edges
- **THEN** edge statements appear in ascending natural `Edge` order regardless of `kind`

### Requirement: Container strategies render with their simple class name

The deterministic DOT renderer SHALL render REALISED edges emitted by the container built-ins (`OptionalContainer`, `ListContainer`, `SetContainer`, `ArrayContainer`) with `label` attributes containing the strategy's simple class name and weight, formatted by the same rule that applies to every REALISED edge (per the existing `Node and edge visual distinction` requirement).

No REALISED edge in any rendered DOT file SHALL carry a `strategyClassFqn` ending in `.SetMap`, `.ListMap`, or `.OptionalMap` — those classes are deleted by `split-container-bridges`. Any DOT file produced by the processor for a mapper compiled against the post-change classpath SHALL be free of those tokens in every edge label.

#### Scenario: A container strategy REALISED edge label contains its simple name and weight
- **WHEN** the renderer writes a REALISED edge with `strategyClassFqn == Optional.of("io.github.joke.percolate.spi.builtins.ListContainer")` and `weight == Weights.CONTAINER`
- **THEN** the edge's `label` attribute contains both the literal `ListContainer` and the literal value of `Weights.CONTAINER` (rendered as the configured integer, e.g. `2`)
- **AND** the `label` does NOT contain the package prefix `io.github.joke.percolate.spi.builtins`

#### Scenario: Set / List / Array / Optional container REALISED edge labels contain their simple names
- **WHEN** the renderer writes a REALISED edge whose `strategyClassFqn` resolves to one of `SetContainer`, `ListContainer`, `ArrayContainer`, or `OptionalContainer` under `io.github.joke.percolate.spi.builtins`
- **THEN** the edge's `label` attribute contains the simple class name verbatim
- **AND** the `label` does NOT contain the package prefix

#### Scenario: No DOT file contains the deleted container-map bridge names
- **WHEN** any per-scope DOT file (any `seed`, `full`, `transforms`, or `plan` view) is produced for a mapper compiled with the post-change `strategies-builtin` module
- **THEN** no edge `label` attribute and no edge attribute string contains the literal token `SetMap`, `ListMap`, or `OptionalMap`

### Requirement: Linear container chains render without diamond shortcuts

The DOT renderer's output for any container-bearing chain (a chain involving an `*Unwrap` and a matching `*Collect`) SHALL be a linear sequence of REALISED edges from the regular-scope source candidate through `ElementLocation` nodes back to the regular-scope target candidate. No additional "outer" REALISED edge SHALL connect the source container directly to the target container in parallel with the chain.

This requirement formalises, at the rendering level, the structural invariant established by `graph-expansion`: chains are linear by construction; the renderer simply renders what the engine produces.

#### Scenario: Integration mapper addresses chain renders linearly in transforms view
- **WHEN** the integration mapper at `~/Projects/joke/percolate-integration/mappers` is rebuilt with `ProcessorOptions.debugGraphs == true` and the produced `transforms`-view file for the `mapHuman` scope is inspected
- **THEN** for the subgraph rooted at `tgt[addresses]:Optional<Set<Human.Address>>`, the REALISED edges trace at least one linear path back to `src[person]:Person`, passing through `elem(element):Optional<Person.Address>`, `elem(element):Person.Address`, `elem(element):Human.Address`, and a `Set<Human.Address>` node
- **AND** no `elem(element)` node in the alive chain has zero outgoing REALISED edges except where it represents the source-parameter-root boundary
- **AND** no parallel REALISED edge connects `src[person.addresses]:List<Optional<Person.Address>>` directly to a `Set<Human.Address>` node with a `*Map`-style label (the old diamond's outer edge)

#### Scenario: No outer container-map shortcut edges in the full view either
- **WHEN** the `full`-view file for the same scope is inspected for the same mapper
- **THEN** for every pair of container-typed nodes joined by the chain pattern (Unwrap → … → Collect), the REALISED edges between them traverse `ElementLocation` nodes
- **AND** no REALISED edge connects two regular-scope container-typed nodes directly with a `*Map`-style strategy label

### Requirement: Plan view filter on MapperGraph

`MapperGraph` SHALL expose an accessor `planView()` that returns a `PlanView` — a non-destructive `GraphSource` exposing only the edges of the **chosen plan**. The view SHALL expose `Stream<Node> nodes()`, `Stream<Edge> edges()`, and `Stream<Node> nodesByScope(Scope)` with the same ordering guarantees as the other `MapperGraph` views, and SHALL NOT mutate the underlying graph.

A `REALISED` edge SHALL be excluded from the plan iff it belongs to at least one `ExpansionGroup` and none of its owning groups has a recorded `GroupOutcome.kind` of `SAT` (i.e. it is owned solely by `UNSAT_NO_PLAN` / `UNSAT_DID_NOT_CONVERGE` groups — a dead multi-fire sibling). Group-less REALISED edges are retained.

Among the retained edges, the plan SHALL select the **cheapest** producer at each OR choice point so that every node reachable in the plan from a return-root has exactly one producing group:

- The selection SHALL use a cost oracle `d(n)` = the minimum total `Edge.weight` of a path from any source-side leaf node to `n` over the retained subgraph. The implementation SHALL compute `d` with JGraphT `DijkstraShortestPath` over the retained subgraph made weighted via `AsWeightedGraph` (edge weight = `Edge.weight`), run from each in-degree-0 source node and taking the per-node minimum.
- Only **non-seed bridge groups** (strategy FQN not under `…stages.seed.`) co-rooted at a node count as competing OR-siblings; seed-registered scaffolding (path-segment / target-chain / directive-binding) co-roots with the real producer and is never pruned.
- At an OR node rooted by more than one competing group, the plan SHALL retain the group `g` minimising `weight(slot_g → node) + d(slot_g)` (tiebreak deterministic by `Node.id()`) and drop the losers' slot→root edges. The plan is then reachability-filtered from each return-root so disconnected loser/dead subtrees drop out.

The selection rule lives in the view consumer, not in the expansion engine: the engine records all siblings and outcomes; `planView()` chooses among them at view-construction time. This is the render-time sibling selection assigned to the consumer by the expansion model.

#### Scenario: planView excludes dead-sibling edges

- **WHEN** the underlying graph has a node with two producing groups, one `SAT` and one `UNSAT_NO_PLAN`, and `MapperGraph.planView()` is queried
- **THEN** `edges()` contains the `SAT` group's edges
- **AND** `edges()` contains no edge belonging only to the `UNSAT_NO_PLAN` group

#### Scenario: planView keeps all slots of an AND node

- **WHEN** a return-root node is the root of a single `ConstructorCall` group with slots `firstName` and `lastName`
- **THEN** the plan view contains the slot→root edges for both `firstName` and `lastName`

#### Scenario: planView picks the cheapest of two SAT siblings

- **WHEN** a node is the root of two competing `SAT` bridge groups whose slots have cost-to-source `d` values such that branch A's `weight + d` is strictly less than branch B's
- **THEN** the plan view contains branch A's edge into the node
- **AND** the plan view does not contain branch B's edge into the node

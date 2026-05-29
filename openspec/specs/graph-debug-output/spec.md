# Graph Debug Output Spec

## Purpose

This spec defines the DumpGraph stage and deterministic DOT renderer that serialize a `MapperGraph` to a DOT file for debugging and visualization purposes.

## Requirements

### Requirement: Expanded view filter on MapperGraph

`MapperGraph` SHALL expose an accessor `expandedView()` that returns an `ExpandedGraphView` — a non-destructive, pure-function filter over the underlying graph. The view SHALL be implemented as a JGraphT `MaskSubgraph` so the full graph is not copied, modified, or rebuilt. The view SHALL expose `Stream<Node> nodes()`, `Stream<Edge> edges()`, and `Stream<Node> nodesByScope(Scope)` with the same ordering guarantees as the corresponding `MapperGraph` methods.

The view's edge mask SHALL hide every `Edge` whose `kind` is `EdgeKind.SEED` or `EdgeKind.MARKER`. Edges of kind `EdgeKind.REALISED` SHALL pass through.

The view's vertex mask SHALL hide every untyped placeholder node (a `Node` whose type segment is the placeholder rendering used for nodes without a concrete type) when, and only when, another `Node` in the underlying graph shares the same `(scope, loc)` pair and carries a concrete (non-placeholder) type. Untyped nodes with no typed counterpart at the same `(scope, loc)` SHALL be retained — they are diagnostic evidence of an unresolved slot.

The view SHALL NOT mutate the underlying `MapperGraph`.

#### Scenario: expandedView accessor returns a non-null view
- **WHEN** `MapperGraph.expandedView()` is invoked on a non-empty graph
- **THEN** the returned `ExpandedGraphView` instance exposes `nodes()`, `edges()`, and `nodesByScope(Scope)` methods returning streams in the documented order

#### Scenario: SEED edges are filtered out of the view
- **WHEN** a graph containing edges of kind `SEED`, `REALISED`, and `MARKER` is exposed via `expandedView()`
- **THEN** the stream returned by `edges()` contains no edge with `kind == EdgeKind.SEED`

#### Scenario: MARKER edges are filtered out of the view
- **WHEN** a graph containing edges of kind `SEED`, `REALISED`, and `MARKER` is exposed via `expandedView()`
- **THEN** the stream returned by `edges()` contains no edge with `kind == EdgeKind.MARKER`

#### Scenario: REALISED edges are retained in the view
- **WHEN** a graph containing edges of kind `SEED`, `REALISED`, and `MARKER` is exposed via `expandedView()`
- **THEN** the stream returned by `edges()` contains every `REALISED` edge from the underlying graph

#### Scenario: Untyped placeholder is hidden when a typed counterpart exists
- **WHEN** the underlying graph contains a node `Nu` with the untyped placeholder type AND another node `Nt` with the same `(scope, loc)` pair as `Nu` but a concrete type
- **AND** `expandedView()` is queried
- **THEN** `Nu` is absent from the stream returned by `nodes()`
- **AND** `Nt` is present in the stream returned by `nodes()`

#### Scenario: Untyped placeholder is retained when no typed counterpart exists
- **WHEN** the underlying graph contains a node `Nu` with the untyped placeholder type AND no other node shares `Nu`'s `(scope, loc)` pair with a concrete type
- **AND** `expandedView()` is queried
- **THEN** `Nu` is present in the stream returned by `nodes()`

#### Scenario: View construction does not mutate the underlying graph
- **WHEN** `MapperGraph.expandedView()` is invoked
- **THEN** the underlying `MapperGraph` retains all its original nodes and edges (including those the view masks), as observable via `MapperGraph.nodes()` and `MapperGraph.edges()`

### Requirement: Node labels include the simple type segment

The DOT renderer SHALL render every node `label` attribute as a two-line value: the location segment (`src[…]`, `tgt[…]`, or the element-role segment for `ElementLocation` nodes) on the first line, followed by a newline (DOT `\n`), followed by the short type name on the second line. The short type name SHALL be derived from the node's type segment as follows:

- The prefix `java.lang.` SHALL be stripped from class names so that `java.lang.String` renders as `String` and `java.lang.Integer` renders as `Integer`. Rationale: `java.lang` is implicitly imported in Java source; unqualified rendering matches reading expectations.
- Other package prefixes SHALL be preserved verbatim so that `io.github.joke.testing.Person.Address` renders as `io.github.joke.testing.Person.Address`. Rationale: same-simple-name types across user packages would otherwise render indistinguishably.
- Generic type arguments SHALL be rewritten recursively under the same rule so that `java.util.List<java.util.Optional<java.lang.String>>` renders as `java.util.List<java.util.Optional<String>>`.
- The untyped placeholder SHALL render as the literal `?`.

Fully qualified types SHALL remain in `Node.id()` for graph determinism and uniqueness — only the visible `label` attribute is simplified.

The two-line label format SHALL apply uniformly to all rendered DOT output (both `seed.dot` and `expanded.dot`).

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
- **THEN** the DOT statement's node identifier (the quoted string preceding the attribute block) contains `java.lang.String` verbatim — only the `label` attribute is simplified

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

Each rendered edge's `kind` SHALL be identifiable from the DOT output without consulting the source graph data. The required identifier varies by kind:

- For `EdgeKind.SEED` edges, the edge `label` attribute SHALL include the literal token `SEED`.
- For `EdgeKind.REALISED` edges, the kind is identified by the combination of the edge's style attributes and the strategy short name in its label (see the "Node and edge visual distinction" requirement); the explicit token `REALISED` is not required in the label.
- For `EdgeKind.MARKER` edges (rendered only when the renderer is given such an edge directly, outside the expanded view), the `label` attribute SHALL include the literal token `MARKER`.

#### Scenario: SEED edge label includes the SEED token
- **WHEN** the renderer writes an edge with `kind == EdgeKind.SEED`
- **THEN** the edge's `label` attribute contains the literal `SEED`

#### Scenario: REALISED edge kind is identified by style and strategy
- **WHEN** the renderer writes an edge with `kind == EdgeKind.REALISED` and `strategyClassFqn == Optional.of("io.github.joke.percolate.spi.builtins.IterableUnwrap")`
- **THEN** the edge's `label` attribute contains the simple class name `IterableUnwrap`
- **AND** the edge's style attributes match the documented REALISED styling (distinct from SEED)
- **AND** the edge's `label` attribute does NOT contain the literal token `REALISED`

#### Scenario: MARKER edge rendered directly retains the MARKER token
- **WHEN** the renderer is given an edge with `kind == EdgeKind.MARKER` and renders it directly (outside the expanded view)
- **THEN** the edge's `label` attribute contains the literal `MARKER`

### Requirement: Node and edge visual distinction

The DOT renderer SHALL render nodes with shape attributes that visually distinguish source-located, target-located, and phantom container element nodes. Source-located nodes SHALL render with one shape (e.g., `box`); target-located nodes SHALL render with another (e.g., `oval`); phantom container element nodes SHALL render with a third distinct shape (e.g., `diamond`).

The DOT renderer SHALL render edges with style attributes (colour and/or line style) keyed off `Edge.kind`. The styling table SHALL define a distinct visual for each of `SEED` and `REALISED`. REALISED edges SHALL render with the heaviest visible stroke (e.g., `solid` line with elevated `penwidth`) — they represent the load-bearing transformations of the graph and SHALL dominate the visual hierarchy. SEED edges SHALL retain their prior styling (relevant for `seed.dot` rendering where they are the only edge kind present). MARKER edges, when rendered directly (outside the expanded view), MAY use the default fallback style; no dedicated MARKER style is required. The exact style attribute values are implementation-defined but SHALL be stable across runs.

For REALISED edges, the `label` attribute SHALL include the simple class name derived from `strategyClassFqn` and the edge's `weight`, formatted in a stable, byte-deterministic way (e.g., `IterableUnwrap (2)`). When `weight == Weights.SENTINEL_UNREALISED` the renderer SHALL emit the literal `∞` (U+221E) in place of the numeric value.

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
- **AND** that styling is visually heavier than the SEED styling

#### Scenario: Sentinel weight renders as infinity in REALISED labels
- **WHEN** the renderer writes a REALISED edge with `weight == Weights.SENTINEL_UNREALISED`
- **THEN** the edge's `label` attribute contains the literal `∞` (U+221E) instead of the numeric value

#### Scenario: REALISED edge label contains strategy short name and weight
- **WHEN** rendering a REALISED edge with `strategyClassFqn == Optional.of("io.github.joke.percolate.spi.builtins.IterableUnwrap")` and `weight == 2`
- **THEN** the edge's `label` attribute contains both the literal `IterableUnwrap` and the literal `2`
- **AND** the `label` does NOT contain the package prefix `io.github.joke.percolate.spi.builtins`

#### Scenario: SEED edge label retains kind, weight, and directive marker
- **WHEN** rendering a SEED edge with `weight == Weights.SENTINEL_UNREALISED` and a non-empty `directive`
- **THEN** the edge's `label` attribute contains the literal `SEED`
- **AND** the `label` contains the literal `∞`
- **AND** the `label` contains a marker (e.g., the literal `directive`) indicating its directive origin

#### Scenario: Group membership rendered via subgraph cluster
- **WHEN** the renderer writes a graph containing one or more registered `ExpansionGroup`s
- **THEN** each group is rendered as a DOT subgraph cluster grouping its root, slots, and slot-incoming REALISED edges
- **AND** edges belonging to multiple groups appear in each cluster their group's view contains them

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

The processor SHALL define a stage `DumpExpandedGraph` in package `io.github.joke.percolate.processor.stages.dump` that consumes the post-validation `MapperGraph` plus the originating `TypeElement` and, when enabled by `ProcessorOptions.debugGraphs`, writes a DOT representation of the **expanded view** of the graph (`MapperGraph.expandedView()`) to `StandardLocation.SOURCE_OUTPUT`. `DumpExpandedGraph` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

`DumpExpandedGraph` SHALL depend on `Filer`, `Diagnostics`, `ProcessorOptions`, and the deterministic DOT renderer (the same renderer used by `DumpGraph`).

`DumpExpandedGraph` SHALL run after `ValidateRealisationStage` in the pipeline. It SHALL write its file *regardless* of whether the mapper was scarred by validation — debug output is most valuable on failure.

The dumped file SHALL contain only the nodes and edges exposed by the expanded view (REALISED edges only; typed nodes plus any untyped placeholder nodes with no typed counterpart). SEED edges, MARKER edges, and hidden untyped placeholder nodes SHALL NOT appear in the file.

#### Scenario: Option off does not write a file
- **WHEN** `DumpExpandedGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == false`
- **THEN** no resource is created via `Filer`
- **AND** no diagnostic is emitted

#### Scenario: Option on writes a .expanded.dot file at SOURCE_OUTPUT
- **WHEN** `DumpExpandedGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == true` for a non-empty `MapperGraph` and a `TypeElement` representing FQN `com.example.PersonMapper`
- **THEN** `Filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "com.example.PersonMapper.expanded.dot", <originating element>)` is invoked
- **AND** the resource's contents are the DOT representation of `graph.expandedView()` as produced by the deterministic DOT renderer

#### Scenario: File is written even when mapper has validation errors
- **WHEN** `DumpExpandedGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == true` for a `MapperGraph` whose mapper was scarred by `ValidateMarkersPhase` or had Tier-3 errors emitted
- **THEN** the `.expanded.dot` file is still written
- **AND** the file contents include every REALISED edge exposed by the expanded view at the time of the dump
- **AND** the file contents do not include any SEED edge or MARKER edge

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

### Requirement: New container bridges render with their simple class name

The deterministic DOT renderer SHALL render REALISED edges emitted by the new container built-ins (`IterableUnwrap`, `OptionalCollect`, `SetCollect`, `ListCollect`, `ArrayCollect`) with `label` attributes containing the bridge's simple class name and weight, formatted by the same rule that applies to every REALISED edge (per the existing `Node and edge visual distinction` requirement).

No REALISED edge in any rendered DOT file SHALL carry a `strategyClassFqn` ending in `.SetMap`, `.ListMap`, or `.OptionalMap` — those classes are deleted by `split-container-bridges`. Any DOT file produced by the processor for a mapper compiled against the post-change classpath SHALL be free of those tokens in every edge label.

#### Scenario: IterableUnwrap REALISED edge label contains its simple name and weight
- **WHEN** the renderer writes a REALISED edge with `strategyClassFqn == Optional.of("io.github.joke.percolate.spi.builtins.IterableUnwrap")` and `weight == Weights.CONTAINER`
- **THEN** the edge's `label` attribute contains both the literal `IterableUnwrap` and the literal value of `Weights.CONTAINER` (rendered as the configured integer, e.g. `2`)
- **AND** the `label` does NOT contain the package prefix `io.github.joke.percolate.spi.builtins`

#### Scenario: SetCollect / ListCollect / ArrayCollect / OptionalCollect REALISED edge labels contain their simple names
- **WHEN** the renderer writes a REALISED edge whose `strategyClassFqn` resolves to one of `SetCollect`, `ListCollect`, `ArrayCollect`, or `OptionalCollect` under `io.github.joke.percolate.spi.builtins`
- **THEN** the edge's `label` attribute contains the simple class name verbatim
- **AND** the `label` does NOT contain the package prefix

#### Scenario: No DOT file contains the deleted container-map bridge names
- **WHEN** any `<MapperFQN>.seed.dot`, `<MapperFQN>.full.dot`, or `<MapperFQN>.transforms.dot` is produced for a mapper compiled with the post-change `strategies-builtin` module
- **THEN** no edge `label` attribute and no edge attribute string contains the literal token `SetMap`, `ListMap`, or `OptionalMap`

### Requirement: Linear container chains render without diamond shortcuts

The DOT renderer's output for any container-bearing chain (a chain involving an `*Unwrap` and a matching `*Collect`) SHALL be a linear sequence of REALISED edges from the regular-scope source candidate through `ElementLocation` nodes back to the regular-scope target candidate. No additional "outer" REALISED edge SHALL connect the source container directly to the target container in parallel with the chain.

This requirement formalises, at the rendering level, the structural invariant established by `graph-expansion`: chains are linear by construction; the renderer simply renders what the engine produces.

#### Scenario: Integration mapper addresses chain renders linearly in transforms.dot
- **WHEN** the integration mapper at `~/Projects/joke/percolate-integration/mappers` is rebuilt with `ProcessorOptions.debugGraphs == true` and the produced `PersonMapper.transforms.dot` is inspected
- **THEN** for the subgraph rooted at `tgt[addresses]:Optional<Set<Human.Address>>`, the REALISED edges trace at least one linear path back to `src[person]:Person`, passing through `elem(element):Optional<Person.Address>`, `elem(element):Person.Address`, `elem(element):Human.Address`, and a `Set<Human.Address>` node
- **AND** no `elem(element)` node in the alive chain has zero outgoing REALISED edges except where it represents the source-parameter-root boundary
- **AND** no parallel REALISED edge connects `src[person.addresses]:List<Optional<Person.Address>>` directly to a `Set<Human.Address>` node with a `*Map`-style label (the old diamond's outer edge)

#### Scenario: No outer container-map shortcut edges in full.dot either
- **WHEN** `PersonMapper.full.dot` is inspected for the same mapper
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

### Requirement: DumpPlan stage

The processor SHALL define a stage `DumpPlan` in package `io.github.joke.percolate.processor.stages.dump` that, when enabled by `ProcessorOptions.isDebugGraphs()`, writes a DOT representation of `MapperGraph.planView()` to `StandardLocation.SOURCE_OUTPUT`. `DumpPlan` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)` and SHALL depend on `Filer`, `Diagnostics`, `ProcessorOptions`, and the deterministic DOT renderer — mirroring `DumpTransforms`.

`DumpPlan` SHALL write its file regardless of whether the mapper was scarred by validation (debug output is most valuable on failure), and SHALL NOT write a file when the graph has zero nodes and zero edges. A `Filer`/`IOException` failure SHALL be reported as a `Diagnostics` warning (not an error) and SHALL NOT abort the compile.

The `.transforms.dot` output (`MapperGraph.transformsView()`) SHALL be left unchanged and SHALL continue to include dead (`UNSAT`) multi-fire sibling branches — its purpose is debugging, and the dead branches are part of that picture. `.plan.dot` is the complementary view that shows only the chosen plan.

#### Scenario: Option off does not write a plan file

- **WHEN** `DumpPlan` runs with `ProcessorOptions.isDebugGraphs() == false`
- **THEN** no resource is created via `Filer`
- **AND** no diagnostic is emitted

#### Scenario: Option on writes a .plan.dot file at SOURCE_OUTPUT

- **WHEN** `DumpPlan` runs with `ProcessorOptions.isDebugGraphs() == true` for a non-empty `MapperGraph` and a `TypeElement` representing FQN `com.example.PersonMapper`
- **THEN** `Filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "com.example.PersonMapper.plan.dot", <originating element>)` is invoked
- **AND** the resource's contents are the DOT representation of `graph.planView()` as produced by the deterministic DOT renderer

#### Scenario: plan.dot and transforms.dot coexist

- **WHEN** the pipeline completes for `com.example.PersonMapper` with `ProcessorOptions.isDebugGraphs() == true` and a non-empty graph
- **THEN** both `com.example.PersonMapper.plan.dot` and `com.example.PersonMapper.transforms.dot` are present in `SOURCE_OUTPUT`
- **AND** `com.example.PersonMapper.transforms.dot` still contains the dead-sibling edges absent from `com.example.PersonMapper.plan.dot`

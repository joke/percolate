## ADDED Requirements

### Requirement: Expanded view filter on MapperGraph

`MapperGraph` SHALL expose an accessor `expandedView()` that returns an `ExpandedGraphView` — a non-destructive, pure-function filter over the underlying graph. The view SHALL be implemented as a JGraphT `MaskSubgraph` so the full graph is not copied, modified, or rebuilt. The view SHALL expose `Stream<Node> nodes()`, `Stream<Edge> edges()`, and `Stream<Node> nodesByScope(Scope)` with the same ordering guarantees as the corresponding `MapperGraph` methods.

The view's edge mask SHALL hide every `Edge` whose `kind` is `EdgeKind.SEED` or `EdgeKind.MARKER`. Edges of kind `EdgeKind.REALISED` and `EdgeKind.SUB_SEED` SHALL pass through.

The view's vertex mask SHALL hide every untyped placeholder node (a `Node` whose type segment is the placeholder rendering used for nodes without a concrete type) when, and only when, another `Node` in the underlying graph shares the same `(scope, loc)` pair and carries a concrete (non-placeholder) type. Untyped nodes with no typed counterpart at the same `(scope, loc)` SHALL be retained — they are diagnostic evidence of an unresolved slot.

The view SHALL NOT mutate the underlying `MapperGraph`.

#### Scenario: expandedView accessor returns a non-null view
- **WHEN** `MapperGraph.expandedView()` is invoked on a non-empty graph
- **THEN** the returned `ExpandedGraphView` instance exposes `nodes()`, `edges()`, and `nodesByScope(Scope)` methods returning streams in the documented order

#### Scenario: SEED edges are filtered out of the view
- **WHEN** a graph containing edges of kind `SEED`, `REALISED`, `SUB_SEED`, and `MARKER` is exposed via `expandedView()`
- **THEN** the stream returned by `edges()` contains no edge with `kind == EdgeKind.SEED`

#### Scenario: MARKER edges are filtered out of the view
- **WHEN** a graph containing edges of kind `SEED`, `REALISED`, `SUB_SEED`, and `MARKER` is exposed via `expandedView()`
- **THEN** the stream returned by `edges()` contains no edge with `kind == EdgeKind.MARKER`

#### Scenario: REALISED and SUB_SEED edges are retained in the view
- **WHEN** a graph containing edges of kind `SEED`, `REALISED`, `SUB_SEED`, and `MARKER` is exposed via `expandedView()`
- **THEN** the stream returned by `edges()` contains every `REALISED` edge from the underlying graph
- **AND** the stream returned by `edges()` contains every `SUB_SEED` edge from the underlying graph

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

## MODIFIED Requirements

### Requirement: DumpExpandedGraph stage

The processor SHALL define a stage `DumpExpandedGraph` in package `io.github.joke.percolate.processor.stages.dump` that consumes the post-validation `MapperGraph` plus the originating `TypeElement` and, when enabled by `ProcessorOptions.debugGraphs`, writes a DOT representation of the **expanded view** of the graph (`MapperGraph.expandedView()`) to `StandardLocation.SOURCE_OUTPUT`. `DumpExpandedGraph` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

`DumpExpandedGraph` SHALL depend on `Filer`, `Diagnostics`, `ProcessorOptions`, and the deterministic DOT renderer (the same renderer used by `DumpGraph`).

`DumpExpandedGraph` SHALL run after `ValidateRealisationStage` in the pipeline. It SHALL write its file *regardless* of whether the mapper was scarred by validation — debug output is most valuable on failure.

The dumped file SHALL contain only the nodes and edges exposed by the expanded view (REALISED and SUB_SEED edges; typed nodes plus any untyped placeholder nodes with no typed counterpart). SEED edges, MARKER edges, and hidden untyped placeholder nodes SHALL NOT appear in the file.

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
- **AND** the file contents include every REALISED and SUB_SEED edge exposed by the expanded view at the time of the dump
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

### Requirement: Edge label includes EdgeKind marker

Each rendered edge's `kind` SHALL be identifiable from the DOT output without consulting the source graph data. The required identifier varies by kind:

- For `EdgeKind.SEED` edges, the edge `label` attribute SHALL include the literal token `SEED`.
- For `EdgeKind.SUB_SEED` edges, the edge `label` attribute SHALL contain the literal token `SUB_SEED` (and no other content — see the "Node and edge visual distinction" requirement).
- For `EdgeKind.REALISED` edges, the kind is identified by the combination of the edge's style attributes and the strategy short name in its label (see the "Node and edge visual distinction" requirement); the explicit token `REALISED` is not required in the label.
- For `EdgeKind.MARKER` edges (rendered only when the renderer is given such an edge directly, outside the expanded view), the `label` attribute SHALL include the literal token `MARKER`.

#### Scenario: SEED edge label includes the SEED token
- **WHEN** the renderer writes an edge with `kind == EdgeKind.SEED`
- **THEN** the edge's `label` attribute contains the literal `SEED`

#### Scenario: SUB_SEED edge label is the SUB_SEED token
- **WHEN** the renderer writes an edge with `kind == EdgeKind.SUB_SEED`
- **THEN** the edge's `label` attribute is the literal `SUB_SEED`

#### Scenario: REALISED edge kind is identified by style and strategy
- **WHEN** the renderer writes an edge with `kind == EdgeKind.REALISED` and `strategyClassFqn == Optional.of("io.github.joke.percolate.processor.spi.builtins.GetterRead")`
- **THEN** the edge's `label` attribute contains the simple class name `GetterRead`
- **AND** the edge's style attributes match the documented REALISED styling (distinct from SEED and SUB_SEED)
- **AND** the edge's `label` attribute does NOT contain the literal token `REALISED`

#### Scenario: MARKER edge rendered directly retains the MARKER token
- **WHEN** the renderer is given an edge with `kind == EdgeKind.MARKER` and renders it directly (outside the expanded view)
- **THEN** the edge's `label` attribute contains the literal `MARKER`

### Requirement: Node and edge visual distinction

The DOT renderer SHALL render nodes with shape attributes that visually distinguish source-located, target-located, and phantom container element nodes. Source-located nodes SHALL render with one shape (e.g., `box`); target-located nodes SHALL render with another (e.g., `oval`); phantom container element nodes SHALL render with a third distinct shape (e.g., `diamond`).

The DOT renderer SHALL render edges with style attributes (colour and/or line style) keyed off `Edge.kind`. The styling table SHALL define a distinct visual for each of `SEED`, `REALISED`, and `SUB_SEED`. REALISED edges SHALL render with the heaviest visible stroke (e.g., `solid` line with elevated `penwidth`) — they represent the load-bearing transformations of the graph and SHALL dominate the visual hierarchy. SUB_SEED edges SHALL render with a visually secondary style (e.g., `solid` line, low `penwidth`, gray colour) so the eye lands on REALISED first. SEED edges SHALL retain their prior styling (relevant for `seed.dot` rendering where they are the only edge kind present). MARKER edges, when rendered directly (outside the expanded view), MAY use the default fallback style; no dedicated MARKER style is required. The exact style attribute values are implementation-defined but SHALL be stable across runs.

For REALISED edges, the `label` attribute SHALL include the simple class name derived from `strategyClassFqn` and the edge's `weight`, formatted in a stable, byte-deterministic way (e.g., `GetterRead (1)`). When `weight == Weights.SENTINEL_UNREALISED` the renderer SHALL emit the literal `∞` (U+221E) in place of the numeric value. For REALISED edges with a non-empty `groupId`, the renderer SHALL include the `groupId` value as an edge attribute (or as part of the label) so grouped edges (e.g., constructor argument bundles) are visually identifiable.

For SUB_SEED edges, the `label` attribute SHALL contain only the literal token `SUB_SEED`. The strategy attribution, weight, and directive marker SHALL NOT appear in SUB_SEED edge labels — they are intentionally omitted to reduce visual clutter. The strategy that emitted a SUB_SEED is recoverable from the REALISED edge the same strategy contributes elsewhere in the graph.

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
- **AND** that styling is visually heavier than the SUB_SEED and SEED stylings

#### Scenario: SUB_SEED edge style is visually secondary
- **WHEN** the renderer writes an edge with `kind == EdgeKind.SUB_SEED`
- **THEN** the edge's style attributes are the documented SUB_SEED styling, distinct from REALISED and SEED

#### Scenario: Sentinel weight renders as infinity in REALISED labels
- **WHEN** the renderer writes a REALISED edge with `weight == Weights.SENTINEL_UNREALISED`
- **THEN** the edge's `label` attribute contains the literal `∞` (U+221E) instead of the numeric value

#### Scenario: REALISED edge label contains strategy short name and weight
- **WHEN** rendering a REALISED edge with `strategyClassFqn == Optional.of("io.github.joke.percolate.processor.spi.builtins.GetterRead")` and `weight == 1`
- **THEN** the edge's `label` attribute contains both the literal `GetterRead` and the literal `1`
- **AND** the `label` does NOT contain the package prefix `io.github.joke.percolate.processor.spi.builtins`

#### Scenario: SUB_SEED edge label contains only the kind token
- **WHEN** rendering a SUB_SEED edge with `strategyClassFqn == Optional.of("io.github.joke.percolate.processor.spi.builtins.OptionalWrap")`, `weight == Weights.SENTINEL_UNREALISED`, and a non-empty `directive`
- **THEN** the edge's `label` attribute is exactly `SUB_SEED`
- **AND** the `label` does NOT contain the strategy class name (full or simple)
- **AND** the `label` does NOT contain the literal `∞` (since the weight is omitted)
- **AND** the `label` does NOT contain the literal `directive`

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

### Requirement: DOT renderer renders REALISED, MARKER, and SUB_SEED edges

The DOT renderer SHALL emit DOT statements for `EdgeKind.REALISED`, `EdgeKind.MARKER`, and `EdgeKind.SUB_SEED` edges in addition to `EdgeKind.SEED`. Edge ordering SHALL remain ascending natural `Edge` order across all kinds (no kind-based grouping at the top level — the visual styling discriminates).

Per the "Node and edge visual distinction" requirement, REALISED edges SHALL include the strategy short name and weight in their label, and SUB_SEED edges SHALL include only the kind token. SEED and MARKER edges retain their prior label content when rendered.

#### Scenario: All edge kinds are emitted when given to the renderer
- **WHEN** rendering a graph containing one edge of each kind (`SEED`, `REALISED`, `SUB_SEED`, `MARKER`)
- **THEN** the DOT output contains exactly one edge statement per input edge
- **AND** each statement is keyed off its endpoints in the documented edge ordering

#### Scenario: Edge ordering is the natural edge order across all kinds
- **WHEN** rendering a graph with mixed-kind edges
- **THEN** edge statements appear in ascending natural `Edge` order regardless of `kind`

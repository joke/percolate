## MODIFIED Requirements

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
- **WHEN** the renderer writes an edge with `kind == EdgeKind.REALISED` and `strategyClassFqn == Optional.of("io.github.joke.percolate.spi.builtins.DirectAssign")`
- **THEN** the edge's `label` attribute contains the simple class name `DirectAssign`
- **AND** the edge's style attributes match the documented REALISED styling (distinct from SEED and SUB_SEED)
- **AND** the edge's `label` attribute does NOT contain the literal token `REALISED`

#### Scenario: REALISED edge with sentinel strategyClassFqn
- **WHEN** the renderer writes an edge with `kind == EdgeKind.REALISED` and `strategyClassFqn == Optional.of("io.github.joke.percolate.processor.stages.expand.DirectiveBinding")`
- **THEN** the edge's `label` attribute contains the simple name `DirectiveBinding`
- **AND** the simple name is derived from the FQN by string split on `.`; no class with that FQN is required to exist on the classpath

#### Scenario: MARKER edge rendered directly retains the MARKER token
- **WHEN** the renderer is given an edge with `kind == EdgeKind.MARKER` and renders it directly (outside the expanded view)
- **THEN** the edge's `label` attribute contains the literal `MARKER`

### Requirement: Node and edge visual distinction

The DOT renderer SHALL render nodes with shape attributes that visually distinguish source-located, target-located, and phantom container element nodes. Source-located nodes SHALL render with one shape (e.g., `box`); target-located nodes SHALL render with another (e.g., `oval`); phantom container element nodes SHALL render with a third distinct shape (e.g., `diamond`).

The DOT renderer SHALL render edges with style attributes (colour and/or line style) keyed off `Edge.kind`. The styling table SHALL define a distinct visual for each of `SEED`, `REALISED`, and `SUB_SEED`. REALISED edges SHALL render with the heaviest visible stroke (e.g., `solid` line with elevated `penwidth`) — they represent the load-bearing transformations of the graph and SHALL dominate the visual hierarchy. SUB_SEED edges SHALL render with a visually secondary style (e.g., `solid` line, low `penwidth`, gray colour) so the eye lands on REALISED first. SEED edges SHALL retain their prior styling (relevant for `seed.dot` rendering where they are the only edge kind present). MARKER edges, when rendered directly (outside the expanded view), MAY use the default fallback style; no dedicated MARKER style is required. The exact style attribute values are implementation-defined but SHALL be stable across runs.

For REALISED edges, the `label` attribute SHALL include the simple class name derived from `strategyClassFqn` and the edge's `weight`, formatted in a stable, byte-deterministic way (e.g., `DirectAssign (1)`). When `weight == Weights.SENTINEL_UNREALISED` the renderer SHALL emit the literal `∞` (U+221E) in place of the numeric value. For REALISED edges with a non-empty `groupId`, the renderer SHALL include the `groupId` value as an edge attribute (or as part of the label) so grouped edges (e.g., constructor argument bundles) are visually identifiable.

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
- **WHEN** rendering a REALISED edge with `strategyClassFqn == Optional.of("io.github.joke.percolate.spi.builtins.DirectAssign")` and `weight == 1`
- **THEN** the edge's `label` attribute contains both the literal `DirectAssign` and the literal `1`
- **AND** the `label` does NOT contain the package prefix `io.github.joke.percolate.spi.builtins`

#### Scenario: SUB_SEED edge label contains only the kind token
- **WHEN** rendering a SUB_SEED edge with `strategyClassFqn == Optional.of("io.github.joke.percolate.spi.builtins.OptionalWrap")`, `weight == Weights.SENTINEL_UNREALISED`, and a non-empty `directive`
- **THEN** the edge's `label` attribute is exactly `SUB_SEED`
- **AND** the `label` does NOT contain the strategy class name (full or simple)

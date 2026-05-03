## ADDED Requirements

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

## MODIFIED Requirements

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

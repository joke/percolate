## MODIFIED Requirements

### Requirement: EdgeKind enum
The processor SHALL define an enum `EdgeKind` in `io.github.joke.percolate.processor.graph` with exactly five values:
- `SEED` — directive-seeded edges produced by `SeedGraph` from user `@Map` directives.
- `REALISED` — edges produced by expansion strategies from real Java machinery (getter call, setter call, method call, container extract/collect, conversion). Not produced in this change.
- `MARKER` — `realises` edges linking a seed node to its realised typed counterpart. Not produced in this change.
- `SUB_SEED` — sub-directive edges emitted by strategies mid-expansion to mark "this still needs realising". Not produced in this change.
- `ELEMENT_SEED` — element-scope seed edges emitted by container strategies to express the per-element conversion their outer edge promises. Connect two `ElementLocation` phantom nodes. Distinct from `SEED` so that the user-directive interpretation and the strategy-promise interpretation are separable by views, renderers, and the satisfy() search.

`EdgeKind` SHALL be referenced by `Edge.kind` and SHALL participate in `Edge` equality, hash, and comparison.

#### Scenario: EdgeKind has exactly five values
- **WHEN** `EdgeKind.values()` is invoked
- **THEN** the result contains exactly the values `SEED`, `REALISED`, `MARKER`, `SUB_SEED`, `ELEMENT_SEED` in that declaration order

### Requirement: Edge value type
The processor SHALL define a Lombok `@Value` class `Edge` in `io.github.joke.percolate.processor.graph` with the following fields:
- `Node from`
- `Node to`
- `int weight` — uses the scale documented in `Weights`. `SEED`, `SUB_SEED`, and `ELEMENT_SEED` edges use `Weights.SENTINEL_UNREALISED`. `REALISED` edges use a value in `{0, 1, 2, 3}`. `MARKER` edges use `Weights.NOOP`.
- `EdgeKind kind` — categorises the edge for view filtering, DOT styling, and dispatch.
- `Optional<AnnotationMirror> directive` — present when the edge was seeded by a user `@Map` directive (i.e., `kind == SEED` and emitted from `SeedGraph`); empty for `SUB_SEED`, `ELEMENT_SEED`, `REALISED`, and `MARKER` edges.
- `Optional<String> groupId` — present when the edge participates in a coordinated multi-edge group (constructor parameters, builder chain); empty otherwise.
- `Optional<EdgeCodegen> codegen` — present on `REALISED` edges; empty on `SEED`, `SUB_SEED`, `ELEMENT_SEED`, `MARKER` edges.
- `Optional<String> strategyClassFqn` — fully-qualified class name of the strategy that emitted this edge; populated by strategies via `getClass().getName()` at edge construction; empty for edges emitted by `SeedGraph` (which is not a strategy).

`Edge` SHALL be annotated `@Value @EqualsAndHashCode(exclude = {"codegen", "strategyClassFqn"})` so that equality and hashing are structural over `(from, to, weight, kind, directive, groupId)`. The `codegen` and `strategyClassFqn` fields are metadata and SHALL NOT participate in equality.

`Edge` SHALL implement `Comparable<Edge>` ordered by `(from.id(), to.id(), weight, kind, presence-of-directive, groupId)` so that sorted iteration of edges is well-defined.

`Edge` SHALL provide static factory methods that enforce per-kind invariants. The all-args constructor SHALL be package-private; consumers SHALL go through the factories:
- `Edge.seed(Node from, Node to, AnnotationMirror directive)` — produces an edge with `kind = SEED`, `weight = Weights.SENTINEL_UNREALISED`, `directive = Optional.of(directive)`, all other Optional fields empty.
- `Edge.realised(Node from, Node to, int weight, Optional<String> groupId, EdgeCodegen codegen, String strategyClassFqn)` — produces an edge with `kind = REALISED`, the supplied weight, `directive = Optional.empty()`, `codegen = Optional.of(codegen)`, `strategyClassFqn = Optional.of(strategyClassFqn)`.
- `Edge.marker(Node from, Node to, String strategyClassFqn)` — produces an edge with `kind = MARKER`, `weight = Weights.NOOP`, `directive`, `groupId`, `codegen` empty, `strategyClassFqn` populated.
- `Edge.subSeed(Node from, Node to, String strategyClassFqn)` — produces an edge with `kind = SUB_SEED`, `weight = Weights.SENTINEL_UNREALISED`, `directive`, `groupId`, `codegen` empty, `strategyClassFqn` populated.
- `Edge.elementSeed(Node from, Node to, String strategyClassFqn)` — produces an edge with `kind = ELEMENT_SEED`, `weight = Weights.SENTINEL_UNREALISED`, `directive`, `groupId`, `codegen` empty, `strategyClassFqn` populated. Both `from` and `to` MUST have `loc` of type `ElementLocation`.

#### Scenario: Directive-seeded edge carries the mirror, kind SEED, sentinel weight
- **WHEN** `Edge.seed(...)` is invoked with a `@Map(target = "lastName", source = "lastName")` directive's mirror
- **THEN** the resulting edge has `kind == EdgeKind.SEED`, `weight == Weights.SENTINEL_UNREALISED`, non-empty `directive` containing that `AnnotationMirror`, and empty `groupId`, `codegen`, `strategyClassFqn`

#### Scenario: Realised edge factory populates codegen and strategyClassFqn
- **WHEN** `Edge.realised(from, to, Weights.STEP, Optional.empty(), <closure>, "com.example.GetterReadStrategy")` is invoked
- **THEN** the resulting edge has `kind == EdgeKind.REALISED`, `weight == 1`, empty `directive`, empty `groupId`, non-empty `codegen` referencing the closure, non-empty `strategyClassFqn` equal to `"com.example.GetterReadStrategy"`

#### Scenario: Marker edge has weight zero, no codegen
- **WHEN** `Edge.marker(seedNode, realisedNode, "com.example.GetterReadStrategy")` is invoked
- **THEN** the resulting edge has `kind == EdgeKind.MARKER`, `weight == 0`, empty `directive`, `groupId`, `codegen`, and non-empty `strategyClassFqn`

#### Scenario: SubSeed edge has sentinel weight, no directive
- **WHEN** `Edge.subSeed(from, to, "com.example.SomeStrategy")` is invoked
- **THEN** the resulting edge has `kind == EdgeKind.SUB_SEED`, `weight == Weights.SENTINEL_UNREALISED`, empty `directive`, `groupId`, `codegen`, and non-empty `strategyClassFqn`

#### Scenario: ElementSeed edge has sentinel weight, ELEMENT_SEED kind, no directive
- **WHEN** `Edge.elementSeed(elemFrom, elemTo, "com.example.SetMap")` is invoked where both `elemFrom` and `elemTo` have `loc` of type `ElementLocation`
- **THEN** the resulting edge has `kind == EdgeKind.ELEMENT_SEED`, `weight == Weights.SENTINEL_UNREALISED`, empty `directive`, `groupId`, `codegen`, and non-empty `strategyClassFqn`

#### Scenario: Edge equality excludes codegen and strategyClassFqn
- **WHEN** two `Edge` instances are constructed with field-equal `(from, to, weight, kind, directive, groupId)` but different `codegen` closures and different `strategyClassFqn` strings
- **THEN** they compare equal under `equals` and produce identical hash codes

#### Scenario: Edge equality includes kind
- **WHEN** two `Edge` instances are constructed with field-equal `(from, to, weight, directive, groupId)` but different `kind` values
- **THEN** they compare unequal under `equals`

#### Scenario: Edges have stable ordering
- **WHEN** two `Edge` values are compared
- **THEN** the comparison is determined entirely by `from.id()`, then `to.id()`, then `weight`, then `kind`, then directive-presence, then `groupId` (no reliance on identity hashes)

### Requirement: RealisedSubgraph view
The processor SHALL define a class `RealisedSubgraph` in `io.github.joke.percolate.processor.graph` exposing a read-only view over a `MapperGraph` filtered to:
- **edges:** only edges with `kind == EdgeKind.REALISED` (excludes `SEED`, `MARKER`, `SUB_SEED`, `ELEMENT_SEED`),
- **nodes:** only nodes incident on at least one `REALISED` edge.

`RealisedSubgraph` SHALL expose:
- `nodes()` returning `Stream<Node>` in ascending `id()` order,
- `edges()` returning `Stream<Edge>` in ascending natural order,
- `nodesByScope(Scope)` returning the filtered subset of nodes for that scope, in ascending `id()` order.

`RealisedSubgraph` SHALL be read-only — no `addNode` / `addEdge` surface. The wrapper SHALL be implemented over `org.jgrapht.graph.MaskSubgraph` or an equivalent JGraphT view; no graph copy is performed.

In this change `MapperGraph.realisedSubgraph()` always returns an empty subgraph (zero nodes, zero edges) because no `REALISED` edges are produced.

#### Scenario: RealisedSubgraph is empty for seed-only graphs
- **WHEN** `MapperGraph.realisedSubgraph()` is invoked on a graph produced by `SeedGraph` for any non-empty mapper
- **THEN** `nodes().count() == 0` and `edges().count() == 0`

#### Scenario: RealisedSubgraph is read-only
- **WHEN** the public surface of `RealisedSubgraph` is inspected
- **THEN** no method exposes adding nodes or edges to the underlying graph

#### Scenario: RealisedSubgraph filter excludes SEED, MARKER, SUB_SEED, ELEMENT_SEED edges
- **WHEN** a `MapperGraph` is constructed with one `SEED` edge, one `MARKER` edge, one `SUB_SEED` edge, and one `ELEMENT_SEED` edge (via direct construction in tests)
- **THEN** `realisedSubgraph().edges().count() == 0`

#### Scenario: RealisedSubgraph includes only nodes incident on REALISED edges
- **WHEN** a `MapperGraph` is constructed with a `REALISED` edge between nodes A and B, plus a third unconnected node C (via direct construction in tests)
- **THEN** `realisedSubgraph().nodes()` contains A and B and SHALL NOT contain C

## ADDED Requirements

### Requirement: MapperGraph is append-only after construction
After the discover and seed/expand stages have populated a `MapperGraph`, no stage SHALL remove nodes or edges from it. `MapperGraph` exposes no removal methods; the invariant is structural rather than enforced at runtime. Filtering for any downstream consumer (validation, dumping, future codegen) SHALL be expressed as a view (`RealisedSubgraph`, `transformsView`, or a pass-through) rather than as a destructive mutation.

This invariant is what permits views to be shared across consumers within a per-mapper pipeline run without lifetime or cache-invalidation concerns: the underlying data cannot change underneath them.

`MARKER` edges (and any other expansion artifacts that no downstream consumer requires) SHALL nonetheless remain in the underlying graph after expansion. The decision whether to render them is made at the view / renderer layer.

#### Scenario: MapperGraph exposes no node or edge removal
- **WHEN** the public surface of `MapperGraph` is inspected
- **THEN** no method removes a node or edge
- **AND** no method takes a `Node` or `Edge` argument with the intent to delete it

#### Scenario: MARKER edges survive into the post-expansion graph
- **WHEN** `ExpandStage` completes for a mapper that produced one or more `MARKER` edges during expansion
- **THEN** every `MARKER` edge produced during expansion is still present in `MapperGraph.edges()` after the stage returns
- **AND** they appear in any view that does not explicitly mask them

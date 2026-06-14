# Graph Debug Output Spec

## Purpose

This spec defines the debug-graph dump stages and the deterministic DOT renderer that serialize a `MapperGraph` (and its views) to DOT files for debugging and visualization purposes. Output is off by default (gated on `ProcessorOptions.debugGraphs`) and is written one file per scope per view.

## Requirements

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

### Requirement: Bipartite DOT rendering

The DOT renderer SHALL draw the bipartite graph Petri-style: `Operation` vertices as boxes labelled
with their codegen's simple class name (and weight), `Value` vertices as ellipses labelled with
location, simple type segment, and nullness, and `Dep` edges labelled with their port id (when
feeding an Operation port). Scope-owning Operations render their child scope as a DOT cluster.
Rendering remains deterministic (stable vertex and edge ordering).

#### Scenario: Vertex kinds are visually distinct
- **WHEN** a graph containing Values and Operations is rendered
- **THEN** Operations render as boxes and Values as ellipses, with port ids on port edges

#### Scenario: Child scope renders as a cluster
- **WHEN** a scope-owning container Operation is rendered
- **THEN** its child scope's vertices appear inside a DOT cluster attached to the Operation

### Requirement: Three dumps over the bipartite graph

The three dump stages SHALL be retained with unchanged gating and file naming: the seed dump (roots
and goal-spec annotations), the full dump (every vertex and edge, **reachability-annotated** — i.e.
finite vs infinite extraction cost), and the transforms/plan dump (the extracted plan view only). The
full dump SHALL obtain reachability from the extracted plan's derived `reachable`/`cost` query, not
from a stored SAT predicate.

#### Scenario: Plan dump shows only chosen producers
- **WHEN** the plan dump is written for a method with competing constructors
- **THEN** only the chosen constructor Operation and its supply chains appear

#### Scenario: Full dump annotates reachability from cost
- **WHEN** the full dump is written
- **THEN** each vertex is annotated reachable/unreachable by finite vs infinite extraction cost, with
  no reference to a stored SAT bit

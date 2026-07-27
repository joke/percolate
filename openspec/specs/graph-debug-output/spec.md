# Graph Debug Output Spec

## Purpose

This spec defines the debug-graph dump stages and the deterministic DOT renderer that serialize a `MapperGraph` (and its views) to DOT files for debugging and visualization purposes. Output is off by default (gated on `ProcessorOptions.debugGraphs`) and is written one file per scope per view.

## Requirements

### Requirement: Deterministic DOT renderer

The processor SHALL define a `DotRenderer` in `io.github.joke.percolate.processor.graph` that produces a `String` DOT representation of a **single scope's** slice of the bipartite `MapperGraph` by delegating to JGraphT `org.jgrapht.nio.dot.DOTExporter`. The renderer SHALL NOT hand-assemble DOT statement text or emit `subgraph cluster_*` blocks; the overall statement structure and label/attribute-value escaping SHALL be owned by `DOTExporter`. The renderer MAY pre-quote the vertex-id and graph-caption strings it hands to the exporter's id/attribute providers (escaping `"` and `\`), since those identifiers derive from `GraphVertex.id()` and `Scope.encode()`.

The renderer SHALL be handed an `org.jgrapht.Graph<GraphVertex, Dep>` restricted to one `Scope` — the per-scope slice built by `GraphDumpWriter`, filtered to the vertices of that scope and the `Dep` edges among them.

The renderer SHALL configure `DOTExporter` so that:
- the vertex id provider returns `GraphVertex.id()` (fully qualified, stable, unique).
- the vertex attribute provider supplies the vertex `label` (per the "Bipartite DOT rendering" requirement: typed `label` for an `Operation`, readable type for a `Value`) and the visual attributes (box vs ellipse, fill, and the unreachable dimming).
- the edge attribute provider supplies the `Dep`'s port id as its `label` (when feeding an Operation port).
- `setGraphAttributeProvider` supplies a graph-level `label` attribute carrying the human-readable scope description, so the rendered graph is captioned with its scope.

Determinism: given the same scope slice, the produced `String` SHALL be byte-stable across runs. Vertices SHALL be presented to the exporter in ascending `GraphVertex.id()` order and edges in a deterministic `Dep` order (by source id, target id, then port id).

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
- **THEN** vertex statements appear in ascending `GraphVertex.id()` order

#### Scenario: Edge iteration order
- **WHEN** rendering a scope slice
- **THEN** edge statements appear in a deterministic `Dep` order (source id, target id, then port id)

#### Scenario: Special characters in labels are escaped by the exporter
- **WHEN** a node's label contains `"` or `\` or a newline character
- **THEN** the rendered DOT escapes those characters via `DOTExporter` so that the output is parseable by Graphviz

### Requirement: File naming

The processor SHALL write one DOT file per `(scope, view)` pair. Each file SHALL be named
`<MapperFQN>.<methodSimpleName>.<view>.dot`, where `<view>` is one of `full`, `transforms`, `plan`,
and `<methodSimpleName>` is the simple name of the scope's method. When two scopes of the same mapper
share a method simple name (overloads), the colliding files SHALL be disambiguated as
`<MapperFQN>.<methodSimpleName>-<n>.<view>.dot`, where `<n>` is a deterministic index assigned in a
stable order over the colliding scopes. There is no `seed` view: the graph starts empty and is grown
by expansion, so there is no pre-expansion snapshot to dump.

A single mapper with multiple scopes SHALL therefore produce one file per scope per view; the scopes
do not share a file.

A **child (element) scope** owned by a scope-owning container Operation has no method of its own; its
file SHALL be named under its enclosing method with an `-elem` infix
(`<MapperFQN>.<enclosingMethod>-elem.<view>.dot`), rather than encoding the owning Operation's id into
the file name (which would compound for deep container nesting). Multiple element scopes under one
method SHALL be disambiguated by the same `-<n>` indexing as overloads.

#### Scenario: Child scope file uses the -elem infix under its enclosing method

- **WHEN** the `full` view is written for a mapper whose method `mapPeople` owns a container element
  scope
- **THEN** the element scope's file is named `<MapperFQN>.mapPeople-elem.full.dot` (or `mapPeople-elem-<n>`
  when several element scopes share the method)

#### Scenario: File name encodes mapper, method, and view

- **WHEN** the `full` view is written for `com.example.PersonMapper`, scope `mapHuman(...)`
- **THEN** the file name passed to `Filer.createResource(...)` is `com.example.PersonMapper.mapHuman.full.dot`

#### Scenario: Each scope of a mapper gets its own file

- **WHEN** the `full` view is written for `com.example.PersonMapper` which has scopes `mapHuman(...)` and `mapAddress(...)`
- **THEN** two files are written: `com.example.PersonMapper.mapHuman.full.dot` and `com.example.PersonMapper.mapAddress.full.dot`

#### Scenario: Overloaded methods are disambiguated by index

- **WHEN** a mapper has two scopes whose method simple name is both `map`
- **THEN** the two `full` files are named with distinct `map-<n>` infixes (e.g. `...map-0.full.dot` and `...map-1.full.dot`), assigned in a stable order

### Requirement: Shared dump IO via GraphDumpWriter

The processor SHALL define a single collaborator `GraphDumpWriter` in package `io.github.joke.percolate.processor.internal.stages.dump` that owns the entire dump IO mechanism: the `ProcessorOptions.isDebugGraphs()` gate, the empty-graph skip, the errored-mapper skip, the per-scope partition, the `DotRenderer` pass, the `Filer.createResource(StandardLocation.SOURCE_OUTPUT, …)` write per scope, and the `IOException`→warning handling. `GraphDumpWriter` SHALL be `@Inject`-constructed and SHALL depend on `Filer`, `ProcessorOptions`, and the `DotRenderer` — **not** on any diagnostics collaborator, there being none: a write failure is recorded as a warning `Diagnostic` on the per-mapper `MapperContext` it is handed.

Each dump stage (`DumpFullGraphStage`, `DumpTransformsStage`, `DumpPlanStage`) SHALL delegate to `GraphDumpWriter`,
supplying a vertex-inclusion `Predicate<GraphVertex>` and its `<view>` infix: `full` includes every
vertex (`vertex -> true`) and additionally requests unreachable-dimming; `transforms` includes the
reachable vertices (`plan::reachable`); `plan` includes the in-plan vertices (chosen-producer membership).
`full` and `plan` SHALL additionally request refusal rendering; `transforms` SHALL NOT, so that view is
unchanged by this capability's refusal requirement.
All dump stages SHALL run **after** the expansion stage (there is no pre-expansion seed dump). A
`Filer`/`IOException` failure SHALL be recorded as a warning `Diagnostic`, SHALL NOT be an error, and SHALL
NOT abort the compile.

When partitioning a view's edges by scope, an edge SHALL be assigned to the scope of its `from` node, so that no edge is dropped even though, by construction, edges do not span scopes.

#### Scenario: Option off writes no file for any view
- **WHEN** a dump stage runs with `ProcessorOptions.isDebugGraphs() == false`
- **THEN** no resource is created via `Filer`

#### Scenario: Empty graph writes no file even when option on
- **WHEN** a dump stage runs with the option on and the mapper's graph has no vertices
- **THEN** no resource is created via `Filer`

#### Scenario: One write per scope when option on
- **WHEN** a dump stage runs with the option on over a graph spanning several scopes
- **THEN** exactly one resource is created per scope

#### Scenario: Filer failure is a warning, not an error
- **WHEN** a dump stage runs with the option on and `Filer.createResource(...)` (or the subsequent write) throws `IOException`
- **THEN** a warning `Diagnostic` is recorded on the mapper's context
- **AND** no error is recorded
- **AND** the stage returns normally so the compile is not aborted

#### Scenario: Edge is partitioned to its from-node scope
- **WHEN** a view's edges are partitioned by scope for file output
- **THEN** each edge is rendered in the file of its `from` node's scope

#### Scenario: An errored mapper writes no file
- **WHEN** a dump stage runs for a mapper whose context already carries an error
- **THEN** no resource is created via `Filer`, so a refusal that failed the compile is explained by the diagnostic rather than by a dump

### Requirement: Bipartite DOT rendering

The DOT renderer SHALL draw the bipartite graph directly: `Value` vertices and `Operation` vertices, with port
edges from a feeding `Value` to the consuming `Operation` and an output edge from an `Operation` to the `Value`
it produces, grouped into clusters by `Scope`. Beside the producers of a `Value` it SHALL additionally draw that
`Value`'s recorded refusals as non-participating negative space.

A `Value`'s nullness mark SHALL be taken from the nullness recorded on the graph. The renderer SHALL NOT derive
nullness by inspecting a type's annotations, and SHALL NOT match an annotation by simple name.

#### Scenario: Values and Operations are distinct vertices
- **WHEN** a graph containing one conversion is rendered
- **THEN** the output contains a vertex for each `Value` and a vertex for the `Operation`, with port and output edges between them

#### Scenario: Scopes render as clusters
- **WHEN** a graph with a method scope and a child scope is rendered
- **THEN** each scope's vertices are grouped into its own cluster

#### Scenario: The nullness mark comes from the graph
- **WHEN** a `Value` whose recorded nullness is `NULLABLE` is rendered
- **THEN** it is marked nullable, and the renderer inspects no annotation to decide this

#### Scenario: A type in a null-marked scope renders as non-null
- **WHEN** a `Value` carries a type with no explicit annotation inside a `@NullMarked` scope, recorded `NON_NULL`
- **THEN** it renders as non-null, agreeing with the resolver rather than with an annotation-name match

### Requirement: Three dumps over the bipartite graph

The three dump stages SHALL be retained with unchanged gating and file naming, all running **after**
expansion: the full dump (every vertex and edge, **reachability-annotated**), the transforms dump, and
the plan dump (the extracted plan view only). There is no seed dump (no separate seed stage exists).
The full dump SHALL obtain reachability from the extracted plan's derived `reachable`/`cost` query
(not from a stored SAT predicate) and SHALL render **all** vertices, visually **dimming** the
unreachable ones (e.g. grey fill / dashed outline) rather than omitting them — so the pruned
over-emission is distinguishable from the surviving plan at a glance.

#### Scenario: Plan dump shows only chosen producers
- **WHEN** the plan dump is written for a method with competing constructors
- **THEN** only the chosen constructor Operation and its supply chains appear

#### Scenario: Full dump dims unreachable vertices
- **WHEN** the full dump is written for a target with one surviving producer and several pruned
  over-emitted candidates
- **THEN** every vertex is present, the reachable ones rendered normally and the unreachable ones
  dimmed (grey/dashed) by finite vs infinite extraction cost, with no reference to a stored SAT bit

### Requirement: The full and plan dumps render inadmissible productions

The `full` and `plan` DOT dumps SHALL render the refusals recorded on each `Value` as visually distinct
negative space beside that `Value`'s producers, each labelled with its message. The `transforms` dump SHALL be
unchanged.

Rendering the refused candidates in the `plan` dump is deliberate: with an over-emit-and-prune engine, the
question a plan dump must answer is why a cheaper-looking candidate did not win, which is unanswerable from a
graph that draws only survivors.

Refusal rendering SHALL be deterministic, ordered as the refusals were recorded, and SHALL treat a refusal as
opaque text — the renderer SHALL NOT classify a refusal by its origin.

#### Scenario: The full dump shows a refused production
- **WHEN** a `Value` carries a refusal and the full graph is dumped
- **THEN** the refusal appears beside that `Value`, visually distinct from its producers, labelled with its message

#### Scenario: The plan dump shows why a candidate is absent
- **WHEN** a candidate was refused at a `Value` reached by the winning plan
- **THEN** the plan dump renders that refusal, so the absent candidate and its reason are both visible

#### Scenario: The transforms dump is unchanged
- **WHEN** the transforms dump is produced
- **THEN** it renders exactly as before, with no refusals

#### Scenario: Refusal rendering is deterministic
- **WHEN** the same graph is dumped twice
- **THEN** the refusals appear in the same order in both outputs

#### Scenario: A refused production is never drawn as an operation
- **WHEN** a dump renders a refusal
- **THEN** it is not drawn as an `Operation` vertex and carries no weight or port edges

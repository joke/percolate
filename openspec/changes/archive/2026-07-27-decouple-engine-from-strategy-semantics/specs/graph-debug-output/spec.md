## ADDED Requirements

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

## MODIFIED Requirements

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

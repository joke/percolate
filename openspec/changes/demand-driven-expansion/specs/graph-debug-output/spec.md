## MODIFIED Requirements

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

The processor SHALL define a single collaborator `GraphDumpWriter` in package
`io.github.joke.percolate.processor.stages.dump` that owns the entire dump IO mechanism: the
`ProcessorOptions.isDebugGraphs()` gate, the empty-graph skip, the per-scope partition, the
`DOTExporter` rendering pass, the `Filer.createResource(StandardLocation.SOURCE_OUTPUT, …)` write per
scope, and the `IOException`→warning handling. `GraphDumpWriter` SHALL be `@Inject`-constructed and
SHALL depend on `Filer`, `Diagnostics`, `ProcessorOptions`, and the `DotRenderer`.

Each dump stage (`DumpFullGraph`, `DumpTransforms`, `DumpPlan`) SHALL delegate to `GraphDumpWriter`,
supplying only its view selector (`g -> g`, `g -> g.transformsView()`, `g -> g.planView()`) and its
`<view>` infix. All dump stages SHALL run **after** the expansion stage (there is no pre-expansion
seed dump). A `Filer`/`IOException` failure SHALL be reported as a `Diagnostics` warning referencing
the originating `TypeElement`, SHALL NOT be an error, and SHALL NOT abort the compile.

When partitioning a view's edges by scope, an edge SHALL be assigned to the scope of its `from` node,
so that no edge is dropped even though, by construction, edges do not span scopes.

#### Scenario: Option off writes no file for any view

- **WHEN** a dump stage runs with `ProcessorOptions.isDebugGraphs() == false`
- **THEN** no resource is created via `Filer`
- **AND** no diagnostic is emitted

#### Scenario: Empty graph writes no file even when option on

- **WHEN** a dump stage runs with `ProcessorOptions.isDebugGraphs() == true` but the graph has zero nodes and zero edges
- **THEN** no resource is created via `Filer`
- **AND** no diagnostic is emitted

#### Scenario: One write per scope when option on

- **WHEN** `DumpFullGraph` runs with `ProcessorOptions.isDebugGraphs() == true` for a non-empty `MapperGraph` with two scopes
- **THEN** `GraphDumpWriter` invokes `Filer.createResource(...)` once per scope, each with the scope's `<MapperFQN>.<method>.full.dot` name
- **AND** each resource's contents are the `DOTExporter` rendering of that scope's slice

#### Scenario: Filer failure is a warning, not an error

- **WHEN** a dump stage runs with the option on and `Filer.createResource(...)` (or the subsequent write) throws `IOException`
- **THEN** a warning diagnostic is emitted via `Diagnostics` referencing the originating `TypeElement`
- **AND** no error is emitted
- **AND** the stage returns normally so the compile is not aborted

#### Scenario: Edge is partitioned to its from-node scope

- **WHEN** a view's edges are partitioned by scope for file output
- **THEN** each edge is rendered in the file of its `from` node's scope

### Requirement: Three dumps over the bipartite graph

The three dump stages SHALL be retained with unchanged gating and file naming, all running **after**
expansion: the full dump (every vertex and edge, **reachability-annotated** — i.e. finite vs infinite
extraction cost), the transforms dump, and the plan dump (the extracted plan view only). There is no
seed dump (no separate seed stage exists). The full dump SHALL obtain reachability from the extracted
plan's derived `reachable`/`cost` query, not from a stored SAT predicate.

#### Scenario: Plan dump shows only chosen producers

- **WHEN** the plan dump is written for a method with competing constructors
- **THEN** only the chosen constructor Operation and its supply chains appear

#### Scenario: Full dump annotates reachability from cost

- **WHEN** the full dump is written
- **THEN** each vertex is annotated reachable/unreachable by finite vs infinite extraction cost, with
  no reference to a stored SAT bit

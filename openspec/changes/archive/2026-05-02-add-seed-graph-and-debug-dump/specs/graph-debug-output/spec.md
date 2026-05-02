## ADDED Requirements

### Requirement: DumpGraph stage
The processor SHALL define a stage `DumpGraph` in package `io.github.joke.percolate.processor` that consumes a `MapperGraph` plus the originating `TypeElement` and, when enabled by `ProcessorOptions.debugGraphs`, writes a DOT representation of the graph to `StandardLocation.SOURCE_OUTPUT`. `DumpGraph` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

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

### Requirement: Node and edge visual distinction
The DOT renderer SHALL render nodes with shape attributes that visually distinguish source-located, target-located, and (in the future) intermediate nodes. Source-located nodes SHALL render with one shape (e.g., `box`); target-located nodes SHALL render with another (e.g., `oval`). Intermediate nodes (no `Location`) — not produced in this change — SHALL render with a third distinct shape when introduced.

Edge labels SHALL include the edge's `weight` and SHALL include a marker that it is directive-seeded when `directive` is non-empty.

#### Scenario: Source nodes render as box
- **WHEN** rendering a node whose `loc` is a `SourceLocation`
- **THEN** the DOT output contains `shape=box` (or another stable shape attribute documented in the implementation) for that node's statement

#### Scenario: Target nodes render with a different shape than source nodes
- **WHEN** rendering a node whose `loc` is a `TargetLocation`
- **THEN** the DOT output uses a shape distinct from the one used for `SourceLocation` nodes

#### Scenario: Directive-seeded edge is marked
- **WHEN** rendering an edge whose `directive` `Optional` is non-empty
- **THEN** the edge's attributes include a marker indicating its directive origin (e.g., `style=solid` and a `label` referencing the directive) distinguishable from a future strategy-seeded edge

### Requirement: File naming
The DOT file SHALL be named `<MapperFQN>.seed.dot`. The infix `.seed.` SHALL be reserved so that future expansion stages can write `<MapperFQN>.expanded.dot` alongside without collision.

#### Scenario: File name uses .seed.dot infix
- **WHEN** `DumpGraph.apply(...)` writes a file for `com.example.PersonMapper`
- **THEN** the file name passed to `Filer.createResource(...)` is exactly `"com.example.PersonMapper.seed.dot"`

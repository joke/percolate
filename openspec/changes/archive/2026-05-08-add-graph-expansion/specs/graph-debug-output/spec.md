## ADDED Requirements

### Requirement: DumpExpandedGraph stage

The processor SHALL define a stage `DumpExpandedGraph` in package
`io.github.joke.percolate.processor` that consumes the post-validation
`MapperGraph` plus the originating `TypeElement` and, when enabled by
`ProcessorOptions.debugGraphs`, writes a DOT representation of the
*full* graph (including SEED, REALISED, MARKER, and SUB_SEED edges) to
`StandardLocation.SOURCE_OUTPUT`. `DumpExpandedGraph` SHALL be
`@Inject`-constructed via Lombok
`@RequiredArgsConstructor(onConstructor_ = @Inject)`.

`DumpExpandedGraph` SHALL depend on `Filer`, `Diagnostics`,
`ProcessorOptions`, and the deterministic DOT renderer (the same
renderer used by `DumpGraph`).

`DumpExpandedGraph` SHALL run after `ValidateRealisationStage` in the
pipeline. It SHALL write its file *regardless* of whether the mapper
was scarred by validation — debug output is most valuable on failure.

#### Scenario: Option off does not write a file
- **WHEN** `DumpExpandedGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == false`
- **THEN** no resource is created via `Filer`
- **AND** no diagnostic is emitted

#### Scenario: Option on writes a .expanded.dot file at SOURCE_OUTPUT
- **WHEN** `DumpExpandedGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == true` for a non-empty `MapperGraph` and a `TypeElement` representing FQN `com.example.PersonMapper`
- **THEN** `Filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "com.example.PersonMapper.expanded.dot", <originating element>)` is invoked
- **AND** the resource's contents are the DOT representation of the full post-expansion graph as produced by the deterministic DOT renderer

#### Scenario: File is written even when mapper has validation errors
- **WHEN** `DumpExpandedGraph.apply(...)` is invoked with `ProcessorOptions.debugGraphs == true` for a `MapperGraph` whose mapper was scarred by `ValidateMarkersPhase` or had Tier-3 errors emitted
- **THEN** the `.expanded.dot` file is still written
- **AND** the file contents include all SEED, REALISED, MARKER, and SUB_SEED edges present in the graph at the time of the dump

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

The expanded-graph DOT file SHALL be named
`<MapperFQN>.expanded.dot`. The `.expanded.` infix mirrors the
`.seed.` infix used by `DumpGraph` and ensures the two DOT files
coexist in the same `SOURCE_OUTPUT` directory without collision.

#### Scenario: File name uses .expanded.dot infix
- **WHEN** `DumpExpandedGraph.apply(...)` writes a file for `com.example.PersonMapper`
- **THEN** the file name passed to `Filer.createResource(...)` is exactly `"com.example.PersonMapper.expanded.dot"`

#### Scenario: Both .seed.dot and .expanded.dot coexist
- **WHEN** the pipeline completes for `com.example.PersonMapper` with `ProcessorOptions.debugGraphs == true` and a non-empty graph
- **THEN** both `com.example.PersonMapper.seed.dot` and `com.example.PersonMapper.expanded.dot` are present in `SOURCE_OUTPUT`

### Requirement: DOT renderer renders REALISED, MARKER, and SUB_SEED edges

The DOT renderer SHALL emit DOT statements for `EdgeKind.REALISED`,
`EdgeKind.MARKER`, and `EdgeKind.SUB_SEED` edges using the kind-aware
styling table shipped by the `align-graph-for-expansion` change. The
renderer SHALL apply the same per-kind colour / line-style mapping
already specified for SEED edges, extended uniformly to the other
three kinds. Edge ordering SHALL remain ascending natural `Edge`
order across all kinds (no kind-based grouping at the top level — the
visual styling already discriminates).

The renderer SHALL include each edge's `strategyClassFqn` in its
attributes when present (per the requirement shipped by the alignment
change). For REALISED edges, this surfaces strategy provenance
directly in the DOT output for diagnostic and review purposes.

For edges with a non-empty `groupId`, the renderer SHALL include the
`groupId` value as an edge attribute (or as part of the label) so
that grouped edges (e.g., constructor argument bundles) are visually
identifiable.

#### Scenario: REALISED edge renders with documented styling
- **WHEN** the renderer writes an edge with `kind == EdgeKind.REALISED`
- **THEN** the edge's style attributes match the documented `REALISED` styling, distinct from `SEED`, `MARKER`, and `SUB_SEED`

#### Scenario: MARKER edge renders with documented styling
- **WHEN** the renderer writes an edge with `kind == EdgeKind.MARKER`
- **THEN** the edge's style attributes match the documented `MARKER` styling, distinct from the other three kinds

#### Scenario: SUB_SEED edge renders with documented styling
- **WHEN** the renderer writes an edge with `kind == EdgeKind.SUB_SEED`
- **THEN** the edge's style attributes match the documented `SUB_SEED` styling, distinct from the other three kinds

#### Scenario: groupId appears in edge label when present
- **WHEN** the renderer writes a REALISED edge whose `groupId` is non-empty
- **THEN** the edge's attributes include the `groupId` value (rendered as a stable string)
- **AND** edges sharing the same `groupId` render with the same `groupId` value, allowing the reader to identify the group

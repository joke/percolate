## MODIFIED Requirements

### Requirement: DumpGraph stage
The processor SHALL define a stage `DumpGraph` in package `io.github.joke.percolate.processor.stages.dump` that consumes a `MapperGraph` plus the originating `TypeElement` and, when enabled by `ProcessorOptions.debugGraphs`, writes a DOT representation of the graph to `StandardLocation.SOURCE_OUTPUT`. `DumpGraph` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

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

### Requirement: DumpExpandedGraph stage

The processor SHALL define a stage `DumpExpandedGraph` in package `io.github.joke.percolate.processor.stages.dump` that consumes the post-validation `MapperGraph` plus the originating `TypeElement` and, when enabled by `ProcessorOptions.debugGraphs`, writes a DOT representation of the *full* graph (including SEED, REALISED, MARKER, and SUB_SEED edges) to `StandardLocation.SOURCE_OUTPUT`. `DumpExpandedGraph` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

`DumpExpandedGraph` SHALL depend on `Filer`, `Diagnostics`, `ProcessorOptions`, and the deterministic DOT renderer (the same renderer used by `DumpGraph`).

`DumpExpandedGraph` SHALL run after `ValidateRealisationStage` in the pipeline. It SHALL write its file *regardless* of whether the mapper was scarred by validation — debug output is most valuable on failure.

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

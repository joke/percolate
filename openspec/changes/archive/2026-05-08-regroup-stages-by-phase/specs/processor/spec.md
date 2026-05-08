## MODIFIED Requirements

### Requirement: Stage interface

The processor SHALL define an interface `Stage` in package `io.github.joke.percolate.processor.stages` such that every pipeline stage implements it. The interface signature is implementation-defined but SHALL allow stages to consume and produce state via a shared context carrier (e.g., a per-mapper `MapperContext` instance) so that downstream stages can read state produced by upstream stages.

The `Stage` interface and its context carrier SHALL be internal to the processor module. They are not part of any public API.

#### Scenario: Stage interface exists in the processor.stages package
- **WHEN** the source of the `processor.stages` package is inspected
- **THEN** an interface or abstract base type `Stage` exists in `io.github.joke.percolate.processor.stages`

#### Scenario: All pipeline stages implement Stage
- **WHEN** the source of `DiscoverAbstractMethods`, `DiscoverMappings`, `ValidateNoDuplicateTargets`, `ValidateSourceParameters`, `SeedGraph`, `DumpGraph`, `ExpandStage`, `ValidateRealisationStage`, and `DumpExpandedGraph` is inspected
- **THEN** each declared type implements `Stage` (directly or transitively)

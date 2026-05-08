## MODIFIED Requirements

### Requirement: ValidateRealisationStage

The processor SHALL define a stage `ValidateRealisationStage` in package `io.github.joke.percolate.processor.stages.validate` that consumes the post-expansion `MapperGraph` and emits diagnostics for unrealisable directives. `ValidateRealisationStage` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

`ValidateRealisationStage` SHALL execute two sequential `ValidationPhase`s in declared order:
- `ValidateMarkersPhase` (Tier-2),
- `ValidatePathsPhase` (Tier-3).

Tier-2 SHALL run first and scar the mapper on failure. Tier-3 SHALL skip scarred mappers.

#### Scenario: Stage runs Tier-2 then Tier-3
- **WHEN** `ValidateRealisationStage.apply(graph, typeElement)` is invoked
- **THEN** `ValidateMarkersPhase.apply(graph, typeElement)` runs first
- **AND** `ValidatePathsPhase.apply(graph, typeElement)` runs next

#### Scenario: ValidateRealisationStage uses Lombok-generated injection
- **WHEN** the source of `ValidateRealisationStage` is inspected
- **THEN** the class carries `@RequiredArgsConstructor(onConstructor_ = @Inject)`
- **AND** the generated constructor accepts the two phase classes

## MODIFIED Requirements

### Requirement: ExpandStage

The processor SHALL define a stage `ExpandStage` in package `io.github.joke.percolate.processor.stages.expand` that consumes a `MapperGraph` populated with `EdgeKind.SEED` edges and augments it with `EdgeKind.REALISED`, `EdgeKind.MARKER`, and (forward-compat) `EdgeKind.SUB_SEED` edges. `ExpandStage` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

`ExpandStage` SHALL execute three sequential `ExpansionPhase`s in declared order: `ResolveSourceChainsPhase`, `ResolveTargetChainsPhase`, `BridgeSourceToTargetPhase`. Phases SHALL NOT be re-run; the pipeline is linear with no back-jumping.

#### Scenario: ExpandStage runs the three phases in declared order
- **WHEN** `ExpandStage.apply(graph)` is invoked
- **THEN** `ResolveSourceChainsPhase.apply(graph)` runs first
- **AND** `ResolveTargetChainsPhase.apply(graph)` runs next
- **AND** `BridgeSourceToTargetPhase.apply(graph)` runs last

#### Scenario: Each phase runs exactly once
- **WHEN** `ExpandStage.apply(graph)` completes for a given mapper
- **THEN** each phase has been invoked exactly once on that graph

#### Scenario: ExpandStage uses Lombok-generated injection
- **WHEN** the source of `ExpandStage` is inspected
- **THEN** the class carries `@RequiredArgsConstructor(onConstructor_ = @Inject)`
- **AND** the generated constructor accepts the three phase classes

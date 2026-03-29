## MODIFIED Requirements

### Requirement: Pipeline chains stages sequentially
The `Pipeline` SHALL execute stages in order: analyze → discover → build graph → validate → resolve transforms → validate transforms → generate. If any stage returns a failure, the pipeline SHALL stop processing that mapper, report all diagnostics to `Messager`, and return `null`.

#### Scenario: All stages succeed
- **WHEN** all stages return success for a mapper
- **THEN** the pipeline SHALL write the generated `JavaFile` via `Filer` and return it

#### Scenario: Stage fails mid-pipeline
- **WHEN** a stage returns failure
- **THEN** the pipeline SHALL report all diagnostics from that failure to `Messager` and skip remaining stages for that mapper

### Requirement: Stages are Dagger-injected
All stages SHALL be constructor-injected by Dagger. The `Pipeline` SHALL receive all stages via its constructor. Stages SHALL declare dependencies on `Elements`, `Types`, `Messager`, or `Filer` as needed from the existing `ProcessorModule`.

#### Scenario: Pipeline receives stages from Dagger
- **WHEN** the `ProcessorComponent` is built
- **THEN** the `Pipeline` SHALL have all seven stages injected and ready to execute

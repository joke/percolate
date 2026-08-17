## ADDED Requirements

### Requirement: ProcessorOptions exposes constructionPreference

`ProcessorOptions` SHALL grow a `constructionPreference` field of an enum type with values `CONSTRUCTOR` and `BUILDER`, parsed from the compiler option `-Apercolate.construction.preference=…` (case-insensitive). The field SHALL default to `CONSTRUCTOR` when the option is absent or unrecognised.

The value SHALL be consumed by the assembly strategies of the `builder-assembly` capability, which read it through the generic `ResolveCtx.option(String)` lookup and price themselves accordingly. The processor's expansion, extraction, and generate stages SHALL NOT read it.

#### Scenario: Option absent yields CONSTRUCTOR
- **WHEN** `processingEnv.getOptions()` does not contain `"percolate.construction.preference"`
- **THEN** the produced `ProcessorOptions.constructionPreference` is `CONSTRUCTOR`

#### Scenario: Option set to builder yields BUILDER
- **WHEN** `processingEnv.getOptions()` contains `"percolate.construction.preference" -> "builder"`
- **THEN** the produced `ProcessorOptions.constructionPreference` is `BUILDER`

#### Scenario: Parsing is case-insensitive
- **WHEN** `processingEnv.getOptions()` contains `"percolate.construction.preference" -> "BUILDER"`
- **THEN** the produced `ProcessorOptions.constructionPreference` is `BUILDER`

#### Scenario: Unrecognised value falls back to CONSTRUCTOR
- **WHEN** `processingEnv.getOptions()` contains `"percolate.construction.preference" -> "nonsense"`
- **THEN** the produced `ProcessorOptions.constructionPreference` is `CONSTRUCTOR`

### Requirement: construction.preference option is declared

`PercolateProcessor.getSupportedOptions()` SHALL include the string `"percolate.construction.preference"` in its returned set, alongside the existing supported options.

#### Scenario: construction.preference option is declared
- **WHEN** `PercolateProcessor.getSupportedOptions()` is invoked
- **THEN** the returned set contains the string `"percolate.construction.preference"`

### Requirement: Declared option values reach strategies through the generic seam

Every processor option a strategy consumes SHALL reach that strategy through `ResolveCtx.option(String)`, keyed by the option's full `percolate.*` name. `ProcessorOptions` SHALL remain the parsing owner for engine-internal consumers, and the per-mapper `ResolveCtx` SHALL be constructed with access to the raw option map so that any declared key is answerable without a per-feature field.

#### Scenario: A strategy-consumed option needs no bespoke seam field
- **WHEN** the per-mapper `ResolveCtx` is constructed
- **THEN** it can answer `option(key)` for any declared `percolate.*` key
- **AND** it carries no field named for an individual feature's option

#### Scenario: Engine-internal options keep their typed fields
- **WHEN** an engine-internal consumer reads `debugGraphs`, `localsFinal`, or `classesFinal`
- **THEN** it reads the typed `ProcessorOptions` field, not the raw seam lookup

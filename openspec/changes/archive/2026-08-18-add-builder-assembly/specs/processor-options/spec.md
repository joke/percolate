## ADDED Requirements

### Requirement: construction.preference option is declared

`PercolateProcessor.getSupportedOptions()` SHALL include the string `"percolate.construction.preference"` in its returned set, alongside the existing supported options, and `ProcessorOptions` SHALL declare the key as a constant.

The option's accepted values are `constructor` (the default) and `builder`. It carries **no** typed field on `ProcessorOptions`: like every other strategy-consumed option it is read raw through `ResolveCtx.option(String)` and parsed by the assembly strategies that own its meaning.

#### Scenario: construction.preference option is declared
- **WHEN** `PercolateProcessor.getSupportedOptions()` is invoked
- **THEN** the returned set contains the string `"percolate.construction.preference"`

#### Scenario: The option carries no typed field
- **WHEN** `ProcessorOptions` is inspected
- **THEN** it declares the `percolate.construction.preference` key constant
- **AND** it declares no `constructionPreference` field

### Requirement: Strategy-consumed options carry no typed field

`ProcessorOptions` SHALL carry a typed field only for an option an **engine-internal** consumer reads. An option consumed by a strategy SHALL live only in the raw option map, be reached through `ResolveCtx.option(String)`, and be parsed by the strategy that owns its meaning — so exactly one parser exists per option, in the module that gives it meaning.

`ProcessorOptions` SHALL carry the raw `-A` option map verbatim, so the per-mapper `ResolveCtx` can answer `option(key)` for any declared key without a per-feature field.

#### Scenario: Engine-internal options keep their typed fields
- **WHEN** an engine-internal consumer reads `debugGraphs`, `localsFinal`, `parametersFinal`, `methodsFinal`, `classesFinal`, `docTags`, or `customNullableAnnotations`
- **THEN** it reads the typed `ProcessorOptions` field

#### Scenario: Strategy-consumed options have no typed field
- **WHEN** `ProcessorOptions` is inspected
- **THEN** it declares no `timeZone`, `switchStyle`, or `constructionPreference` field
- **AND** each of those options is reachable through the raw map by its declared key

#### Scenario: Exactly one parser exists per strategy-consumed option
- **WHEN** the parsing of `percolate.switch.style` is located
- **THEN** it lives solely in the enum-conversion strategy that reads it
- **AND** `ProcessorOptionsReader` parses it nowhere

#### Scenario: A strategy-consumed option needs no bespoke seam field
- **WHEN** the per-mapper `ResolveCtx` is constructed
- **THEN** it can answer `option(key)` for any declared `percolate.*` key
- **AND** it carries no field named for an individual feature's option

## REMOVED Requirements

### Requirement: ProcessorOptions exposes timeZone

**Reason**: `percolate.time.zone` is consumed by the temporal zone-bridge strategy, not by the engine. With the generic `ResolveCtx.option(String)` seam the strategy reads the raw value directly, leaving the typed `timeZone` field written by `ProcessorOptionsReader` and read by nothing. Keeping it would preserve a per-feature field on a value type whose remaining purpose is engine-internal options.

**Migration**: A consumer that read `ProcessorOptions.getTimeZone()` reads `ResolveCtx.option("percolate.time.zone")` instead, which returns the same raw `Optional<String>`. The option's key constant, its `getSupportedOptions()` entry, and every documented behaviour are unchanged.

### Requirement: ProcessorOptions exposes switchStyle

**Reason**: `percolate.switch.style` is consumed by the enum-conversion strategy's codegen, not by the engine. With the generic seam the strategy reads and parses the raw value itself, so the typed `switchStyle` field and `ProcessorOptionsReader.parseSwitchStyle` became a second, unread parser for an option already parsed in the strategy.

**Migration**: A consumer that read `ProcessorOptions.getSwitchStyle()` reads `ResolveCtx.option("percolate.switch.style")` and parses it. The `SwitchStyle` enum itself is unchanged and still published from `percolate-spi`; the `AUTO`/`CLASSIC`/`ARROW` values, the case-insensitive parse, and the degrade-to-`AUTO` behaviour for an absent or unrecognised value all move intact into the strategy.

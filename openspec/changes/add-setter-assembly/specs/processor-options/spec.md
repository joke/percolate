## ADDED Requirements

### Requirement: ProcessorOptions exposes helpersVisibility and helpersStatic

`ProcessorOptions` SHALL declare two typed fields describing the modifiers of every strategy-requested class member, both read by the generate stage:

- `MemberVisibility helpersVisibility` — parsed from `-Apercolate.helpers.visibility`, defaulting to `private` when the option is absent, empty, or unrecognised. Parsing SHALL be case-insensitive. `MemberVisibility` is a new enum in the `processor` module with the constants `PRIVATE`, `PACKAGE`, `PROTECTED` and `PUBLIC`. It SHALL NOT reuse the SPI's `Visibility`, which names scope-input reachability and is unrelated.
- `boolean helpersStatic` — `true` when `-Apercolate.helpers.static` is absent, and `true` when it is set to `true` in any letter case. Any other value yields `false`.

Both are engine-internal options, so both carry typed fields, in accordance with *Strategy-consumed options carry no typed field*. Exactly one parser SHALL exist for each, in `ProcessorOptionsReader`.

#### Scenario: Absent options yield the defaults
- **WHEN** `processingEnv.getOptions()` contains neither key
- **THEN** the produced `ProcessorOptions` has `helpersVisibility` equal to `private` and `helpersStatic == true`

#### Scenario: Visibility parses case-insensitively
- **WHEN** `processingEnv.getOptions()` contains the entry `"percolate.helpers.visibility" -> "PROTECTED"`
- **THEN** the produced `ProcessorOptions` has `helpersVisibility` equal to `protected`

#### Scenario: An unrecognised visibility degrades to private
- **WHEN** `processingEnv.getOptions()` contains the entry `"percolate.helpers.visibility" -> "wombat"`
- **THEN** the produced `ProcessorOptions` has `helpersVisibility` equal to `private`

#### Scenario: static defaults to true and is switched off explicitly
- **WHEN** `processingEnv.getOptions()` contains the entry `"percolate.helpers.static" -> "false"`
- **THEN** the produced `ProcessorOptions` has `helpersStatic == false`

#### Scenario: static parses case-insensitively
- **WHEN** `processingEnv.getOptions()` contains the entry `"percolate.helpers.static" -> "TRUE"`
- **THEN** the produced `ProcessorOptions` has `helpersStatic == true`

### Requirement: helpers.visibility and helpers.static options are declared

`PercolateProcessor.getSupportedOptions()` SHALL include the strings `"percolate.helpers.visibility"` and `"percolate.helpers.static"` in its returned set, alongside the existing supported options, and `ProcessorOptions` SHALL declare both keys as constants.

#### Scenario: Both options are declared
- **WHEN** `PercolateProcessor.getSupportedOptions()` is invoked
- **THEN** the returned set contains `"percolate.helpers.visibility"` and `"percolate.helpers.static"`

#### Scenario: Both keys are declared as constants
- **WHEN** `ProcessorOptions` is inspected
- **THEN** it declares a key constant for each of the two options

## MODIFIED Requirements

### Requirement: construction.preference option is declared

`PercolateProcessor.getSupportedOptions()` SHALL include the string `"percolate.construction.preference"` in its returned set, alongside the existing supported options, and `ProcessorOptions` SHALL declare the key as a constant.

The option's value is an **ordered, comma-separated list** of assembly form tokens drawn from `constructor`, `builder` and `setter`. Omitted tokens are appended in the fixed default order `constructor,builder,setter`, so an absent option ranks the constructor first. Both previously accepted values, `constructor` and `builder`, remain valid as one-element lists and keep their previous effect.

It carries **no** typed field on `ProcessorOptions`: like every other strategy-consumed option it is read raw through `ResolveCtx.option(String)` and parsed by the assembly strategies that own its meaning.

#### Scenario: construction.preference option is declared
- **WHEN** `PercolateProcessor.getSupportedOptions()` is invoked
- **THEN** the returned set contains the string `"percolate.construction.preference"`

#### Scenario: The option carries no typed field
- **WHEN** `ProcessorOptions` is inspected
- **THEN** it declares the `percolate.construction.preference` key constant
- **AND** it declares no `constructionPreference` field

#### Scenario: A previously accepted single value keeps its effect
- **WHEN** a build sets `-Apercolate.construction.preference=builder`
- **THEN** the builder form ranks first, exactly as before this change

### Requirement: Strategy-consumed options carry no typed field

`ProcessorOptions` SHALL carry a typed field only for an option an **engine-internal** consumer reads. An option consumed by a strategy SHALL live only in the raw option map, be reached through `ResolveCtx.option(String)`, and be parsed by the strategy that owns its meaning — so exactly one parser exists per option, in the module that gives it meaning.

`ProcessorOptions` SHALL carry the raw `-A` option map verbatim, so the per-mapper `ResolveCtx` can answer `option(key)` for any declared key without a per-feature field.

#### Scenario: Engine-internal options keep their typed fields
- **WHEN** an engine-internal consumer reads `debugGraphs`, `localsFinal`, `parametersFinal`, `methodsFinal`, `classesFinal`, `docTags`, `helpersVisibility`, `helpersStatic`, or `customNullableAnnotations`
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

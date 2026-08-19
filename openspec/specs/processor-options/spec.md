# Processor Options Spec

## Purpose

This spec defines the `ProcessorOptions` value type that parses compiler options and makes them available throughout the annotation processor via Dagger injection.

## Requirements

### Requirement: ProcessorOptions value type
The processor SHALL define a Lombok `@Value` class `ProcessorOptions` in `io.github.joke.percolate.processor` with at least the following field:
- `boolean debugGraphs` — `true` when the compiler option `-Apercolate.debug.graphs=true` is set; `false` otherwise.

`ProcessorOptions` SHALL be parsed from `processingEnv.getOptions()` once per round via a `@Provides` method on `ProcessorModule`.

#### Scenario: Option absent yields debugGraphs = false
- **WHEN** `processingEnv.getOptions()` does not contain the key `"percolate.debug.graphs"`
- **THEN** the produced `ProcessorOptions` has `debugGraphs == false`

#### Scenario: Option present and "true" yields debugGraphs = true
- **WHEN** `processingEnv.getOptions()` contains the entry `"percolate.debug.graphs" -> "true"`
- **THEN** the produced `ProcessorOptions` has `debugGraphs == true`

#### Scenario: Option present and "TRUE" yields debugGraphs = true
- **WHEN** `processingEnv.getOptions()` contains the entry `"percolate.debug.graphs" -> "TRUE"`
- **THEN** the produced `ProcessorOptions` has `debugGraphs == true`
- **AND** the parsing is case-insensitive

#### Scenario: Option present but not "true" yields debugGraphs = false
- **WHEN** `processingEnv.getOptions()` contains the entry `"percolate.debug.graphs" -> "yes"`
- **THEN** the produced `ProcessorOptions` has `debugGraphs == false`

### Requirement: Option declaration
`PercolateProcessor` SHALL override `getSupportedOptions()` to return a `Set<String>` containing at least the entry `"percolate.debug.graphs"`.

#### Scenario: Option is declared
- **WHEN** `PercolateProcessor.getSupportedOptions()` is invoked
- **THEN** the returned set contains the string `"percolate.debug.graphs"`

### Requirement: ProcessorOptions exposes customNullableAnnotations

`ProcessorOptions` SHALL grow a field `Set<String> customNullableAnnotations` carrying any additional `@Nullable` annotation FQNs configured by the user. The set SHALL be parsed from the compiler option `-Apercolate.nullable.annotations=foo.Bar,baz.Qux` (comma-separated FQNs, no whitespace tolerated within an FQN). Absent option yields an empty set.

The parsed set SHALL be wrapped via `Set.copyOf(...)` before storage so the field is immutable.

`ProcessorOptions` MAY similarly expose `Set<String> customNullMarkedAnnotations` and `Set<String> customNullUnmarkedAnnotations` for future extension; if added in this change, they follow the same `-Apercolate.nullmarked.annotations=…` and `-Apercolate.nullunmarked.annotations=…` parsing pattern. If deferred, the fields are out of scope for this requirement.

The `customNullableAnnotations` value SHALL be consumed by the `nullability` capability's `NullabilityAnnotations` provider, which merges these FQNs with the JSpecify defaults.

#### Scenario: Option absent yields empty customNullableAnnotations
- **WHEN** `processingEnv.getOptions()` does not contain `"percolate.nullable.annotations"`
- **THEN** the produced `ProcessorOptions.customNullableAnnotations` is an empty `Set`

#### Scenario: Option with one FQN yields a singleton set
- **WHEN** `processingEnv.getOptions()` contains `"percolate.nullable.annotations" -> "com.example.Nullable"`
- **THEN** the produced `ProcessorOptions.customNullableAnnotations` equals `Set.of("com.example.Nullable")`

#### Scenario: Option with multiple comma-separated FQNs yields each entry
- **WHEN** `processingEnv.getOptions()` contains `"percolate.nullable.annotations" -> "com.example.Nullable,org.foo.Optional"`
- **THEN** the produced `ProcessorOptions.customNullableAnnotations` contains both `"com.example.Nullable"` and `"org.foo.Optional"`

#### Scenario: customNullableAnnotations is immutable
- **WHEN** any caller attempts to mutate `ProcessorOptions.getCustomNullableAnnotations()`
- **THEN** the mutation either throws `UnsupportedOperationException` or has no effect on the stored set
- **AND** later reads return the original parsed contents

### Requirement: customNullableAnnotations option is declared

`PercolateProcessor.getSupportedOptions()` SHALL include the string `"percolate.nullable.annotations"` in its returned set, alongside the existing `"percolate.debug.graphs"`. If additional null-marker option keys are added in this change (`percolate.nullmarked.annotations`, `percolate.nullunmarked.annotations`), they SHALL be similarly declared.

#### Scenario: nullable.annotations option is declared
- **WHEN** `PercolateProcessor.getSupportedOptions()` is invoked
- **THEN** the returned set contains the string `"percolate.nullable.annotations"`

### Requirement: ProcessorOptions exposes docTags

`ProcessorOptions` SHALL grow a boolean `docTags` flag, parsed from the compiler option
`-Apercolate.docTags=true`. The flag SHALL default to `false` when the option is absent, so ordinary
consumer builds emit clean generated code and only a documentation build sets it. The value SHALL be
consumed by the `code-generation` capability's documentation-tag emission.

#### Scenario: Option absent yields docTags false
- **WHEN** `processingEnv.getOptions()` does not contain `"percolate.docTags"`
- **THEN** the produced `ProcessorOptions.docTags` is `false`

#### Scenario: Option set true enables docTags
- **WHEN** `processingEnv.getOptions()` contains `"percolate.docTags" -> "true"`
- **THEN** the produced `ProcessorOptions.docTags` is `true`

### Requirement: docTags option is declared

`PercolateProcessor.getSupportedOptions()` SHALL include the string `"percolate.docTags"` in its returned
set, alongside the existing supported options.

#### Scenario: docTags option is declared
- **WHEN** `PercolateProcessor.getSupportedOptions()` is invoked
- **THEN** the returned set contains the string `"percolate.docTags"`

### Requirement: time.zone option is declared

`PercolateProcessor.getSupportedOptions()` SHALL include the string `"percolate.time.zone"` in its returned set, alongside the existing supported options.

#### Scenario: time.zone option is declared
- **WHEN** `PercolateProcessor.getSupportedOptions()` is invoked
- **THEN** the returned set contains the string `"percolate.time.zone"`

### Requirement: ProcessorOptions exposes parametersFinal

`ProcessorOptions` SHALL grow a boolean `parametersFinal` field, parsed from the compiler option `-Apercolate.parameters.final=true`. The flag SHALL default to `false` when the option is absent, so a generated method's parameters carry no `final` modifier unless the option is explicitly set. The value SHALL be consumed by the `code-generation` capability's generated-parameter rendering.

#### Scenario: Option absent yields parametersFinal false
- **WHEN** `processingEnv.getOptions()` does not contain `"percolate.parameters.final"`
- **THEN** the produced `ProcessorOptions.parametersFinal` is `false`

#### Scenario: Option set true enables parametersFinal
- **WHEN** `processingEnv.getOptions()` contains `"percolate.parameters.final" -> "true"`
- **THEN** the produced `ProcessorOptions.parametersFinal` is `true`

### Requirement: parameters.final option is declared

`PercolateProcessor.getSupportedOptions()` SHALL include the string `"percolate.parameters.final"` in its returned set, alongside the existing supported options.

#### Scenario: parameters.final option is declared
- **WHEN** `PercolateProcessor.getSupportedOptions()` is invoked
- **THEN** the returned set contains the string `"percolate.parameters.final"`

### Requirement: ProcessorOptions exposes methodsFinal

`ProcessorOptions` SHALL grow a boolean `methodsFinal` field, parsed from the compiler option `-Apercolate.methods.final=true`. The flag SHALL default to `false` when the option is absent, so a generated method carries no `final` modifier unless the option is explicitly set. The value SHALL be consumed by the `code-generation` capability's generated-method rendering.

#### Scenario: Option absent yields methodsFinal false
- **WHEN** `processingEnv.getOptions()` does not contain `"percolate.methods.final"`
- **THEN** the produced `ProcessorOptions.methodsFinal` is `false`

#### Scenario: Option set true enables methodsFinal
- **WHEN** `processingEnv.getOptions()` contains `"percolate.methods.final" -> "true"`
- **THEN** the produced `ProcessorOptions.methodsFinal` is `true`

### Requirement: methods.final option is declared

`PercolateProcessor.getSupportedOptions()` SHALL include the string `"percolate.methods.final"` in its returned set, alongside the existing supported options.

#### Scenario: methods.final option is declared
- **WHEN** `PercolateProcessor.getSupportedOptions()` is invoked
- **THEN** the returned set contains the string `"percolate.methods.final"`

### Requirement: ProcessorOptions exposes classesFinal

`ProcessorOptions` SHALL grow a boolean `classesFinal` field, parsed from the compiler option `-Apercolate.classes.final=true`. The flag SHALL default to `false` when the option is absent, so the generated `<Mapper>Impl` class carries no `final` modifier unless the option is explicitly set — a behavior change from the previously unconditional `final` class. The value SHALL be consumed by the `code-generation` capability's generated-class-shape rendering.

#### Scenario: Option absent yields classesFinal false
- **WHEN** `processingEnv.getOptions()` does not contain `"percolate.classes.final"`
- **THEN** the produced `ProcessorOptions.classesFinal` is `false`

#### Scenario: Option set true enables classesFinal
- **WHEN** `processingEnv.getOptions()` contains `"percolate.classes.final" -> "true"`
- **THEN** the produced `ProcessorOptions.classesFinal` is `true`

### Requirement: classes.final option is declared

`PercolateProcessor.getSupportedOptions()` SHALL include the string `"percolate.classes.final"` in its returned set, alongside the existing supported options.

#### Scenario: classes.final option is declared
- **WHEN** `PercolateProcessor.getSupportedOptions()` is invoked
- **THEN** the returned set contains the string `"percolate.classes.final"`

### Requirement: A HelperStyle value carries the generated-member modifiers

The `processor` module SHALL define a `HelperStyle` value type carrying the modifiers of every strategy-requested class member, parsed once by `ProcessorOptionsReader` and provided to the generate stage on its own rather than as a `ProcessorOptions` field. The two options always travel together, and the stage that reads them needs nothing else from `ProcessorOptions`, so one value injected directly is both the smaller seam and the honest model.

`HelperStyle` SHALL carry:

- a `MemberVisibility` — parsed from `-Apercolate.helpers.visibility`, defaulting to `private` when the option is absent, empty, or unrecognised, case-insensitively. `MemberVisibility` is a new enum in the `processor` module with the constants `PRIVATE`, `PACKAGE`, `PROTECTED` and `PUBLIC`. It SHALL NOT reuse the SPI's `Visibility`, which names scope-input reachability and is unrelated.
- a `boolean` static flag — `true` when `-Apercolate.helpers.static` is absent, and `true` when it is set to `true` in any letter case. Any other value yields `false`.

Both are engine-internal options, so both are parsed into a typed value, in accordance with *Strategy-consumed options carry no typed field*. Exactly one parser SHALL exist for each, in `ProcessorOptionsReader`.

#### Scenario: Absent options yield the defaults
- **WHEN** `processingEnv.getOptions()` contains neither key
- **THEN** the produced `HelperStyle` has visibility `private` and its static flag set

#### Scenario: Visibility parses case-insensitively
- **WHEN** `processingEnv.getOptions()` contains the entry `"percolate.helpers.visibility" -> "PROTECTED"`
- **THEN** the produced `HelperStyle` has visibility `protected`

#### Scenario: An unrecognised visibility degrades to private
- **WHEN** `processingEnv.getOptions()` contains the entry `"percolate.helpers.visibility" -> "wombat"`
- **THEN** the produced `HelperStyle` has visibility `private`

#### Scenario: static defaults to true and is switched off explicitly
- **WHEN** `processingEnv.getOptions()` contains the entry `"percolate.helpers.static" -> "false"`
- **THEN** the produced `HelperStyle` has its static flag cleared

#### Scenario: static parses case-insensitively
- **WHEN** `processingEnv.getOptions()` contains the entry `"percolate.helpers.static" -> "TRUE"`
- **THEN** the produced `HelperStyle` has its static flag set

#### Scenario: The style is injected on its own
- **WHEN** the generate stage's member-plan factory is inspected
- **THEN** it declares a `HelperStyle` dependency and no `ProcessorOptions` dependency

### Requirement: helpers.visibility and helpers.static options are declared

`PercolateProcessor.getSupportedOptions()` SHALL include the strings `"percolate.helpers.visibility"` and `"percolate.helpers.static"` in its returned set, alongside the existing supported options, and `ProcessorOptions` SHALL declare both keys as constants.

#### Scenario: Both options are declared
- **WHEN** `PercolateProcessor.getSupportedOptions()` is invoked
- **THEN** the returned set contains `"percolate.helpers.visibility"` and `"percolate.helpers.static"`

#### Scenario: Both keys are declared as constants
- **WHEN** `ProcessorOptions` is inspected
- **THEN** it declares a key constant for each of the two options

### Requirement: switch.style option is declared

`PercolateProcessor.getSupportedOptions()` SHALL include the string `"percolate.switch.style"` in its returned set,
alongside the existing supported options.

#### Scenario: switch.style option is declared
- **WHEN** `PercolateProcessor.getSupportedOptions()` is invoked
- **THEN** the returned set contains the string `"percolate.switch.style"`

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
- **WHEN** an engine-internal consumer reads `debugGraphs`, `localsFinal`, `parametersFinal`, `methodsFinal`, `classesFinal`, `docTags`, or `customNullableAnnotations`
- **THEN** it reads the typed `ProcessorOptions` field
- **AND** the `helpers.*` options reach their consumer as a typed `HelperStyle`, parsed by the same reader

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

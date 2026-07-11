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

### Requirement: ProcessorOptions exposes timeZone

`ProcessorOptions` SHALL grow an `Optional<String> timeZone` field carrying the project-wide default zone id for temporal conversions, parsed from the compiler option `-Apercolate.time.zone=Europe/Berlin`. The field SHALL be **absent** (an empty `Optional`) when the option is not set, so that an unpinned mapper defers to a generated `ZoneId.systemDefault()` at the consumer's runtime rather than to any build-machine zone. When present, the value SHALL be the raw zone id string, rendered by the temporal zone bridge as a frozen `ZoneId.of("…")`. The processor SHALL NOT substitute its own build-JVM zone when the option is absent.

#### Scenario: Option absent yields an empty timeZone
- **WHEN** `processingEnv.getOptions()` does not contain `"percolate.time.zone"`
- **THEN** the produced `ProcessorOptions.timeZone` is an empty `Optional`

#### Scenario: Option present carries the zone id
- **WHEN** `processingEnv.getOptions()` contains `"percolate.time.zone" -> "Europe/Berlin"`
- **THEN** the produced `ProcessorOptions.timeZone` is present with value `"Europe/Berlin"`

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

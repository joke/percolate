## ADDED Requirements

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

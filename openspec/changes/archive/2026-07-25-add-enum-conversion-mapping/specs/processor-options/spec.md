## ADDED Requirements

### Requirement: ProcessorOptions exposes switchStyle

`ProcessorOptions` SHALL grow a `switchStyle` field of an enum type with values `AUTO`, `CLASSIC`, and `ARROW`,
parsed from the compiler option `-Apercolate.switch.style=…` (case-insensitive). The field SHALL default to `AUTO`
when the option is absent or unrecognised. `AUTO` defers the classic-vs-modern decision to the consuming strategy,
which resolves it against the target `SourceVersion`; `CLASSIC` forces a classic switch statement; `ARROW` forces a
modern switch expression. The value SHALL be consumed by the `enum-conversion` capability's switch rendering; the
processor's generate stage SHALL NOT read it.

#### Scenario: Option absent yields AUTO
- **WHEN** `processingEnv.getOptions()` does not contain `"percolate.switch.style"`
- **THEN** the produced `ProcessorOptions.switchStyle` is `AUTO`

#### Scenario: Option set forces a style
- **WHEN** `processingEnv.getOptions()` contains `"percolate.switch.style" -> "classic"`
- **THEN** the produced `ProcessorOptions.switchStyle` is `CLASSIC`

#### Scenario: Unrecognised value falls back to AUTO
- **WHEN** `processingEnv.getOptions()` contains `"percolate.switch.style" -> "nonsense"`
- **THEN** the produced `ProcessorOptions.switchStyle` is `AUTO`

### Requirement: switch.style option is declared

`PercolateProcessor.getSupportedOptions()` SHALL include the string `"percolate.switch.style"` in its returned set,
alongside the existing supported options.

#### Scenario: switch.style option is declared
- **WHEN** `PercolateProcessor.getSupportedOptions()` is invoked
- **THEN** the returned set contains the string `"percolate.switch.style"`

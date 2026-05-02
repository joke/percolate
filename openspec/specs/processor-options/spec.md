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

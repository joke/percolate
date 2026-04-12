## ADDED Requirements

### Requirement: ProcessorOptions provides annotation processor configuration
A `ProcessorOptions` value class SHALL be provided by `ProcessorModule` and SHALL expose:
- `isDebugGraphs()`: `boolean`, parsed from processor option `percolate.debug.graphs`, default `false`
- `getDebugGraphsFormat()`: `String`, parsed from processor option `percolate.debug.graphs.format`, default `"dot"`

`ProcessorModule` SHALL read these values from `processingEnvironment.getOptions()`.

#### Scenario: Debug graphs enabled via processor option
- **WHEN** `-Apercolate.debug.graphs=true` is passed to javac
- **THEN** `ProcessorOptions.isDebugGraphs()` SHALL return `true`

#### Scenario: Default options when nothing specified
- **WHEN** no `percolate.debug.*` options are passed
- **THEN** `isDebugGraphs()` SHALL return `false` and `getDebugGraphsFormat()` SHALL return `"dot"`

### Requirement: PercolateProcessor declares supported options
`PercolateProcessor` SHALL declare `percolate.debug.graphs` and `percolate.debug.graphs.format` via `@SupportedOptions` so that build tools do not emit warnings about unrecognized options.

#### Scenario: Supported options declared
- **WHEN** the annotation processor is loaded
- **THEN** `getSupportedOptions()` SHALL include `"percolate.debug.graphs"` and `"percolate.debug.graphs.format"`

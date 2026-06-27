## ADDED Requirements

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

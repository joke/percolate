## ADDED Requirements

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

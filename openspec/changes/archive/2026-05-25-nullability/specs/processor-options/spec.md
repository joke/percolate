## ADDED Requirements

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

#### Scenario: Option is declared
- **WHEN** `PercolateProcessor.getSupportedOptions()` is invoked
- **THEN** the returned set contains the string `"percolate.nullable.annotations"`

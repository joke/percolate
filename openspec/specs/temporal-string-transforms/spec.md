# Temporal String Transforms Spec

## Purpose

Defines the `TemporalToStringStrategy` and `StringToTemporalStrategy` TypeTransformStrategy implementations that convert between `java.time` temporal types and `java.lang.String`, including optional `DATE_FORMAT` pattern support and validation rules for incompatible uses.

## Requirements

### Requirement: TemporalToStringStrategy converts java.time types to String
A `TypeTransformStrategy` implementation `TemporalToStringStrategy` SHALL be registered via `@AutoService`. It SHALL propose a transformation when the source type is one of the supported `java.time` temporal types and the target type is `java.lang.String`.

The supported temporal types SHALL be: `LocalDate`, `LocalTime`, `LocalDateTime`, `Instant`, `ZonedDateTime`, `OffsetDateTime`, `OffsetTime`, `Duration`, `Period`.

#### Scenario: LocalDate to String without format option
- **WHEN** source type is `java.time.LocalDate` and target type is `java.lang.String` and no `DATE_FORMAT` option is present
- **THEN** the strategy SHALL return a `TransformProposal` with `CodeTemplate` producing `$L.toString()`

#### Scenario: LocalDateTime to String without format option
- **WHEN** source type is `java.time.LocalDateTime` and target type is `java.lang.String` and no `DATE_FORMAT` option is present
- **THEN** the strategy SHALL return a `TransformProposal` with `CodeTemplate` producing `$L.toString()`

#### Scenario: Instant to String without format option
- **WHEN** source type is `java.time.Instant` and target type is `java.lang.String` and no `DATE_FORMAT` option is present
- **THEN** the strategy SHALL return a `TransformProposal` with `CodeTemplate` producing `$L.toString()`

#### Scenario: Duration to String
- **WHEN** source type is `java.time.Duration` and target type is `java.lang.String`
- **THEN** the strategy SHALL return a `TransformProposal` with `CodeTemplate` producing `$L.toString()`

#### Scenario: Period to String
- **WHEN** source type is `java.time.Period` and target type is `java.lang.String`
- **THEN** the strategy SHALL return a `TransformProposal` with `CodeTemplate` producing `$L.toString()`

#### Scenario: LocalDate to String with DATE_FORMAT option
- **WHEN** source type is `java.time.LocalDate` and target type is `java.lang.String` and `DATE_FORMAT` option is `"dd.MM.yyyy"`
- **THEN** the strategy SHALL return a `TransformProposal` with `CodeTemplate` producing `$L.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))`

#### Scenario: Instant to String with DATE_FORMAT option
- **WHEN** source type is `java.time.Instant` and target type is `java.lang.String` and `DATE_FORMAT` option is `"dd.MM.yyyy"`
- **THEN** the strategy SHALL return a `TransformProposal` with `CodeTemplate` producing `$L.atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))`

#### Scenario: Non-temporal source type
- **WHEN** source type is `java.lang.Integer` and target type is `java.lang.String`
- **THEN** the strategy SHALL return `Optional.empty()`

#### Scenario: Non-String target type
- **WHEN** source type is `java.time.LocalDate` and target type is `java.time.Instant`
- **THEN** the strategy SHALL return `Optional.empty()`

### Requirement: StringToTemporalStrategy converts String to java.time types
A `TypeTransformStrategy` implementation `StringToTemporalStrategy` SHALL be registered via `@AutoService`. It SHALL propose a transformation when the source type is `java.lang.String` and the target type is one of the supported `java.time` temporal types.

The supported temporal types SHALL be: `LocalDate`, `LocalTime`, `LocalDateTime`, `Instant`, `ZonedDateTime`, `OffsetDateTime`, `OffsetTime`, `Duration`, `Period`.

#### Scenario: String to LocalDate without format option
- **WHEN** source type is `java.lang.String` and target type is `java.time.LocalDate` and no `DATE_FORMAT` option is present
- **THEN** the strategy SHALL return a `TransformProposal` with `CodeTemplate` producing `java.time.LocalDate.parse($L)`

#### Scenario: String to Instant without format option
- **WHEN** source type is `java.lang.String` and target type is `java.time.Instant` and no `DATE_FORMAT` option is present
- **THEN** the strategy SHALL return a `TransformProposal` with `CodeTemplate` producing `java.time.Instant.parse($L)`

#### Scenario: String to Duration without format option
- **WHEN** source type is `java.lang.String` and target type is `java.time.Duration` and no `DATE_FORMAT` option is present
- **THEN** the strategy SHALL return a `TransformProposal` with `CodeTemplate` producing `java.time.Duration.parse($L)`

#### Scenario: String to LocalDate with DATE_FORMAT option
- **WHEN** source type is `java.lang.String` and target type is `java.time.LocalDate` and `DATE_FORMAT` option is `"dd.MM.yyyy"`
- **THEN** the strategy SHALL return a `TransformProposal` with `CodeTemplate` producing `java.time.LocalDate.parse($L, java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))`

#### Scenario: String to Instant with DATE_FORMAT option
- **WHEN** source type is `java.lang.String` and target type is `java.time.Instant` and `DATE_FORMAT` option is `"dd.MM.yyyy HH:mm:ss"`
- **THEN** the strategy SHALL return a `TransformProposal` with `CodeTemplate` producing `java.time.LocalDateTime.parse($L, java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")).atZone(java.time.ZoneId.systemDefault()).toInstant()`

#### Scenario: Non-String source type
- **WHEN** source type is `java.lang.Integer` and target type is `java.time.LocalDate`
- **THEN** the strategy SHALL return `Optional.empty()`

#### Scenario: Non-temporal target type
- **WHEN** source type is `java.lang.String` and target type is `java.lang.Integer`
- **THEN** the strategy SHALL return `Optional.empty()`

### Requirement: Temporal-to-String strategies compose with container strategies
The BFS resolver SHALL compose temporal strategies with existing container strategies. No special handling is required — composition is automatic via the BFS expansion graph.

#### Scenario: Optional of LocalDate to Optional of String
- **WHEN** source type is `Optional<LocalDate>` and target type is `Optional<String>`
- **THEN** the BFS resolver SHALL find a path via `OptionalMapStrategy` composing with `TemporalToStringStrategy`

#### Scenario: Stream of LocalDate to Stream of String
- **WHEN** source type is `Stream<LocalDate>` and target type is `Stream<String>`
- **THEN** the BFS resolver SHALL find a path via `StreamMapStrategy` composing with `TemporalToStringStrategy`

#### Scenario: List of String to List of LocalDate
- **WHEN** source type is `List<String>` and target type is `List<LocalDate>`
- **THEN** the BFS resolver SHALL find a path via `StreamFromCollectionStrategy`, `StreamMapStrategy` composing with `StringToTemporalStrategy`, and `CollectToListStrategy`

### Requirement: DATE_FORMAT validation for incompatible types
The `ValidateTransformsStage` SHALL emit a compile error when a `DATE_FORMAT` option is present on a mapping where neither the source nor the target type is `java.lang.String`.

#### Scenario: DATE_FORMAT on LocalDate to LocalDateTime mapping
- **WHEN** a mapping has source type `LocalDate`, target type `LocalDateTime`, and option `DATE_FORMAT = "dd.MM.yyyy"`
- **THEN** the processor SHALL emit a compile error indicating `DATE_FORMAT` requires a String source or target

#### Scenario: DATE_FORMAT on LocalDate to String mapping
- **WHEN** a mapping has source type `LocalDate`, target type `String`, and option `DATE_FORMAT = "dd.MM.yyyy"`
- **THEN** the processor SHALL NOT emit a validation error

### Requirement: DATE_FORMAT validation for Duration and Period
The `ValidateTransformsStage` SHALL emit a compile error when a `DATE_FORMAT` option is present on a mapping involving `Duration` or `Period`, even if one side is `String`. These types are not `TemporalAccessor` and cannot be formatted with `DateTimeFormatter`.

#### Scenario: DATE_FORMAT on Duration to String mapping
- **WHEN** a mapping has source type `Duration`, target type `String`, and option `DATE_FORMAT = "HH:mm:ss"`
- **THEN** the processor SHALL emit a compile error indicating `DATE_FORMAT` is not supported for `Duration`

#### Scenario: DATE_FORMAT on String to Period mapping
- **WHEN** a mapping has source type `String`, target type `Period`, and option `DATE_FORMAT = "yyyy-MM-dd"`
- **THEN** the processor SHALL emit a compile error indicating `DATE_FORMAT` is not supported for `Period`

### Requirement: Instant and OffsetTime require zone context for DateTimeFormatter
When `DATE_FORMAT` is used with `Instant` (either direction) or when formatting requires zone-aware access, the generated code SHALL use `ZoneId.systemDefault()` to provide zone context.

#### Scenario: Instant to String with custom format
- **WHEN** source type is `Instant`, target type is `String`, and `DATE_FORMAT` is `"yyyy-MM-dd"`
- **THEN** the generated code SHALL use `.atZone(ZoneId.systemDefault()).format(...)` to provide zone context

#### Scenario: String to Instant with custom format
- **WHEN** source type is `String`, target type is `Instant`, and `DATE_FORMAT` is `"yyyy-MM-dd HH:mm:ss"`
- **THEN** the generated code SHALL parse via `LocalDateTime.parse(str, formatter).atZone(ZoneId.systemDefault()).toInstant()`

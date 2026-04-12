# Legacy Date Bridge Spec

## Purpose

Defines the `LegacyDateBridgeStrategy` TypeTransformStrategy implementation that bridges legacy `java.util.Date` and `java.sql.*` date types to their natural `java.time` counterparts, enabling BFS-composed conversion chains without locale-dependent string output.

## Requirements

### Requirement: LegacyDateBridgeStrategy bridges java.util.Date to Instant
A `TypeTransformStrategy` implementation `LegacyDateBridgeStrategy` SHALL be registered via `@AutoService`. It SHALL propose bidirectional transformations between legacy date types and their natural `java.time` counterparts.

For `java.util.Date` ↔ `java.time.Instant`:

#### Scenario: java.util.Date to Instant
- **WHEN** source type is `java.util.Date` and target type is `java.time.Instant`
- **THEN** the strategy SHALL return a `TransformProposal` with `CodeTemplate` producing `$L.toInstant()`

#### Scenario: Instant to java.util.Date
- **WHEN** source type is `java.time.Instant` and target type is `java.util.Date`
- **THEN** the strategy SHALL return a `TransformProposal` with `CodeTemplate` producing `java.util.Date.from($L)`

### Requirement: LegacyDateBridgeStrategy bridges java.sql.Date to LocalDate
For `java.sql.Date` ↔ `java.time.LocalDate`:

#### Scenario: java.sql.Date to LocalDate
- **WHEN** source type is `java.sql.Date` and target type is `java.time.LocalDate`
- **THEN** the strategy SHALL return a `TransformProposal` with `CodeTemplate` producing `$L.toLocalDate()`

#### Scenario: LocalDate to java.sql.Date
- **WHEN** source type is `java.time.LocalDate` and target type is `java.sql.Date`
- **THEN** the strategy SHALL return a `TransformProposal` with `CodeTemplate` producing `java.sql.Date.valueOf($L)`

### Requirement: LegacyDateBridgeStrategy bridges java.sql.Time to LocalTime
For `java.sql.Time` ↔ `java.time.LocalTime`:

#### Scenario: java.sql.Time to LocalTime
- **WHEN** source type is `java.sql.Time` and target type is `java.time.LocalTime`
- **THEN** the strategy SHALL return a `TransformProposal` with `CodeTemplate` producing `$L.toLocalTime()`

#### Scenario: LocalTime to java.sql.Time
- **WHEN** source type is `java.time.LocalTime` and target type is `java.sql.Time`
- **THEN** the strategy SHALL return a `TransformProposal` with `CodeTemplate` producing `java.sql.Time.valueOf($L)`

### Requirement: LegacyDateBridgeStrategy bridges java.sql.Timestamp to Instant
For `java.sql.Timestamp` ↔ `java.time.Instant`:

#### Scenario: java.sql.Timestamp to Instant
- **WHEN** source type is `java.sql.Timestamp` and target type is `java.time.Instant`
- **THEN** the strategy SHALL return a `TransformProposal` with `CodeTemplate` producing `$L.toInstant()`

#### Scenario: Instant to java.sql.Timestamp
- **WHEN** source type is `java.time.Instant` and target type is `java.sql.Timestamp`
- **THEN** the strategy SHALL return a `TransformProposal` with `CodeTemplate` producing `java.sql.Timestamp.from($L)`

### Requirement: Legacy types do not use toString() for String conversion
The `LegacyDateBridgeStrategy` SHALL NOT propose direct legacy-type-to-String conversions. Legacy types SHALL reach `String` only through their `java.time` bridge counterpart. This avoids locale-dependent and non-parseable output from `java.util.Date.toString()` and the non-ISO format of `java.sql.Timestamp.toString()`.

#### Scenario: java.util.Date to String routes through Instant
- **WHEN** source type is `java.util.Date` and target type is `java.lang.String`
- **THEN** `LegacyDateBridgeStrategy` SHALL return `Optional.empty()` for this pair, and the BFS resolver SHALL find the 2-hop path `java.util.Date → Instant → String`

#### Scenario: java.sql.Date to String routes through LocalDate
- **WHEN** source type is `java.sql.Date` and target type is `java.lang.String`
- **THEN** `LegacyDateBridgeStrategy` SHALL return `Optional.empty()` for this pair, and the BFS resolver SHALL find the 2-hop path `java.sql.Date → LocalDate → String`

#### Scenario: String to java.util.Date routes through Instant
- **WHEN** source type is `java.lang.String` and target type is `java.util.Date`
- **THEN** the BFS resolver SHALL find the 2-hop path `String → Instant → java.util.Date`

### Requirement: Legacy bridge does not match non-legacy or non-bridge types
The strategy SHALL only match the specific legacy↔modern pairs defined above.

#### Scenario: java.util.Date to LocalDate
- **WHEN** source type is `java.util.Date` and target type is `java.time.LocalDate`
- **THEN** the strategy SHALL return `Optional.empty()` (no direct bridge; BFS finds `Date → Instant → String → LocalDate`)

#### Scenario: Unrelated types
- **WHEN** source type is `java.lang.String` and target type is `java.lang.Integer`
- **THEN** the strategy SHALL return `Optional.empty()`

## 1. Annotation API — Mapping Options

- [x] 1.1 Create `MapOptKey` enum in `annotations` module with `DATE_FORMAT` constant
- [x] 1.2 Create `@MapOpt` annotation with `MapOptKey key()` and `String value()` elements
- [x] 1.3 Add `MapOpt[] options() default {}` element to `@Map` annotation

## 2. Processor Model — Options Threading

- [x] 2.1 Add `Map<MapOptKey, String> options` field to `MapDirective`
- [x] 2.2 Update `AnalyzeStage.parseDirectives` to extract options from `@Map` annotations into `MapDirective`, including duplicate key detection
- [x] 2.3 Add `Map<MapOptKey, String> options` field to `MappingEdge`
- [x] 2.4 Update `BuildGraphStage.processDirectives` to pass options from `MapDirective` to `MappingEdge`
- [x] 2.5 Update `BuildGraphStage.addSourceChain` to accept and propagate options to `MappingEdge`
- [x] 2.6 Update `BuildGraphStage.autoMap` to create `MappingEdge` with empty options
- [x] 2.7 Add `Map<MapOptKey, String> options` field to `ResolutionContext`
- [x] 2.8 Update `ResolveTransformsStage.resolveMethod` to read options from `MappingEdge` and create per-mapping `ResolutionContext`

## 3. Temporal-to-String Strategy

- [x] 3.1 Create `TemporalToStringStrategy` implementing `TypeTransformStrategy` with `@AutoService`
- [x] 3.2 Implement type matching for all 9 supported `java.time` types (`LocalDate`, `LocalTime`, `LocalDateTime`, `Instant`, `ZonedDateTime`, `OffsetDateTime`, `OffsetTime`, `Duration`, `Period`)
- [x] 3.3 Implement default ISO code template using `.toString()`
- [x] 3.4 Implement `DATE_FORMAT` code template using `DateTimeFormatter.ofPattern(...)` with `ZoneId.systemDefault()` for `Instant`
- [x] 3.5 Write Spock tests for each supported temporal type without format option
- [x] 3.6 Write Spock tests for temporal types with `DATE_FORMAT` option
- [x] 3.7 Write Spock tests for non-matching type pairs returning `Optional.empty()`

## 4. String-to-Temporal Strategy

- [x] 4.1 Create `StringToTemporalStrategy` implementing `TypeTransformStrategy` with `@AutoService`
- [x] 4.2 Implement type matching for all 9 supported `java.time` types
- [x] 4.3 Implement default ISO code template using `T.parse($L)`
- [x] 4.4 Implement `DATE_FORMAT` code template using `T.parse($L, DateTimeFormatter.ofPattern(...))` with zone bridging for `Instant`
- [x] 4.5 Write Spock tests for each supported temporal type without format option
- [x] 4.6 Write Spock tests for temporal types with `DATE_FORMAT` option
- [x] 4.7 Write Spock tests for non-matching type pairs returning `Optional.empty()`

## 5. Legacy Date Bridge Strategy

- [x] 5.1 Create `LegacyDateBridgeStrategy` implementing `TypeTransformStrategy` with `@AutoService`
- [x] 5.2 Implement `java.util.Date` ↔ `Instant` bridge (`toInstant()` / `Date.from()`)
- [x] 5.3 Implement `java.sql.Date` ↔ `LocalDate` bridge (`toLocalDate()` / `Date.valueOf()`)
- [x] 5.4 Implement `java.sql.Time` ↔ `LocalTime` bridge (`toLocalTime()` / `Time.valueOf()`)
- [x] 5.5 Implement `java.sql.Timestamp` ↔ `Instant` bridge (`toInstant()` / `Timestamp.from()`)
- [x] 5.6 Write Spock tests for all bidirectional bridge pairs
- [x] 5.7 Write Spock tests verifying non-bridge pairs return `Optional.empty()`

## 6. Validation

- [x] 6.1 Add validation in `ValidateTransformsStage`: `DATE_FORMAT` on mapping where neither side is `String` produces compile error
- [x] 6.2 Add validation in `ValidateTransformsStage`: `DATE_FORMAT` on `Duration` or `Period` mapping produces compile error
- [x] 6.3 Add error message templates to `ErrorMessages` for option validation failures
- [x] 6.4 Write Spock tests for `DATE_FORMAT` on non-String mapping error
- [x] 6.5 Write Spock tests for `DATE_FORMAT` on Duration/Period error
- [x] 6.6 Write Spock tests confirming valid `DATE_FORMAT` usage does not produce errors

## 7. Integration Tests

- [x] 7.1 Write compile-testing integration test: `LocalDate` ↔ `String` mapping with default ISO format
- [x] 7.2 Write compile-testing integration test: `LocalDate` → `String` with custom `DATE_FORMAT`
- [x] 7.3 Write compile-testing integration test: `java.util.Date` → `String` (2-hop via `Instant`)
- [x] 7.4 Write compile-testing integration test: `java.sql.Date` → `String` (2-hop via `LocalDate`)
- [x] 7.5 Write compile-testing integration test: `Optional<LocalDate>` → `Optional<String>` composition
- [x] 7.6 Write compile-testing integration test: `List<LocalDate>` → `List<String>` composition
- [x] 7.7 Write compile-testing integration test: validation error for `DATE_FORMAT` on non-String target

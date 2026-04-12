## Why

The processor currently has no built-in strategies for mapping between temporal types (`java.time.*`, `java.util.Date`, `java.sql.*`) and `String`. Users who need date-to-string or string-to-date conversions have no path available, and cross-type temporal conversions (e.g., `LocalDate` to `Instant`) are also unsupported. This is a common mapping requirement in real-world applications.

## What Changes

- Add a generic per-mapping options mechanism: a `MapOptKey` enum, a `@MapOpt` annotation, and an `options` attribute on `@Map` so strategies can receive configuration from the user
- Thread options from annotation through `MapDirective` and `ResolutionContext` into strategy `canProduce` calls
- Add `TemporalToStringStrategy` for converting `java.time` temporal types to `String` (using `.toString()` for ISO default, or `DateTimeFormatter.ofPattern(...)` when `DATE_FORMAT` option is present)
- Add `StringToTemporalStrategy` for converting `String` to `java.time` temporal types (using `T.parse()` for ISO default, or `DateTimeFormatter`-based parsing when `DATE_FORMAT` option is present)
- Add `LegacyDateBridgeStrategy` for converting legacy date types (`java.util.Date`, `java.sql.Date`, `java.sql.Time`, `java.sql.Timestamp`) to/from their natural `java.time` counterparts
- Add validation: `DATE_FORMAT` option present but neither source nor target is `String` produces a compile error
- Add validation: `DATE_FORMAT` on `Duration` or `Period` (which are not `TemporalAccessor`) produces a compile error

## Capabilities

### New Capabilities

- `mapping-options`: Generic per-mapping options mechanism (`MapOptKey` enum, `@MapOpt` annotation, `options` on `@Map`, threading through `MapDirective` and `ResolutionContext`)
- `temporal-string-transforms`: Bidirectional `java.time` temporal-to-String and String-to-temporal strategies, with optional `DATE_FORMAT` custom formatting
- `legacy-date-bridge`: Bridge strategies between legacy date types and their `java.time` equivalents

### Modified Capabilities

- `type-transform-strategy`: `ResolutionContext` gains an `options` field (`Map<MapOptKey, String>`) so strategies can access per-mapping options

## Impact

- **Annotation API** (`annotations` module): `@Map` gains `MapOpt[] options()`, new `@MapOpt` annotation and `MapOptKey` enum
- **Processor model** (`processor` module): `MapDirective` gains `options` field, `AnalyzeStage` parses options from annotation
- **Processor SPI** (`processor` module): `ResolutionContext` gains `options` field
- **Processor strategies** (`processor` module): Three new `TypeTransformStrategy` implementations
- **Processor validation** (`processor` module): `ValidateTransformsStage` checks option/type compatibility
- **Supported temporal types**: `LocalDate`, `LocalTime`, `LocalDateTime`, `Instant`, `ZonedDateTime`, `OffsetDateTime`, `OffsetTime`, `Duration`, `Period`, `java.util.Date`, `java.sql.Date`, `java.sql.Time`, `java.sql.Timestamp`
- **Zone handling**: Legacy types that require zone context use `ZoneId.systemDefault()`

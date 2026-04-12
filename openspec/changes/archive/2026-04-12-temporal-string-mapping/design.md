## Context

The processor resolves type gaps between source and target properties using a BFS expansion loop over `TypeTransformStrategy` implementations discovered via `ServiceLoader`. Currently there are strategies for direct assignability, method calls, Optional wrap/unwrap/map, Stream map, Stream-from-Collection, and collect-to-List/Set. There is no support for temporal type conversions or any mechanism to pass per-mapping configuration to strategies.

The annotation API consists of `@Mapper` (on type), `@Map(source, target)` (on method, `@Repeatable`), and `@MapList` (container). `MapDirective` carries `{source, target}`. `ResolutionContext` carries `{types, elements, mapperType, currentMethod}` and is constructed per-method in `ResolveTransformsStage.execute()`.

## Goals / Non-Goals

**Goals:**
- Generic per-mapping options mechanism that any strategy can consume
- Bidirectional conversion between `java.time` temporal types and `String`
- Bridge between legacy date types (`java.util.Date`, `java.sql.*`) and their `java.time` equivalents
- Custom date formatting via `DATE_FORMAT` option when mapping to/from `String`
- Compile-time validation of option usage (e.g., `DATE_FORMAT` on non-String target)

**Non-Goals:**
- Custom user-provided transform strategies (future work)
- `Year`, `YearMonth`, `MonthDay` support
- Locale-aware formatting (custom formats use `DateTimeFormatter.ofPattern` which is locale-independent by default)
- Number format conversions

## Decisions

### Decision 1: Options as enum-keyed annotation pairs inside `@Map`

Add `MapOpt[] options() default {}` to `@Map`, with `@MapOpt` carrying an enum key and string value:

```java
public enum MapOptKey { DATE_FORMAT }

public @interface MapOpt {
    MapOptKey key();
    String value();
}

@Map(source = "mfgDate", target = "dateStr",
     options = @MapOpt(key = DATE_FORMAT, value = "dd.MM.yyyy"))
```

**Why over named attributes on `@Map`**: Named attributes are cleaner to read but require changing the annotation API for every new option. Enum keys give compile-time safety and IDE autocomplete while keeping `@Map` stable.

**Why over `String` keys**: Enum prevents typos and enables IDE discovery. Strategies document which keys they consume via the enum.

**Why over separate method-level annotations**: Co-locating options inside `@Map` eliminates the binding problem — each option is unambiguously tied to its mapping directive.

### Decision 2: Options flow via `MappingEdge` into per-mapping `ResolutionContext`

```
@Map(options=...) → MapDirective.options → MappingEdge.options → ResolutionContext.options
```

Currently `MappingEdge` is an empty marker class. It will carry `Map<MapOptKey, String> options`. The flow:

1. `AnalyzeStage.parseDirectives` extracts options from `@Map` into `MapDirective`
2. `BuildGraphStage.processDirectives` passes options to `MappingEdge` when creating graph edges
3. `ResolveTransformsStage.resolveMethod` reads options from the `MappingEdge` and creates a per-mapping `ResolutionContext` that includes the options

**Why on `MappingEdge` rather than a lookup table**: The edge already represents the specific source→target mapping. Options are a property of that mapping, so they belong on the edge. This avoids any name-based lookups and keeps the graph self-contained.

**Why per-mapping `ResolutionContext`**: Today `ResolutionContext` is per-method. Options are per-mapping, so the context must be scoped per-mapping. The simplest change: add `Map<MapOptKey, String> options` field to `ResolutionContext` and construct a new context for each mapping resolution. The per-method context (with empty options) stays as default for auto-mapped properties.

### Decision 3: String as universal hub for temporal conversions

All temporal↔temporal conversions route through String using ISO-8601:

```
LocalDate ←→ String ←→ Instant
sql.Date → LocalDate → String → ZonedDateTime
```

Two strategy classes handle the `java.time` ↔ `String` edges:

- **`TemporalToStringStrategy`**: checks if source is a known temporal type and target is `String`
- **`StringToTemporalStrategy`**: checks if source is `String` and target is a known temporal type

A third handles legacy bridging:

- **`LegacyDateBridgeStrategy`**: bridges `java.util.Date` ↔ `Instant`, `sql.Date` ↔ `LocalDate`, `sql.Time` ↔ `LocalTime`, `sql.Timestamp` ↔ `Instant`

BFS composition handles multi-hop automatically: `sql.Date → LocalDate → String → Instant` resolves as a 3-edge path.

**Why String as hub over direct type-to-type edges**: N types with direct edges = N*(N-1) strategies. String as hub = 2*N + bridge edges. The BFS resolver already handles multi-hop paths efficiently. ISO-8601 `.toString()`/`.parse()` on `java.time` types is locale-independent and round-trippable.

**Why separate strategy classes over one table-driven class**: Each class has distinct logic — temporal→string uses `.toString()` or `.format()`, string→temporal uses `T.parse()`, legacy bridging uses `.toInstant()`/`.toLocalDate()` etc. Separate classes keep each concern focused and testable.

### Decision 4: `DATE_FORMAT` option overrides default ISO formatting

When the `DATE_FORMAT` option is present and one side is `String`:

```java
// Temporal → String with format
source.getDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

// String → Temporal with format
LocalDate.parse(source.getDateStr(), DateTimeFormatter.ofPattern("dd.MM.yyyy"))

// Without format (default)
source.getDate().toString()              // ISO
LocalDate.parse(source.getDateStr())     // ISO
```

The strategy reads `ctx.getOption(DATE_FORMAT)` and adjusts its `CodeTemplate` accordingly.

For legacy types with custom format, the bridge composes with the formatted string conversion:
```java
// java.util.Date → String with format
source.getDate().toInstant().atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
```

### Decision 5: Legacy types use `ZoneId.systemDefault()` when zone context is needed

`java.util.Date` and `java.sql.Timestamp` represent instants (millis since epoch). Converting to zone-aware representations requires a zone. We use `ZoneId.systemDefault()` as the implicit zone.

**Why `systemDefault()` over requiring explicit configuration**: This is the common-sense default — the legacy types were always implicitly system-zoned. Users who need explicit zone control can provide a manual mapper method (future: custom strategy).

### Decision 6: Validation in `ValidateTransformsStage`

Two new validation rules:

1. **`DATE_FORMAT` on non-String mapping**: If options contain `DATE_FORMAT` but neither source nor target type is `java.lang.String`, emit a compile error.
2. **`DATE_FORMAT` on Duration/Period**: These types implement `toString()`/`parse()` for ISO-8601 but are not `TemporalAccessor` — `DateTimeFormatter` cannot format them. Emit a compile error.

Validation runs in `ValidateTransformsStage` which already checks resolved mappings. The options are available via `ResolvedMapping` → `MappingEdge` → options chain, or carried directly on the `ResolvedMapping`.

**Why in `ValidateTransformsStage` over the strategies themselves**: Strategies return `Optional.empty()` when they don't match — they have no error channel. Validation is a cross-cutting concern that belongs in the validation stage.

## Risks / Trade-offs

**[Risk] `systemDefault()` zone produces different results on different machines** → This is inherent to the legacy date types. Document it. Users who need deterministic behavior should use `java.time` types directly or provide a manual mapper method.

**[Risk] ISO-8601 intermediate format may lose sub-type precision** → `LocalDate.parse("2026-04-12T14:30:00")` would fail because `LocalDate.parse` expects date-only format. Each type's `.toString()` produces its own ISO variant and `.parse()` expects the same variant. Cross-type conversion (e.g., `LocalDate` → `LocalDateTime`) routes through String as `"2026-04-12"` and `LocalDateTime.parse("2026-04-12")` would fail. **Mitigation**: The `StringToTemporalStrategy` for `LocalDateTime` should not accept String that came from a `LocalDate`. Since BFS finds shortest paths, `LocalDate → String → LocalDateTime` would only be attempted if there's no better path. This is actually fine — there's no lossless conversion from `LocalDate` to `LocalDateTime` without choosing a time, so failing is correct behavior.

**[Trade-off] Enum keys require code changes for new options** → Adding a new `MapOptKey` constant requires a code change in the annotations module. This is acceptable — new options are infrequent and the enum provides compile-time safety.

**[Trade-off] String value on `@MapOpt` loses type safety** → The value is always `String`, so a number-format pattern and a date-format pattern look the same at the annotation level. Validation catches misuse at compile time, but IDE-level type checking is lost. Acceptable given Java annotation type restrictions.

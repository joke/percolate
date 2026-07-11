# Temporal Conversion Spec

## Purpose

Defines automatic date/time (temporal) type mapping in `percolate-strategies-builtin`: a two-hub routing scheme (`Instant` for the absolute family, `LocalDateTime` for the local family) that lets the expansion engine compose multi-hop chains through single-hop spoke conversions, a single zone-consuming bridge between the hubs, zone resolution precedence, the auto-converted temporal roster and its no-truncation invariant, and `@Map(format = "…")` String↔temporal parsing/rendering.

## Requirements

### Requirement: Temporal conversions route through two family hubs

The `percolate-strategies-builtin` module SHALL resolve conversions among the temporal roster by authoring only
**single-hop spoke conversions to and from two hub types**, letting the expansion engine compose multi-hop
chains through synthesized intermediate Values (as it already does for `int → Long` = widen-then-box). The two
hubs SHALL be `java.time.Instant` for the **absolute** family (`java.util.Date`, `java.sql.Timestamp`,
`OffsetDateTime`, `ZonedDateTime`) and `java.time.LocalDateTime` for the **local** family (`LocalDate`).
Spoke↔hub conversions **within** a family SHALL require no zone. No conversion strategy SHALL enumerate the
N×N pairs directly.

#### Scenario: An absolute-family pair composes through Instant with no zone

- **WHEN** a mapper method demands `Instant` from a `java.util.Date` source
- **THEN** the generated code produces the `Instant` via `Date.toInstant()` (the `Date → Instant` spoke)
- **AND** the result equals the source's instant with no zone applied

#### Scenario: A cross-spoke absolute pair composes through the hub

- **WHEN** a mapper method demands `java.util.Date` from an `OffsetDateTime` source
- **THEN** the generated code routes `OffsetDateTime → Instant → java.util.Date` (spoke to hub, hub to spoke)
- **AND** the produced `Date` represents the same instant as the source

### Requirement: The zone bridge is the only zone-consuming hop

The single `Instant ⇄ LocalDateTime` **zone bridge** SHALL be the only temporal hop that reads a zone; it SHALL
implement `ExpansionStrategy` directly, read the resolved zone (per the zone-resolution requirement), bake it
into the generated code, and **stamp `"zone"` as consumed** on its `OperationSpec`. All spoke conversions
SHALL be `Conversion`-base emissions that read no directive and stamp no option. A cross-family conversion
SHALL cross the bridge exactly once.

#### Scenario: A local-to-absolute conversion crosses the bridge and consumes zone

- **WHEN** a mapper method demands `Instant` from a `LocalDate` source under `@Map(zone = "Europe/Berlin")`
- **THEN** the generated code routes `LocalDate → LocalDateTime (atStartOfDay) → Instant`, applying
  `ZoneId.of("Europe/Berlin")` on the bridge hop
- **AND** the bridge operation stamps `"zone"` consumed, so no unconsumed-option diagnostic is raised

#### Scenario: A zone on an absolute-only path is unconsumed

- **WHEN** a mapper method demands `Instant` from a `java.util.Date` source under `@Map(zone = "Europe/Berlin")`
- **THEN** the winning plan never crosses the zone bridge, so `"zone"` is not consumed
- **AND** the unconsumed-option diagnostic reports `zone` as having no effect

### Requirement: Zone resolution precedence

The zone used by the bridge SHALL be resolved by precedence: (1) a present `@Map(zone = "…")` renders
`ZoneId.of("…")` frozen in the generated code; else (2) a `-Apercolate.time.zone=…` processor option renders
`ZoneId.of("…")` frozen; else (3) the generated code renders `ZoneId.systemDefault()`, resolved at the
consumer's runtime. The processor SHALL NOT read its own build-JVM zone and freeze it into generated code.

#### Scenario: Directive zone wins over the option

- **WHEN** `-Apercolate.time.zone=UTC` is set and a binding declares `@Map(zone = "Europe/Berlin")`
- **THEN** the generated bridge code uses `ZoneId.of("Europe/Berlin")`

#### Scenario: Unset zone defers to runtime systemDefault

- **WHEN** neither `@Map(zone = …)` nor `-Apercolate.time.zone` is set for a cross-family conversion
- **THEN** the generated bridge code uses `ZoneId.systemDefault()`
- **AND** no literal zone id from the build machine appears in the generated source

### Requirement: Temporal auto roster and no-truncation invariant

Automatic temporal conversion SHALL cover exactly `java.util.Date`, `java.time.Instant`, `java.sql.Timestamp`,
`java.time.OffsetDateTime`, `java.time.ZonedDateTime`, `java.time.LocalDate`, and `java.time.LocalDateTime`.
Partial temporal types — `LocalTime`, `Year`, `YearMonth`, `MonthDay` — and epoch `long` SHALL NOT be
auto-converted (they remain user-helper territory resolved via `MethodCallBridge`). A hub SHALL be the widest
type in its family, so a hub SHALL NEVER silently drop a source time-of-day; a `00:00:00` value SHALL appear
only when the **source** is a `LocalDate` that inherently carried no time.

#### Scenario: A partial type is not auto-converted

- **WHEN** a mapper method demands `Instant` from a `LocalTime` source with no user helper
- **THEN** no temporal strategy produces the target and the demand is reported unresolved

#### Scenario: A cross-spoke conversion preserves the instant

- **WHEN** a mapper method demands `ZonedDateTime` from an `OffsetDateTime` source
- **THEN** the produced value denotes the same instant as the source (routed through `Instant`), with the
  time-of-day preserved rather than reset to `00:00:00`

### Requirement: @Map(format) parses and renders Strings against temporal types

A `@Map(format = "…")` directive SHALL enable `String ↔ temporal` production via a strategy that reads the
directive and stamps `"format"` consumed. For a `java.time` target/source the strategy SHALL use a
`java.time.format.DateTimeFormatter` **hoisted once** as a shared `private static final` field (it is immutable
and thread-safe). For a `java.util.Date` or `java.sql.*` target/source the strategy SHALL use a **fresh
per-call** `new java.text.SimpleDateFormat(pattern)` and SHALL NOT hoist it (it is not thread-safe).

#### Scenario: Parsing a String into a java.time type uses a hoisted formatter

- **WHEN** a mapper method demands `LocalDate` from a `String` source under `@Map(format = "yyyy-MM-dd")`
- **THEN** the generated code parses via a shared `private static final DateTimeFormatter` and produces the
  `LocalDate` for `"2026-07-11"` equal to `LocalDate.of(2026, 7, 11)`

#### Scenario: Formatting a legacy Date uses a per-call SimpleDateFormat

- **WHEN** a mapper method demands `String` from a `java.util.Date` source under `@Map(format = "yyyy-MM-dd")`
- **THEN** the generated code constructs a new `SimpleDateFormat("yyyy-MM-dd")` at the call site (not a shared
  field) and renders the formatted date

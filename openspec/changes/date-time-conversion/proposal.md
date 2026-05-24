## Why

Mapping between Java date/time types is repetitive boilerplate that every user has to either write by hand or expose as a helper method for `MethodCallBridge` to discover. Today percolate has no built-in awareness of the JSR-310 types, `java.util.Date`, or `java.sql.{Date,Time,Timestamp}`, so common conversions (`Instant` ↔ `OffsetDateTime`, `java.sql.Timestamp` ↔ `LocalDateTime`, `java.sql.Date` ↔ `LocalDate`, …) require user-supplied code even though the JDK conversion is mechanical. Adding built-in bridges removes that boilerplate and makes datetime fields "just work" in the same way primitives and containers do today.

## What Changes

- Introduce hub-and-spoke datetime bridges in `strategies-builtin`. Each domain has one canonical "hub" type that carries the maximum information; every other type in the domain gets one `T → hub` and one `hub → T` `Bridge`. The expansion engine composes 2-hop paths through the hub, so the user can map any pair within a domain without writing helpers.
- Define three domains and three hubs:
  - **Instant-in-time** — hub: `java.time.OffsetDateTime`. Spokes: `java.time.Instant`, `java.time.ZonedDateTime`, `java.time.LocalDateTime`, `java.util.Date`, `java.sql.Timestamp`.
  - **Date-only** — hub: `java.time.LocalDate`. Spokes: `java.sql.Date`.
  - **Time-of-day** — hub: `java.time.OffsetTime`. Spokes: `java.time.LocalTime`, `java.sql.Time`.
- For legs that need a zone/offset (e.g. `LocalDateTime → OffsetDateTime`, `java.util.Date → OffsetDateTime`), default to `java.time.ZoneOffset.UTC`. Make this overridable via a new processor option (e.g. `percolate.datetime.zone`) so generated code stays deterministic and independent of the build machine's `ZoneId.systemDefault()`.
- Cross-domain conversions (e.g. `LocalDate → OffsetDateTime`, `LocalDateTime → LocalDate`) are explicitly **out of scope** for this change. They require both a default time-of-day and a zone — judgement calls best left to a user-supplied helper that `MethodCallBridge` already picks up.
- Extend the `builtin-strategy-unit-tests` enumeration to require Spock specs for each new datetime bridge, following the existing `<StrategyClassSimpleName>Spec.groovy` convention.

## Capabilities

### New Capabilities

- `datetime-conversion`: Built-in `Bridge` implementations that convert between Java date/time types via three domain hubs (`OffsetDateTime`, `LocalDate`, `OffsetTime`). Defines the spoke inventory, the hub topology, lossless vs zone-dependent legs, the processor option for the default zone, and the boundary against cross-domain conversion.

### Modified Capabilities

- `builtin-strategy-unit-tests`: Extend the required-specs enumeration to include the new datetime bridges, so each one ships with a tagged Spock spec mirroring the existing pattern.
- `processor-options`: Register the new `percolate.datetime.zone` option (parsed as a `ZoneOffset` / `ZoneId`, defaulting to UTC) so the processor surfaces it via `getSupportedOptions` and reads it through the existing options machinery.

## Impact

- **Code**: New `Bridge` classes under `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/` — one per spoke per direction (roughly 14 small classes, paired with one fixture-free Spock spec each).
- **APIs**: No SPI changes. New bridges plug in via the existing `@AutoService(Bridge.class)` mechanism.
- **Processor options**: One new option key (`percolate.datetime.zone`); affects `processor-options` spec and the `getSupportedOptions` set the processor advertises.
- **Dependencies**: None new — JSR-310 and `java.sql` are JDK types, available on Java 11.
- **Users / teams**: Anyone currently writing manual `LocalDateTime`/`Instant`/`java.util.Date` helpers can delete them. Generated code that previously emitted no path for these fields will start mapping automatically, which is observable behaviour — flag in release notes.

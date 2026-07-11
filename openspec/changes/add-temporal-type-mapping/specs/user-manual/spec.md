## ADDED Requirements

### Requirement: The manual documents temporal (date/time) mapping

The manual SHALL contain a temporal-mapping feature page, co-located in the `strategies-builtin` module that
owns the temporal strategies and reaching the Antora component via the collector `scan` import. The page SHALL
document, at a user level: (1) automatic conversion across `java.util.Date`, `java.sql.*`, and `java.time.*`
including the two-hub / zone-bridge behaviour and the no-truncation guarantee (a hub never silently drops a
time-of-day; a `00:00:00` only ever comes from a date-only source); (2) `@Map(format = "…")` for `String ↔
temporal` parsing and rendering; (3) `@Map(zone = "…")` and the `-Apercolate.time.zone` compile-time switch,
including the fallback to the consumer's runtime `ZoneId.systemDefault()`. The page SHALL be named by the
user-facing feature, not by any implementation class. Every input snippet and every generated-output snippet on
the page SHALL be single-sourced via `include::` from the backing fixture (input) and from real generated
output (produced under `-Apercolate.docTags`), never hand-typed.

#### Scenario: The temporal page is co-located and single-sourced
- **WHEN** the temporal-mapping page is inspected
- **THEN** it resides in the `strategies-builtin` module's sources and reaches the site via the collector
- **AND** each shown input and generated-output block is an `include::` of a compiled fixture / real generated
  source, with no hand-typed block claimed to be generated

#### Scenario: The temporal feature is backed by a behavioural example
- **WHEN** the temporal-mapping page's example is built
- **THEN** a compiled fixture instantiates the generated mapper and asserts its runtime behaviour (a temporal
  conversion and a `@Map(format = …)` round-trip), and the page includes the real generated output

#### Scenario: The time.zone switch appears in the switches reference
- **WHEN** the compile-time-switches reference is inspected
- **THEN** it documents `-Apercolate.time.zone` with an example and the generated effect (a frozen
  `ZoneId.of("…")` vs the default runtime `ZoneId.systemDefault()`)

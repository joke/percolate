## Why

Percolate maps primitives (boxing/widening) and containers, but has no story for the single most common
field type in real DTOs: dates and times. A mapper that sees `Instant map(Date)`, `LocalDate map(String)`,
or `@Map(source = "ts", format = "yyyy-MM-dd")` today falls straight through to "no producer" and forces the
author to hand-write a helper. Closing this makes percolate usable on the majority of everyday beans, and it
is the first customer that forces two roadmap growth axes (a validated per-directive **option** surface and
**class-member codegen**) into existence — so building it also unblocks `@Named`, `@Context`, and
mapper-uses-mapper later.

## What Changes

- **Temporal type conversion across `java.util.Date`, `java.sql.*`, and `java.time.*`** — resolved not by an
  N×N table but by routing through **two hub types** (`Instant` for the absolute/machine family,
  `LocalDateTime` for the local/human family) with **one zone-consuming bridge** between them. The existing
  engine already composes multi-hop conversions through synthesized intermediate Values (this is how
  `int → Long` resolves as widen-then-box), so only single-hop spoke conversions are authored — the engine
  discovers `A → hub → B` and prunes to the shortest path. Auto roster: `Date`, `Instant`,
  `java.sql.Timestamp`, `OffsetDateTime`, `ZonedDateTime`, `LocalDate`, `LocalDateTime`; partials (`LocalTime`,
  `Year`, `YearMonth`, `MonthDay`, epoch `long`) stay user-helper territory. A **no-truncation invariant** (a
  hub is always the widest type in its family) guarantees a hub can never silently drop a time-of-day.
- **`@Map(format = "…")`** — parse a `String` into, and render a temporal as, a `String`. `java.time` targets
  use a shared thread-safe `DateTimeFormatter`; legacy `java.util.Date`/`java.sql.*` use a fresh per-call
  `SimpleDateFormat` (never shared — it is not thread-safe).
- **`@Map(zone = "…")` + a `-Apercolate.time.zone=` compile-time switch** — the zone for cross-family
  conversions, resolved by precedence: the directive, then the processor option (both frozen as
  `ZoneId.of("…")` in generated code), else generated `ZoneId.systemDefault()` (resolved at the consumer's
  runtime, honouring `-Duser.timezone`). The build machine's zone is **never** baked in.
- **A consumption-tracked directive-option rail** (new, universal) — a strategy that reads a `@Map` option
  stamps its key onto the `OperationSpec` it emits; a late pass diagnoses any option declared but *unconsumed*
  by the winning plan. This is how `@Map(zone = …)` on a non-temporal target becomes a clear error rather than
  silently ignored, with no per-option validation code.
- **A class-member codegen channel** (new axis) — a strategy may request a deduplicated `private static final`
  field on the generated mapper and receive a stable reference, instead of being limited to inline expressions.
  The strategy chooses shared-member vs inline (the thread-safety difference above).
- **Documentation** — a user-facing temporal-mapping chapter co-located in its owning module with
  single-sourced generated output (behavioural doc-e2e under `-Apercolate.docTags`), plus the new
  `-Apercolate.time.zone` switch added to the compile-time-switches reference.

## Capabilities

### New Capabilities
- `directive-options`: the consumption-tracked option rail — `@Map` options are stamped as consumed by the
  strategy that reads them, and any declared-but-unconsumed option on the winning plan is reported as a
  diagnostic (universal validation; `format` and `zone` are its first customers).
- `temporal-conversion`: date/time mapping across `java.util.Date`/`java.sql.*`/`java.time.*` via the two-hub +
  zone-bridge model, the auto roster and no-truncation invariant, `@Map(format = …)` parse/render, and zone
  resolution precedence.

### Modified Capabilities
- `expansion-strategy-spi`: `Directive` gains `format()`/`zone()` accessors; `OperationSpec` gains
  consumed-option-key stamping; the codegen surface gains a member-request channel so a strategy can hoist a
  class-level member and reference it.
- `code-generation`: strategy-requested class members are deduplicated at class scope and emitted as
  `private static final` fields on the generated type — the class-scope sibling of the existing local hoisting.
- `mapping-discovery`: `@Map` `format` and `zone` members are discovered against the `UNSET` sentinel, exactly
  as `constant`/`defaultValue` are today.
- `processor-options`: adds the `percolate.time.zone` option (a project-wide default zone, else generated
  `ZoneId.systemDefault()`).
- `user-manual`: adds the temporal-mapping chapter (conversions with the hub/zone story at a user level,
  `@Map(format = …)`, `@Map(zone = …)`, and the `-Apercolate.time.zone` switch in the switches reference), with
  single-sourced generated output.

## Impact

- **`annotations`**: `@Map` gains `format()` and `zone()` (both defaulting to `UNSET`).
- **`spi`**: `Directive`, `OperationSpec` (option stamping), and the codegen surface (member-request channel).
- **`processor`**: the unconsumed-option diagnostic and its plan-consumption pass; the member-hoisting axis in
  the generate stage (`AssembleMapperType` grows fields + naming/dedup); `ProcessorOptions` +
  `getSupportedOptions()` gain `percolate.time.zone`; `@Map` format/zone discovery.
- **`strategies-builtin`**: the temporal hub conversions, the zone bridge, and the format strategies, plus the
  temporal-mapping doc chapter and its behavioural doc-e2e.
- **`docs`**: the switches reference gains `-Apercolate.time.zone`; the temporal chapter is co-located in
  `strategies-builtin` and xref'd from the spine.
- **Governance**: `module-boundaries` ArchUnit guards (no-private, size-cap, module separation) and the pitest
  ratchet must stay green; no engine-core change (over-emit + cost-prune + grounding are untouched).
- **No breaking changes** — all annotation and SPI additions are additive.

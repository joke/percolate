## 0. Preflight

- [x] 0.1 Load the coding-convention skills before writing any code: `java`/`java11`, `lombok`, `null-safety`, and `spock` conventions (per project memory — no code without them).
- [x] 0.2 Confirm no `net.jqwik` is (re)introduced anywhere; algebra/laws are covered with example-based Spock `where:` tables.
- [x] 0.3 Clear `processor/build/pitHistory.txt` before any pitest run (incremental history lies).

## 1. Annotation + directive discovery (`@Map` grows `format`/`zone`)

- [x] 1.1 Add `String format() default UNSET;` and `String zone() default UNSET;` to `annotations/…/Map.java`, with javadoc mirroring the existing `constant`/`defaultValue` presence semantics (present iff `!Map.UNSET.equals(value)`).
- [x] 1.2 `DiscoverMappingsStage` reads `format` and `zone` against the `UNSET` sentinel (never `String.isEmpty()`); carry them on `MappingDirective`. (spec: `mapping-discovery`)
- [x] 1.3 Add `format()`/`zone()` accessors to the `spi` `Directive` type, present-iff-not-`UNSET`, and surface the discovered values through the processor's `Directive` implementation. (spec: `expansion-strategy-spi` — Directive type)
- [x] 1.4 Unit-spec (Spock, mock seam): discovery reports `format`/`zone` present/absent/empty-string correctly; `Directive` exposes them without compiler internals.

## 2. Spike 0 — consumption-tracked option rail

- [x] 2.1 Add an additive, optional **consumed-option-keys** set to `OperationSpec` (empty by default; existing factory entry points stay source-compatible). (spec: `expansion-strategy-spi` — OperationSpec carries consumed option keys)
- [x] 2.2 In plan extraction, compute per-binding `declared − consumed` where `consumed` is the union of stamps over the **winning** plan's operations; emit a compile **error** at the directive's source position for each leftover option, naming it and why it had no effect. (spec: `directive-options`)
- [x] 2.3 Unit-spec: a stamped key on the winning plan raises no diagnostic; an unstamped declared option (e.g. `zone` on a `String→String` win) raises the unconsumed-option error at the right position; an absent option never diagnoses.
- [x] 2.4 Verify no engine-core change: over-emit/prune/grounding untouched (the rail is a post-extraction read-only pass).

## 3. Spike 1 — class-member codegen axis

- [x] 3.1 Add an additive, optional **member request** to `OperationSpec` (field type, initializer `CodeBlock`, content dedup key) plus a reference indirection for the operation's codegen. (spec: `expansion-strategy-spi` — OperationSpec may request a deduplicated class member)
- [x] 3.2 Add a class-scoped `MemberPlan` collaborator (sibling of `HoistPlan`): collect member requests during the same recursive plan walk, dedup by content key across all method bodies, allocate unique class-scope names via a `NameAllocator`, resolve each requesting codegen's reference through the local-hoist indirection (composer stays field-syntax-free). (spec: `code-generation` — Strategy-requested class members)
- [x] 3.3 `AssembleMapperType` emits each distinct member once as a `private static final` field; relax the generated-class-shape rule to permit strategy-requested static-final fields (no instance fields). If `AssembleMapperType` crosses the ArchUnit size cap, extract the field/naming logic into the `MemberPlan` collaborator. (spec: `code-generation` — Generated class shape)
- [x] 3.4 Unit-spec: two bodies sharing a dedup key emit one field referenced twice; distinct keys emit distinct named fields; a member-less mapper declares no fields; an inline production requests none.
- [x] 3.5 Confirm ArchUnit module-boundary / no-private / size-cap guards stay green.

## 4. Spike 2 — temporal hubs + zone bridge

- [x] 4.1 Author the **absolute-family** spoke conversions (`Conversion` base, target-driven, no directive) to/from `Instant`: `java.util.Date`, `java.sql.Timestamp`, `OffsetDateTime`, `ZonedDateTime`. (spec: `temporal-conversion` — hubs)
- [x] 4.2 Author the **local-family** spoke conversions to/from `LocalDateTime`: `LocalDate` (`atStartOfDay`/`toLocalDate`). Resolve the `java.sql.Date`-vs-`Timestamp` family question (design Open Question) and encode it.
- [x] 4.3 Add `Optional<String> timeZone` to `ProcessorOptions` (parsed from `-Apercolate.time.zone=`, absent → empty) and declare `"percolate.time.zone"` in `getSupportedOptions()`. (spec: `processor-options`)
- [x] 4.4 Implement the `Instant ⇄ LocalDateTime` **zone bridge** as an `ExpansionStrategy` (reads `demand.directive()` zone), resolving the zone by precedence `@Map(zone)` → `-Apercolate.time.zone` (both frozen `ZoneId.of("…")`) → generated `ZoneId.systemDefault()`, and **stamping `"zone"` consumed**. Never freeze the build-JVM zone. (spec: `temporal-conversion` — zone bridge, zone resolution)
- [x] 4.5 Enforce the roster + no-truncation invariant: only the seven listed types auto-convert; partials (`LocalTime`/`Year`/`YearMonth`/`MonthDay`/epoch `long`) do not; a hub never drops a source time-of-day. (spec: `temporal-conversion` — roster)
- [x] 4.6 Unit-spec each spoke + the bridge at the mock seam (myopic: decision from `Demand`+`ResolveCtx` only, no graph); example-based Spock tables cover the roster and the zone-precedence cases; a `zone` on an absolute-only path is unconsumed → diagnostic.

## 5. Spike 3 — `@Map(format=…)` strategies

- [x] 5.1 Implement the `java.time` format strategy (`ExpansionStrategy` directly; reads `format`, stamps it consumed): `parse`/`format` via a **hoisted shared** `private static final DateTimeFormatter` (member request from spike 1). (spec: `temporal-conversion` — format)
- [x] 5.2 Implement the legacy `java.util.Date` + `java.sql.*` format strategy: **per-call** `new SimpleDateFormat(pattern)` (no member request — thread-unsafe, never hoisted); stamps `format` consumed.
- [x] 5.3 Unit-spec: `String→LocalDate` parse round-trip uses the hoisted formatter; `Date→String` uses a per-call `SimpleDateFormat`; `@Map(format=…)` on a non-temporal target is unconsumed → diagnostic.

## 6. Spike 4 — documentation (single-sourced) + behavioural doc-e2e

- [x] 6.1 Add a behavioural doc-e2e in `strategies-builtin` (`@Tag('integration')`, compiled with `-Apercolate.docTags`): instantiate the generated mapper, assert a temporal conversion and a `@Map(format=…)` round-trip, materialise the real generated output for display. (spec: `user-manual`, `e2e-test-architecture`)
- [x] 6.2 Write the temporal-mapping feature page co-located in `strategies-builtin`, named by the feature (not a class): conversions + hub/zone story + no-truncation guarantee, `@Map(format=…)`, `@Map(zone=…)`. Every input and generated-output block is `include::`-single-sourced; hand-type nothing.
- [x] 6.3 Add `-Apercolate.time.zone` to the compile-time-switches reference (`docs`/`processor`) with an example and its generated effect (frozen `ZoneId.of` vs default `systemDefault()`); wire nav/xrefs.
- [x] 6.4 Confirm the Antora build resolves all `include::`/`xref:` targets.

## 7. Verification

- [x] 7.1 Run `./gradlew check` and fix every violation (tests, jacoco 95% gate, ArchUnit, PMD/error-prone, CodeNarc). NEVER continue with violations.
- [x] 7.2 Run pitest on the affected modules (`:processor:pitest`, `:strategies-builtin:pitest`) with cleared history; confirm the ratchet holds for the new classes.
- [ ] 7.3 Commit the completed change with `/commit-commands:commit`.

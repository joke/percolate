## MODIFIED Requirements

### Requirement: The manual covers the bean-mapping consumer topics

The manual SHALL contain pages that document, for a Java developer mapping beans: project integration with **both Maven and Gradle**; a quick-start minimal mapper; basic mapper structure (which methods are discovered versus skipped); the `@Map` annotation including `target`, `source`, `constant`, `defaultValue` and the `UNSET` presence rule; nested target and source path chains; **path access over getters, record accessors, and public fields**; **collection mapping shown as a progression with a worked example per container mechanism** — same-kind (`List<X>→List<Y>`), cross-kind conversion (`Set→List`), a stream intermediate (`Stream→Set`), and presence composed inside/outside a container; **Optional mapping** (wrapping, unwrapping, and composed with containers); **default values and JSpecify nullability crossings**; conversion methods; and default-method conversions. Each feature section SHALL show a worked example, not a prose-only assertion of support.

#### Scenario: Integration documents both build tools

- **WHEN** the integration page is read
- **THEN** it shows adding percolate via the BOM, starter, and annotations for **both** Maven and Gradle

#### Scenario: @Map members are fully documented

- **WHEN** the `@Map` page is read
- **THEN** it documents `target`, `source`, `constant`, and `defaultValue`, and states the `UNSET` presence rule (an empty string is present, not absent)

#### Scenario: Collections are shown by worked example per mechanism

- **WHEN** the collections page is read
- **THEN** it shows a worked example with generated output for same-kind mapping (`List<X>→List<Y>`), cross-kind conversion (`Set→List`), a stream intermediate (`Stream→Set`), and presence composed with a container — not a prose-only table of supported kinds

#### Scenario: Optionals, defaults, and nullness are documented with examples

- **WHEN** the manual's navigation is read
- **THEN** it includes an Optionals page and a defaults-and-nullness page, each with a worked, single-sourced example

#### Scenario: Path access covers fields and records

- **WHEN** the path-access content is read
- **THEN** it shows percolate reading a getter, a record accessor, and a public field, in one section named for the user-facing capability

#### Scenario: Conversion and default-method topics are present

- **WHEN** the manual's navigation is read
- **THEN** it includes a conversion-methods page and a default-method-conversions page

### Requirement: Documented generated output is single-sourced from real generation

Where a page shows the code percolate generates for an example, that output SHALL be brought in via `include::` from real generated source — produced by compiling the example with documentation tags enabled — and never hand-typed as prose. The displayed region SHALL be selected by an AsciiDoc tag the generator emits around generated methods. A page SHALL either single-source its shown output this way or show no output at all; a hand-written block claimed to be generated code is not permitted. This SHALL hold for **every** page in the manual, not a subset.

#### Scenario: Shown output comes from real generation

- **WHEN** a page shows the code generated for an example
- **THEN** the shown block is an `include::` (by tag) of generated source materialised from compiling that example, not hand-inlined code

#### Scenario: No hand-typed generated output remains on any page

- **WHEN** every page in the manual is inspected
- **THEN** no page hand-writes a block presented as percolate's generated output — the previously hand-typed output blocks (conversion-methods, collections, map-annotation, reactive, and the rest) are all replaced by materialised `include::`s

### Requirement: The manual includes an Extending section for strategy authors

The manual SHALL contain an Extending / SPI section aimed at strategy authors, co-located in the `spi` module, that presents a **real, compiled custom strategy as its worked example** — the shipped `reactor` container strategy — rather than a synthetic or prose-only description. The example SHALL be backed by a behavioural e2e so the extension surface shown cannot drift from a working strategy.

#### Scenario: Extending section is reachable and uses a real strategy

- **WHEN** the manual's navigation is read
- **THEN** it contains an Extending (SPI) page whose worked example is the real `reactor` custom strategy, backed by a compiled behavioural e2e

## ADDED Requirements

### Requirement: Feature pages are co-located in their owning module

Each feature's documentation page SHALL live in the module that owns the feature's strategy — `reactor` for reactive, `reactor-blocking` for the blocking bridge, `strategies-builtin` for the basic features, `spi` for Extending, `processor` for compile-time switches — reaching the single Antora `percolate` component via a collector `scan` import. The `docs/` tree SHALL retain only the spine: the introduction, getting-started, mapper-structure, and the navigation. Relocating a page SHALL NOT change the rendered site.

#### Scenario: A feature page lives with its module

- **WHEN** the source of the reactive-containers page is located
- **THEN** it resides under the `reactor` module and is imported into the manual by the collector, not authored under `docs/`

#### Scenario: The docs tree holds only the spine

- **WHEN** `docs/modules/ROOT/pages/` is inspected
- **THEN** it contains only the introduction, getting-started, mapper-structure, and navigation — every feature page lives in its owning module

### Requirement: The manual documents each compile-time processor option

The manual SHALL contain a compile-time-switches reference, co-located in the `processor` module, that documents each processor option (e.g. `docTags`, `locals.final`/`locals.var`, `nullable.annotations`, `debug.graphs`) with a worked example and the **generated output that shows the option's effect**, single-sourced from real generation.

#### Scenario: Each switch shows its effect

- **WHEN** the compile-time-switches page is read
- **THEN** each documented option shows an example and its materialised generated output demonstrating the option's effect

### Requirement: Every documented feature is backed by a behavioural example

Every feature section in the manual SHALL be backed by a compiled fixture that is behaviourally asserted (see `e2e-test-architecture`). A documented capability without a compiled, behaviourally-tested example SHALL NOT ship, and a supported capability with no documentation section SHALL be recorded as a documentation gap or a removal candidate via the change's feature→example census.

#### Scenario: A documented feature has a behavioural example

- **WHEN** any feature section is inspected
- **THEN** its example is a compiled fixture exercised by a behavioural e2e in the owning module

#### Scenario: An undocumented capability is flagged

- **WHEN** the feature→example census is reviewed
- **THEN** every supported user-facing mechanism either has a documentation section with an example or is explicitly recorded as a gap or removal candidate

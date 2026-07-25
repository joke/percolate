## ADDED Requirements

### Requirement: The manual documents enum mapping

The manual SHALL contain an enum-mapping feature page under the Conversions section, co-located in the module that
owns the enum conversion strategy and reaching the Antora component via the collector `scan` import. The page SHALL
document, at a user level: (1) that enum-to-enum mapping is declared as an abstract conversion method
(`Target toX(Source s)` with both types `enum`) whose body percolate generates, and that bean members of the target
enum bridge through it automatically; (2) automatic same-name constant matching for mirrored enums, needing no
directive; (3) `@MapEnum(source = "…", target = "…")` for per-constant overrides where names differ, and that extra
target constants are allowed while an uncovered source constant fails the build; (4) the `-Apercolate.switch.style`
compile-time switch (`AUTO` / `CLASSIC` / `ARROW`), the Java 11 classic-statement vs Java 14+ modern-expression
output, and that the modern expression omits `default` so a forgotten constant fails to compile. The page SHALL be
named by the user-facing feature, not by any implementation class. Every input snippet and every generated-output
snippet on the page SHALL be single-sourced via `include::` from the backing fixture (input) and from real generated
output (produced under `-Apercolate.docTags`), never hand-typed.

#### Scenario: The enum-mapping page is co-located and single-sourced
- **WHEN** the enum-mapping page is inspected
- **THEN** it resides in the enum-conversion strategy's owning module sources and reaches the site via the collector
- **AND** each shown input and generated-output block is an `include::` of a compiled fixture / real generated
  source, with no hand-typed block claimed to be generated

#### Scenario: The enum feature is backed by a behavioural example
- **WHEN** the enum-mapping page's example is built
- **THEN** a compiled fixture instantiates the generated mapper and asserts its runtime behaviour (a same-name
  mapping and a `@MapEnum` override mapping), and the page includes the real generated output

#### Scenario: The switch.style switch appears in the switches reference
- **WHEN** the compile-time-switches reference is inspected
- **THEN** it documents `-Apercolate.switch.style` with an example and the generated effect (a classic switch
  statement on Java 11 vs a `default`-free modern switch expression on Java 14+)

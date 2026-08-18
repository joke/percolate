## ADDED Requirements

### Requirement: The manual documents builder assembly with a worked example per convention

The manual SHALL contain a builder-assembly page documenting that a target may be assembled through a builder as well as a constructor, showing a worked example for each shipped convention — fluent/Lombok (`builder()` with `name(v)`), protobuf (`newBuilder()` with `setName(v)`), with-style (`withName(v)`), and side-located (`new MyClassBuilder()`) — and explaining that only the declared children are set, so a builder exposing further setters still applies.

The page SHALL document the `percolate.construction.preference` switch, stating that `constructor` is the default, that the setting is a preference rather than a restriction, and that the unpreferred form is still used when the preferred one does not fit.

The page SHALL be reachable from the navigation and SHALL be co-located in `percolate-strategies-builtin`, the module owning the strategies, reaching the Antora component through a collector scan.

#### Scenario: Each shipped convention is shown by worked example
- **WHEN** the builder-assembly page is read
- **THEN** it shows a compiling mapper and its generated output for each of the four shipped conventions

#### Scenario: The subset rule is documented
- **WHEN** the builder-assembly page is read
- **THEN** it states that only declared children are set and that surplus builder setters are left untouched

#### Scenario: The preference switch is documented as a preference
- **WHEN** the builder-assembly page's preference discussion is read
- **THEN** it states that `constructor` is the default and that the unpreferred form is still used when the preferred one does not match

#### Scenario: The page is reachable and module-owned
- **WHEN** the manual's navigation and the page's source location are inspected
- **THEN** the navigation contains an entry for builder assembly
- **AND** the page source resides under `strategies-builtin/src/docs/`, not under `docs/`

### Requirement: The compile-time switches reference documents construction.preference

The compile-time switches reference page SHALL document `percolate.construction.preference` alongside the existing switches, listing its accepted values (`constructor`, `builder`), its default (`constructor`), and its effect, and cross-referencing the builder-assembly page.

#### Scenario: The switch appears in the reference table and body
- **WHEN** the compile-time switches page is read
- **THEN** `percolate.construction.preference` appears in the switch table
- **AND** a section documents its accepted values, its default, and its effect

#### Scenario: The switch cross-references its feature page
- **WHEN** the `percolate.construction.preference` section is read
- **THEN** it links to the builder-assembly page

### Requirement: Builder examples are backed by end-to-end tests

The builder-assembly page's examples SHALL be single-sourced from real compiling fixtures whose generated output is materialised by an end-to-end test, so shown source and shown generated output cannot drift from what percolate actually emits. A broken fixture SHALL break the build rather than render a stale page.

#### Scenario: Shown output comes from real generation
- **WHEN** the generated output shown on the builder-assembly page is compared with the fixture's compiled output
- **THEN** they are the same text, included by tag rather than hand-typed

#### Scenario: A broken builder fixture breaks the build
- **WHEN** a builder documentation fixture stops compiling or its mapper stops generating
- **THEN** the docs build fails rather than publishing a stale example

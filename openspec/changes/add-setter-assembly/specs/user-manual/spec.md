## ADDED Requirements

### Requirement: The manual documents setter assembly with a worked example

The manual SHALL contain a setter-assembly page documenting that a target may be assembled through its no-argument constructor and its JavaBean setters, showing a worked mapper and its generated output, including the generated helper method.

The page SHALL state that the gate is containment, so a bean exposing further setters still applies and the surplus setters are left uncalled. It SHALL state that only the JavaBean `setX` naming is recognised, that inherited setters match, and that a `this`-returning setter matches.

The page SHALL document how `percolate.construction.preference` ranks the setter form against the constructor and builder forms, and SHALL state that the setter form ranks last by default.

The page SHALL be reachable from the navigation and SHALL be co-located in `percolate-strategies-builtin`, the module owning the strategy, reaching the Antora component through a collector scan.

#### Scenario: The generated helper is shown
- **WHEN** the setter-assembly page is read
- **THEN** it shows a compiling mapper and its generated output
- **AND** the shown output contains the generated helper method that runs the setter sequence

#### Scenario: The containment rule is documented
- **WHEN** the setter-assembly page is read
- **THEN** it states that only declared children are set and that surplus bean setters are left uncalled

#### Scenario: The match rules are documented
- **WHEN** the setter-assembly page is read
- **THEN** it states that only `setX` naming is recognised, that inherited setters match, and that a `this`-returning setter matches

#### Scenario: The ranking is documented
- **WHEN** the setter-assembly page's preference discussion is read
- **THEN** it states that the setter form ranks last by default and shows how to rank it first

#### Scenario: The page is reachable and module-owned
- **WHEN** the manual's navigation and the page's source location are inspected
- **THEN** the navigation contains an entry for setter assembly
- **AND** the page source resides under `strategies-builtin/src/docs/`, not under `docs/`

### Requirement: Setter examples are backed by end-to-end tests

The setter-assembly page's examples SHALL be single-sourced from real compiling fixtures whose generated output is materialised by an end-to-end test, so shown source and shown generated output cannot drift from what percolate actually emits. A broken fixture SHALL break the build rather than render a stale page.

The fixture set SHALL include a bean that no other assembly form can satisfy, so the page cannot pass through a constructor or a builder by accident.

#### Scenario: Shown output comes from real generation
- **WHEN** the generated output shown on the setter-assembly page is compared with the fixture's compiled output
- **THEN** they are the same text, included by tag rather than hand-typed

#### Scenario: The fixture cannot pass through another form
- **WHEN** the setter documentation fixture is inspected
- **THEN** at least one documented bean declares no matching all-arguments constructor and no builder

#### Scenario: A broken setter fixture breaks the build
- **WHEN** a setter documentation fixture stops compiling or its mapper stops generating
- **THEN** the docs build fails rather than publishing a stale example

### Requirement: The compile-time switches reference documents the helper member options

The compile-time switches reference page SHALL document `percolate.helpers.visibility` and `percolate.helpers.static` alongside the existing switches, listing their accepted values, their defaults, and their effect, each with a worked example and its materialised generated output.

The reference SHALL state that both options apply to every member percolate generates on a mapper, that a generated field stays `final` regardless, and that the defaults reproduce the previous output.

#### Scenario: Both switches appear in the reference table and body
- **WHEN** the compile-time switches page is read
- **THEN** `percolate.helpers.visibility` and `percolate.helpers.static` appear in the switch table
- **AND** a section documents each option's accepted values, its default, and its effect

#### Scenario: Each switch shows its effect on real output
- **WHEN** the helper-option sections are read
- **THEN** each shows materialised generated output demonstrating the option's effect

#### Scenario: The field finality note is present
- **WHEN** the helper-option sections are read
- **THEN** they state that a generated field remains `final` under every setting

## MODIFIED Requirements

### Requirement: The compile-time switches reference documents construction.preference

The compile-time switches reference page SHALL document `percolate.construction.preference` alongside the existing switches, listing its list grammar, its accepted tokens (`constructor`, `builder`, `setter`), its default order (`constructor,builder,setter`), and its effect, and cross-referencing the builder-assembly and setter-assembly pages.

The reference SHALL state that omitted tokens are appended in the default order, that the setting is a preference rather than a restriction, and that an unpreferred form is still used when a preferred one does not fit.

#### Scenario: The switch appears in the reference table and body
- **WHEN** the compile-time switches page is read
- **THEN** `percolate.construction.preference` appears in the switch table
- **AND** a section documents its list grammar, its accepted tokens, its default order, and its effect

#### Scenario: The list grammar is documented
- **WHEN** the `percolate.construction.preference` section is read
- **THEN** it shows a multi-token example and states that omitted tokens are appended in the default order

#### Scenario: The switch cross-references its feature pages
- **WHEN** the `percolate.construction.preference` section is read
- **THEN** it links to the builder-assembly page and to the setter-assembly page

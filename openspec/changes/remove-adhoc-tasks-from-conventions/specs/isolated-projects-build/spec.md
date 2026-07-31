## ADDED Requirements

### Requirement: The build performs no source-code checks of its own

Gradle configuration SHALL NOT inspect project source code. No task in
`buildSrc/src/main/groovy/percolate.conventions.gradle` and no task in any module's own
`build.gradle` SHALL read, grep, or parse files under `src/**` in order to decide whether the
build passes. Source-code analysis SHALL be delegated to analysers applied as plugins — spotless,
PMD, CodeNarc, Error Prone, NullAway, ArchUnit — whose settings the build script MAY configure
freely; configuring an analyser is build configuration, whereas a task that opens a source file
and pronounces a verdict is not. Where no analyser can express an invariant, the invariant SHALL
be left to review rather than scripted in the build, and relocating such a script from the
convention plugin into an individual module SHALL NOT be treated as a remedy — the prohibition is
on the mechanism, not on where it is declared.

#### Scenario: No build script reads project sources

- **WHEN** `percolate.conventions.gradle` and every module `build.gradle` are inspected
- **THEN** no task body opens, reads, or pattern-matches a file under `src/**`; the only source
  consumers are compilation tasks and configured analysers

#### Scenario: An unexpressible invariant is left to review

- **WHEN** an invariant about source text is wanted and no available analyser rule can express it
- **THEN** it is documented as a convention and enforced in review, and no bespoke scanning task
  is written in its place — in any module

#### Scenario: Configuring an analyser remains permitted

- **WHEN** the convention plugin configures `spotless`, `pmd`, `codenarc`, `errorprone`, or
  `nullaway`
- **THEN** that is unaffected by this requirement, because the build script supplies settings to
  a tool rather than performing the inspection itself

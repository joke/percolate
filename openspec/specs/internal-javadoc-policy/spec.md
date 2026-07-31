# internal-javadoc-policy Specification

## Purpose

Defines where javadoc belongs and what happens to the rationale it used to carry. Javadoc is documentation of an external contract, so it lives in the two modules an external developer consumes directly — `annotations` to use the library, `spi` to implement a custom strategy. Every other module is published for classpath reasons but internal in contract, and a block comment there renders into a javadoc jar nobody opens while pretending to be API documentation. Removing it is a demotion, not a deletion: an invariant, a rejected alternative, a cross-change decision or a tooling workaround becomes a `//` comment that sits with the code. In a codebase already subject to the no-private-methods and unused-protected rules, that is nearly every block — a method that exists as its own intercepted seam has a reason for existing that its signature does not carry. The policy is unenforced by the build: no available static-analysis rule can express it, and a hand-written source scan in the build script is not an acceptable substitute, so it is carried by authoring and review instead.
## Requirements
### Requirement: Javadoc is confined to the externally consumed modules

Javadoc comments (`/** ... */`) SHALL appear only in `annotations` and `spi` — the two modules an external developer consumes directly, one to use the library and one to implement a custom strategy. Every other module of percolate's own code is published for classpath reasons but is internal in contract, and SHALL carry no javadoc. Vendored third-party sources SHALL be exempt: `lib/javapoet` contains a patched copy of upstream `com.palantir.javapoet.MethodSpec`, whose comments are upstream's and are already excluded from PMD and Error Prone.

#### Scenario: An internal module declares no javadoc
- **WHEN** `processor`, `strategies-builtin`, `reactor`, `reactor-blocking`, or `percolate` main sources are inspected
- **THEN** no `/** ... */` block is present on any type, field, constructor, or method

#### Scenario: The external contract keeps its documentation
- **WHEN** `annotations` and `spi` main sources are inspected
- **THEN** javadoc is present and unrestricted

#### Scenario: Vendored sources are untouched
- **WHEN** `lib/javapoet` is inspected
- **THEN** upstream's javadoc remains, and the module is not subject to the internal ruleset

### Requirement: Non-obvious rationale survives the javadoc removal as line comments

Removing javadoc from an internal module SHALL be a demotion, not a blanket deletion. A block explaining something the code does not state — an invariant, a rejected alternative, a cross-change decision, a compiler or tooling workaround — SHALL be rewritten as a `//` comment preserving its content. A block that only restates the signature it sits on SHALL be deleted outright. Documentation SHALL cover the non-obvious only; a self-explanatory member SHALL carry no comment at all.

#### Scenario: Design rationale is preserved
- **WHEN** a javadoc block records why a decision was made, such as `ProcessorModule`'s `ServiceLoader` ordering or a graph invariant
- **THEN** it becomes a `//` comment with its explanation intact, rather than being deleted

#### Scenario: A signature-restating block is deleted
- **WHEN** a javadoc block adds nothing beyond the member's name, parameters, and return type
- **THEN** it is removed and not replaced with a `//` comment

#### Scenario: Demotion dominates in practice
- **WHEN** the conversion is applied across a codebase already subject to the no-private-methods and unused-protected rules
- **THEN** nearly every block demotes rather than deletes, because a method that exists as its own intercepted seam has a reason for existing that the signature does not carry

#### Scenario: Trivial comments are not introduced elsewhere
- **WHEN** a self-explanatory method is reviewed
- **THEN** it carries no explanatory comment in either form

### Requirement: The javadoc confinement is a convention, not a build gate

The javadoc confinement SHALL be carried by authoring and review, and SHALL NOT be verified by the build. No Gradle task SHALL scan module sources for javadoc blocks, and no module SHALL declare whether it is public API for the purpose of such a scan. No static-analysis rule SHALL be substituted either: PMD's `CommentRequired` cannot address a package-private method, which is where nearly every helper lives under the no-private-methods rule; PMD 7 no longer exposes comments to a custom XPath rule; and ArchUnit reads bytecode, from which comments are absent. A javadoc block reintroduced into an internal module is therefore an accepted, review-caught regression whose blast radius is a comment in a javadoc jar nobody opens.

#### Scenario: The build carries no javadoc scan
- **WHEN** `buildSrc/src/main/groovy/percolate.conventions.gradle` and every module's `build.gradle` are inspected
- **THEN** no task reads, greps, or parses source files looking for javadoc, and no `percolatePublicApi` declaration is present anywhere

#### Scenario: A reintroduced javadoc block does not fail the build
- **WHEN** a javadoc comment is added to a type or member in an internal module and `check` runs
- **THEN** `check` succeeds, and the block is expected to be caught in review instead

#### Scenario: The policy still governs how code is written
- **WHEN** a member in an internal module needs explanation
- **THEN** it is written as a `//` comment, exactly as before — the confinement and demotion requirements of this capability are unchanged by the absence of enforcement

### Requirement: Publishing obligations are unaffected by the confinement

Every publishable module SHALL continue to produce a javadoc jar. A javadoc jar that is near-empty because its module carries no javadoc SHALL be accepted rather than treated as a build defect or grounds for disabling `withJavadocJar()`.

#### Scenario: An internal module still publishes a javadoc jar
- **WHEN** `processor` is published
- **THEN** a `-javadoc.jar` accompanies the main artifact, satisfying the existing Maven Central requirement, even though it documents nothing

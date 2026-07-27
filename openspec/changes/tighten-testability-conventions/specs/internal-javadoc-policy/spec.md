## ADDED Requirements

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

#### Scenario: Trivial comments are not introduced elsewhere
- **WHEN** a self-explanatory method is reviewed
- **THEN** it carries no explanatory comment in either form

### Requirement: The javadoc confinement is enforced by the build

The build SHALL fail when a javadoc comment appears in a module where this policy forbids it, rather than relying on review. Enforcement SHALL use PMD's `CommentRequired` rule with its per-element `Unwanted` settings, applied through a ruleset distinct from the one governing `annotations` and `spi`, so the two policies are selected per module rather than negotiated within one file.

#### Scenario: A reintroduced javadoc block fails the build
- **WHEN** a javadoc comment is added to a type or member in `processor`, `strategies-builtin`, `reactor`, `reactor-blocking`, or `percolate`
- **THEN** `check` fails with a PMD violation

#### Scenario: The externally consumed modules use the permissive ruleset
- **WHEN** PMD runs against `annotations` or `spi`
- **THEN** the `Unwanted` comment settings do not apply and javadoc passes

### Requirement: Publishing obligations are unaffected by the confinement

Every publishable module SHALL continue to produce a javadoc jar. A javadoc jar that is near-empty because its module carries no javadoc SHALL be accepted rather than treated as a build defect or grounds for disabling `withJavadocJar()`.

#### Scenario: An internal module still publishes a javadoc jar
- **WHEN** `processor` is published
- **THEN** a `-javadoc.jar` accompanies the main artifact, satisfying the existing Maven Central requirement, even though it documents nothing

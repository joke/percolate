## ADDED Requirements

### Requirement: The javadoc confinement is a convention, not a build gate

The javadoc confinement SHALL be carried by authoring and review, and SHALL NOT be verified by
the build. No Gradle task SHALL scan module sources for javadoc blocks, and no module SHALL
declare whether it is public API for the purpose of such a scan. No static-analysis rule SHALL be
substituted either: PMD's `CommentRequired` cannot address a package-private method, which is
where nearly every helper lives under the no-private-methods rule; PMD 7 no longer exposes
comments to a custom XPath rule; and ArchUnit reads bytecode, from which comments are absent. A
javadoc block reintroduced into an internal module is therefore an accepted, review-caught
regression whose blast radius is a comment in a javadoc jar nobody opens.

#### Scenario: The build carries no javadoc scan

- **WHEN** `buildSrc/src/main/groovy/percolate.conventions.gradle` and every module's
  `build.gradle` are inspected
- **THEN** no task reads, greps, or parses source files looking for javadoc, and no
  `percolatePublicApi` declaration is present anywhere

#### Scenario: A reintroduced javadoc block does not fail the build

- **WHEN** a javadoc comment is added to a type or member in an internal module and `check` runs
- **THEN** `check` succeeds, and the block is expected to be caught in review instead

#### Scenario: The policy still governs how code is written

- **WHEN** a member in an internal module needs explanation
- **THEN** it is written as a `//` comment, exactly as before — the confinement and demotion
  requirements of this capability are unchanged by the absence of enforcement

## REMOVED Requirements

### Requirement: The javadoc confinement is enforced by the build

**Reason**: The enforcement was a hand-written `checkNoJavadoc` task in `percolate.conventions`
that opened every file under `src/main/java` and searched it for `/**`. The build system is not
to perform source-code checks: build configuration may configure analysers, but build-script
logic may not read project sources and pronounce a verdict. No analyser can express this
particular ban, so enforcement is withdrawn rather than relocated or reimplemented.

**Migration**: `checkNoJavadoc`, its `check` wiring, and the `ext.percolatePublicApi` flag are
deleted from `percolate.conventions`, and the `percolatePublicApi = true` declarations are
deleted from `annotations/build.gradle` and `spi/build.gradle`. The policy itself is retained and
is now governed by the "javadoc confinement is a convention, not a build gate" requirement above.

# Isolated Projects Build Spec

## Purpose

Defines how percolate's build configuration is organized to be compatible with Gradle's Isolated Projects feature, and the current status of actually enabling it. Cross-module configuration lives in a single `buildSrc` convention plugin rather than a root-script `subprojects{}`/`allprojects{}` block, since the latter is fundamentally incompatible with Isolated Projects (root reaching into every subproject's mutable configuration state). The flag itself is not yet enabled by default, pending a third-party plugin fix outside this repository's control.

## Requirements

### Requirement: Cross-module build configuration lives in a single buildSrc convention plugin

Root `build.gradle` SHALL NOT contain a `subprojects { }` or `allprojects { }` block. All cross-module configuration (compiler settings, static analysis, test wiring, publishing) SHALL be defined in a single precompiled Groovy script convention plugin under `buildSrc/` (`percolate.conventions`), applied by each module's own `plugins { }` block as one explicit id. Splitting this configuration across multiple convention plugins, each requiring its own per-module `id` entry, SHALL NOT be reintroduced — doing so once already multiplied per-module boilerplate beyond what the single root-script block it replaced required, for composability no module used.

#### Scenario: Root build.gradle has no cross-project configuration block

- **WHEN** root `build.gradle` is inspected
- **THEN** it contains no `subprojects { }` or `allprojects { }` block

#### Scenario: A module opts into every applicable convention with one id

- **WHEN** any module's `build.gradle` `plugins { }` block is inspected
- **THEN** exactly one `id 'percolate.conventions'` entry is present (for any module that wants cross-module conventions at all), not multiple `percolate.*-conventions` entries

### Requirement: Build configuration is Isolated Projects-ready, pending a known third-party blocker

The `buildSrc` convention plugin and the version-source computation SHALL be verified to produce zero configuration-cache/Isolated-Projects problems when `org.gradle.unsafe.isolated-projects=true` is set. `org.gradle.unsafe.isolated-projects=true` SHALL NOT be enabled by default in `gradle.properties` while `info.solidsoft.pitest` remains applied to any module, since that plugin's `PitestPlugin.apply()` unconditionally reaches into the root project's `buildscript` from every project it's applied to (a legacy-migration guard with no configuration to disable it, confirmed by decompiling `1.19.0` — the latest available release at the time of this spec) — a violation this repository's own build script cannot fix. Re-enabling the flag is a follow-up, gated on an upstream fix to that plugin (or another workaround).

#### Scenario: buildSrc and version-source alone report zero problems

- **WHEN** `org.gradle.unsafe.isolated-projects=true` is set and `./gradlew projects` is run (forcing full multi-project configuration) against a state where no module applies `info.solidsoft.pitest`
- **THEN** the build succeeds and reports zero configuration-cache problems

#### Scenario: The flag is off by default due to the pitest blocker

- **WHEN** `gradle.properties` is inspected
- **THEN** `org.gradle.unsafe.isolated-projects=true` is absent or commented out, with a comment explaining the `info.solidsoft.pitest` blocker and the condition for re-enabling it

### Requirement: Configuration cache is enabled by default

`org.gradle.configuration-cache=true` SHALL be set (uncommented) in `gradle.properties`, since Isolated Projects requires configuration cache and forbids disabling it.

#### Scenario: Configuration cache is on without an explicit flag

- **WHEN** `./gradlew help` is run with no additional flags
- **THEN** Gradle reports a configuration cache entry being stored or reused, without requiring `--configuration-cache` on the command line

### Requirement: The build performs no source-code checks of its own

Gradle configuration SHALL NOT inspect project source code. No task in `buildSrc/src/main/groovy/percolate.conventions.gradle` and no task in any module's own `build.gradle` SHALL read, grep, or parse files under `src/**` in order to decide whether the build passes. Source-code analysis SHALL be delegated to analysers applied as plugins — spotless, PMD, CodeNarc, Error Prone, NullAway, ArchUnit — whose settings the build script MAY configure freely; configuring an analyser is build configuration, whereas a task that opens a source file and pronounces a verdict is not. Where no analyser can express an invariant, the invariant SHALL be left to review rather than scripted in the build, and relocating such a script from the convention plugin into an individual module SHALL NOT be treated as a remedy — the prohibition is on the mechanism, not on where it is declared.

#### Scenario: No build script reads project sources

- **WHEN** `percolate.conventions.gradle` and every module `build.gradle` are inspected
- **THEN** no task body opens, reads, or pattern-matches a file under `src/**`; the only source consumers are compilation tasks and configured analysers

#### Scenario: An unexpressible invariant is left to review

- **WHEN** an invariant about source text is wanted and no available analyser rule can express it
- **THEN** it is documented as a convention and enforced in review, and no bespoke scanning task is written in its place — in any module

#### Scenario: Configuring an analyser remains permitted

- **WHEN** the convention plugin configures `spotless`, `pmd`, `codenarc`, `errorprone`, or `nullaway`
- **THEN** that is unaffected by this requirement, because the build script supplies settings to a tool rather than performing the inspection itself

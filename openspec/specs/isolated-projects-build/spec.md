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

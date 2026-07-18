## ADDED Requirements

### Requirement: Cross-module build configuration lives in buildSrc convention plugins

Root `build.gradle` SHALL NOT contain a `subprojects { }` or `allprojects { }` block. All cross-module configuration (compiler settings, static analysis, test wiring, publishing) SHALL be defined as precompiled Groovy script convention plugins under `buildSrc/`, applied explicitly by each module's own `plugins { }` block.

#### Scenario: Root build.gradle has no cross-project configuration block

- **WHEN** root `build.gradle` is inspected
- **THEN** it contains no `subprojects { }` or `allprojects { }` block

#### Scenario: A module opts into a convention by declaring its plugin id

- **WHEN** any module's `build.gradle` `plugins { }` block is inspected
- **THEN** every cross-module convention that applies to it (base, java, groovy, lombok, pitest, publishing, as applicable to that module's own plugin choices) is present as an explicit `id 'percolate.<name>-conventions'` entry

### Requirement: Build configuration is Isolated Projects-ready, pending a known third-party blocker

The `buildSrc` convention plugins and the version-source computation SHALL be verified to produce zero configuration-cache/Isolated-Projects problems when `org.gradle.unsafe.isolated-projects=true` is set. `org.gradle.unsafe.isolated-projects=true` SHALL NOT be enabled by default in `gradle.properties` while `info.solidsoft.pitest` remains applied to any module, since that plugin's `PitestPlugin.apply()` unconditionally reaches into the root project's `buildscript` from every project it's applied to (a legacy-migration guard with no configuration to disable it, confirmed by decompiling `1.19.0` — the latest available release at the time of this change) — a violation this repository's own build script cannot fix. Re-enabling the flag is a follow-up, gated on an upstream fix to that plugin (or another workaround).

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

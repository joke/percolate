# Isolated Projects Build Spec

## Purpose

Defines how percolate's build configuration is organized to be compatible with Gradle's Isolated Projects feature, and the current status of actually enabling it. Cross-module configuration lives in a single `buildSrc` convention plugin rather than a root-script `subprojects{}`/`allprojects{}` block, since the latter is fundamentally incompatible with Isolated Projects (root reaching into every subproject's mutable configuration state). The flag itself is not yet enabled by default, pending a third-party plugin fix outside this repository's control.

## Requirements

### Requirement: Cross-module build configuration lives in a single buildSrc convention plugin

Root `build.gradle` SHALL NOT contain a `subprojects { }` or `allprojects { }` block. All cross-module configuration (compiler settings, static analysis, test wiring, publishing, version derivation) SHALL be defined in a single precompiled Groovy script convention plugin under `buildSrc/` (`percolate.conventions`), applied by each module's own `plugins { }` block as one explicit id. Splitting this configuration across multiple convention plugins, each requiring its own per-module `id` entry, SHALL NOT be reintroduced — doing so once already multiplied per-module boilerplate beyond what the single root-script block it replaced required, for composability no module used.

Version derivation SHALL be included in this plugin rather than moved to a settings plugin. Because `project.version` is already set inside the convention plugin, applying the versioning plugin at project level reproduces the existing coverage exactly: the root project and the `lib` container project do not apply `percolate.conventions` and are already unversioned, and neither is published.

#### Scenario: Root build.gradle has no cross-project configuration block

- **WHEN** root `build.gradle` is inspected
- **THEN** it contains no `subprojects { }` or `allprojects { }` block

#### Scenario: A module opts into every applicable convention with one id

- **WHEN** any module's `build.gradle` `plugins { }` block is inspected
- **THEN** exactly one `id 'percolate.conventions'` entry is present (for any module that wants cross-module conventions at all), not multiple `percolate.*-conventions` entries

#### Scenario: Every publishable module is versioned

- **WHEN** each module that applies `maven-publish` is inspected
- **THEN** its `project.version` is not `unspecified`

### Requirement: A project plugin's version is declared in exactly one place

Every project plugin's version SHALL be declared either in `settings.gradle`'s `pluginManagement` block or in `buildSrc/build.gradle`, never in both. A version declared in two places is a version that can disagree with itself, with the `buildSrc` declaration silently winning.

A plugin applied by the convention plugin with `pluginManager.apply(id)` resolves its descriptor from the `buildSrc` classpath and never consults `pluginManagement`; its version SHALL therefore be declared only in `buildSrc/build.gradle`, and it SHALL NOT appear in `pluginManagement`.

A plugin applied from a real `plugins { }` block in root `build.gradle` or in a module SHALL keep its `pluginManagement` entry, which is load-bearing for that resolution.

#### Scenario: No plugin version is declared twice

- **WHEN** the plugin ids in `settings.gradle`'s `pluginManagement` block and the dependency coordinates in `buildSrc/build.gradle` are compared
- **THEN** no plugin appears in both

#### Scenario: Plugins applied by the convention plugin are absent from pluginManagement

- **WHEN** `settings.gradle`'s `pluginManagement` block is inspected
- **THEN** it declares no entry for Spotless, Error Prone, NullAway or the metadata plugin, each of which the convention plugin applies with `pluginManager.apply(id)`

#### Scenario: Plugins applied from a plugins block keep their entry

- **WHEN** `settings.gradle`'s `pluginManagement` block is inspected
- **THEN** it declares entries for the plugins applied from `plugins { }` blocks in root `build.gradle` and in modules, including CPD, Antora, pitest, Shadow and Lombok

### Requirement: The versioning plugin is declared by its plugin marker

`buildSrc/build.gradle` SHALL depend on `io.github.joke.conventional-version` by its **plugin marker** coordinate, not by its implementation jar, because the convention plugin applies it from a `plugins { }` block, which resolves an id the way a build script does. The other `buildSrc` plugin dependencies SHALL keep their implementation-jar coordinates, because they are applied with `pluginManager.apply(id)`, which reads the descriptor from a jar already on the classpath.

#### Scenario: The versioning plugin uses the marker coordinate

- **WHEN** `buildSrc/build.gradle` is inspected
- **THEN** it declares `io.github.joke.conventional-version:io.github.joke.conventional-version.gradle.plugin`

#### Scenario: The other buildSrc plugin dependencies use implementation jars

- **WHEN** the remaining plugin dependencies in `buildSrc/build.gradle` are inspected
- **THEN** each is an implementation-jar coordinate, not a plugin marker

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

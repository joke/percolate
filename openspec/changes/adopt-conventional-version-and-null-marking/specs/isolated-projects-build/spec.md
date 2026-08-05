## MODIFIED Requirements

### Requirement: Cross-module build configuration lives in a single buildSrc convention plugin

Root `build.gradle` SHALL NOT contain a `subprojects { }` or `allprojects { }` block. All
cross-module configuration (compiler settings, static analysis, test wiring, publishing, version
derivation) SHALL be defined in a single precompiled Groovy script convention plugin under
`buildSrc/` (`percolate.conventions`), applied by each module's own `plugins { }` block as one
explicit id. Splitting this configuration across multiple convention plugins, each requiring its own
per-module `id` entry, SHALL NOT be reintroduced — doing so once already multiplied per-module
boilerplate beyond what the single root-script block it replaced required, for composability no
module used.

Version derivation SHALL be included in this plugin rather than moved to a settings plugin. Because
`project.version` is already set inside the convention plugin, applying the versioning plugin at
project level reproduces the existing coverage exactly: the root project and the `lib` container
project do not apply `percolate.conventions` and are already unversioned, and neither is published.

#### Scenario: Root build.gradle has no cross-project configuration block

- **WHEN** root `build.gradle` is inspected
- **THEN** it contains no `subprojects { }` or `allprojects { }` block

#### Scenario: A module opts into every applicable convention with one id

- **WHEN** any module's `build.gradle` `plugins { }` block is inspected
- **THEN** exactly one `id 'percolate.conventions'` entry is present (for any module that wants
  cross-module conventions at all), not multiple `percolate.*-conventions` entries

#### Scenario: Every publishable module is versioned

- **WHEN** each module that applies `maven-publish` is inspected
- **THEN** its `project.version` is not `unspecified`

## ADDED Requirements

### Requirement: A project plugin's version is declared in exactly one place

Every project plugin's version SHALL be declared either in `settings.gradle`'s `pluginManagement`
block or in `buildSrc/build.gradle`, never in both. A version declared in two places is a version
that can disagree with itself, with the `buildSrc` declaration silently winning.

A plugin applied by the convention plugin with `pluginManager.apply(id)` resolves its descriptor
from the `buildSrc` classpath and never consults `pluginManagement`; its version SHALL therefore be
declared only in `buildSrc/build.gradle`, and it SHALL NOT appear in `pluginManagement`.

A plugin applied from a real `plugins { }` block in root `build.gradle` or in a module SHALL keep
its `pluginManagement` entry, which is load-bearing for that resolution.

#### Scenario: No plugin version is declared twice

- **WHEN** the plugin ids in `settings.gradle`'s `pluginManagement` block and the dependency
  coordinates in `buildSrc/build.gradle` are compared
- **THEN** no plugin appears in both

#### Scenario: Plugins applied by the convention plugin are absent from pluginManagement

- **WHEN** `settings.gradle`'s `pluginManagement` block is inspected
- **THEN** it declares no entry for Spotless, Error Prone, NullAway or the metadata plugin, each of
  which the convention plugin applies with `pluginManager.apply(id)`

#### Scenario: Plugins applied from a plugins block keep their entry

- **WHEN** `settings.gradle`'s `pluginManagement` block is inspected
- **THEN** it declares entries for the plugins applied from `plugins { }` blocks in root
  `build.gradle` and in modules, including CPD, Antora, pitest, Shadow and Lombok

### Requirement: The versioning plugin is declared by its plugin marker

`buildSrc/build.gradle` SHALL depend on `io.github.joke.conventional-version` by its **plugin marker**
coordinate, not by its implementation jar, because the convention plugin applies it from a
`plugins { }` block, which resolves an id the way a build script does. The other `buildSrc` plugin
dependencies SHALL keep their implementation-jar coordinates, because they are applied with
`pluginManager.apply(id)`, which reads the descriptor from a jar already on the classpath.

#### Scenario: The versioning plugin uses the marker coordinate

- **WHEN** `buildSrc/build.gradle` is inspected
- **THEN** it declares
  `io.github.joke.conventional-version:io.github.joke.conventional-version.gradle.plugin`

#### Scenario: The other buildSrc plugin dependencies use implementation jars

- **WHEN** the remaining plugin dependencies in `buildSrc/build.gradle` are inspected
- **THEN** each is an implementation-jar coordinate, not a plugin marker

## 1. buildSrc scaffolding

- [x] 1.1 Create `buildSrc/build.gradle` applying `id 'groovy-gradle-plugin'` (enables precompiled Groovy script plugins to be authored under `buildSrc/src/main/groovy/`) — GAVs for spotless/baseline/errorprone/nullaway/metadata resolved from the already-downloaded Gradle module cache, not guessed.
- [x] 1.2 Create `buildSrc/src/main/groovy/percolate.base-conventions.gradle`: `group = 'io.github.joke.percolate'`, `repositories { mavenCentral() }`, and the version computation from design.md D4 (placeholder using the still-current `rootProject.version` until task 4 wires `com.palantir.git-version` in).
- [x] 1.3 Create `buildSrc/src/main/groovy/percolate.java-conventions.gradle`: move the entire `withPlugin('java-base')` cascade (spotless, baseline-encoding/compiler-diagnostics/java-properties/module-jvm-args, jacoco, errorprone, pmd + `JavaCompile` options) plus the nested `withPlugin('com.diffplug.spotless')`/`withPlugin('jacoco')`/`withPlugin('pmd')`/`withPlugin('net.ltgt.errorprone')`/`withPlugin('net.ltgt.nullaway')`/`withPlugin('java')` blocks, verbatim, from root `build.gradle`.
- [x] 1.4 Create `buildSrc/src/main/groovy/percolate.groovy-conventions.gradle`: move the `withPlugin('groovy')` block (codenarc apply + config + deps) verbatim (except the CodeNarc version — see 1.8).
- [x] 1.5 Create `buildSrc/src/main/groovy/percolate.lombok-conventions.gradle`: move the `withPlugin('io.freefair.lombok')` block (version pin) verbatim.
- [x] 1.6 Create `buildSrc/src/main/groovy/percolate.pitest-conventions.gradle`: move the `withPlugin('info.solidsoft.pitest')` block (pitest config + `pitest`/`compileOnly` deps) verbatim.
- [x] 1.7 Create `buildSrc/src/main/groovy/percolate.publishing-conventions.gradle`: move the entire publishing section (the `withPlugin('io.github.sgtsilvio.gradle.maven-central-publishing')`, `withPlugin('io.github.sgtsilvio.gradle.metadata')`, `withPlugin('maven-publish')`, `withPlugin('signing')` blocks from `restructure-publishing-plugin-wiring`) verbatim.
- [x] 1.8 buildSrc needs its own dependency platform access for `codenarc platform(project.project(':dependencies'))` in 1.4 — resolved: `project.project(':dependencies')` cannot resolve at all from buildSrc (separate build, no access to the root build's project graph, not just an Isolated-Projects violation). Pinned CodeNarc directly to `3.7.0-groovy-4.0` in `percolate.groovy-conventions.gradle`, matching the exact version `dependencies/build.gradle` pins (`api 'org.codenarc:CodeNarc:3.7.0-groovy-4.0'`), with a comment explaining why and noting the two must be kept in sync.

## 2. Module plugin declarations

- [x] 2.1 `annotations/build.gradle`: add `id 'percolate.base-conventions'`, `id 'percolate.java-conventions'`, `id 'percolate.publishing-conventions'`.
- [x] 2.2 `architecture-tests/build.gradle`: add `id 'percolate.base-conventions'`, `id 'percolate.java-conventions'`, `id 'percolate.groovy-conventions'`.
- [x] 2.3 `bom/build.gradle`: add `id 'percolate.base-conventions'`, `id 'percolate.publishing-conventions'` (no java-conventions — `java-platform` never triggers `java-base`).
- [x] 2.4 `dependencies/build.gradle`: add `id 'percolate.base-conventions'` only.
- [x] 2.5 `percolate/build.gradle`: add `id 'percolate.base-conventions'`, `id 'percolate.java-conventions'`, `id 'percolate.publishing-conventions'`.
- [x] 2.6 `percolate-javapoet/build.gradle`: add `id 'percolate.base-conventions'`, `id 'percolate.java-conventions'`, `id 'percolate.publishing-conventions'`.
- [x] 2.7 `percolate-smoke/build.gradle`: add `id 'percolate.base-conventions'`, `id 'percolate.java-conventions'`.
- [x] 2.8 `processor/build.gradle`: add `id 'percolate.base-conventions'`, `id 'percolate.java-conventions'`, `id 'percolate.groovy-conventions'`, `id 'percolate.pitest-conventions'`, `id 'percolate.lombok-conventions'`, `id 'percolate.publishing-conventions'`.
- [x] 2.9 `reactor/build.gradle`: add `id 'percolate.base-conventions'`, `id 'percolate.java-conventions'`, `id 'percolate.groovy-conventions'`, `id 'percolate.lombok-conventions'`, `id 'percolate.publishing-conventions'` (no pitest).
- [x] 2.10 `reactor-blocking/build.gradle`: same set as `reactor` (task 2.9).
- [x] 2.11 `spi/build.gradle`: same full set as `processor` (task 2.8).
- [x] 2.12 `strategies-builtin/build.gradle`: same full set as `processor` (task 2.8).
- [x] 2.13 `test-foundation/build.gradle`: add `id 'percolate.base-conventions'`, `id 'percolate.java-conventions'`, `id 'percolate.groovy-conventions'`.

## 3. Root build.gradle cleanup

- [x] 3.1 Delete the entire `subprojects { project -> ... }` block from root `build.gradle`.
- [x] 3.2 Remove plugin ids from root `build.gradle`'s `plugins { }` block that are now owned entirely by `buildSrc` convention plugins and no longer need a root-level `apply false` declaration. Removed: `com.diffplug.gradle.spotless`, `com.palantir.baseline-config`, `net.ltgt.errorprone`, `net.ltgt.nullaway`, `io.github.sgtsilvio.gradle.metadata` (never applied by any module directly, only ever cascaded from a buildSrc precompiled script, now resolved via buildSrc's own classpath dependency). Kept: `info.solidsoft.pitest`, `io.github.sgtsilvio.gradle.maven-central-publishing`, `io.freefair.lombok` (still declared directly by several modules' own `plugins{}` blocks, matching the existing self-documenting root-registry convention), `org.antora`, `org.danilopianini.cpd` (pre-existing inert/commented-out), `org.shipkit.shipkit-auto-version` (swapped in task 4).
- [x] 3.3 Confirm `antora { }`, `tasks.named('antora') { ... }`, and `wrapper { }` blocks are untouched and still present at the end of root `build.gradle` — confirmed, root is now 41 lines total.
- [x] 3.4 Confirm `settings.gradle`'s `pluginManagement.plugins` versions still match what each `buildSrc` convention plugin and each module now needs. Removed the now-unused `org.shipkit.shipkit-auto-version` entry (task 4.1); no new entry was needed for `com.palantir.git-version` — like spotless/baseline/errorprone/nullaway/metadata, it's applied only from inside a buildSrc precompiled script via `pluginManager.apply(...)`, resolved through buildSrc's own classpath dependency, never through a bare `id '...'` in any module's `plugins{}` block, so `pluginManagement` was never the right place for it.

## 4. Version-source swap

- [x] 4.1 In `settings.gradle`'s `pluginManagement.plugins`, remove `id 'org.shipkit.shipkit-auto-version' version '2.1.2'` entirely — no replacement entry needed (see 3.4).
- [x] 4.2 In root `build.gradle`'s `plugins { }` block, remove `id 'org.shipkit.shipkit-auto-version'` entirely — root itself no longer needs a version-source plugin, since nothing reads `rootProject.version` anymore (every project computes its own via `percolate.base-conventions`); confirmed GAV `com.palantir.gradle.gitversion:gradle-git-version:5.0.0` (and plugin marker `com.palantir.git-version:...:5.0.0`) via the Gradle Plugin Portal's own `maven-metadata.xml`/POM, not guessed.
- [x] 4.3 In `percolate.base-conventions.gradle` (task 1.2), wire `project.version` from `versionDetails()`: exactly-on-tag resolves to `lastTag` bare, otherwise (including zero tags) resolves to `"${lastTag}-SNAPSHOT"` — per design.md D4, **using `isCleanTag` rather than `commitDistance == 0`** (see 4.4).
- [x] 4.4 Verify locally: with this repo's current zero git tags, confirmed the build does not fail — but the first implementation (`commitDistance == 0`) was WRONG: probing `versionDetails()`'s actual fields showed `lastTag=ec9411f commitDistance=0 isCleanTag=false` with zero tags, i.e. `commitDistance` is 0 in the no-tag fallback too, so it produced a bare (non-SNAPSHOT) version, failing the spec's "No tags exist in the repository" scenario. Fixed to key off `isCleanTag` instead; re-verified `./gradlew :annotations:properties -q | grep version:` now shows `ec9411f-SNAPSHOT`. design.md D4 updated with this correction.

## 5. Validate the restructuring alone (before touching cache/Isolated Projects flags)

- [x] 5.1 Run `./gradlew check --no-configuration-cache` at the repo root and confirm it is green across every module — this validates tasks 1-4 are behavior-preserving independent of the configuration-cache/Isolated-Projects work in tasks 6-7. `BUILD SUCCESSFUL in 2m 52s`, 126 actionable tasks (119 executed, 7 up-to-date).

## 6. Enable configuration cache

- [x] 6.1 Uncomment `org.gradle.configuration-cache=true` in `gradle.properties`.
- [x] 6.2 Run `./gradlew help` (property now on by default) and `./gradlew projects` (forces full multi-project configuration, not just root) — both `BUILD SUCCESSFUL`, "Configuration cache entry stored", zero problems. Confirms the `com.palantir.git-version` swap actually resolved the original `shipkit-auto-version` blocker.
- [x] 6.3 Run `./gradlew check` and confirm it succeeds with configuration cache on. `BUILD SUCCESSFUL in 1s`, 126 actionable tasks (12 executed, 114 up-to-date from the prior `--no-configuration-cache` run's outputs), fresh configuration cache entry stored.

## 7. Enable Isolated Projects and fix forward

- [x] 7.1 Add `org.gradle.unsafe.isolated-projects=true` to `gradle.properties`.
- [x] 7.2 Run `./gradlew projects` (forces full multi-project configuration) with Isolated Projects enabled. First run reported 13 problems — one per module, all `"Project ':<module>' cannot access 'Project.pluginManager' functionality on another project ':'"`, traced to `com.palantir.git-version`'s own `apply()` reaching into the root project unconditionally (see design.md D4 for the full finding and the fix: replaced with a hand-rolled `providers.exec()` computation in `percolate.base-conventions`, no third-party plugin involved). After the fix: `BUILD SUCCESSFUL`, zero problems, "Configuration cache entry stored"; `./gradlew :annotations:properties -q` confirms `version: ec9411f3-SNAPSHOT` still resolves correctly.
- [x] 7.3 Run a full `./gradlew check` with Isolated Projects enabled. Reported 3 new problems, all identical shape: `"Project ':<processor|spi|strategies-builtin>' cannot access 'Project.buildscript' functionality on another project ':'"`, traced to `info.solidsoft.pitest`'s `PitestPlugin.apply()` unconditionally checking root's legacy `buildscript{}` for an old-style `pitest` config entry (confirmed by decompiling `gradle-pitest-plugin-1.19.0.jar` — the latest available release, no fix to upgrade to). Unlike the git-version issue, this one has no reasonable hand-rolled replacement (mutation testing is substantial, genuinely relied-upon tooling, not a few lines of git-describe logic) — see design.md D6. **Decision, confirmed with the change requester: ship without Isolated Projects enabled.** `org.gradle.unsafe.isolated-projects=true` removed from `gradle.properties` (left commented out with the reasoning inline); everything else (buildSrc convention plugins, configuration cache, version-source fix) stays, fully verified working. proposal.md/design.md/specs updated to reflect this narrowed, accepted final scope.

## 8. Final validation and commit

- [x] 8.1 Run `./gradlew check` and confirm it is green (configuration cache on by default per task 6, Isolated Projects off per task 7.3's outcome). `BUILD SUCCESSFUL`, 126 actionable tasks, fresh configuration cache entry stored.
- [x] 8.2 Run `./gradlew antora` and confirm the docs site build still succeeds (it depends on several modules' `integrationTest`/`compileTestJava` tasks — a good cross-module smoke test that convention-plugin wiring reaches every module that needs it). `BUILD SUCCESSFUL`, "Site generation complete!".
- [ ] 8.3 Commit the change with `/commit-commands:commit`.

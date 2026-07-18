## 1. Root build.gradle restructuring

- [x] 1.1 Replace the `pluginManager.withPlugin('maven-publish') { ... }` block's imperative `pluginManager.apply 'io.github.sgtsilvio.gradle.metadata'` with a cascade from the primary plugin: `pluginManager.withPlugin('io.github.sgtsilvio.gradle.maven-central-publishing') { pluginManager.apply 'io.github.sgtsilvio.gradle.metadata' }`.
- [x] 1.2 Move the `description = '...'` line and the `metadata { readableName = ...; license { apache2() }; developers { ... }; github { ... } }` block into a new, separate `pluginManager.withPlugin('io.github.sgtsilvio.gradle.metadata') { ... }` block.
- [x] 1.3 Remove the explicit `pluginManager.apply 'io.github.sgtsilvio.gradle.maven-central-publishing'` and `pluginManager.apply 'signing'` lines from root — both are applied transitively by the module-declared primary plugin now.
- [x] 1.4 Replace `useInMemoryPgpKeys(findProperty('signingKey') as String, findProperty('signingPassword') as String)` with `useGpgCmd()`; keep the existing `required { gradle.taskGraph.allTasks.any { it.name.contains('MavenCentral') } }` block unchanged, moved into a new top-level `pluginManager.withPlugin('signing') { signing { required {...}; useGpgCmd() } }` block.
- [x] 1.5 Rework `withPlugin('java')` and `withPlugin('java-platform')` so each only registers its `publishing { publications { maven(MavenPublication) { from components.X } } }` (and, for `java`, `java { withJavadocJar(); withSourcesJar() }`) — both nested under `pluginManager.withPlugin('maven-publish') { ... }`.
- [x] 1.6 Add a single shared `signing { sign publishing.publications.maven }` call — nested as `pluginManager.withPlugin('signing') { ... ; pluginManager.withPlugin('maven-publish') { signing { sign publishing.publications.maven } } }` — replacing the two duplicated `signing { sign ... }` calls previously inside the `java`/`java-platform` branches.
- [x] 1.7 Re-read the resulting publishing section top to bottom and confirm no `pluginManager.apply` call remains for `maven-publish`, `signing`, or `io.github.sgtsilvio.gradle.maven-central-publishing` (only `io.github.sgtsilvio.gradle.metadata` is still cascaded via `pluginManager.apply`, from the primary plugin's block).

## 2. Module plugin declarations

- [x] 2.1 `annotations/build.gradle`: replace `id 'maven-publish'` with `id 'io.github.sgtsilvio.gradle.maven-central-publishing'`.
- [x] 2.2 `processor/build.gradle`: replace `id 'maven-publish'` with `id 'io.github.sgtsilvio.gradle.maven-central-publishing'`.
- [x] 2.3 `spi/build.gradle`: replace `id 'maven-publish'` with `id 'io.github.sgtsilvio.gradle.maven-central-publishing'`.
- [x] 2.4 `reactor/build.gradle`: replace `id 'maven-publish'` with `id 'io.github.sgtsilvio.gradle.maven-central-publishing'`.
- [x] 2.5 `reactor-blocking/build.gradle`: replace `id 'maven-publish'` with `id 'io.github.sgtsilvio.gradle.maven-central-publishing'`.
- [x] 2.6 `bom/build.gradle`: replace `id 'maven-publish'` with `id 'io.github.sgtsilvio.gradle.maven-central-publishing'`.
- [x] 2.7 `percolate/build.gradle`: replace `id 'maven-publish'` with `id 'io.github.sgtsilvio.gradle.maven-central-publishing'`.
- [x] 2.8 `percolate-javapoet/build.gradle`: replace `id 'maven-publish'` with `id 'io.github.sgtsilvio.gradle.maven-central-publishing'`.
- [x] 2.9 `strategies-builtin/build.gradle`: replace `id 'maven-publish'` with `id 'io.github.sgtsilvio.gradle.maven-central-publishing'` (missed in the original task breakdown — caught via `grep -rn "id 'maven-publish'"` still matching after 2.1-2.8).
- [x] 2.10 `grep -rn "id 'maven-publish'" --include=build.gradle .` from repo root and confirm zero remaining matches.

## 3. CI workflow reconciliation

- [x] 3.1 Re-inspect the uncommitted `.github/workflows/release.yml` diff (GPG-agent import step already added, `signingKey`/`signingPassword` env vars already removed) and confirm it needs no further edits now that `build.gradle` uses `useGpgCmd()` — confirmed consistent, YAML validated with `python3 -c "import yaml; yaml.safe_load(...)"`.
- [x] 3.2 `grep -rn "signingKey\|signingPassword" .github/ *.md openspec/specs/ 2>/dev/null` (excluding this change's own artifacts) and remove/update any other lingering reference to the retired in-memory-key properties — only hits are `openspec/specs/maven-central-publishing/spec.md` (main spec, updated by this change's own delta and synced on archive, not hand-edited mid-implementation) and `openspec/changes/archive/2026-07-12-automate-release-publishing/**` (immutable historical record, left untouched); no other lingering references found.

## 4. Validation

- [x] 4.1 Run `./gradlew :annotations:publishToMavenLocal` (or another plain `java-library` module) locally. Original expectation (signing skipped for a non-`MavenCentral` task) was disproven: `useGpgCmd()` treats a signatory as "configured" independent of `required { }`, so `signMavenPublication` ran and attempted a real signature against the local GPG agent's key, failing only on pinentry (no TTY in this environment) — see design.md D5/Risks for the corrected understanding, confirmed accepted as intended by the change requester. Wiring itself validated: `generatePomFileForMavenPublication`/`generateMetadataFileForMavenPublication`/jar/sourcesJar/javadocJar all succeeded before the sign step, proving the publication and plugin cascade are correctly configured.
- [x] 4.2 Run `./gradlew :processor:tasks --all` (or equivalent) and confirm `publishToMavenCentral`-related tasks and the `maven` publication both exist as before — confirmed: `publishToMavenCentral`, `publishMavenPublicationToMavenCentral`, `uploadMavenCentralBundle`, `signMavenPublication`, and the `maven` publication itself all present under `processor`.
- [x] 4.3 Run `./gradlew check --no-configuration-cache` at the repo root and confirm it is green across every module (per project convention — global config-cache is known to break shipkit's `git describe --tags`) — `BUILD SUCCESSFUL in 2m 10s`, 119 actionable tasks (43 executed, 76 up-to-date).
- [x] 4.4 Commit the change with `/commit-commands:commit` — committed as `89777d2` (implementation).

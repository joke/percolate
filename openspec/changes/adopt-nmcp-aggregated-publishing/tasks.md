## 1. Plugin declarations (settings.gradle, root build.gradle)

- [x] 1.1 Add `com.gradleup.nmcp.settings` (1.6.1) to `settings.gradle`'s `pluginManagement.plugins` block
- [x] ~~1.2 Add `io.freefair.maven-central.validate-poms` (9.5.0) to `settings.gradle`'s `pluginManagement.plugins` block~~ — reverted (see section 6): the plugin doesn't check dependency versions, only POM metadata, so it was dropped from scope entirely
- [x] 1.3 Remove `io.github.sgtsilvio.gradle.maven-central-publishing` from `settings.gradle`'s `pluginManagement.plugins` block
- [x] 1.4 Apply `com.gradleup.nmcp.settings` in `settings.gradle`'s top-level `plugins { }` block
- [x] 1.5 Add the `nmcpSettings { centralPortal { ... } }` block in `settings.gradle`: `publishingType = "AUTOMATIC"`, credentials bound to the existing `mavenCentralUsername`/`mavenCentralPassword` Gradle properties via `providers.gradleProperty(...)` — configuration-time wiring confirmed correct (`./gradlew help` succeeds); live credential exchange only verifiable on a real release
- [x] 1.6 Remove `id 'io.github.sgtsilvio.gradle.maven-central-publishing' apply false` from root `build.gradle`
- [x] 1.7 (discovered during implementation) `com.gradleup.nmcp.settings` failed the build with "some projects have the same name" — `rootProject.name = 'percolate'` collides with the `:percolate` module's own project name (intentional, no per-module artifactId prefix). Fixed via `nmcpAggregation { allowDuplicateProjectNames.set(true) }` in root `build.gradle` (the settings-level `nmcpSettings{}` extension has no such property; it lives on the `NmcpAggregationExtension` the settings plugin registers on the root project — confirmed by decompiling `nmcp-1.6.1.jar`)

## 2. Module build.gradle updates

- [x] 2.1 `annotations/build.gradle`: replace `id 'io.github.sgtsilvio.gradle.maven-central-publishing'` with `id 'maven-publish'`
- [x] 2.2 `processor/build.gradle`: same replacement
- [x] 2.3 `spi/build.gradle`: same replacement
- [x] 2.4 `strategies-builtin/build.gradle`: same replacement
- [x] 2.5 `reactor/build.gradle`: same replacement
- [x] 2.6 `reactor-blocking/build.gradle`: same replacement
- [x] 2.7 `percolate/build.gradle`: same replacement
- [x] 2.8 `bom/build.gradle`: same replacement
- [x] 2.9 `lib/javapoet/build.gradle`: same replacement

## 3. Convention plugin restructuring (buildSrc/percolate.conventions.gradle)

- [x] 3.1 Change the `io.github.sgtsilvio.gradle.metadata` cascade trigger from `withPlugin('io.github.sgtsilvio.gradle.maven-central-publishing')` to `withPlugin('maven-publish')`
- [x] 3.2 Change the `signing` plugin application (and its `useGpgCmd()`/`required { }` config) to cascade from `withPlugin('maven-publish')` instead of the removed plugin id
- [x] 3.3 Update the signing `required { gradle.taskGraph.allTasks.any { it.name.contains(...) } }` gate: change the matched substring from `'MavenCentral'` to `'Aggregation'`; verified against the real task graph (`./gradlew tasks --all`) — `publishAggregationToCentralPortal`/`publishAggregationToCentralSnapshots` are the actual task names
- [x] ~~3.4 Apply `io.freefair.maven-central.validate-poms` cascaded from `withPlugin('maven-publish')`~~ — reverted (see section 6)
- [x] ~~3.5 Wire the plugin's `ValidateMavenPom` tasks into `check`~~ — reverted (see section 6)
- [x] 3.6 Confirmed the existing `publications { maven(MavenPublication) { from components.java / components.javaPlatform } }` block and `withJavadocJar()`/`withSourcesJar()` wiring is otherwise unchanged

## 4. CI workflow update

- [x] 4.1 In `.github/workflows/release.yml`, remove the `./gradlew generateMetadataFileForMavenPublication` step
- [x] 4.2 Replace `./gradlew publishToMavenCentral` with `./gradlew publishAggregationToCentralPortal`
- [x] 4.3 Confirm the GPG import step and `mavenCentralUsername`/`mavenCentralPassword` env vars are unchanged
- [x] 4.4 No extra validation step added ahead of publish: `validatePomFiles` (from `io.freefair.maven-central.validate-poms`) was tried and reverted per section 6; the `publish` job goes straight from GPG import/setup-gradle to `./gradlew publishAggregationToCentralPortal`, relying on Central Portal's own validation plus the new atomic-aggregation guarantee

## 5. Local verification

- [x] 5.1 Full `./gradlew publishToMavenLocal` can't be exercised end-to-end in this sandbox (no working `gpg`/`gpg-agent` identity for `useGpgCmd()` — pre-existing, unrelated to this change, the old plugin had identical signing wiring). Verified instead: `generatePomFileForMavenPublication` succeeds for every module, and `publishToMavenLocal -x signMavenPublication` assembles jar/sources/javadoc/POM correctly for everything except the two modules whose publication needs the `.asc` signature files that only the excluded sign task produces (expected, not a defect)
- [x] 5.2 Inspected `reactor`'s and `reactor-blocking`'s generated POMs: after the `compileOnly` fix (section 6), `reactor-core` is entirely absent from both POMs' dependency lists (not just correctly versioned) — zero `dependencyManagement` occurrences in either file
- [x] 5.3 Ran `./gradlew tasks --all | grep -i central` — confirmed `publishAggregationToCentralPortal` and `publishAggregationToCentralSnapshots` are the real task names, exactly as assumed in tasks 1.5/3.3/4.2; no correction needed

## 6. Regression check: does validate-poms actually catch today's bug class — reverted after investigation

- [x] 6.1 Wired `io.freefair.maven-central.validate-poms` in (tasks 1.2/3.4/3.5) and reintroduced today's exact defect in `reactor/build.gradle` (`implementation platform(project(':dependencies'))` + unversioned `implementation 'io.projectreactor:reactor-core'`)
- [x] 6.2 Ran `./gradlew :reactor:validatePomFileForMavenPublication` — it **passed** despite the reproduced POM having zero `<version>` for `reactor-core` and a dangling `dependencyManagement` import of the unpublished `:dependencies` project. Decompiled `io.freefair.gradle.plugins.maven.central.ValidateMavenPom.check()` (from `maven-plugin-9.5.0.jar`) and confirmed it only checks POM metadata completeness (`groupId`/`artifactId`/`version`/`name`/`description`/`url`/`licenses`/`developers`/`scm`) — it never inspects the `<dependencies>` list at all
- [x] 6.3 Reported the finding to the user; decided (their call) to drop `io.freefair.maven-central.validate-poms` from scope entirely rather than keep a plugin whose stated justification was false. Reverted tasks 1.2, 3.4, 3.5, 4.4's original form, and the corresponding proposal/design/spec claims
- [x] 6.4 Closed the actual defect class architecturally instead: moved `io.projectreactor:reactor-core` from `implementation` to `compileOnly` in `reactor/build.gradle` and `reactor-blocking/build.gradle` (it's a peer dependency — consumers already depend on it themselves), removing it from the published dependency list entirely rather than relying on any validation gate to catch a missing version

## 7. Final verification

- [x] 7.1 Ran `./gradlew check` — initially failed (`:spi:compileTestJava`: "code generation failed: reactor/core/publisher/Mono"), root-caused to `spi`'s own `testAnnotationProcessor project(':reactor')` no longer transitively getting `reactor-core` now that it's `compileOnly` in `:reactor` (the exact consumer-facing risk flagged in design.md, surfacing internally). Fixed by adding `testAnnotationProcessor platform(project(':dependencies'))` + `testAnnotationProcessor 'io.projectreactor:reactor-core'` to `spi/build.gradle`. Full `./gradlew check` now passes clean, no violations

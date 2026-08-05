## 1. Strand 1 — release-please manifest mode

- [x] 1.1 Confirm the current release version: `git tag --sort=-v:refname | head -1` yields `v1.2.0`; the manifest seed is that value without the `v` prefix
- [x] 1.2 Create `release-please-config.json` at the repository root with exactly one package `.`, `release-type` `simple`
- [x] 1.3 Create `.release-please-manifest.json` at the repository root seeded `{ ".": "1.2.0" }`
- [x] 1.4 Remove the `release-type: simple` input from the `release-please` step in `.github/workflows/release.yml`
- [x] 1.5 Verify no other `release-please` configuration remains inline in either workflow

## 2. Strand 1 — CI full history

- [x] 2.1 Add `fetch-depth: 0` to the `actions/checkout` step of the `build` job in `.github/workflows/build.yml`
- [x] 2.2 Audit every `actions/checkout` step in `release.yml` that precedes a Gradle invocation and add `fetch-depth: 0` where missing
- [x] 2.3 Confirm no remaining job configures the Gradle build from a shallow checkout

## 3. Strand 1 — versioning plugin

- [x] 3.1 Add `io.github.joke.conventional-version:io.github.joke.conventional-version.gradle.plugin:2.1.0` to `buildSrc/build.gradle`
- [x] 3.2 Add a `plugins { id 'io.github.joke.conventional-version' }` block at the top of `buildSrc/src/main/groovy/percolate.conventions.gradle`
- [x] 3.3 Delete the two `providers.exec` git-describe blocks (`isCleanTag`, `describeAlways`) and the `version =` ternary from the convention plugin
- [x] 3.4 Verify `./gradlew :spi:properties | grep '^version:'` reports `1.2.1-SNAPSHOT` (21 commits past `v1.2.0`, highest type `fix`)
- [x] 3.5 Verify every module applying `maven-publish` resolves a non-`unspecified` version
- [x] 3.6 Verify the configuration cache is not invalidated by version resolution: run `./gradlew help` twice and confirm the second run reuses the cached configuration

## 4. Strand 1 — plugin version declaration

- [x] 4.1 Remove the `com.diffplug.gradle.spotless`, `net.ltgt.errorprone`, `net.ltgt.nullaway` and `io.github.sgtsilvio.gradle.metadata` entries from `settings.gradle`'s `pluginManagement` block
- [x] 4.2 Confirm the six remaining entries (CPD, Antora, pitest, Shadow, Lombok, nmcp.settings) are each still applied from a real `plugins { }` block
- [x] 4.3 Confirm NullAway now resolves only from `buildSrc`; kept at `3.0.0` (the version actually in effect) — bumping to `3.1.0` is a separate change
- [x] 4.4 Verify no plugin appears in both `pluginManagement` and `buildSrc/build.gradle`

## 5. Strand 1 — gate

- [x] 5.1 Run `./gradlew check` and confirm it is green before starting strand 2, so a later bisect lands in one strand or the other

## 6. Strand 2 — generator wiring

- [x] 6.1 Remove the `includeBuild('../jspecify')` block and its `dependencySubstitution` from `settings.gradle`
- [x] 6.2 Add `io.github.joke.jspecify:processor:1.0.0` to `dependencies/build.gradle` (note the group is `io.github`, not the `com.github` the substitution was masking)
- [x] 6.3 Replace the unversioned `com.github.joke.jspecify:processor` line in `percolate-smoke/build.gradle` with the correct coordinate
- [x] 6.4 Decide where the `annotationProcessor`/`testAnnotationProcessor` wiring lives — the convention plugin under an existing `pluginManager.withPlugin('java')` gate, or per module — and record the reason in the change
- [x] 6.5 Wire the processor for both source sets across every module that compiles Java
- [x] 6.6 Confirm the processor coexists with Dagger and Lombok on the processorpath without a `-Werror` warning

## 7. Strand 2 — the round-visibility experiment

- [x] 7.1 Build a fixture mapper with a `@Nullable` source member crossing to a non-null target member and no declared default value, in a package with a checked-in `@NullMarked` `package-info.java`; capture the generated mapper source as the baseline
- [x] 7.2 Delete that package's `package-info.java` so the mark is generated instead, rebuild, and capture the generated mapper source
- [x] 7.3 Diff the two. Identical output means percolate observes the generated mark; a missing `requireNonNull` guard means it resolves `UNKNOWN` and does not
- [x] 7.4 Record the result in `design.md` under Open Questions and update the `null-marking` delta spec's round-visibility requirement to match what was observed
- [x] 7.5 From the result, fix the conversion scope: all source sets if marks are observed, otherwise only those percolate's own processor does not compile

## 8. Strand 2 — conversion

- [x] 8.1 Delete the boilerplate `package-info.java` files in the cleared scope (up to 44 files across `annotations`, `processor`, `spi`, `strategies-builtin`, `reactor`, `reactor-blocking`, `percolate-smoke`, both `main` and `test`)
- [x] 8.2 Leave `strategies-builtin/src/test/java/io/github/joke/percolate/docs/mapannotation/package-info.java` untouched — it is deliberately `@NullUnmarked` and already carries its explanatory comment
- [x] 8.3 No per-file comments added — retention is stated by rule in the spec, not repeated in 19 files
- [x] 8.4 Verify generated mapper sources are byte-identical before and after conversion for at least one module in each of `strategies-builtin`, `reactor` and `processor`
- [x] 8.5 Confirm NullAway still reports the same results, i.e. that the marks genuinely reached the compilation

## 9. Optional — Antora version attribute

- [x] 9.1 Decided AGAINST: root `build.gradle` has no `conventionalVersion` extension, so it would hand-parse JSON — more code than the one `git describe` it removes
- [x] 9.2 N/A — 9.1 not taken

## 10. Verification

- [x] 10.1 Run `./gradlew check` and confirm it is green. NEVER continue if there are violations
- [ ] 10.2 Confirm the version resolves correctly from a fresh full clone, and fails as designed from a shallow one (`git clone --depth 1`)
- [ ] 10.3 Inspect the first `release-please` PR after this lands and confirm it proposes `1.2.1`, matching the build's `1.2.1-SNAPSHOT`, before merging it
- [ ] 10.4 Commit the completed spec with `/commit-commands:commit`

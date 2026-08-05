## Why

Two build-configuration facts in this repository are currently transcribed by hand, and both have a
released tool that derives them instead.

**The version lies.** `percolate.conventions.gradle` computes `project.version` from `git describe`,
which cannot read commits and so never bumps. Merge a `feat:` after `v1.2.0` and the build publishes
`v1.2.0-5-gabc1234-SNAPSHOT` while `release-please` is going to cut `1.3.0`. The snapshot coordinate
names a release that will never exist, and it changes on every commit, so nothing can depend on it.
`io.github.joke.conventional-version:2.1.0` predicts the number `release-please` will publish, from
the same files `release-please` reads.

**The null-marking is transcribed 45 times.** JSpecify has no altitude above `package` at which to
state "this whole codebase is null-marked", so the policy is copied into one `package-info.java` per
package. 44 of percolate's 45 are a single annotation line and nothing else.
`io.github.joke.jspecify:processor:1.0.0` generates them.

Both are Gradle build wiring, and both were validated first in the `jspecify` repository, whose
convention plugin is this one with the versioning already migrated. Doing them together means one
pass over `buildSrc`, `settings.gradle` and the CI workflows rather than two.

## What Changes

### Version derivation

- Apply `io.github.joke.conventional-version` from the `plugins {}` block of
  `percolate.conventions.gradle`, replacing the two `providers.exec` git-describe calls and the
  `version = isCleanTag ? ... : "...-SNAPSHOT"` ternary.
- **BREAKING (coordinates):** published snapshot coordinates change shape, from
  `v1.2.0-5-gabc1234-SNAPSHOT` to `1.3.0-SNAPSHOT`. Anything pinning a current snapshot coordinate
  stops resolving. No released coordinate changes.
- Migrate `release-please` to **manifest mode**: add `release-please-config.json` and
  `.release-please-manifest.json` at the repository root, seeded to the current release, and remove
  the inline `release-type: simple` input from `release.yml`. The plugin requires manifest mode and
  fails the build rather than guessing.
- Add `fetch-depth: 0` to every CI job that resolves a version, including the `check` job in
  `build.yml`, which currently has none. A shallow clone is now a hard build failure rather than a
  silent fallback.

### Plugin version declaration

- Remove the four dead `pluginManagement` entries from `settings.gradle` — `spotless`, `errorprone`,
  `nullaway`, `metadata`. The convention plugin applies all four with `pluginManager.apply(id)`,
  which reads the descriptor from `buildSrc`'s classpath and never consults `pluginManagement`.
- This resolves a live disagreement: `settings.gradle` declares NullAway `3.1.0` while
  `buildSrc/build.gradle` declares `3.0.0`, and `3.0.0` is what actually runs.
- The remaining six entries stay — they are applied from real `plugins {}` blocks in the root build
  script and in modules, so `pluginManagement` is load-bearing for them.

### Null-marking

- Add `io.github.joke.jspecify:processor:1.0.0` as `annotationProcessor` **and**
  `testAnnotationProcessor`; declare its version in `dependencies/`, never in a module.
- Delete the hand-written `package-info.java` files whose only content is `@NullMarked`, replacing
  them with generated equivalents.
- Retain hand-written `package-info.java` as the opt-out mechanism. The one deliberate
  `@NullUnmarked` package (`docs/mapannotation`) is preserved untouched.
- **Gated on verification.** A Filer-created `package-info` is not entered until the round after it
  is written, so percolate's own processor — which resolves nullness by reading
  `PackageElement.getAnnotationMirrors()` — may observe an unmarked package in the compilation where
  the mark is being generated, resolving `UNKNOWN` where the hand-written file gave `NON_NULL`. The
  deletion is scoped by what an explicit experiment shows, not by assumption. Packages where
  percolate's processor also runs are only converted if the experiment clears them.

### Removed

- The composite `includeBuild('../jspecify')` and its `dependencySubstitution` block from
  `settings.gradle`, together with the `com.github.joke.jspecify:processor` coordinate it was
  masking. The real group is `io.github.joke.jspecify`.

## Capabilities

### New Capabilities

- `null-marking`: the repository-wide JSpecify null-marking policy — that `@NullMarked` is generated
  rather than transcribed, that a hand-written `package-info.java` is the opt-out, and the
  annotation-processing-round constraint governing where generated marks may be relied upon.

### Modified Capabilities

- `release-versioning`: version is derived from conventional commits against the `release-please`
  manifest instead of from `git describe`; `release-please` moves to manifest mode; version-resolving
  CI jobs require full git history.
- `isolated-projects-build`: the single `buildSrc` convention plugin gains the versioning plugin, and
  a rule is added that a project plugin's version is declared in exactly one place.

## Impact

**Build configuration** — `buildSrc/build.gradle` (new marker dependency),
`buildSrc/src/main/groovy/percolate.conventions.gradle` (version block replaced),
`settings.gradle` (composite build removed, four dead entries removed), `dependencies/build.gradle`
(processor version), `percolate-smoke/build.gradle` and every module wiring the new processor.

**CI** — `.github/workflows/build.yml` (`fetch-depth: 0`), `.github/workflows/release.yml`
(manifest mode). New repository-root files `release-please-config.json` and
`.release-please-manifest.json`.

**Source tree** — up to 44 `package-info.java` deletions across `annotations`, `processor`, `spi`,
`strategies-builtin`, `reactor`, `reactor-blocking` and `percolate-smoke`, in both `main` and `test`
source sets. The exact set is determined by the round-visibility experiment.

**Dependencies** — adds `io.github.joke.jspecify:processor:1.0.0` to the annotation processor path
of modules that compile Java, alongside the existing Dagger and Lombok processors. Adds
`io.github.joke.conventional-version` to the `buildSrc` buildscript classpath; the plugin declares no
runtime dependencies.

**Downstream consumers** — none for released artifacts. Consumers tracking snapshots must update the
coordinate they pin.

**Teams** — single-maintainer repository; no cross-team coordination required. The one external
dependency is that `release-please` must keep working across the manifest-mode switch, which is
verified by the first release after the change lands.

**Risk** — the irreversible surface is release plumbing: a wrong manifest seed makes `release-please`
cut the wrong number, and a Maven Central release cannot be withdrawn. The manifest seed is checked
against the current tag before the first post-change release.

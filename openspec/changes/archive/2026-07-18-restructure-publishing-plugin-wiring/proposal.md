## Why

Root `build.gradle`'s publishing wiring is gated on the wrong plugin and duplicates config. Every publishable module declares `maven-publish` directly, and root force-applies `io.github.sgtsilvio.gradle.metadata`, `io.github.sgtsilvio.gradle.maven-central-publishing`, and `signing` from inside a `withPlugin('maven-publish')` block via imperative `pluginManager.apply` calls — even though `maven-central-publishing` already applies both `maven-publish` and `signing` itself (verified by decompiling the installed 0.5.0 jar; its own README is stale on this point), making the explicit `signing` apply a redundant second application. This inverts the "primary declares, root cascades additionals reactively" convention used everywhere else in the file (e.g. `net.ltgt.errorprone` → `net.ltgt.nullaway`), and the `publishing { publications { ... } }` + `signing { sign ... }` pair is written out twice verbatim (once per `java`/`java-platform` branch). Separately, an in-flight uncommitted change to `.github/workflows/release.yml` already swapped CI to import a real GPG key into a gpg-agent (`crazy-max/ghaction-import-gpg@v7`) and dropped the `signingKey`/`signingPassword` secrets that used to feed `useInMemoryPgpKeys(...)` — so the Gradle-side signing config is currently stale and a real release would fail signing today.

## What Changes

- **BREAKING** (internal build wiring only, no consumer-facing effect): every publishable module (`annotations`, `processor`, `spi`, `reactor`, `reactor-blocking`, `strategies-builtin`, `bom`, `percolate`, `percolate-javapoet`) replaces `id 'maven-publish'` with `id 'io.github.sgtsilvio.gradle.maven-central-publishing'` as its sole publishing-related plugin declaration.
- Root `build.gradle`: `io.github.sgtsilvio.gradle.metadata` is no longer force-applied inside a `maven-publish` gate; it is cascaded from `maven-central-publishing`'s own `withPlugin` block (`pluginManager.apply`), mirroring the existing errorprone→nullaway pattern, then configured in its own separate `withPlugin('io.github.sgtsilvio.gradle.metadata')` block.
- Root `build.gradle`: the explicit `pluginManager.apply 'signing'` is removed (redundant — `maven-central-publishing` already applies it). `maven-publish` and `signing` configuration both move to purely reactive `withPlugin(id) { ... }` blocks with no `pluginManager.apply` counterpart, since both plugins are guaranteed present transitively.
- Root `build.gradle`: the duplicated `signing { sign publishing.publications.maven }` call (previously written once under `withPlugin('java')` and once under `withPlugin('java-platform')`) collapses to a single call shared by both branches.
- Root `build.gradle`: signing switches from `useInMemoryPgpKeys(findProperty('signingKey'), findProperty('signingPassword'))` to `useGpgCmd()`, matching the already-uncommitted CI change that imports a real key into a gpg-agent instead of passing raw key material through Gradle properties.
- `.github/workflows/release.yml`: verify/finish the in-flight uncommitted diff (GPG-agent import already added, stale secret env vars already removed) so it's consistent with the new `useGpgCmd()` signing config; grep for any other lingering references to the old `signingKey`/`signingPassword` secrets.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `maven-central-publishing`: the "Every published artifact is signed" requirement changes from in-memory PGP key properties (`signingKey`/`signingPassword`) to `useGpgCmd()` delegating to a real GPG agent populated by CI's key-import step. The "Declarative POM metadata" requirement's plugin-wiring detail (metadata applied via a `maven-publish` gate) changes to cascade from `maven-central-publishing` instead — the user-facing outcome (one `metadata {}` block, applied uniformly) is unchanged.

## Impact

- Affected files: root `build.gradle` (publishing section), every publishable module's `build.gradle` (`annotations`, `processor`, `spi`, `reactor`, `reactor-blocking`, `strategies-builtin`, `bom`, `percolate`, `percolate-javapoet`), `.github/workflows/release.yml` (already mid-edit, uncommitted).
- No change to published artifact coordinates, POM content, or the Central Portal upload mechanism itself.
- Local `publishToMavenLocal` now requires a real GPG key available to `gpg`/`gpg-agent` on the developer's machine (no more in-memory-key fallback via Gradle properties) — confirmed acceptable since `required { }` already gates real signing to tasks that mention `MavenCentral`.

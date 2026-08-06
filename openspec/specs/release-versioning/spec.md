# Release Versioning Spec

## Purpose

Defines how percolate's version is computed and how releases are decided and cut. There is no hand-edited version constant, and no derivation from git tag history: `project.version` is computed at Gradle configure time from the same `release-please` manifest and conventional-commit history that `release-please` itself reads, so the build's coordinate names the version `release-please` will publish next rather than describing the last one. `release-please` remains the sole authority that cuts a release — the build only predicts. Because the calculation depends on full commit history, it hard-fails on a shallow clone rather than falling back to a plausible-but-wrong coordinate.

## Requirements

### Requirement: Version derived from conventional commits

`project.version` SHALL be computed at Gradle configure time by the `io.github.joke.conventional-version` plugin, applied from the `plugins { }` block of the `percolate.conventions` convention plugin. It SHALL NOT be computed from a hardcoded string, and SHALL NOT be computed from `git describe`. Every subproject that applies `percolate.conventions` SHALL share the single computed version, matching the single-version model of the build.

The version SHALL be derived from the base version recorded in `.release-please-manifest.json` and the conventional commits since that release, so that it names the version `release-please` will publish next. It SHALL carry no commit hash and no commit counter, so that every commit in the same release range resolves to the same coordinate.

The computation SHALL be configuration-cache compatible. It SHALL hard-fail, rather than fall back to a plausible default, when the repository state cannot support a correct answer — specifically on a shallow clone or when the `release-please` configuration files are absent — because a plausible-but-wrong coordinate reaching a repository is unrecoverable while a failed build is not.

#### Scenario: The release commit yields the bare release version

- **WHEN** the current commit is the release commit for version `1.2.0`
- **THEN** every subproject's `project.version` resolves to `1.2.0`, with no `-SNAPSHOT` suffix

#### Scenario: A feature commit past a release yields the next minor as a snapshot

- **WHEN** the manifest records `1.2.0` and a `feat:` commit has landed since that release
- **THEN** every subproject's `project.version` resolves to `1.3.0-SNAPSHOT`

#### Scenario: A fix commit past a release yields the next patch as a snapshot

- **WHEN** the manifest records `1.2.0` and the highest-ranked conventional type since that release is `fix:`, `perf:`, `revert:` or `deps:`
- **THEN** every subproject's `project.version` resolves to `1.2.1-SNAPSHOT`

#### Scenario: A breaking change yields the next major as a snapshot

- **WHEN** the manifest records `1.2.0` and a `feat!:` commit or a `BREAKING CHANGE:` footer has landed since that release
- **THEN** every subproject's `project.version` resolves to `2.0.0-SNAPSHOT`

#### Scenario: Every commit in a release range shares one coordinate

- **WHEN** two different commits in the same range between releases are built
- **THEN** both resolve to the identical version string, containing no commit hash and no commit counter

#### Scenario: No hardcoded version remains in root build.gradle

- **WHEN** root `build.gradle` is inspected
- **THEN** it contains no literal version string assignment for subprojects

#### Scenario: The convention plugin does not compute the version itself

- **WHEN** `buildSrc/src/main/groovy/percolate.conventions.gradle` is inspected
- **THEN** it contains no `git describe` invocation and no `version =` assignment
- **AND** it applies `io.github.joke.conventional-version` from its `plugins { }` block

#### Scenario: Version computation is configuration-cache compatible

- **WHEN** `./gradlew help --configuration-cache` is run
- **THEN** the build succeeds and no configuration-cache problem is reported for version computation

#### Scenario: A missing release-please configuration fails the build

- **WHEN** either `release-please-config.json` or `.release-please-manifest.json` is absent from the repository root
- **THEN** the build fails naming the missing file, rather than resolving to a default version

#### Scenario: A shallow clone fails the build

- **WHEN** the version is resolved in a checkout without full history and tags
- **THEN** the build fails, rather than falling back to a default version

### Requirement: Conventional-commit-driven release PR

A `release-please` GitHub Actions job SHALL run on every push to `main`, inspecting conventional commits since the last release to open or update a single release pull request containing the next computed version and an updated changelog. Merging that release PR SHALL create a git tag and a GitHub release.

`release-please` SHALL be configured in **manifest mode**: its configuration SHALL live in `release-please-config.json` and `.release-please-manifest.json` at the root of the repository, and the workflow step SHALL declare no `release-type` input. This is what allows the build's version calculation to read the same configuration `release-please` reads, so the two cannot disagree.

`release-please` SHALL remain the sole release authority. The versioning plugin SHALL only calculate: it SHALL create no tag, write no changelog and contribute no task.

#### Scenario: Pending conventional commits update the release PR

- **WHEN** one or more `feat:`/`fix:`/`feat!:`-style commits land on `main` since the last release
- **THEN** the release-please job opens or updates a release PR proposing the next semantic version and changelog entries derived from those commits

#### Scenario: Merging the release PR creates a tag and release

- **WHEN** the open release PR is merged into `main`
- **THEN** a git tag matching the proposed version is created and a corresponding GitHub release is published, and the job reports `release_created`

#### Scenario: Ordinary merges do not create a release

- **WHEN** a commit is merged to `main` that is not the release PR itself
- **THEN** no tag or GitHub release is created; at most the pending release PR is updated

#### Scenario: Configuration lives in the repository, not the workflow

- **WHEN** the `release-please` job in `.github/workflows/release.yml` is inspected
- **THEN** the action step declares no `release-type` input
- **AND** `release-please-config.json` and `.release-please-manifest.json` exist at the repository root

#### Scenario: The build proposes the same version release-please will cut

- **WHEN** the pending release PR proposes version `X` and the build resolves `project.version`
- **THEN** the resolved version is `X-SNAPSHOT`

### Requirement: Single repo-wide version

`release-please` SHALL be configured with the `simple` release type and exactly one package, `.`: one version and one changelog for the whole repository, not independent per-module versions. This SHALL match the build's single-`version`-across-subprojects model.

Because exactly one package is declared, no `component` SHALL be required, and every Gradle project covered by the convention plugin SHALL resolve to that one package's version.

#### Scenario: One version proposed regardless of which modules changed

- **WHEN** commits touching multiple different modules (e.g. `spi` and `processor`) land since the last release
- **THEN** the release PR proposes a single next version covering the whole repository, not one version per touched module

#### Scenario: The configuration declares exactly one package

- **WHEN** `release-please-config.json` is inspected
- **THEN** its `packages` object contains exactly one entry, keyed `.`, with `release-type` `simple`

### Requirement: Version-resolving CI jobs use full git history

Every GitHub Actions job that configures the Gradle build SHALL check out with `fetch-depth: 0`. This includes jobs that only run `check` and publish nothing, because configuring the build resolves the version, and the version calculation fails on a shallow clone by design.

#### Scenario: The check job checks out unshallow

- **WHEN** the checkout step of the `build` job in `.github/workflows/build.yml` is inspected
- **THEN** it declares `fetch-depth: 0`

#### Scenario: All version-resolving checkouts are unshallow

- **WHEN** every `actions/checkout` step across `build.yml` and `release.yml` that precedes a Gradle invocation is inspected
- **THEN** each declares `fetch-depth: 0`

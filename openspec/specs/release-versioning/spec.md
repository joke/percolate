# Release Versioning Spec

## Purpose

Defines how percolate's version is computed and how releases are decided and cut. There is no hand-edited version constant: `project.version` is derived from git tag history at Gradle configure time, and the decision of *when* to cut the next version (and what it is) comes from conventional commits via a `release-please` GitHub Actions job, which is also the sole creator of the git tag that the build later reads.

## Requirements

### Requirement: Version derived from git tags

`project.version` SHALL be computed from git tag history at Gradle configure time (via `org.shipkit.shipkit-auto-version`), not from a hardcoded string in `build.gradle`. Every subproject SHALL share the single computed version, matching the existing single-version model of the build.

#### Scenario: Build reads version from the latest release tag

- **WHEN** the repository's most recent tag is `v1.2.3` and no further commits exist
- **THEN** every subproject's `project.version` resolves to `1.2.3`

#### Scenario: No hardcoded version remains in root build.gradle

- **WHEN** root `build.gradle` is inspected
- **THEN** it contains no literal version string assignment (e.g. `version = '0.1.0-SNAPSHOT'`) for subprojects

### Requirement: Conventional-commit-driven release PR

A `release-please` GitHub Actions job SHALL run on every push to `main`, inspecting conventional commits since the last release to open or update a single release pull request containing the next computed version and an updated changelog. Merging that release PR SHALL create a git tag and a GitHub release.

#### Scenario: Pending conventional commits update the release PR

- **WHEN** one or more `feat:`/`fix:`/`feat!:`-style commits land on `main` since the last release
- **THEN** the release-please job opens or updates a release PR proposing the next semantic version and changelog entries derived from those commits

#### Scenario: Merging the release PR creates a tag and release

- **WHEN** the open release PR is merged into `main`
- **THEN** a git tag matching the proposed version is created and a corresponding GitHub release is published, and the job reports `release_created`

#### Scenario: Ordinary merges do not create a release

- **WHEN** a commit is merged to `main` that is not the release PR itself
- **THEN** no tag or GitHub release is created; at most the pending release PR is updated

### Requirement: Single repo-wide version

release-please SHALL be configured with the `simple` release type: one version and one changelog for the whole repository, not independent per-module versions. This SHALL match the build's existing single-`version`-across-subprojects model.

#### Scenario: One version proposed regardless of which modules changed

- **WHEN** commits touching multiple different modules (e.g. `spi` and `processor`) land since the last release
- **THEN** the release PR proposes a single next version covering the whole repository, not one version per touched module

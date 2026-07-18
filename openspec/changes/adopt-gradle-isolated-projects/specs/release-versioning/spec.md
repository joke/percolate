## MODIFIED Requirements

### Requirement: Version derived from git tags

`project.version` SHALL be computed from git tag history at Gradle configure time (via a `providers.exec()`-based `git describe` computation in the `percolate.base-conventions` convention plugin), not from a hardcoded string in `build.gradle`. Every subproject SHALL share the single computed version, matching the existing single-version model of the build. The computation SHALL be configuration-cache compatible and SHALL NOT hard-fail when no git tag exists in the repository — a commit with no tag history SHALL resolve to a `-SNAPSHOT`-suffixed version instead.

#### Scenario: Build reads version from the latest release tag

- **WHEN** the repository's most recent tag is `v1.2.3` and the current commit is exactly that tag (zero commits since)
- **THEN** every subproject's `project.version` resolves to `1.2.3`

#### Scenario: No hardcoded version remains in root build.gradle

- **WHEN** root `build.gradle` is inspected
- **THEN** it contains no literal version string assignment (e.g. `version = '0.1.0-SNAPSHOT'`) for subprojects

#### Scenario: A commit past the latest tag resolves to a SNAPSHOT version

- **WHEN** the current commit is one or more commits past the most recent tag `v1.2.3`
- **THEN** every subproject's `project.version` resolves to `1.2.3-SNAPSHOT`

#### Scenario: No tags exist in the repository

- **WHEN** the repository has zero git tags
- **THEN** the build does not fail, and `project.version` resolves to a `-SNAPSHOT`-suffixed version derived from the available commit information, without requiring a special-cased command-line flag or disabling configuration cache

#### Scenario: Version computation is configuration-cache compatible

- **WHEN** `./gradlew help --configuration-cache` is run (with or without any git tags present)
- **THEN** the build succeeds and no configuration-cache problem is reported for version computation

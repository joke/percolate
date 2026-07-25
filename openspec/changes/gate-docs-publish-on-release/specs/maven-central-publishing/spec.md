## MODIFIED Requirements

### Requirement: Publish gated strictly on release creation

The CI `publish` job SHALL run only when the `release-please` job reports `release_created`, AND
only after the `deploy` (GitHub Pages docs) job in the same workflow run has succeeded — the
`publish` job's `needs` SHALL include `deploy` in addition to `release-please`. No other trigger —
including ordinary pushes to `main` or an updated-but-unmerged release PR — SHALL cause artifacts to
be built for publication or uploaded to Maven Central. No `-SNAPSHOT` version SHALL ever be
published. A failed docs build or Pages deploy SHALL prevent the Maven Central publish from running,
since a bad docs deploy is trivially recoverable while a completed Maven Central publish is not.

#### Scenario: Ordinary merge does not publish
- **WHEN** a commit merges into `main` without triggering `release_created`
- **THEN** the `publish` job does not run and no artifact is uploaded to Maven Central

#### Scenario: Release PR merge triggers the publish job after docs deploy
- **WHEN** the release-please job reports `release_created = true` for a merged release PR and the
  `deploy` job succeeds
- **THEN** the `publish` job runs `./gradlew publishAggregationToCentralPortal` against the tagged
  commit

#### Scenario: A failed docs deploy blocks the Maven Central publish
- **WHEN** `release_created = true` but the `docs` or `deploy` job fails
- **THEN** the `publish` job does not run and no artifact is uploaded to Maven Central

#### Scenario: No snapshot version is ever published
- **WHEN** any workflow run occurs on a commit that is not a release tag
- **THEN** no task in that run publishes a `-SNAPSHOT`-versioned artifact to Maven Central

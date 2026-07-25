## Why

The Antora user manual currently deploys to GitHub Pages on every push to `main` (gated only on
`./gradlew check` passing). This means the published docs move ahead of the latest published Maven
Central artifact on every merge, potentially advertising in-development behaviour against a release
tag that hasn't shipped yet. Docs publishing should instead be gated on `release-please` actually
cutting a release, the same way `publishAggregationToCentralPortal` already is — and, since a bad
docs deploy is trivially recoverable while a bad Maven Central publish is not, the docs deploy should
run and succeed *before* the Maven Central publish is attempted on the same release.

## What Changes

- Move the `docs` and `deploy` (Pages) jobs out of `.github/workflows/build.yml` entirely — `build.yml`
  keeps only the `check` job, no docs build runs on ordinary `main` pushes or PRs.
- Add `docs` and `deploy` jobs to `.github/workflows/release.yml`, gated on
  `needs.release-please.outputs.release_created` (mirroring the existing `publish` job's gate).
- Both new jobs check out `ref: ${{ needs.release-please.outputs.tag_name }}` — the release tag —
  rather than trusting `main` at pipeline time, consistent with how `publish` already pins its ref.
- Reorder `release.yml` so `publish` (Maven Central) depends on `deploy` (Pages) succeeding first —
  `needs: [release-please, deploy]` — instead of only on `release-please`. If the docs build/deploy
  fails, the irreversible Maven Central publish never runs.
- The Pages `deploy` job's `environment: github-pages`, `permissions: pages: write, id-token: write`,
  and `concurrency: group: pages` configuration move along with it into `release.yml`.

## Capabilities

### Modified Capabilities
- `user-manual`: the "site deploys to GitHub Pages on every push to main" requirement changes —
  the site now deploys only when `release-please` reports `release_created`, checked out at the
  release tag, and runs before the Maven Central publish in the same workflow run.
- `maven-central-publishing`: the "publish gated strictly on release creation" requirement gains an
  ordering constraint — the publish job now also depends on the docs deploy job succeeding, not
  solely on `release_created`.

## Impact

- `.github/workflows/build.yml`: `docs` and `deploy` jobs removed.
- `.github/workflows/release.yml`: `docs` and `deploy` jobs added between `release-please` and
  `publish`; `publish`'s `needs` updated.
- `openspec/specs/user-manual/spec.md`: deploy-trigger requirement rewritten.
- No application code changes; no new dependencies.

## MODIFIED Requirements

### Requirement: The site deploys to GitHub Pages only on release

The repository SHALL define `docs` and `deploy` jobs in `.github/workflows/release.yml` that run
only when the `release-please` job reports `release_created`, building the Antora site and
publishing it to GitHub Pages via `actions/upload-pages-artifact` and `actions/deploy-pages`. No
other trigger — including an ordinary push to `main` that does not cut a release, or an
updated-but-unmerged release PR — SHALL cause the docs site to build or deploy.

The `docs` and `deploy` jobs SHALL check out `ref: ${{ needs.release-please.outputs.tag_name }}`
(the release tag), not the `main` branch HEAD at pipeline time, so the published manual and its
install-snippet version reflect exactly the released commit. The `docs`/`deploy` job pair SHALL
check out full git history (`fetch-depth: 0`) so the latest release tag remains reachable for the
install-snippet version derivation (see "Install snippets advertise the latest released version").

The `deploy` job SHALL grant `pages: write` and `id-token: write` permissions and SHALL run in the
`github-pages` environment with `concurrency: group: pages` (matching its prior configuration in
`build.yml`). The advertised documentation URL SHALL stay consistent across `README.md` and
`.github/settings.yml`.

`.github/workflows/build.yml` SHALL NOT define a `docs` or `deploy` job: no docs build or Pages
deploy SHALL run on a pull request or an ordinary push to `main`.

#### Scenario: Ordinary merge does not build or deploy docs
- **WHEN** a commit merges into `main` without triggering `release_created`
- **THEN** no docs build runs and no Pages deployment occurs

#### Scenario: A release triggers a docs deploy
- **WHEN** the release-please job reports `release_created = true` for a merged release PR
- **THEN** the `docs` job builds the Antora site checked out at the release tag, and the `deploy`
  job publishes it to GitHub Pages

#### Scenario: Workflow has the permissions Pages requires
- **WHEN** the `deploy` job in `release.yml` is inspected
- **THEN** it declares `pages: write` and `id-token: write`, runs in the `github-pages` environment,
  and both `docs` and `deploy` check out with `fetch-depth: 0` at `tag_name`

#### Scenario: The advertised URL is consistent
- **WHEN** `README.md`'s documentation link and `.github/settings.yml`'s `homepage` are compared
- **THEN** both point at the same GitHub Pages base URL

#### Scenario: A failing docs build does not deploy or publish
- **WHEN** the Antora build fails during the `docs` job on a release
- **THEN** the `deploy` job does not run, the previously published site is left untouched, and the
  Maven Central `publish` job does not run either

#### Scenario: build.yml carries no docs job
- **WHEN** `.github/workflows/build.yml` is inspected
- **THEN** it defines no `docs` or `deploy` job; only `check` runs there

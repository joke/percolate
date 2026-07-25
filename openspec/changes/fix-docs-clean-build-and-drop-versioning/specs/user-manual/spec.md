## ADDED Requirements

### Requirement: The site is single-version and unversioned

The user manual SHALL be published as exactly one version — the current state of the default branch — with
no version selector and no version segment in page URLs. `antora-playbook.yml` SHALL source content only
from `HEAD`, and `docs/antora.yml` SHALL declare an unversioned component. The playbook SHALL NOT filter or
aggregate release tags, and the site SHALL NOT be assembled from a `gh-pages` branch, committed HTML, or
any non-git versioning tool.

#### Scenario: Only HEAD is aggregated

- **WHEN** the playbook's content sources are inspected
- **THEN** the sole source is this repository at `HEAD` with `start_path` pointing at `docs`
- **AND** no tag filter is configured

#### Scenario: Release tags do not produce additional versions

- **WHEN** the site is built in a repository that has release tags
- **THEN** exactly one version is produced and no tag appears as a selectable version

#### Scenario: Page URLs carry no version segment

- **WHEN** a built page's URL is inspected
- **THEN** it contains no version path segment between the component name and the page name

### Requirement: Documented generated output survives a build-cache hit

Generated example output that pages `include::` SHALL be produced by a task that declares the directory it
writes as a task output, so that a build-cache hit restores those files rather than silently skipping their
creation. A build in which the producing tasks are satisfied from the build cache SHALL yield the same
complete site as a build in which they execute.

#### Scenario: A cache-satisfied build still produces a complete site

- **WHEN** the tasks that materialise generated doc examples are satisfied from the build cache, with their
  project build directories otherwise absent
- **THEN** the materialised example files are present before the site is generated
- **AND** the site builds successfully with every generated `include::` resolved

#### Scenario: Materialisation directories are declared outputs

- **WHEN** a task that materialises generated documentation examples is inspected
- **THEN** the directory it writes is registered as a declared output of that task

### Requirement: Install snippets advertise the latest released version

The version rendered into the manual's dependency and install snippets SHALL be derived at build time from
the repository's latest release tag, not hand-maintained in a descriptor. It SHALL be the latest released
version rather than the in-development version, so that a snippet never advertises an unpublished artifact.
Where no release tag is reachable, the rendered value SHALL be visibly not a release version rather than a
plausible-looking one.

#### Scenario: The rendered version matches the latest release tag

- **WHEN** the site is built in a repository whose latest release tag is `v1.2.3`
- **THEN** the manual's install snippets render `1.2.3`

#### Scenario: An in-development version is never advertised

- **WHEN** the site is built from a commit past the latest release tag
- **THEN** the install snippets still render the latest released version, not a `-SNAPSHOT` version

#### Scenario: No hardcoded version remains in the component descriptor

- **WHEN** `docs/antora.yml` is inspected
- **THEN** it declares no literal version value for the install-snippet attribute

## MODIFIED Requirements

### Requirement: The user manual builds as an Antora site

The repository SHALL define an Antora documentation component under `docs/` (a `docs/antora.yml`
component descriptor and a `docs/modules/ROOT/` module with `nav.adoc` and `pages/`) plus a root
`antora-playbook.yml`, such that running the Antora site generator produces a static HTML site. A build
that contains an unresolved `include::`, a broken cross-reference, or a missing navigation target SHALL
fail rather than emit a silently incomplete site. The site generator's failure tolerance SHALL be
configured so that a reported problem at warning level or above causes a non-zero exit; reporting a
problem while exiting successfully SHALL NOT be permitted. A build that does not exit successfully SHALL
NOT publish.

#### Scenario: A clean tree builds a site
- **WHEN** the Antora site generator runs against `antora-playbook.yml` on a clean checkout
- **THEN** it exits successfully and emits a site whose landing page and navigation resolve

#### Scenario: A broken include fails the build
- **WHEN** a page references an `include::` target or `xref:` that does not resolve
- **THEN** the Antora build reports the failure and does not exit successfully

#### Scenario: A reported problem cannot pass silently
- **WHEN** the site generator logs a problem at warning level or above
- **THEN** the build exits non-zero rather than emitting a site and reporting success

#### Scenario: A failed docs build does not deploy
- **WHEN** the documentation build does not exit successfully
- **THEN** no site artifact is published to GitHub Pages and the previously published site remains in place

## REMOVED Requirements

### Requirement: Versioning is derived from git refs, not a separate publish tool

**Reason**: Multi-version publishing cannot work with this repository's documentation architecture, and is
withdrawn rather than repaired. Generated example output is materialised into module build directories by
compile tests; `@antora/collector-extension` checks each non-`HEAD` ref out into a fresh worktree that
contains no build outputs and runs no build commands, so every tagged version would silently lose all
generated `include::`s. Making tagged versions correct would require a full Gradle build per tag on every
docs run — roughly 470 tags over three years at the current release cadence — and the alternatives that
avoid that cost all require committing generated sources to git, which is rejected. The requirement was
introduced but never exercised: zero tags are aggregated and the published site is already unversioned, so
withdrawal changes no published behaviour.

**Migration**: Replaced by *The site is single-version and unversioned*. No consumer action is required and
no URLs move — the live site already publishes without a version segment, and it is retaining this
requirement that would have relocated every page under a version path. Readers of older releases are served
by the changelog and by install snippets that name the latest released version.

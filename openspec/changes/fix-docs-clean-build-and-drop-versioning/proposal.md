# Fix docs generation on a clean build, and drop multi-version publishing

## Why

The `docs` job publishes a **broken site, silently**. On a clean runner Antora reports
`target of include not found` for the generated-output snippets and then exits successfully, so the
incomplete site deploys to GitHub Pages. Two independent defects combine:

1. Three doc-e2e specs materialise generated sources into `build/generated-doc-examples/` as an
   **undeclared side effect** of a `Test` task. The `antora` task's `dependsOn` edges are satisfied by a
   build-cache hit, which restores only *declared* outputs — so the task never executes, the directories
   are never created, and every generated `include::` fails to resolve.
2. `user-manual` already requires that "a build that contains an unresolved `include::` … SHALL fail
   rather than emit a silently incomplete site" (`specs/user-manual/spec.md`, *A broken include fails the
   build*). The implementation never honoured it. That is why defect 1 reached production instead of
   failing CI.

Separately, commit `0e0079c7` (this morning, never built or deployed) introduced multi-version docs via
release tags. Investigating the above showed that path cannot work as configured:
`@antora/collector-extension` creates a **fresh worktree per non-`HEAD` ref**, containing no build outputs
and running no `run:` commands — so every tagged version would silently lose all ~30 generated
`include::`s. Making it work would require rebuilding every tag on every docs run: roughly 470 tags over
three years at the current cadence. Committing generated sources to git was considered and rejected.
Since zero tags are aggregated today, the feature is inert and untested, and withdrawing it costs nothing.

While confirming the above, the manual's install snippets were found to advertise
`io.github.joke.percolate:bom:0.1.0` — a version predating the `v1.0.0` and `v1.0.1` releases — from a
hardcoded attribute carrying a "bump alongside a release" comment describing exactly the manual step that
was forgotten.

## What Changes

- **Declare the materialisation outputs.** `build/generated-doc-examples` becomes a declared output of
  `integrationTest` in `reactor`, `processor` and `reactor-blocking`, so a cache hit restores the files
  and a cache miss re-runs the specs. Closes defect 1.
- **Make the `antora` task fail on asciidoctor `ERROR` output**, honouring the existing requirement.
  Closes defect 2, and would have caught defect 1 before deploy.
- **Withdraw multi-version docs.** Remove the `tags:` glob and the `!v1.0.0` / `!v1.0.1` exclusions from
  `antora-playbook.yml`; revert `docs/antora.yml` to an unversioned component. Effectively reverts
  `0e0079c7`. **Not breaking**: the live site is already unversioned (`data-version=""`), so URLs stay at
  `/percolate/<page>.html` — it is *keeping* versioning that would have moved every page to
  `/percolate/main/`.
- **Derive `percolate-version` from the latest git tag** (`git describe --tags --abbrev=0`, leading `v`
  stripped) and inject it as an Antora attribute, replacing the hardcoded value. Removes the manual
  bump step.
- **Align `gradle/actions/setup-gradle` to `v6`** in the `docs` job of `build.yml`, which lagged at `v5`
  while the `build` job and `release.yml` use `v6`.

## Capabilities

### New Capabilities

None. This change corrects and narrows existing documented behaviour.

### Modified Capabilities

- `user-manual`: **Removes** the *Versioning is derived from git refs, not a separate publish tool*
  requirement (multi-version publishing is withdrawn) and replaces it with a single-version requirement.
  **Adds** a requirement that documented generated output is produced by a task with declared outputs, so
  it survives a build-cache hit on a clean runner. **Adds** a requirement that install snippets advertise
  the latest released version, derived rather than hand-maintained. **Adds** a scenario tightening the
  existing build-failure requirement so that reported asciidoctor errors fail the build and block deploy.

## Impact

- `antora-playbook.yml` — content sources lose `tags:` and the tag exclusions.
- `docs/antora.yml` — `version:` returns to unversioned; the hardcoded `percolate-version` attribute is
  removed in favour of build-time injection.
- Root `build.gradle` — the `antora` extension gains an `attributes` entry under `options`
  (`AntoraExtension` converts a map there into `--attribute name=value`), fed by a `providers.exec` git
  derivation matching the configuration-cache-safe pattern already used in `percolate.conventions.gradle`.
  The `antora` task gains failure-on-error behaviour.
- `reactor`, `processor`, `reactor-blocking` build scripts (or the shared convention plugin) — declared
  outputs on `integrationTest`.
- `.github/workflows/build.yml` — action version alignment. Note this changes the docs job's Gradle cache
  key, so the next run is a genuine cache miss and will transiently mask the bug being fixed.
- No production code, no public API, no consumer-visible artifact changes. The user manual is the only
  deliverable affected.
- Single-maintainer repository; no cross-team coordination required. Consumers are affected only in that
  the published install snippets become correct.

## Open Questions

- Does a playbook/CLI `--attribute` hard-beat a component-descriptor value in `docs/antora.yml`? Expected
  yes (CLI attributes are hard-set by default), to be confirmed empirically rather than assumed.
- What should `percolate-version` resolve to when no tag is reachable (shallow or tagless clone)? CI uses
  `fetch-depth: 0`, so tags are present there; a silently wrong version is the failure mode being fixed,
  so the fallback must not look like a valid release.

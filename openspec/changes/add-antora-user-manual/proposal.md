## Why

`README.md` and `.github/settings.yml` already advertise a manual at
`https://joke.github.io/percolate/`, but that URL 404s — no user-facing documentation exists. The only
guidance today is three short module READMEs and Javadoc, scattered across the build. A Java developer who
wants to map beans has no integrated place to learn how to add percolate to a project and write a mapper.
Consumer packaging (the `percolate-bom` platform and the `percolate` starter) has just shipped, so the
consumer surface is finally stable enough to document without immediately rotting.

## What Changes

- Add an **Antora (AsciiDoc) documentation component** under `docs/` (`docs/antora.yml` +
  `docs/modules/ROOT/{nav.adoc,pages,examples}`) that builds into a static site.
- Add `antora-playbook.yml` that aggregates git refs — `HEAD` as the current version now, release tags
  later — so versioning is **native and declarative** (the version dropdown populates itself once tags
  exist; no `mike`, no `gh-pages` branch, no committed HTML).
- Add `.github/workflows/docs.yml`: `mise` → `npx antora` → `actions/upload-pages-artifact` →
  `actions/deploy-pages`, triggered on **every push to `main`**. Pages build source = "GitHub Actions"
  (requires `pages: write` + `id-token: write`; needs full git history/tags via `fetch-depth: 0`).
- Add the Antora CLI and site-generator to `.mise.toml` (Node is already present for the existing
  `npm:` openspec tool — no new language runtime).
- **Author the consumer manual** for a Java bean-mapping developer:
  - Integration (Maven **and** Gradle): BOM + starter + annotations, optional `percolate-reactor`.
  - Quick start: a minimal `@Mapper` interface.
  - Basic mapper structure: which methods are discovered vs skipped.
  - `@Map`: `target` / `source` / `constant` / `defaultValue` and the `UNSET` presence rule.
  - Nested target and source chains (multi-segment paths via getter/record-accessor/field resolution).
  - Collections: which kinds are supported (`List`, `Set`, `Optional`; reactive `Flux`/`Mono` via the
    optional module) and how element mapping composes.
  - Conversion methods (a second single-parameter `@Mapper` method used as a bridge).
  - Default-method conversions (a `default` method on the `@Mapper` interface used as a bridge).
  - Plus an **Extending / SPI** section for strategy authors (sourced from the existing `spi` and
    `strategies-builtin` READMEs).
- **Single-source every code example** via AsciiDoc `include::` from compiling fixtures, so a documented
  snippet cannot drift from the code CI compiles. (The include-bridge mechanism — how fixtures in test
  source sets reach Antora's content catalog — is a design decision, deferred to `design.md`.)
- Add **two new end-to-end fixtures + Spock specs**: a conversion-method mapper and a default-method
  conversion mapper. These back the two manual sections above and close a real coverage gap — method-call
  bridge conversions currently have **unit coverage only** (`MethodCallBridgeSpec`); no compiling
  end-to-end mapper proves the generated code calls a custom or `default` conversion method.

Out of scope (noted for a follow-up): trimming `README.md` and the module READMEs down to pointers once the
manual is canonical.

## Capabilities

### New Capabilities

- `user-manual`: the hosted, versioned Antora user manual — its required topic coverage for the bean-mapping
  audience, the rule that every documented code example is single-sourced from a compiling fixture, the
  Antora build, native git-ref versioning, and deploy-on-push-to-`main` to GitHub Pages.

### Modified Capabilities

- (none) — the two new end-to-end fixtures exercise already-specified behavior
  (`callable-method-discovery`, the `MethodCallBridge` strategy, `type-conversion`); they add e2e
  *coverage*, not new requirements. If the specs phase judges the end-to-end "a `default` method is called
  in generated code" behavior to be under-specified, it may add a scenario to an existing spec at that
  point.

## Impact

- **New files**: `docs/` tree (`antora.yml`, `modules/ROOT/{nav.adoc,pages/*.adoc,examples/}`),
  `antora-playbook.yml`, `.github/workflows/docs.yml`, two end-to-end Spock specs + their mapper fixtures.
- **Edited files**: `.mise.toml` (Antora tools), `.gitignore` (drop the now-vestigial `docs/site/` entry —
  Antora output lands under the already-ignored `build/`). `README.md`'s Documentation link already points
  at the target URL.
- **Repo setting** (one-time, outside the build): GitHub Pages build source = "GitHub Actions". The probot
  `settings.yml` app may not manage this; it is set in repo settings / via API.
- **Audience**: Java developers integrating percolate to map beans; secondarily strategy authors (the
  Extending section). Solo maintainer otherwise — the main operational note is the new CI Pages permissions
  and the one-time Pages enablement.
- **No engine or runtime code changes**; generated mappers are unaffected. The docs workflow is a separate
  job and does not slow `./gradlew check`. The two new e2e specs run inside the normal Gradle test phase.

## Context

Percolate advertises a manual at `https://joke.github.io/percolate/` (in `README.md` and
`.github/settings.yml`) that does not exist. The only documentation is three module READMEs and Javadoc.
The consumer surface (the `percolate-bom` platform and `percolate` starter) has stabilised, so a manual
can be written without immediate rot.

Constraints that shaped this design:

- The build is Gradle + `mise`. `mise` already provisions Java and a Node-backed `npm:` tool (openspec),
  so **Node is already on the box**; Python is not.
- The project is rigorous (errorprone, NullAway, PMD, spotless, baseline) and spec-driven. Documentation
  examples for a code-*generation* tool must not drift from what CI compiles.
- The maintainer prefers AsciiDoc over Markdown.
- The codebase is pre-1.0 (`0.1.0-SNAPSHOT`, zero git tags, no release workflow).
- Existing end-to-end specs build mapper sources as **inline Groovy string lists** compiled via Google
  compile-testing — those strings are not includable into docs.

## Goals / Non-Goals

**Goals:**

- A versioned, hosted Antora (AsciiDoc) manual covering the bean-mapping consumer surface plus an
  Extending/SPI section.
- Every behavioural code example single-sourced from a fixture the build compiles.
- Close the end-to-end coverage gap for method-call-bridge conversions (custom and `default` methods),
  which today have unit coverage only.
- Deploy on every push to `main` with the minimum moving parts.

**Non-Goals:**

- Trimming `README.md` / module READMEs to pointers (follow-up).
- Multi-repo Antora aggregation, a custom UI bundle, or release-tag-driven deploy automation.
- Converting the *existing* inline-string e2e fixtures to files. Only the two new fixtures are authored as
  files; the existing `PersonMapper.java` fixture is reused where it already is a real file.

## Decisions

### D1 — Antora + AsciiDoc, deployed via the Pages artifact flow

Antora over MkDocs Material or Jekyll. Rationale: AsciiDoc is preferred; the Node toolchain already
exists (MkDocs would add Python); Antora's versioning is git-native (no `mike`, no `gh-pages` branch); and
AsciiDoc `include::` with tagged regions pulls real compiling source into pages — decisive for a codegen
tool. Cost accepted: AsciiDoc authoring and Antora's `modules/ROOT/{nav,pages,examples}` ceremony.

Deploy uses `actions/upload-pages-artifact` + `actions/deploy-pages` (Pages source = "GitHub Actions"),
**not** a `gh-pages` branch. A branch with committed HTML is only needed by `mike`, which Antora's native
versioning makes unnecessary.

*Alternatives considered:* MkDocs Material + `mike` (rejected: new Python runtime, imperative versioning,
committed HTML on a `gh-pages` branch); plain Jekyll/`/docs` (rejected: weak theming/search, Markdown,
Ruby).

### D2 — The include-bridge: docs own the example, the test reads it

Antora resolves `include::` only within its component's content catalog; it cannot reach into the
`strategies-builtin` test source set. So **example mappers live as real `.java` files under
`docs/modules/ROOT/examples/`** (Antora's idiomatic place for includable, non-published source), and the
**test compiles the doc-owned file** rather than the reverse.

Mechanism: the new end-to-end Spock specs load each example via compile-testing
`JavaFileObjects.forResource(...)` instead of inline strings; the docs examples directory is registered as
an additional **test resources `srcDir`** for the e2e module so those `.java` files sit on the test
classpath. Pages then `include::example$<file>.java[tags=…]` the same file. One file, two consumers — a
broken example breaks the test, satisfying the single-sourcing requirement.

*Alternatives considered:* keep fixtures in the test tree and stage them into the catalog with the
community `antora-collector` extension or a Gradle copy step (rejected: extra moving parts, and Antora
reaching *out* of its component is the awkward direction); inline snippets (rejected: violates the
single-sourcing requirement we chose Antora for).

*Convention:* behavioural mapper examples are doc-owned `.java` files compiled by an e2e/compile test.
Non-behavioural fragments (Maven `pom.xml`, Gradle `build.gradle` snippets) may be authored directly under
`examples/` and `include::`d without a compile gate.

### D3 — Versioning: start unversioned, transition at first release

`docs/antora.yml` starts as an **unversioned** component (`version: ~`) → clean URLs and a single honest
version while no tags exist. `antora-playbook.yml` wires the tag filter (`tags: ['v*.*.*']`) from the
start; it is inert with zero tags. At the first release, `antora.yml` adopts a concrete version and the
dropdown populates from tags — a config-only transition, no rework of the workflow.

*Alternatives considered:* `version: true` (ref-derived → an ugly `main` version segment now);
hardcoded `version: '0.1'` now (a version segment in the URL before any release exists).

### D4 — Toolchain and output

`.mise.toml` gains the pinned **`npm:antora` umbrella package** (3.1.x), which bundles `@antora/cli` and
`@antora/site-generator` as direct dependencies so the CLI and generator stay co-resolvable. (mise's `npm:`
backend installs each tool entry in isolation; declaring the two Antora packages separately would leave the
CLI unable to resolve the generator — the umbrella avoids that, and puts the `antora` binary on `PATH` so
builds invoke `antora` directly rather than via `npx`.) The playbook output goes to `build/site`, already
covered by the `build/` gitignore; the now-vestigial `docs/site/` entry (a leftover MkDocs convention) is
removed. The remote **default Antora UI bundle** is used — zero UI setup; a custom UI
is a later concern.

## Risks / Trade-offs

- **Antora builds committed git content, not the worktree** → for the current branch Antora uses the
  working tree, so local `antora` preview reflects uncommitted edits on the checked-out branch; other
  versions come from refs. Document the `--fetch` / local-preview note for contributors.
- **Test coupled to the docs examples directory** (D2) → one `sourceSets.test.resources.srcDir` line in
  the e2e module; documented in tasks so it is not mistaken for stray config.
- **First-ever e2e coverage of method-bridge conversions may surface a real engine bug** → that is a
  feature of adding the tests, not of this change. If a genuine bug appears, it is fixed separately; the
  docs change must not paper over a failing example to make the build green.
- **GitHub Pages must be enabled once, manually** (source = GitHub Actions); CI cannot self-enable on a
  fresh repo → call it out in the migration steps; the first workflow run is expected to require it.
- **AsciiDoc translation of Markdown READMEs** → accepted, one-time; the maintainer prefers AsciiDoc.

## Migration Plan

1. Land the Antora skeleton, manual pages, fixtures, e2e specs, workflow, and `.mise.toml`/`.gitignore`
   edits; `./gradlew check` (the two new e2e specs) and a local `antora` build both pass.
2. One-time repo setting: enable GitHub Pages with build source = "GitHub Actions".
3. Merge to `main`; the `docs.yml` workflow deploys; verify the advertised URL resolves.

*Rollback:* the change is additive and isolated to docs/CI plus two test specs — reverting the merge
removes the site and the new tests with no engine impact. A failed deploy leaves the previous site (if
any) untouched.

## Open Questions

- Exact pinned Antora CLI / site-generator versions (resolve at implementation against the current
  release).
- Whether the quick-start and `@Map`/nested-chain pages reuse the existing `PersonMapper.java` fixture or
  introduce dedicated doc-owned example files — a per-page authoring call during implementation, bounded by
  the D2 convention.

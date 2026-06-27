## 1. Spike: collector dir resolution (the structural unknown)

- [x] 1.1 Add `@antora/collector-extension` to `.mise.toml` and register it in `antora-playbook.yml`; with one `scan` entry pointed at one module's example dir, run `antora` locally and confirm whether `scan.dir` resolves relative to the content-source worktree root or the `docs/` start_path
  - **Spike outcome:** a plain `scan.dir` resolves against the content-source **worktree root = repo root** (`url: .`); a dot-prefixed path resolves against the `docs/` start_path. Confirmed by reading `@antora/collector-extension` 1.0.3 (`base: worktreeDir`) and by a probe build that resolved `example$…` from `dir: docs/modules/ROOT/examples`.
- [x] 1.2 Record the outcome and pick the wiring: name modules directly (worktree-relative) or use `../`/a relocated content-source root (start_path-relative) — settle this before moving any fixture
  - **Decision:** name modules directly — `dir: strategies-builtin/src/test/resources/examples`, `dir: reactor/src/test/resources/examples`. The extension-isolation caveat (it can't be a standalone `mise npm:` tool) is **resolved by adopting the `org.antora` Gradle plugin** (task 3.1): its `packages` map installs the collector in Antora's own Node context.

## 2. Opt-in doc-tag generation

- [x] 2.1 Add a boolean `docTags` flag to `ProcessorOptions`, parsed from `-Apercolate.docTags=true`, defaulting to `false`; declare `"percolate.docTags"` in `PercolateProcessor.getSupportedOptions()`
- [x] 2.2 In the generator's printer, bracket each generated method **body** with `// tag::<methodName>[]` / `// end::<methodName>[]` when `docTags` is on; emit nothing when off (in `BuildMethodBodies`, `CodeBlock`-wrapping the body — JavaPoet can't comment between type members, so body-bracketing is the clean printer-level approach; no change to the plan walk or weaving)
- [x] 2.3 Add a generation test (`DocTagsEmissionSpec`, FakeStrategy-driven) asserting tags bracket the body only under the option and the default output carries none

## 3. Adopt the org.antora plugin and invert example ownership via the collector

- [x] 3.1 Adopt the `org.antora` Gradle plugin (managed Node): applied `id 'org.antora' 1.0.0` on the root build (so `url: .` resolves to the repo root), configured `version = '3.1.15'`, `playbook = 'antora-playbook.yml'`, `options = [clean: true, fetch: true]`, `packages = ['@antora/collector-extension': '1.0.3']`; registered the extension in the playbook's `antora.extensions`; removed `npm:antora` from `.mise.toml`. `./gradlew antora` builds the site on Gradle 9.3/Java 25, and the required collector extension loads — proving `packages` installs it in Antora's Node context (isolation finding resolved)
- [x] 3.2 Move the six example mappers from `docs/modules/ROOT/examples/` into `strategies-builtin/src/test/resources/examples/`; update the e2e specs' `forResource`/`generate` paths to the `examples/...` resource prefix; delete the `rootProject.file('docs/.../examples')` `srcDir` from `strategies-builtin/build.gradle`. `:strategies-builtin:integrationTest` green
- [x] 3.3 Added the antora-collector `scan` (plain repo-root `dir: strategies-builtin/src/test/resources/examples`, `into: modules/ROOT/examples`) to `docs/antora.yml`; `./gradlew antora` renders the pages with the same `example$` subpaths (collections page shows the collected `TeamMapper`), confirming the collector reads the worktree

## 4. Single-source the generated output

- [x] 4.1 `DocExamplesEndToEndSpec.generate` now compiles each fixture with `-Apercolate.docTags=true`, asserts the semantic invariant, and materialises the tagged generated source to `build/generated-doc-examples/<category>/<Impl>.java`; a second collector `scan` imports that dir, and `tasks.named('antora') { dependsOn ':strategies-builtin:integrationTest' }` makes the ordering a task edge
- [x] 4.2 Replaced the hand-typed generated-output block in `nested-paths.adoc` with `include::example$nested-paths/ProfileMapperImpl.java[tag=map]`; verified the page renders the real generated body with the tag-directive lines stripped

## 5. Reactive example in the reactor module

- [ ] 5.1 Add a `Flux`/`Mono` example mapper fixture in `reactor/src/test/resources`, a reactor end-to-end spec exercising it, a manual page (and nav entry), and a collector `scan` entry — the example compiles where its atoms live

## 6. Gate the deploy on a green build

- [ ] 6.1 Reconcile `build.yml` and `docs.yml` so the Pages deploy `needs:` a green `./gradlew check`: with the site build now a `./gradlew antora` task, the CI job runs `./gradlew check` then `./gradlew antora` and uploads `build/site`, and the deploy job `needs:` it; carry the `fetch-depth: 0` checkout and `pages: write`/`id-token: write` permissions, and resolve the `build` job-name collision

## 7. Verify

- [ ] 7.1 Confirm the manual is updated: input and output examples are single-sourced, the reactive page is present, no hand-typed generated output remains
- [ ] 7.2 Run `./gradlew check` and verify everything passes; run a local `antora` build and confirm it renders inputs and outputs from real fixtures. NEVER continue if there are violations
- [ ] 7.3 Commit the completed change with `/commit-commands:commit`

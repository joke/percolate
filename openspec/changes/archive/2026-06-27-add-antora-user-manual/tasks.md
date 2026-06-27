## 1. Toolchain and Antora skeleton

- [x] 1.1 Add the pinned `npm:antora` umbrella package (bundles `@antora/cli` + `@antora/site-generator`) to `.mise.toml`; run `mise install` and confirm `antora` is on PATH and no new language runtime is added (Node already present)
- [x] 1.2 Create `antora-playbook.yml`: content source `url: .`, `branches: HEAD`, `tags: ['v*.*.*']`, `start_path: docs`; `output.dir: build/site`; remote default Antora UI bundle
- [x] 1.3 Create `docs/antora.yml` (component `percolate`, title, `version: ~`, nav → `modules/ROOT/nav.adoc`)
- [x] 1.4 Create `docs/modules/ROOT/nav.adoc` and `pages/index.adoc` landing page; verify `npx antora antora-playbook.yml` builds and the landing page + nav resolve
- [x] 1.5 Remove the vestigial `docs/site/` entry from `.gitignore` (Antora output lands under the already-ignored `build/`)

## 2. End-to-end fixtures and specs (include-bridge)

- [x] 2.1 Register `docs/modules/ROOT/examples` as an additional test-resources `srcDir` for the e2e module (`strategies-builtin`) so doc-owned `.java` files reach the test classpath
- [x] 2.2 Author the conversion-method example mapper as `docs/modules/ROOT/examples/.../*.java` — a second single-parameter `@Mapper` method used as a bridge for a nested field — with AsciiDoc `tag::`/`end::` regions
- [x] 2.3 Add `ConversionMethodEndToEndSpec` that loads the example via compile-testing `JavaFileObjects.forResource`; assert generation succeeds and the generated source invokes the conversion method
- [x] 2.4 Author the default-method conversion example mapper as a doc-owned `.java` file (a `default` method used as a bridge) with AsciiDoc tag regions
- [x] 2.5 Add `DefaultMethodConversionEndToEndSpec`; assert generation succeeds and the generated source invokes the `default` conversion method
- [x] 2.6 Run `./gradlew check`; both new specs pass. If a real engine bug surfaces (first-ever e2e coverage of method bridges), stop and report — do not weaken the example to make the build green

## 3. Consumer manual pages

- [x] 3.1 Integration page: add percolate via BOM + starter + annotations (+ optional `percolate-reactor`) for **both Maven and Gradle**
- [x] 3.2 Quick-start page: a minimal `@Mapper` (reuse `PersonMapper.java` or a doc-owned example), `include::`d
- [x] 3.3 Basic mapper structure page: which methods are discovered vs skipped (`@Mapper` abstract methods; defaults/Object/static skipped)
- [x] 3.4 `@Map` page: `target`, `source`, `constant`, `defaultValue`, and the `UNSET` presence rule (empty string is present, not absent)
- [x] 3.5 Nested target and source chains page: multi-segment paths (getter / record-accessor / field resolution), with an `include::`d example
- [x] 3.6 Collections page: name the supported kinds (`List`, `Set`, `Optional`, reactive `Flux`/`Mono` via the optional module) and show how element mapping composes
- [x] 3.7 Conversion-methods page: explanation + `include::` of the fixture from 2.2 (tagged regions)
- [x] 3.8 Default-method-conversions page: explanation + `include::` of the fixture from 2.4 (tagged regions)
- [x] 3.9 Wire all consumer pages into `nav.adoc`; `npx antora` build resolves every `include::` and `xref:`

## 4. Extending section

- [x] 4.1 Extending / SPI page for strategy authors, derived from the `spi` and `strategies-builtin` READMEs; add it to `nav.adoc`

## 5. Deploy wiring

- [x] 5.1 Create `.github/workflows/docs.yml`: on push to `main`, checkout with `fetch-depth: 0`, `mise install`, `npx antora`, `actions/upload-pages-artifact`, `actions/deploy-pages`; job permissions `pages: write` + `id-token: write`
- [x] 5.2 Confirm `README.md`'s documentation link and `.github/settings.yml`'s `homepage` point at the same GitHub Pages base URL
- [x] 5.3 Document the one-time manual step: enable GitHub Pages with build source = "GitHub Actions" (CI cannot self-enable)

## 6. Verification

- [x] 6.1 `./gradlew check` is green, including the two new end-to-end specs
- [x] 6.2 Local `npx antora antora-playbook.yml` is green: no unresolved includes/xrefs, exactly one version (no tags yet), nav fully resolves
- [x] 6.3 Run `/opsx:verify` against the specs before archiving

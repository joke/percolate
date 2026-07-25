## 1. Make the failure visible first (D2)

Landing the failure gate before any fix converts the silent bug into a red build and proves the
reproducer works. Do not skip ahead — a cache miss makes the defect invisible.

- [x] 1.1 Add `runtime.log.failure_level: warn` to `antora-playbook.yml`, with a comment recording that
  Antora's default tolerance is `fatal`, so ERROR-level asciidoctor messages previously logged and still
  exited `0`
- [x] 1.2 Reproduce the defect deliberately: run `./gradlew integrationTest --build-cache`, then
  `rm -rf reactor/build processor/build reactor-blocking/build`, then `./gradlew antora --build-cache`
- [x] 1.3 Confirm the reproducer now **fails** (it previously logged `target of include not found` and
  exited successfully), and record which tasks reported `FROM-CACHE`

## 2. Declare the materialisation outputs (D1)

- [x] 2.1 In `reactor/build.gradle`, declare `layout.buildDirectory.dir('generated-doc-examples')` as an
  output of `integrationTest`
- [x] 2.2 Same in `processor/build.gradle`
- [x] 2.3 Same in `reactor-blocking/build.gradle`
- [x] 2.4 Re-run the 1.2 reproducer sequence and confirm it now **passes** — tasks resolve `FROM-CACHE`,
  the example files are restored, and every generated `include::` resolves
- [x] 2.5 Confirm the three directories are populated after a cache-hit build, not merely after a
  cache-miss build (the distinction is the whole point of this change)

## 3. Withdraw multi-version docs (D4)

- [x] 3.1 Remove the `tags:` entry and the `!v1.0.0` / `!v1.0.1` exclusions — and their explanatory
  comment block — from `antora-playbook.yml`
- [x] 3.2 Revert `docs/antora.yml` `version: true` to an unversioned component, restoring a comment that
  states the site is deliberately single-version (do not leave behind the withdrawn multi-version
  rationale)
- [x] 3.3 Rebuild the site and confirm pages emit at `/percolate/<page>.html` with no version segment and
  no version selector in the navbar
- [x] 3.4 Confirm this leaves the published URLs unchanged relative to the currently deployed site

## 4. Derive the install-snippet version (D3)

- [x] 4.1 Add a `providers.exec`-based latest-release-tag derivation to root `build.gradle` using
  `git describe --tags --abbrev=0`, stripping the leading `v`, mirroring the configuration-cache-safe
  pattern in `buildSrc/src/main/groovy/percolate.conventions.gradle:7-19`
- [x] 4.2 Define the no-tag fallback as a visibly-invalid placeholder (per design: not a plausible release
  version), with `ignoreExitValue = true` so a tagless clone does not hard-fail the build
- [x] 4.3 Wire it through the antora extension as
  `options = [clean: true, fetch: true, attributes: ['percolate-version': <derived>]]`
- [x] 4.4 Remove the hardcoded `percolate-version: 0.1.0` attribute and its "bump alongside a release"
  comment from `docs/antora.yml`
- [x] 4.5 Rebuild and confirm `getting-started.adoc` and `extending.adoc` render `1.0.1` (the latest
  release tag) rather than `0.1.0`, in both the Maven and Gradle snippets
- [x] 4.6 Confirm no unresolved `{percolate-version}` reference is rendered on any page
- [x] 4.7 Verify configuration cache stays clean: `./gradlew antora --configuration-cache` reports no
  configuration-cache problem for the new derivation

## 5. CI hygiene (D5)

- [x] 5.1 Confirm `gradle/actions/setup-gradle` is `v6` in the `docs` job of `.github/workflows/build.yml`
  (already edited in the working tree), matching the `build` job and `release.yml`
- [ ] 5.2 Note in the PR description that this changes the docs job's cache key, so the first run after
  merge is a genuine cache miss and will pass for reasons unrelated to the fix — a second run on `main` is
  the real verification

## 6. Spec alignment

- [x] 6.1 Run `/opsx:sync` to apply the `user-manual` delta to `openspec/specs/user-manual/spec.md`
- [x] 6.2 Manually update the `user-manual` spec's **Purpose** paragraph, which is prose rather than a
  requirement and so is not carried by the delta: it currently describes a "hosted, **versioned** Antora
  user manual" that "derives its versions natively from this repository's git refs" — both clauses are now
  false
- [x] 6.3 Confirm no other spec still asserts multi-version documentation behaviour

## 7. Verification

- [x] 7.1 Run `./gradlew check` and confirm it passes with zero violations. NEVER continue if there are
  violations. (If configuration cache interferes, retry with `--no-configuration-cache` and record why.)
- [x] 7.2 Run `./gradlew antora` from a clean tree and confirm the site builds with zero warnings — the
  `failure_level: warn` gate from 1.1 makes any warning a build failure
- [x] 7.3 Run `openspec validate fix-docs-clean-build-and-drop-versioning`
- [ ] 7.4 Commit the completed change with `/commit-commands:commit`

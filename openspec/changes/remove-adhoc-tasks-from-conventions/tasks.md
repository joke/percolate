## 1. Remove the scan from the convention plugin

- [x] 1.1 Delete the `tasks.register('checkNoJavadoc')` block, its `tasks.named('check') { dependsOn javadocScan }` wiring, and the surrounding explanatory comment from `buildSrc/src/main/groovy/percolate.conventions.gradle`
- [x] 1.2 Delete the `ext.percolatePublicApi = false` default and its comment from the same file
- [x] 1.3 Confirm the enclosing `pluginManager.withPlugin('java')` block is removed entirely if `checkNoJavadoc` was its only content, rather than left as an empty block

## 2. Remove the per-module opt-in

- [x] 2.1 Delete `percolatePublicApi = true` and its comment from `annotations/build.gradle`
- [x] 2.2 Delete `percolatePublicApi = true` and its comment from `spi/build.gradle`
- [x] 2.3 Grep the whole repository (excluding `build/` and `openspec/changes/archive/`) for `percolatePublicApi`, `checkNoJavadoc`, and `no-javadoc` and confirm zero live hits

## 3. Verify

- [ ] 3.1 Run `./gradlew check --no-configuration-cache` and confirm it is green with no `checkNoJavadoc` task in the executed task list
- [x] 3.2 Confirm no module's `build.gradle` gained a replacement scan, and that no other task in the build reads files under `src/**` (compilation tasks and configured analysers excepted)
- [x] 3.3 Confirm `annotations` and `spi` javadoc, and every internal module's sources, are untouched by this change (`git diff --stat` shows only Gradle files)

## 4. Spec sync

- [x] 4.1 Run `openspec` sync for this change and confirm the `internal-javadoc-policy` REMOVED/ADDED requirements and the `isolated-projects-build` ADDED requirement land in the main specs
- [x] 4.2 Hand-edit the `## Purpose` paragraph of `openspec/specs/internal-javadoc-policy/spec.md` — a delta cannot reach it — replacing "Enforcement scans source text, because the policy is about source text and no available static-analysis rule can express it." with a sentence stating the policy is unenforced by the build and why
- [ ] 4.3 Archive the change once `check` is green and the specs read correctly

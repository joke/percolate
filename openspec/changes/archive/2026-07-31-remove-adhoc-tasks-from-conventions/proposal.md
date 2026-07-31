## Why

`percolate.conventions` registers `checkNoJavadoc`, a hand-written Gradle task that reads every
file under a module's `src/main/java` and fails the build when it finds a `/**` block. Scanning
source text is a static analyser's job, not the build script's: a `doLast` grep has no
suppression mechanism, no report, no IDE integration, no tests of its own, and it lifts one
module's policy problem into the configuration of every module. The build system is not to
perform source-code checks — the objection is to the mechanism, so relocating the scan into the
modules that want it is not an acceptable answer either.

## What Changes

- Remove `tasks.register('checkNoJavadoc')` and its `check` wiring from
  `buildSrc/src/main/groovy/percolate.conventions.gradle`, along with the
  `ext.percolatePublicApi` flag that existed only to feed it.
- Remove the now-unused `percolatePublicApi = true` declarations from `annotations/build.gradle`
  and `spi/build.gradle`.
- Add **no** replacement task — not in the convention plugin, not in any module's own
  `build.gradle`. No analyser can express the rule (PMD's `CommentRequired` cannot address a
  package-private method and PMD 7 no longer exposes comments to a custom XPath rule), so the
  javadoc confinement reverts to an authoring and review convention with no automated gate.
- State the general prohibition in the build spec so it does not have to be re-litigated: Gradle
  configuration SHALL NOT read, grep, or parse project source files; source-code analysis is
  delegated to real analysers (spotless, PMD, CodeNarc, Error Prone, ArchUnit) or is not
  automated at all.
- The javadoc *policy itself* is unchanged — javadoc still belongs only to `annotations` and
  `spi`, and existing sources stay as they are. Only its enforcement mechanism is withdrawn.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `internal-javadoc-policy`: the "javadoc confinement is enforced by the build" requirement is
  removed. The policy becomes a convention carried by authoring and review, with no build-time
  gate and no per-module public-API declaration.
- `isolated-projects-build`: adds a requirement that the build performs no source-code checks of
  its own — no Gradle task reads or parses `src/**`; that work belongs to configured analysers.

## Impact

- `buildSrc/src/main/groovy/percolate.conventions.gradle` — ~35 lines removed (task, ext flag,
  and their explanatory comments).
- `annotations/build.gradle`, `spi/build.gradle` — one line and its comment removed from each.
- `check` no longer depends on `checkNoJavadoc` in any module; the
  `build/reports/no-javadoc.txt` output disappears.
- No production Java or Groovy source changes; no published artifact changes; no test changes.
- Accepted risk: a javadoc block reintroduced into an internal module will not fail the build.
  This is the deliberate trade — a narrow, cosmetic, easily reverted regression is preferred over
  keeping a hand-rolled source scan in the build.

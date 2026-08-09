## Why

Percolate's architecture rules are hand-rolled: a central `architecture-tests` module holds one Spock spec
that imports **every** module's classes off a fat `testImplementation` classpath, filters that union back
down with three brittle `ImportOption` path-string predicates, and wraps each rule in `when:`/
`notThrown(AssertionError)` boilerplate. One of those filters (`-test-fixtures.jar`) exists only because an
earlier spelling silently let a class through until an unrelated rule happened to catch it — the mechanism
fails open. A second mechanism sits beside it: the "no code outside the engine reaches a `processor`
internal" rule is a `testFixtures`-published rule builder that three strategy modules each copy into their
own near-identical `EngineEncapsulationSpec`.

`com.netflix.nebula.archrules` 1.3.1 replaces both mechanisms with one: rules are authored once as
`ArchRulesService` implementations in a rules **library** module, and a **runner** plugin evaluates them
per source set inside each consuming module, against that module's own classes. The union classpath, the
path-string filters, and the duplicated specs all disappear.

## What Changes

- Add the `com.netflix.nebula.archrules.library` and `com.netflix.nebula.archrules.runner` plugins
  (version 1.3.1, declared in `settings.gradle` `pluginManagement`), configured centrally in
  `percolate.conventions.gradle` via the established `pluginManager.withPlugin` pattern. No module is named
  in `buildSrc`; each module opts in by declaring the plugin id in its own `plugins {}` block.
- Convert `architecture-tests` into a standalone rules library: rules move from
  `src/test/groovy/**/*Spec.groovy` into `src/archRules/java/`, published as an `archRules` variant and
  discovered in consuming modules by `ServiceLoader`. `architecture-tests` drops its `java-test-fixtures`
  plugin and its `testImplementation` dependency on all six sibling modules.
- Apply the runner to every module with production sources — `annotations`, `spi`, `processor`,
  `strategies-builtin`, `reactor`, `reactor-blocking`, `test-foundation`, `percolate`. It is **not** applied
  to `architecture-tests` (self-apply), `lib:javapoet` (its patched `com.palantir.javapoet.MethodSpec`
  overlay would trip the relocation rule against its own package), or the `java-platform` modules
  `bom`/`dependencies` (no source sets).
- Set `failureThreshold('LOW')` centrally. The plugin's default is to fail on **nothing**; every percolate
  architecture rule is load-bearing, so leaving the default would silently downgrade the whole suite to
  advisory console output.
- Delete the three duplicated `EngineEncapsulationSpec` copies (`strategies-builtin`, `reactor`,
  `reactor-blocking`) and the `EncapsulationRules` testFixture they share. The rule becomes one library rule
  that runs everywhere the runner is applied.
- Delete the three `ImportOption` predicates (`notShadedLib`, `notTestFixtures`,
  `DO_NOT_INCLUDE_TESTS`). Source-set selection replaces them: `skipSourceSet('test')` and
  `skipSourceSet('testFixtures')`, and shaded `lib` classes are never in a module's own source-set output.
- **BREAKING (rule semantics)**: the unused-protected-method rule (`@VisibleForTesting`) gains an exemption
  for the published `spi` surface. Per-source-set import cannot see a subclass in a downstream module, and
  the only two methods that rely on that — `spi.Container#containerOf` and `spi.Container#wrapNullness`,
  whose sole overriders are `ArrayContainer` and `OptionalContainer` in `strategies-builtin` — are genuine
  template-method extension points on a published API. The exemption reuses the shape the no-static rule
  already carries for the same surface. Every other cross-module base class (`spi.Accessor`,
  `spi.Conversion`) is unaffected: their protected members are abstract or already annotated.
- **BREAKING (rule removal)**: the package-acyclicity rule is deleted. Its slice pattern
  (`io.github.joke.percolate.(*)..`) yields one slice per module, so it never checked intra-module cycles;
  and every cross-module pair it did check is already forbidden by the four inter-module no-edge rules plus
  the Gradle module DAG. It enforces nothing the suite does not already enforce.
- Add an `archRulesTest` suite covering each ported rule with a deliberately-violating fixture, so a
  mis-transcribed package string fails loudly instead of passing vacuously. Today's rules have no such
  coverage — a false negative is invisible.
- Apply `com.netflix.nebula.archrules.aggregate` to the root project for a consolidated console report.

## Capabilities

### New Capabilities
- `architecture-rule-distribution`: how architecture rules are authored once, published as a library
  variant, discovered by `ServiceLoader`, evaluated per source set in each consuming module, gated on a
  failure threshold, and covered by their own negative tests.

### Modified Capabilities
- `module-boundaries`: the enforcement mechanism changes from a central union-classpath Spock suite plus
  duplicated per-module specs to per-source-set evaluation of one shared rule library; the
  unused-protected-method requirement gains a published-`spi` exemption; the acyclicity requirement is
  removed.
- `test-coverage-tooling`: `architecture-tests`' Spock suite moves from `src/test` to `src/archRulesTest`,
  so its `SpockConfig.groovy` moves with it.

## Impact

- **Build**: `settings.gradle` (three plugin versions), `buildSrc/src/main/groovy/percolate.conventions.gradle`
  (two new `withPlugin` blocks), root `build.gradle` (aggregate plugin), and the `plugins {}` block of every
  module that opts in.
- **Modules**: `architecture-tests` restructured; `strategies-builtin`, `reactor`, `reactor-blocking` each
  lose a spec and a `testFixtures` dependency.
- **Dependencies**: new external plugin `com.netflix.nebula.archrules` 1.3.1 — published 2026-08-06, three
  days before this proposal. Young for a load-bearing guard; the mitigating factor is that a total plugin
  failure surfaces as a `check` failure rather than as silently-unenforced rules, provided
  `failureThreshold('LOW')` is set.
- **Not affected**: no production code changes. `pitest` enrollment is unchanged (`architecture-tests` has
  never applied it). Isolated Projects stays off, already blocked on `info.solidsoft.pitest`; the aggregate
  plugin's root-project reach is the same shape and does not make that worse.
- **Teams**: solo-maintained repository; no cross-team coordination required. Third-party strategy authors
  are unaffected — no published API changes.

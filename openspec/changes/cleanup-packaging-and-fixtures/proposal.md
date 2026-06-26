## Why

Two cleanup threads left over from earlier work, bundled into one change. **Packaging:** every published percolate POM imports the internal `:dependencies` version platform (modules use `platform(project(':dependencies'))` on `api`/`implementation`), so consumers inherit a `<dependencyManagement>` import of internal third-party constraints — and it forced us to publish that platform as `percolate-dependencies` just so the POMs resolve; separately, the consumer smoke is a standalone publish-to-`mavenLocal` build that can only fail *after* the artifact exists, pollutes `~/.m2`, and is not a `check` gate. **Test harness:** `TypeUniverse` carries fossils from the removed jqwik layer (`pool()`/`TYPE_POOL`/`anyConstruct()`/`INSTANT`/`LOCAL_DATE_TIME`), kept alive only by a self-justifying `TypeUniverseSpec` size assertion, and every fixture is resolved by a fully-qualified **string** (`TypeUniverse.element('…fixtures.AddressFluent')`) so the IDE reports fixtures as unused and renames silently break them.

## What Changes

- Make published POMs **self-contained**: pin concrete versions (Gradle `versionMapping`) and keep the internal platform off published variants, so no `<dependencyManagement>` import leaks; **stop publishing** `percolate-dependencies`.
- **Replace** the standalone `percolate-smoke` build with a **normal in-build module** (`annotationProcessor project(':percolate')` + `compileOnly project(':annotations')`) that runs the generated mapper and is wired into `./gradlew check` — a one-pass gate against the exact build outputs. POM-coordinate validation is deliberately *not* re-added as a committed test; it belongs in a deferred release-time staging gate.
- **Prune the TypeUniverse fossils** (`pool()`, `TYPE_POOL`, `anyConstruct()`, `INSTANT`, `LOCAL_DATE_TIME`) and the `TypeUniverseSpec` assertion that only existed to count them.
- Add **`TypeUniverse.of(Class<?>)`** and migrate fixture references from `element('…fixtures.X')` strings to `of(X.class)`, so fixtures are typed references the IDE tracks and renames stay safe. `element(String)` stays for JDK/dynamic names.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `consumer-packaging`: self-contained-POM guarantee (no internal-platform import; `percolate-dependencies` not published); the `mavenLocal` smoke requirement removed and replaced by an in-build smoke module gated by `check`.
- `expansion-test-harness`: `TypeUniverse` gains `of(Class<?>)` for rename-safe type resolution; unused fossils removed.
- `builtin-strategy-unit-tests`: fixture types are resolved via `TypeUniverse.of(<Fixture>.class)` rather than fully-qualified strings.

## Impact

- **Build files:** publication convention gains `versionMapping`; the internal platform moves off published configs; `:dependencies` drops `maven-publish`; `percolate-smoke` becomes a subproject in `settings.gradle`.
- **Test fixtures:** `TypeUniverse` (spi testFixtures) loses dead members and gains `of(Class)`; ~7 strategy specs migrate fixture refs to `of(<Fixture>.class)`; `TypeUniverseSpec` drops the `pool()` assertion.
- **Published artifacts:** percolate-* POMs carry concrete versions and no `percolate-dependencies` import; one fewer published artifact.
- **Spec:** revises requirements introduced by the archived `introduce-consumer-packaging` change and the earlier `expansion-test-harness` / `builtin-strategy-unit-tests` specs.

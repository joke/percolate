## Why

Percolate has no consumer-facing distribution story, and its test suite quietly violates the module boundaries the build graph declares. The engine only ships with usable strategies because `processor` carries `runtimeOnly project(':strategies-builtin')`; that single edge both forces consumers to assemble internal modules by hand and leaks the builtins onto `processor`'s own test classpath, so the engine module's end-to-end specs assert builtin-specific output (`Integer.valueOf`, `new Foo(...)`). The result: refactors regress in ways only caught downstream, because no module systematically compile-tests its *own* atoms and the engine is tested through a classpath it should not see. This change establishes a published packaging surface, makes the processor↔builtins separation enforced by the dependency graph, and re-slices the end-to-end tests so each module owns its own — sharing one compile harness, with the engine tested against fakes.

## What Changes

- Add a **`percolate-bom`** consumer version platform managing versions of percolate's own published artifacts (annotations, starter, processor, spi, strategies-builtin, reactor, reactor-blocking). Distinct from the internal `:dependencies` platform that pins third-party versions.
- Add a **`percolate`** convenience starter — an aggregator that puts `processor` + `strategies-builtin` on the consumer's `annotationProcessor` classpath via one coordinate. Percolate is compile-time-only (generated code references only `javax.annotation.processing.Generated` from `java.base`; `@Mapper`/`@Map` are `CLASS`-retention), so the starter is an annotation-processor bundle, not a runtime library.
- Add **maven-publish** to every publishable module and validate the real consumer path with `publishToMavenLocal`.
- Add a **`percolate-smoke`** standalone consumer build that resolves `percolate` by GAV from `mavenLocal()` and compiles a fixed mapper — black-box, depending only on published artifacts, never on internal modules or the test harness.
- **BREAKING (internal):** Remove `runtimeOnly project(':strategies-builtin')` from `processor`. The starter owns bundling; the engine no longer compiles, ships, or sees the builtins.
- Add a **`test-foundation`** module housing the World-2 compile harness (`compileMapper(...) -> Compilation`) and a reusable `FakeStrategy`. It depends on `processor` + compile-testing and is consumed by every strategy module's e2e tests and by `processor`'s own engine tests.
- **Re-slice the end-to-end specs to match module boundaries:** relocate the builtin-output e2e specs from `processor` into `strategies-builtin` (which gains `testImplementation` on `processor` + `test-foundation`); keep genuinely engine-level specs in `processor` but rewrite them against `FakeStrategy` so the engine suite is builtin-free. `reactor`/`reactor-blocking` migrate onto the shared harness.

## Capabilities

### New Capabilities
- `consumer-packaging`: how percolate is published and assembled for downstream developers — the BOM, the starter that supplies engine + builtins via `annotationProcessor`, the compile-time-only footprint contract, maven publication, and the black-box smoke build.
- `e2e-test-architecture`: where end-to-end (compile) tests live and how they are written — the shared `test-foundation` compile harness, per-module ownership (each strategy module compile-tests its own atoms), the engine tested against a `FakeStrategy` rather than real builtins, and the rule that the engine module declares no edge to any strategy module.

### Modified Capabilities
<!-- None. The `processor` capability describes processor behaviour, not packaging/bundling or test placement; no behavioural requirement of the processor changes. -->

## Impact

- **New modules:** `bom`, `percolate` (starter), `test-foundation`; plus a standalone `percolate-smoke` build. `settings.gradle` realises `// include 'bom'` and adds the starter + test-foundation.
- **`processor`:** `runtimeOnly project(':strategies-builtin')` removed; its builtin-dependent e2e specs relocated to `strategies-builtin`; remaining engine specs rewritten against `FakeStrategy`.
- **`strategies-builtin`:** gains `testImplementation project(':processor')` + `project(':test-foundation')` + compile-testing; becomes the home of builtin compile-tests.
- **`reactor` / `reactor-blocking`:** e2e specs migrate onto the `test-foundation` harness (already depend on `processor` in test scope).
- **Publishing:** maven-publish coordinates under group `io.github.joke.percolate` on annotations, spi, processor, strategies-builtin, reactor, reactor-blocking, starter, BOM.
- **Consumers:** recommended setup `annotationProcessor 'io.github.joke.percolate:percolate'` + `compileOnly 'io.github.joke.percolate:percolate-annotations'`, versions via `platform('io.github.joke.percolate:percolate-bom')`.

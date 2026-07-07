## Why

`type-query-seam` narrowed the `processor` and `spi` unit specs onto a mockable `ResolveCtx` seam but stopped at the strategy boundary: ~15 `strategies-builtin` unit specs still drive a real javac session through `ResolveCtxBuilder`/`TypeUniverse`, and the module has no mutation testing. Wiring pitest onto that suite as-is would repeat the `harden-engine-as-library` mistake — chasing coverage on logic trapped behind `private` (e.g. `MethodCallBridge`'s hidden BFS subtype-distance walk, the reason its spec carries 16 real-javac references). This change finishes the cutover on *clean* code, so mutation testing measures genuinely isolable units. It is Thread A, upstream of `features-as-documentation` (Thread B); the 32 existing `strategies-builtin` e2e compile-tests remain the safety net across it.

## What Changes

- **Decompose the Bucket-2 strategies** that hide real substance behind `private` into individually-mockable collaborators: extract `MethodCallBridge`'s subtype-distance walk (`subtypeDistance`/`bfsDistance`/`Pair`) to a `SubtypeDistance` collaborator (**spiked first** — decide library-primitive vs keep-BFS); extract `GetterPathResolver`'s getter / boolean-`is` matching predicates; light widen-and-inline on `ConstructorCall` and `NullnessCrossing`.
- **Migrate ALL `strategies-builtin` unit specs to a mock-based `ResolveCtx` seam** (the existing `ListContainerSeamSpec` pattern): every unit spec stubs seam questions and asserts `OperationSpec` metadata only — no javac; `TypeMirror` stays an opaque, never-stubbed token.
- **Delete the javac test scaffolding**: `ResolveCtxBuilder`, `ResolveCtxBuilderSpec`, `FixtureTypeSmokeSpec`, and the shape fixtures. The shared-static **`TypeUniverse` fixture (+ `TypeUniverseSpec`) is deleted** — once strategies-builtin migrates, its only code consumers are gone. The per-spec **`PrivateTypeUniverse` survives** for the `processor` compiler-boundary specs (`discover`, `AssembleMapperType`), which genuinely need real mirrors.
- **Widen the two co-enforced ArchUnit guards** (Rule A: no `private` methods; Rule B: ≤15 methods per class; `DECOMPOSED_ENGINE_PACKAGES` in `ModuleBoundariesSpec`) from the engine packages to `spi.builtins`, so the cleanliness can't regress.
- **Wire pitest** mutation testing onto the now-clean `strategies-builtin` unit suite (threaded, history-plugin, floor-gated), mirroring the spi rollout.

**Non-goals (explicit):** NO new integration tests and NO fat unit suites — codegen-output correctness stays Thread B's e2e=doc job. Containers / `StreamMap` / scalar strategies (Bucket 1) are already clean and only gain the thin mock-seam detection spec. `types()`/`elements()` are **not** removed from the seam (the production impl still delegates through them); their full removal is a later phase.

## Capabilities

### New Capabilities
<!-- none: this is test-infrastructure and a light source decomposition; no new user-facing behaviour -->

### Modified Capabilities
- `builtin-strategy-unit-tests`: unit specs become mock-based over the `ResolveCtx` seam. The single-substrate-javac invariant, the `ResolveCtxBuilder` helper, and the shape-fixture requirements are **removed**; metadata-only assertion and per-strategy presence/naming are **retained and reinforced** ("no javac in any builtin unit spec"). Adds that the builtin unit suite is decomposed into individually testable units and is mutation-tested under threaded pitest.
- `module-boundaries`: Rule A (engine-internal methods are never private) and Rule B (class size ceiling) widen their scope to also cover `spi.builtins`.
- `expansion-test-harness`: the shared-static `TypeUniverse` fixture is **deleted** (its last consumers were strategies-builtin specs); only `PrivateTypeUniverse` remains, scoped to the `processor` `discover`/`AssembleMapperType` boundary specs.
- `expansion-strategy-spi`: correct the `types()`/`elements()` retention scenario — they remain for the production real-javac impl's default-method delegation, no longer as a transitional `strategies-builtin` test bridge.

## Impact

- **Source (light decompose):** `strategies-builtin` — `MethodCallBridge` (+ new package-private `SubtypeDistance`), `GetterPathResolver`, `ConstructorCall`, `NullnessCrossing`. Containers and scalar strategies unchanged.
- **Tests:** ~15 `strategies-builtin` unit specs rewritten mock-only; `ResolveCtxBuilder`/`ResolveCtxBuilderSpec`/`FixtureTypeSmokeSpec` and shape fixtures deleted; shared-static `TypeUniverse`/`TypeUniverseSpec` deleted from `spi` testFixtures (dangling `{@link TypeUniverse}` javadoc in `PrivateTypeUniverse` and two `processor` specs re-worded). The 32 e2e compile-tests are unchanged (safety net).
- **Build:** `strategies-builtin/build.gradle` gains the pitest block (+ `pitest-history-plugin` dep); `DECOMPOSED_ENGINE_PACKAGES` in `architecture-tests` widened to `spi.builtins`.
- **SPI surface:** unchanged — `types()`/`elements()` retained on `ResolveCtx`.
- **Teams:** single-maintainer repo (percolate engine/SPI); no external consumers affected.

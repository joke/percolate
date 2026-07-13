## Why

`PrivateTypeUniverse` is the last real-javac substrate on the unit-test path — a per-spec `JavacTask` fixture four `processor` boundary specs stand up to reach their subject. The reason they reach for a compiler is **not** that their logic needs one: it is that four discovery/codegen stages each fuse a *raw-javax read* with *pure decision logic* in one god-method, so the only way to exercise the logic is through a real compiler. This is the same shape `decompose-engine-stages` already fixed for the engine — "the fake was the tax for zero seams." Applying that playbook here lets the pure cores unit-test on plain data (no javac, no fake, no mock-of-dozens) while the thin javax readers are covered by the *existing* compile-based feature-e2e layer (real `CompileResolveCtx`), after which `PrivateTypeUniverse` falls away and the whole `processor` unit path is javac-free and parallel-safe by construction.

## What Changes

- Decompose each of the four boundary stages into a **thin javax reader** (the genuinely compiler-backed leaf) and a **pure core** (the decision logic), following the `decompose-engine-stages` collaborator pattern:
  - `DiscoverMappingsStage` → thin annotation reader (`AnnotationMirror` → raw member strings + carried opaque `AnnotationValue` tokens, `@Map`/`@MapList` FQN classification) + pure directive builder (`Map.UNSET`-sentinel presence, `@MapList` ordering, `MappingDirective` assembly).
  - `DiscoverAbstractMethodsStage` → thin member reader (`getLocalAndInheritedMethods` + `Object` element) + pure method filter (`isAbstract` / `isObjectMethod`).
  - `DiscoverCallableMethodsStage` → thin member indexer (`getAllMembers`) + pure filter/assignability core (single-param / `METHOD` / non-`Object`; return-type `isAssignable`).
  - `AssembleMapperType` → thin javax/codegen leaf (`TypeName.get(mirror)` rendering + `Filer` write) + pure assembly decisions (finality-modifier selection, `extends`-vs-`implements`).
- Rewrite the four unit specs to drive the **pure cores on plain data** — opaque javax tokens passed through, never stubbed (the seam's own discipline). No unit spec constructs a `JavacTask`, and **no `FakeType`/`FakeResolveCtx` is introduced** (fakes are a unit crutch; the thin readers are covered by *real compilation*).
- Confirm/extend the compile-based feature-e2e coverage of the thin readers so the `processor` pitest mutation/line/test-strength floors are **held, not lowered** (the finality switches are already covered by the switches doc-e2e; add targeted `@Mapper` fixtures for any reader branch a real compile does not already exercise).
- **BREAKING (test infra):** delete the `io.github.joke.percolate.spi.test.PrivateTypeUniverse` `testFixtures` export and its `DirectiveFixtures` coupling. `spi/testFixtures` then export no javac-backed type at all.
- Remove stale textual references to the already-deleted `TypeUniverse` type: comments in `build.gradle`, `processor/build.gradle`, `spi/build.gradle`, `strategies-builtin/build.gradle`, and the `spi/README.md` line advertising `TypeUniverse`.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `expansion-test-harness`: ADD a requirement that the discovery stages are decomposed so pure logic unit-tests without javac and the thin javax reader is covered by the compile-based feature-e2e layer (generalizing the existing codegen rule to `discover`); resolve the codegen requirement's "reduced or removed" to *removed*; flip the "`PrivateTypeUniverse` remains for the processor boundary" invariant to "`PrivateTypeUniverse` is deleted".
- `builtin-strategy-unit-tests`: reframe the no-javac prohibition — both former javac fixtures (`TypeUniverse`, `PrivateTypeUniverse`) are now deleted; the requirement stands as a reintroduction guard.

## Impact

- **Production (`processor/src/main`):** four stages decomposed; new pure collaborator types (e.g. a directive builder, a method filter, an assignability/index core, an assembly-decision helper) alongside thin javax readers. Must respect the ArchUnit no-private-method + size-cap guard and the `*Stage`-suffix rule (only `Stage` implementors carry the suffix; collaborators do not).
- **Tests (`processor/src/test`):** `DiscoverAbstractMethodsStageSpec`, `DiscoverMappingsStageSpec`, `DiscoverCallableMethodsStageSpec`, `AssembleMapperTypeSpec` rewritten to drive pure cores on plain data; `DirectiveFixtures` decoupled from the fixture; compile-based e2e fixtures extended where needed to hold the pitest floor.
- **Test fixtures (`spi`):** `PrivateTypeUniverse.groovy` deleted; `spi` `testFixtures` export no javac-backed type.
- **Build/docs hygiene:** stale `TypeUniverse` comments removed from four `build.gradle` files; `spi/README.md` corrected.
- **Specs:** `expansion-test-harness` and `builtin-strategy-unit-tests` deltas.
- **Quality gates:** `./gradlew check` green; `processor` pitest floors held (never lowered); no jqwik reintroduced.
- **Teams affected:** solo maintainer (Joke) — no external API surface changes (internal decomposition, test infra, and build comments only).

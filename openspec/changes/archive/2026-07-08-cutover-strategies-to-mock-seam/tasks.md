## 1. Spike & extract `SubtypeDistance` (go/no-go gate)

- [x] 1.1 Spike the extraction of `MethodCallBridge`'s `subtypeDistance`/`bfsDistance`/`Pair` walk into a package-private `SubtypeDistance` collaborator whose only surface is the `ResolveCtx` seam (`isSameType`/`isAssignable`/`superclassOf`/`isDeclared`); decide **library-primitive (JGraphT) vs keep-BFS** and record the decision in `design.md` Open Questions
- [x] 1.2 Implement `SubtypeDistance` in `spi.builtins` with **no `private` methods** (design D1 litmus); rewire `MethodCallBridge` to delegate to it and remove `subtypeDistance`/`bfsDistance`/`Pair`
- [x] 1.3 Write `SubtypeDistanceSpec` (`@Tag('unit')`, `ResolveCtx = Mock()`, opaque `TypeMirror` tokens): a multi-hop distance, plus the pinned same-type=`0` / non-assignable=`0` finding with a `// FOLLOW-UP:` marker; assert the resulting `OperationSpec` `weight` built by `MethodCallBridge`
- [x] 1.4 **Go/no-go checkpoint** — if the collaborator wants graph access or the extraction is awkward (a `strategies-stay-myopic` red flag), stop and reassess before touching any other class

## 2. Decompose the remaining Bucket-2 strategies

- [x] 2.1 Extract `GetterPathResolver`'s getter / boolean-`is` matching predicates into an individually-testable form (no `private` methods); `GetterPathResolver` delegates through the seam over opaque member tokens
- [x] 2.2 Light widen-and-inline on `ConstructorCall` (inline `resolveTypeElement`; widen/relocate `parameterNames`/`constructorLabel`; keep `buildCodegen` as an `OperationCodegen` value) so no `private` method remains
- [x] 2.3 Light widen-and-inline on `NullnessCrossing` (extract/widen `optionalOf`; widen `requireNonNull`/`coalesce`/`coalesceSpec`) so no `private` method remains
- [x] 2.4 Review every class in `spi.builtins` against the design D1/D5 litmus — each method describable in one sentence without "and", zero `private` methods

## 3. Migrate all `strategies-builtin` unit specs to the mocked seam

- [x] 3.1 Migrate the Bucket-1 container specs (`Array`/`Collection`/`List`/`Set`/`Stream`/`Optional`Container) and `StreamMapSpec`/`DirectAssignSpec`/`ConstantValueSpec` to `ResolveCtx = Mock()` (the `ListContainerSeamSpec` pattern): stub only the seam questions asked, assert `OperationSpec` metadata only, `TypeMirror` opaque tokens
- [x] 3.2 Fold `ListContainerSeamSpec` into a mock-based canonical `ListContainerSpec` (satisfying the presence requirement) and delete `ListContainerSeamSpec`
- [x] 3.3 Migrate `GetterPathResolverSpec`/`FieldPathResolverSpec`/`MethodPathResolverSpec` to the mocked seam (stub `membersOf`/`isMethod`/`isField`/`kind`/`qualifiedName`), keeping the per-resolver scenario coverage and the `Weights.STEP_*` pins
- [x] 3.4 Migrate `PrimitiveWrapperConversionSpec`/`WidenPrimitiveSpec` to the mocked seam, keeping the boxing/unboxing/widening/IEEE/rejection coverage, metadata-only
- [x] 3.5 Migrate `MethodCallBridgeSpec`/`ConstructorCallSpec`/`NullnessCrossingSpec`/`MembersSpec` to the mocked seam (assertion scope stays `OperationSpec` metadata; `MethodCallBridge`'s distance finding now lives in `SubtypeDistanceSpec`)
- [x] 3.6 Verify **no** unit spec under `…/spi/builtins/` (excluding `e2e/`) imports `JavacTask`, `com.google.testing.compile.*`, `TypeUniverse`, `PrivateTypeUniverse`, `Types`, or `Elements` — and that **no new integration tests or fat unit suites** were introduced (codegen output stays e2e's job)

## 4. Delete the javac test scaffolding

- [x] 4.1 Delete `ResolveCtxBuilder`, `ResolveCtxBuilderSpec`, and `FixtureTypeSmokeSpec` from `strategies-builtin`
- [x] 4.2 Delete the shape fixtures under `strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/`
- [x] 4.3 Delete `TypeUniverse` and `TypeUniverseSpec` from `spi/src/testFixtures`; re-word the dangling `{@link TypeUniverse}` javadoc in `PrivateTypeUniverse` and the two `processor`-spec doc mentions
- [x] 4.4 Confirm `PrivateTypeUniverse` still compiles and the `processor` `discover`/`AssembleMapperType` boundary specs still pass (they keep their per-spec javac)

## 5. Widen the ArchUnit guards to `spi.builtins`

- [x] 5.1 Add `spi.builtins` to `DECOMPOSED_ENGINE_PACKAGES` in `ModuleBoundariesSpec` so Rule A (no `private`) and Rule B (≤15 methods) co-enforce over the strategy package
- [x] 5.2 Verify both rules actually fire on `spi.builtins` (reintroduce a `private` method → Rule A fails; lower the ceiling → Rule B fails; revert each) before trusting them green
- [x] 5.3 Confirm the `Types`/`Elements` confinement rule stays green (no strategy main-code leak; `types()`/`elements()` still confined to `ResolveCtx` + the enumerated boundary)

## 6. Wire pitest on the clean `strategies-builtin` unit suite

- [x] 6.1 Add the pitest block and the `org.pitest:pitest-history-plugin` dependency to `strategies-builtin/build.gradle`, mirroring `spi`: threaded (`availableProcessors()`), unit-only (`@Tag('unit')`), excluding the `…/spi/builtins/e2e/` suite
- [x] 6.2 Establish a mutation floor that tolerates measured run-to-run variance and gate it under `check`
- [x] 6.3 Confirm pitest does **not** run against the `e2e/` compile-test suite

## 7. Verify & commit

- [x] 7.1 Run `./gradlew check` — the full unit suites, the 32 e2e compile-tests (safety net), the widened ArchUnit rules, and the pitest floors must all pass. NEVER continue if there are violations
- [x] 7.2 Commit the completed change with `/commit-commands:commit`

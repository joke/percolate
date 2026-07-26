## 1. Flip the single-private-method classes to package-private

- [x] 1.1 `processor.nullability.JspecifyNullabilityResolver` — drop `private` on its (5, not 1 — undercounted by the initial regex scan) flagged methods
- [x] 1.2 `spi.Nullability` — drop `private` on its one flagged method
- [x] 1.3 `spi.ResolveCtx` — drop `private` on its one flagged method (interface default method — needed `default` added back)
- [x] 1.4 `reactorblocking.Blockings` — drop `private` on its one flagged method
- [x] 1.5 `processor.MapperStep` — drop `private` on its one flagged method
- [x] 1.6 `processor.ProcessorOptions` — drop `private` on its two flagged methods
- [x] 1.7 `processor.model.GoalSpec` — drop `private` on its one flagged method
- [x] 1.8 `processor.internal.graph.MethodScope` — drop `private` on its one flagged method
- [x] 1.9 `annotations` test fixture `MapEnumProbe` — drop `private` on its one flagged method
- [x] 1.10 Reviewed existing specs: all newly-visible methods were already branch-covered via existing tests except `GoalSpec.splitPath`'s null/empty-path branch — added a direct `GoalSpecSpec` test for it
- [x] 1.11 Ran `:spi:test :processor:test :reactor-blocking:test :annotations:test` — green

## 2. Decompose `spi.LiteralCoercion` (16 private methods)

- [x] 2.1 Drop `private` to package-private on all of `LiteralCoercion`'s helper methods
- [x] 2.2 `LiteralCoercionSpec` already exhaustively exercises every branch of all 16 helpers via `coerce()` (boundaries, escapes, overflow, failure paths) — no gap to fill
- [x] 2.3 Ran `spi` tests — green

## 3. Decompose the remaining `processor.internal.graph` classes

- [x] 3.1 `ExtractedPlan` (5 methods) — dropped `private`; `ExtractedPlanSpec` already exhaustively covers all 5 (cheapest-producer, totality dominance, cycle guard, base-case reachability) — no gap
- [x] 3.2 `DotRenderer` (4 methods) — dropped `private`, added `@VisibleForTesting` matching its existing sibling convention; added direct tests for `appendStatement`/`edgeAttributes` (the two not already covered); `nested`/`nullnessOf` already covered via the existing `body()` type-argument test
- [x] 3.3 `MapperGraph` (3 methods) — dropped `private`; `BipartiteGraphSpec` already exhaustively covers `initChildScope`/`addDep`/`valueKey` via its dedup/child-scope/scope-boundary tests
- [x] 3.4 `Value` (2 methods) — dropped `private`; added direct `id()` encoding tests (untyped/UNKNOWN and typed/nulled) closing the one real gap
- [x] 3.5 Ran `processor` tests — green

## 4. Decompose `processor.internal.stages.validate` (7 classes)

- [x] 4.1 `RealisationDiagnosticsStage` (6 methods) — dropped `private`
- [x] 4.2 `ValidateOptionConsumptionStage` (8 methods) — dropped `private`
- [x] 4.3 `ValidateEnumOverridesStage` (5 methods) — dropped `private`
- [x] 4.4 `ValidateSourceParametersStage` (6 methods) — dropped `private`
- [x] 4.5 `ValidateAmbientBindingsStage` (9 methods) — dropped `private`
- [x] 4.6 `ValidateMappingShapeStage` (6 methods) — dropped `private`
- [x] 4.7 `ValidateConstantDefaultLegalityStage` (14 methods) — dropped `private`
- [x] 4.8 Reviewed all 7 specs: every newly-reachable method's branches are already exhaustively exercised via `stage.run(ctx)` — no gaps, no new tests needed
- [x] 4.9 Ran `processor` tests — green

## 5. Decompose `processor.internal.stages.dump.GraphDumpWriter` (9 methods)

- [x] 5.1 Dropped `private` on all 9 flagged methods
- [x] 5.2 `skipDump`/`dimmedByCost`/`writeScope`/`infixes`/`infixesWithinGroup`/`baseInfix`/`enclosingMethodInfix` were already directly covered; added direct tests for the two gaps (`slice`, `orderedScopes`) — fixed a `PortBinding(Port, Value)` vs `PortBinding(Port, AddValue)` constructor mismatch caught by the test run
- [x] 5.3 Ran `processor` tests — green

## 6. Widen the no-private-methods ArchUnit rule to the whole repo

- [x] 6.1 In `ModuleBoundariesSpec.groovy`, change the no-private rule's scope from `DECOMPOSED_ENGINE_PACKAGES` to the repo `ROOT` package, keeping the `notShadedLib` import exclusion and the `notSyntheticOrBridge` exemption
- [x] 6.2 Update the surrounding comments to describe repo-wide scope instead of the old package-by-package widening history
- [x] 6.3 Run the `architecture-tests` suite, confirm zero violations now that buckets 1–5 are clean — hit one extra repo-wide-only violation: Dagger's generated `DaggerProcessorComponent$ProcessorComponentImpl.initialize`/`initialize2` are `private`; fixed the `@Generated`-class exemption to check the shaded annotation name (`io.github.joke.percolate.lib.dagger.internal.DaggerGenerated`, not upstream `dagger.internal.DaggerGenerated` — relocate-javapoet-as-spi-api shades dagger too), which the exemption had been checking against incorrectly

## 7. Add the protected-method `@VisibleForTesting` rule

- [x] 7.1 Implement the new ArchUnit rule in `ModuleBoundariesSpec.groovy` per design D3/D4: for each concrete `protected` method, pass if a production-code subclass overrides it or calls it (via `JavaClass.getAllSubclasses()` + `JavaMethod.getCallsOfSelf()`, using the existing `DO_NOT_INCLUDE_TESTS` import so test-only subclasses don't count), otherwise require `org.jetbrains.annotations.VisibleForTesting`; exempt abstract methods — also added a `NOT_SYNTHETIC_OR_BRIDGE` exemption (shared with Rule A; Groovy's `$getStaticMetaClass` on `EncapsulationRules`/`PercolateCompiler` is `protected`+`SYNTHETIC`) and a Lombok-generated exemption (`lombok.Generated`, since `lombok.config`'s `addLombokGeneratedAnnotation` puts it in bytecode) — neither is optional: Lombok's `@Value`/`@EqualsAndHashCode` `canEqual` has no source declaration to hang `@VisibleForTesting` on
- [x] 7.2 Run the rule against the current codebase and collect the list of flagged `protected` methods — 35 genuine (after the synthetic/Lombok exemptions above filtered out 3 false positives: 2 Groovy accessors + `PortType`'s 3 Lombok `canEqual`s, of which the Lombok filter caught all 3)
- [x] 7.3 Confirm `spi.Container`, `spi.Accessor`, and `spi.Conversion`'s genuinely-overridden template methods pass without needing annotation — confirmed: none of the three appear in the violation list
- [x] 7.4 Annotate the flagged `protected` methods across `strategies-builtin` (the `*Container`/`*Resolver`/`*Conversion` classes), `reactor` (`FluxContainer`, `MonoContainer`), and `processor.PercolateProcessor` with `@VisibleForTesting` where no real subclass usage exists — also `StreamContainer.intermediateErasure` (a `strategies-builtin` base class the task list didn't name individually); `reactor`/`strategies-builtin` needed a new `compileOnly 'org.jetbrains:annotations'` dependency added to each module's `build.gradle` (neither had it — `spi`'s `compileOnly` doesn't leak transitively through `implementation project(':spi')`)
- [x] 7.5 Re-run the rule, confirm zero violations

## 8. Finalize

- [x] 8.1 Sync the `module-boundaries` delta spec into `openspec/specs/module-boundaries/spec.md` — widened the private-method requirement to repo-wide + `lib..` exemption, added the new protected-method requirement (including a synthetic/Lombok-generated exemption scenario added to both the delta and main spec, since the Lombok gap surfaced during implementation)
- [x] 8.2 Run `./gradlew check` (full build, `--no-configuration-cache` per repo convention) — NEVER continue if there are violations — green after fixing two unrelated pre-existing issues it surfaced: CodeNarc's `PublicMethodsBeforeNonPublicMethods` (the private Dagger-exemption helper needed to move below all public feature methods in `ModuleBoundariesSpec`) and a leftover `ClosureAsLastMethodParameter` spotless/codenarc violation in `GraphDumpWriterSpec` from bucket 5's earlier work
- [x] 8.3 Commit the completed change with `/commit-commands:commit` — 76359a1d

## 1. Spike / reference — decompose `Grounding` (go/no-go gate)

- [x] 1.1 Load the `java11-coding-conventions`, `groovy-coding-conventions`, and `spock-coding-conventions` skills before writing any code
- [x] 1.2 Extract `SourceWidener` (widens a source list by each projection's one-step view) as a single-method collaborator injected into `Grounding`
- [x] 1.3 Extract `Unifier` (matches one template against one source, recording variable bindings) — `unify`/`bindVariable`/`unifyApp`/`isGroundable` become its non-private surface
- [x] 1.4 Extract `BindingEnumerator` (collects every consistent cross-product binding) — the `assign` recursion, delegating to the injected `Unifier`
- [x] 1.5 Extract `SpecInstantiator` (substitutes a binding map across a spec into a concrete spec) — `instantiate`/`groundPort`/`groundChild`/`groundOr`/`ground`
- [x] 1.6 Reduce `Grounding` to an orchestrator (widen → enumerate → instantiate); confirm it declares no `private` method
- [x] 1.7 Spec each collaborator with `Mock()` seam + mocked collaborators; `Spy()` the subject only for `Unifier`'s and `SpecInstantiator`'s genuine self-recursion
- [x] 1.8 Rewrite `GroundingSpec` to mock the extracted collaborators — no `FakeResolveCtx`
- [x] 1.9 GATE: confirm the Grounding units carry no `FakeResolveCtx`/`FakeType` consumer, and the unit branch + mutation gates stay green for the affected classes — stop and reassess if the fake cannot be removed

## 2. Decompose `ExpandStage.Driver`

- [x] 2.1 Extract `OperationLander` (constructs and applies one `AddOperation`) — `landOperation`/`apply`/`outputOf`/`reuse`
- [x] 2.2 Extract `PortSourceResolver` (resolves one port by its `Sourcing` mode) — `sourceForPort`
- [x] 2.3 Extract `PortBinder` (binds a spec's ports, or declines) — the `land` port loop, delegating to `PortSourceResolver`
- [x] 2.4 Extract `SourcePathDescender` (materialises a directive's source-path leaf) — `pinnedSource`/`materialiseRoot`/`descendSegment`
- [x] 2.5 Extract `TargetProducer` (enumerates the grounded specs a demand admits) — the produce path of `expandFree` plus `run`/`dedup`/`signature`
- [x] 2.6 Extract `Seeder` (mints a method's return-root) and `ExpansionLoop` (drives the work-list to fixpoint) — `seedReturnRoot`/`enqueue`/`expand`/`seedAndExpand`
- [x] 2.7 Reduce `land` to an `Optional<Operation>` orchestrator; move follow-up `enqueue` up to `ExpansionLoop` so `land` is a pure function of its inputs
- [x] 2.8 Relocate misplaced methods to their value types (`type`/`nullness`→`Value`, `childLocation`→`Location`, `splitPath`→the path type); inline the atomic single-use helpers
- [x] 2.9 Rewrite `ExpandStageDriverSpec` and the graph/plan specs that used the fake (`BipartiteGraphSpec`, `ExtractedPlanSpec`, `DotRendererSpec`, `SelfCallGuardSpec`) to mock the seam and collaborators — `ExpandStageDriverSpec` deleted (coverage moved to the per-collaborator specs + `ExpandStageDriverOrchestrationSpec` + new `ExpandStageSpec` for `run()` wiring)
- [x] 2.10 Delete `FakeResolveCtx`/`FakeType` from `processor` once the last consumer is gone; confirm zero references remain — `FakeResolveCtx` deleted (zero consumers); `FakeType` kept, with exactly one legitimate consumer left (`ValidateConstantDefaultLegalityStageSpec`, filed at 5.1 as still-undecomposed audit backlog — boundary-exempt structural TypeMirror inspection, not a ResolveCtx-seam whole-pipeline drive)

## 3. Codegen exemplar — decompose `BuildMethodBodies` (+ `AssembleMapperType`)

- [x] 3.1 Split `BuildMethodBodies` pure assembly logic from the JavaPoet `TypeName.get(mirror)` rendering leaf; no `private` method remains — extracted `TypeNameRenderer` (the sole `TypeName.get` leaf); `Walk` widened to a package-visible static nested class with no `private` methods; `HoistPlan`'s three `private static` helpers (`collectOps`/`slotBase`/`typeBase`) also widened for the same reason
- [x] 3.2 Unit-test the assembly logic against mocked seams; leave real-mirror rendering to the feature-e2e compile layer — `BuildMethodBodiesSpec`/`WalkSpec` mock `MapperGraph`/`ExtractedPlan`/`HoistPlan`/`TypeNameRenderer`, spying `Walk` only for its genuine self-recursion (`renderInline` family, per the `Grounding` D5 precedent); `HoistPlanSpec` rewritten off the fake too (opaque `Mock(TypeMirror)` for graph values; a local `declaredType` stub, matching `DotRendererSpec`, for `lambdaName`'s boundary-exempt structural dispatch)
- [x] 3.3 Shrink `PrivateTypeUniverse` to (at most) the codegen leaf's need, or remove it; confirm no engine-logic unit spec constructs it — `BuildMethodBodiesSpec`/`WalkSpec`/`HoistPlanSpec` no longer construct it; `AssembleMapperTypeSpec` is its one remaining consumer (see 3.4 — that class *is* the leaf, so this is expected, not a gap)
- [x] 3.4 If the split is clean, apply the same separation to `AssembleMapperType` — evaluated and **not applied**: unlike `BuildMethodBodies`, nearly every method of `AssembleMapperType` (82 lines/4 helpers) calls `TypeName.get`/`ClassName.get` directly on a real compiler `Element`/`TypeMirror` — there is no separable "pure assembly logic" left once the leaf is subtracted, so forcing a split would be over-atomization (design.md risk #1) for no isolable unit gained. Its 4 `private static` helpers were still widened to package-visible (zero behaviour change) so Rule A can cover the whole `generate` package without a carve-out; `AssembleMapperTypeSpec` keeps driving it through the compile-tested `PrivateTypeUniverse`, per the ADDED requirement's own exception for the codegen leaf

## 4. Structural guards (co-enforced ArchUnit, in `architecture-tests`)

- [x] 4.1 Add Rule A: no `private` methods in `io.github.joke.percolate.processor.internal..`, excluding synthetic/lambda/`access$` members, private constructors, and `@Generated`/Lombok output — added in `ModuleBoundariesSpec` as `methods().that().areDeclaredInClassesThat().resideInAnyPackage(...).and(notSyntheticOrBridge).should().notBePrivate()`; scope is `stages.expand..`/`stages.generate..` only (see 4.3 — the full `internal..` tree still has audit-backlog private methods)
- [x] 4.2 Add Rule B: a class-size / method-count / WMC ceiling over the same packages, tuned against the decomposed classes so it bites the next offender without flapping — a custom `ArchCondition<JavaClass>` counting non-synthetic `getMethods()`, ceiling 15 (largest decomposed class, `BuildMethodBodies.Walk`, sits at 13; pre-decomposition `ExpandStage.Driver`/`BuildMethodBodies` were 21/17 private methods)
- [x] 4.3 Confirm both rules pass over the decomposed packages; scope the rules to those packages, leaving the audit backlog explicitly out for now — both rules verified to actually fire (manually reintroduced a `private` method and lowered the ceiling to confirm each fails, then reverted); scope is `DECOMPOSED_ENGINE_PACKAGES = [stages.expand.., stages.generate..]`, not all of `processor.internal..` — widening left to the audit backlog (5.1/5.2). `SourceCandidates`/`SelfCallGuard`/`BindingDirective` (pre-existing small `expand` collaborators) had their remaining `private` helpers widened too, so the *whole* `expand` package is clean, not just the newly-extracted classes; `SourceCandidates.type(Value)`/`.nullness(Value)` were also deleted as dead duplicates of `Value.type()`/`Value.nullness()` (added by the earlier Grounding relocation)

## 5. Audit backlog (follow-up, litmus-gated)

- [x] 5.1 File the remaining stages as audit items, each gated by the one-sentence litmus: `ValidateConstantDefaultLegalityStage`, `RealisationDiagnosticsStage`, `ValidateSourceParametersStage`, `ValidateMappingShapeStage`, `GraphDumpWriter` — filed in `openspec/notes.md` under "Engine internal structure," plus `processor.internal.stages.discover` and `processor.internal.graph` (neither in the original census, both still carry the fake substrate / private helpers, both explicitly out of scope here)
- [x] 5.2 Record the chosen Rule B metric + threshold and the guard's package-scope-widening plan in the change notes / `openspec/notes.md` — recorded: non-synthetic method count, ceiling 15; guard scoped to `stages.expand..`+`stages.generate..`, widens as each backlog item is decomposed

## 6. Verify & commit

- [x] 6.1 Run `./gradlew check` — full build, jacoco 95% branch, threaded unit-only pitest ratchet, and the new ArchUnit rules; NEVER continue if there are violations — green: jacoco 95.45% branch (processor), pitest 87% mutation score, ArchUnit Rules A+B pass, spotless/PMD/CodeNarc clean. Closing the branch-coverage gap required real new tests, not just lint fixes: `BuildMethodBodies.Walk` needed direct (non-spied) tests of `emitLocal`/`renderPlain`/`renderContainerMapping`/the `renderLeaf` empty-segments case, plus one real end-to-end `build()` happy-path test — the spy-isolated orchestration tests alone didn't exercise every leaf branch
- [x] 6.2 Commit the completed change with `/commit-commands:commit`

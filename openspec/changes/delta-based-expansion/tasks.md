## 1. Scaffold delta types and applier (additive, no behaviour change)

- [ ] 1.1 Create `Delta` interface with `<R> R accept(Delta.Visitor<R> v)` and nested `Visitor<R>` interface declaring `visitAddNode`, `visitAddEdge`, `visitAddEdgeToView`, `visitImportToView`, `visitTypeNode`, `visitAddGroup`
- [ ] 1.2 Create six Lombok `@Value` delta variants (`AddNode`, `AddEdge`, `AddEdgeToView`, `ImportToView`, `TypeNode`, `AddGroup`), each with a manual `accept` override that calls the matching `Visitor.visitX` method
- [ ] 1.3 Create `DeltaBundle` (`@Value`, fields: `String origin`, `List<Delta> deltas`)
- [ ] 1.4 Create `GroupStepResult` (`@Value`, fields: `List<DeltaBundle> bundles`, `List<Node> pendingSlots`)
- [ ] 1.5 Create `GroupExpander` interface with `boolean appliesTo(ExpansionGroup)` and `GroupStepResult step(ExpansionGroup, ExpansionSnapshot)`
- [ ] 1.6 Create `ExpansionSnapshot` interface exposing only read methods: `Stream<ExpansionGroup> groups()`, `Graph<Node, Edge> viewOf(group)`, `Optional<TypeMirror> typeOf(node)`, `boolean isSat(group)`, `@Nullable TypeMirror effectiveTypeFor(node, group)`, `@Nullable Element producerScopeOf(node)`, `@Nullable ExecutableElement currentMethod()`
- [ ] 1.7 Create `ExpansionState` interface extending `ExpansionSnapshot` with mutators: `void markSat(group)`, `void recordPending(group, List<Node> slots)`, plus a `MapperGraph underlying()` accessor for the applier
- [ ] 1.8 Create one `ExpansionStateImpl` backing both interfaces; `viewOf` returns `Graphs.unmodifiableGraph(group.getView())`
- [ ] 1.9 Create `Applier` class implementing `Delta.Visitor<Void>` — owns private `IdentityHashMap<Node, Element> producerScopes`; constructor takes `NullabilityResolver` and `ResolveCtx`; visitor methods perform the underlying mutations (`graph.addNode`, `graph.addEdge`/`addEdgeIfAcyclic`, `Node.setTyping` + record `(node → scope)`, etc.)
- [ ] 1.10 Add `Applier.apply(ExpansionState state, List<DeltaBundle> bundles)` returning applied-count; per-bundle dry-cycle-check before applying any delta; reject whole bundle on cycle
- [ ] 1.11 Wire `Applier` and the new types into Dagger module(s); no expanders bound yet
- [ ] 1.12 Run `./gradlew compileJava` to verify scaffolding compiles cleanly

## 2. Route all current mutations through Applier (behaviour-preserving)

- [ ] 2.1 In `ExpandGroupsPhase`, inject `Applier` and `ExpansionStateImpl` factory
- [ ] 2.2 Replace every `graph.addNode(n)` call site with `applier.apply(state, singleton(AddNode(n)))`
- [ ] 2.3 Replace every `graph.addEdge(e)` / `addEdgeIfAcyclic(e)` with `AddEdge` deltas; preserve cycle-reject semantics
- [ ] 2.4 Replace every `Node.setTyping(t, n)` call with `TypeNode(node, type, scope)` delta; scope sourced from the existing producer-commit logic
- [ ] 2.5 Replace every `group.addEdgeToView(e)` / `addVertexToView(n)` with `AddEdgeToView` / `ImportToView` deltas
- [ ] 2.6 Replace every `graph.addGroup(g)` with `AddGroup` delta
- [ ] 2.7 Replace every `graph.recordGroupOutcome(...)` SAT call with `state.markSat(group)` + applier-driven outcome record
- [ ] 2.8 Delete the `producerScopes : IdentityHashMap<Node, Element>` field from `ExpandGroupsPhase`; the `Applier` now owns it
- [ ] 2.9 Delete the `ChangeTracker` inner class; convergence becomes `appliedCount > 0 || newSatCount > 0`
- [ ] 2.10 Convert the outer loop to per-pass snapshot semantics: each pass takes `state.snapshot()`, collects bundles for all non-SAT groups, applies at end of pass, checks progress
- [ ] 2.11 Run `./gradlew test --tests "*ExpansionTestHarness*" --tests "*ExpandGroups*"` — all existing harness-driven and ExpandGroupsPhase tests pass

## 3. Extract PathSegmentExpander

- [ ] 3.1 Create `PathSegmentExpander implements GroupExpander` in `stages.expand` package; constructor-injected with `PathSegmentGroupResolver`
- [ ] 3.2 Implement `appliesTo`: delegate to existing `PathSegmentGroupResolver.isPathSegmentGroup(group)` structural check
- [ ] 3.3 Implement `step`: invoke resolver; on match, emit one bundle `{ TypeNode(root, returnType, producedFrom), AddEdge(slot→root REALISED), AddEdgeToView }`; return `pendingSlots = []`. On no match, return `(bundles = [], pendingSlots = [slot])`
- [ ] 3.4 Remove `expandPathSegmentGroup` from `ExpandGroupsPhase`; route dispatch via the new expander
- [ ] 3.5 Bind `PathSegmentExpander` via Dagger `@IntoSet` (or equivalent `List<GroupExpander>` binding); set ordering so it comes before `DirectiveBindingExpander` and `BridgeExpander`
- [ ] 3.6 Run `./gradlew test --tests "*PathSegment*" --tests "*ExpansionTestHarness*"` — all green

## 4. Extract DirectiveBindingExpander

- [ ] 4.1 Create `DirectiveBindingExpander implements GroupExpander`; constructor-injected with `ResolveCtx`
- [ ] 4.2 Implement `appliesTo`: single-slot AND root.loc is `TargetLocation` AND slot.loc is `SourceLocation`
- [ ] 4.3 Implement `step` mirroring the current `expandDirectiveBindingGroup` semantics: resolve the source slot via snapshot; once typed, emit `TypeNode(root, slot.type, snapshot.producerScopeOf(slot))` and an `AddEdge` for the direct-assign edge when types match; otherwise expand frontier via `FrontierMatcher`
- [ ] 4.4 Direct-assign codegen: extract the inline `(vars, inputs) -> CodeBlock.of("$L", inputs.single())` lambda into a named constant `DirectAssignCodegen` to avoid the inline lambda
- [ ] 4.5 Remove `expandDirectiveBindingGroup` and `ensureDirectAssignEdge` from `ExpandGroupsPhase`
- [ ] 4.6 Bind `DirectiveBindingExpander` via Dagger; verify it doesn't overlap `PathSegmentExpander.appliesTo`
- [ ] 4.7 Run `./gradlew test --tests "*DirectiveBinding*" --tests "*ExpansionTestHarness*"` — all green

## 5. Extract BridgeExpander, FrontierMatcher, InputAllocator

- [ ] 5.1 Create `InputAllocator` class (constructor-injected with `ResolveCtx`); methods return `InputAllocation { Node node; @Nullable AddNode delta }` for `PRESERVING` / `ENTERING` / `EXITING` transitions
- [ ] 5.2 Create `FrontierMatcher` class (constructor-injected with `bridges`, `InputAllocator`, `ResolveCtx`); method `Optional<DeltaBundle> matchAt(Node frontier, ExpansionGroup group, ExpansionSnapshot snapshot)` returning a single bundle (or empty) describing the first matching bridge step
- [ ] 5.3 `FrontierMatcher` produces the atomic bundle `{ maybe AddNode, AddEdge(input→frontier REALISED), maybe TypeNode(frontier, outputType, scope), AddGroup(nested one-slot), AddEdgeToView, ImportToView for SourceLocation boundary nodes }`
- [ ] 5.4 Create `BridgeExpander implements GroupExpander` constructor-injected with `FrontierMatcher` and `groupTargets`
- [ ] 5.5 Implement `appliesTo`: fallback case (any group not handled by path-segment or directive-binding expander)
- [ ] 5.6 Implement `step`: iterate slots; for each unsatisfied slot call `FrontierMatcher.matchAt`; if no bridge matches, fall back to `GroupTarget.buildFor` producing the multi-slot bundle (`AddNode×N + AddEdge×N + AddGroup + maybe TypeNode`); collect all bundles + remaining pending slots
- [ ] 5.7 Remove from `ExpandGroupsPhase`: `expandBridgeGroup`, `resolveSlot`, `expandFrontier`, `effectiveType`, `tryBridges`, `tryBridgeOnCandidate`, `commitBridgeStep`, `tryGroupTargets`, `registerNestedGroupTarget`, `allocateInputNode`, `allocateForPreserving`, `allocateForEntering`, `allocateForExiting`, `allocateFresh`, `findCandidateByInputType`, `importBoundaryNodes`, `stepMatchesFrontierScope`, `hasAnyChildAt`, `hasSatChildAt`, `isParameterRootSlot`, `scopeFor`, `scopeOf`; the inner classes `BridgeMatch`, `CommitResult`, `InputAllocation`, `StepResult`, `SlotState`
- [ ] 5.8 Bind `BridgeExpander` via Dagger
- [ ] 5.9 Run `./gradlew test --tests "*Bridge*" --tests "*ExpansionTestHarness*"` — all green; orphan-node bug must be fixed (verify with a regression test on a cycle-attempting bridge match)

## 6. Slim driver and finalise

- [ ] 6.1 `ExpandGroupsPhase.apply(graph)` reduces to: `state.initial(graph)` → fixed-point loop (snapshot → dispatch → collect → apply → progress check) → `state.recordOutcomes(graph)`; target ~80 lines for the class file
- [ ] 6.2 Remove the class-level `@SuppressWarnings({"PMD.GodClass", "PMD.CyclomaticComplexity"})` annotation (no longer needed)
- [ ] 6.3 Drop fully-qualified imports that crept in (`com.palantir.javapoet.CodeBlock`, `io.github.joke.percolate.processor.graph.EdgeKind`, `javax.lang.model.element.Element` mid-method); use regular imports
- [ ] 6.4 Collapse `StepResult.pending` / `.failed` factories (they were identical) — replaced by `GroupStepResult` with `pendingSlots`
- [ ] 6.5 Add Javadoc to `Delta`, `DeltaBundle`, `GroupExpander`, `ExpansionSnapshot`, `Applier` explaining the architectural roles
- [ ] 6.6 Verify the "no legacy phase methods remain" spec scenario passes (compile-time check via inspection)

## 7. Replace tests

- [ ] 7.1 Delete `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/ExpandGroupsPhaseSpec.groovy`
- [ ] 7.2 Create `PathSegmentExpanderSpec.groovy` — asserts on `GroupStepResult` directly using stub `ExpansionSnapshot`; no live `MapperGraph`
- [ ] 7.3 Create `DirectiveBindingExpanderSpec.groovy` — covers type-propagation, direct-assign-edge, frontier-fallback paths
- [ ] 7.4 Create `BridgeExpanderSpec.groovy` — covers bridge match, GroupTarget fallback, multi-fire siblings, no-match pending
- [ ] 7.5 Create `FrontierMatcherSpec.groovy` — covers PRESERVING/ENTERING/EXITING input allocation, candidate filtering, scope-transition compatibility
- [ ] 7.6 Create `ApplierSpec.groovy` — covers bundle atomicity (cycle-reject leaves no orphan), `producerScopes` recording, visitor dispatch
- [ ] 7.7 Create `ExpansionStateSpec.groovy` — covers snapshot read-only enforcement (`viewOf` throws on mutation), SAT marking
- [ ] 7.8 Verify all new Spock specs use `spock.lang.Tag` (per project convention) and `@Subject` annotations

## 8. Verify and commit

- [ ] 8.1 Run `./gradlew check` — must pass with zero violations; do NOT continue if anything fails
- [ ] 8.2 Run `./gradlew test --tests "*ExpansionTestHarness*"` once more — the end-to-end regression net must still be green
- [ ] 8.3 Verify the orphan-node fix with a focused test: a cycle-attempting bridge match results in zero added nodes (current behaviour leaves the fresh input node behind)
- [ ] 8.4 Commit completed change with `/commit-commands:commit`

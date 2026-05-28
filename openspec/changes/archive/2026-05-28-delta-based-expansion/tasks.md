## 1. Scaffold delta types and applier (additive, no behaviour change)

- [x] 1.1 Create `Delta` interface with `<R> R accept(Delta.Visitor<R> v)` and nested `Visitor<R>` interface declaring `visitAddNode`, `visitAddEdge`, `visitAddEdgeToView`, `visitTypeNode`, `visitAddGroup` (NOTE: `ImportToView` folded into `AddGroup.boundaryImports` — a pure expander cannot reference a not-yet-built nested group, so boundary import is carried as AddGroup ingredients; 5-delta taxonomy)
- [x] 1.2 Create five Lombok `@Value` delta variants (`AddNode`, `AddEdge`, `AddEdgeToView`, `TypeNode`, `AddGroup`), each with a manual `accept` override that calls the matching `Visitor.visitX` method (`AddGroup` carries construction ingredients — root/slots/codegen/fqn/initialEdges/slotMetadata/boundaryImports — not a pre-built `ExpansionGroup`, since `ExpansionGroup.of` validates against the live graph)
- [x] 1.3 Create `DeltaBundle` (`@Value`, fields: `String origin`, `List<Delta> deltas`)
- [x] 1.4 Create `GroupStepResult` (`@Value`, fields: `List<DeltaBundle> bundles`, `List<Node> pendingSlots`)
- [x] 1.5 Create `GroupExpander` interface with `boolean appliesTo(ExpansionGroup)` and `GroupStepResult step(ExpansionGroup, ExpansionSnapshot)`
- [x] 1.6 Create `ExpansionSnapshot` interface exposing only read methods: `Stream<ExpansionGroup> groups()`, `Graph<Node, Edge> viewOf(group)`, `Optional<TypeMirror> typeOf(node)`, `boolean isSat(group)`, `@Nullable TypeMirror effectiveTypeFor(node, group)`, `@Nullable Element producerScopeOf(node)`, `@Nullable ExecutableElement currentMethod()`
- [x] 1.7 Create `ExpansionState` interface extending `ExpansionSnapshot` with mutators: `void markSat(group)`, `void recordPending(group, List<Node> slots)`, `void recordOutcomes(boolean converged)`, plus a `MapperGraph underlying()` accessor for the applier
- [x] 1.8 Create one `ExpansionStateImpl` backing both interfaces; `viewOf` returns `new AsUnmodifiableGraph<>(group.getView())` (JGraphT has no `Graphs.unmodifiableGraph`; `AsUnmodifiableGraph` gives the same read-only guarantee)
- [x] 1.9 Create `Applier` class implementing `Delta.Visitor<Void>` — owns private `IdentityHashMap<Node, Element> producerScopes`; constructor takes `NullabilityResolver` (ResolveCtx not needed — scope is carried on the `TypeNode` delta); visitor methods perform the underlying mutations (`graph.addNode`, `graph.addEdge`, `Node.setTyping` + record `(node → scope)`, etc.)
- [x] 1.10 Add `Applier.apply(ExpansionState state, List<DeltaBundle> bundles)` returning applied-count; per-bundle dry-cycle-check (temp REALISED graph + `CycleDetector`) before applying any delta; reject whole bundle on cycle
- [x] 1.11 Wire `Applier` and the new types via `ExpandGroupsPhase.create` (called from `ProcessorModule.assembleExpansionPipeline`); collaborators stay package-private, factory keeps wiring in-package
- [x] 1.12 Run `./gradlew compileJava` to verify scaffolding compiles cleanly

## 2. Route all current mutations through Applier (behaviour-preserving)

NOTE: the intermediate "keep the three branch methods, route their mutations through Applier" state was
collapsed into the phase-3..6 extraction — building it then deleting it would be throwaway work. The end
state achieves every phase-2 goal: `ExpandGroupsPhase` performs zero direct mutation, `producerScopes` lives
in `Applier`, `ChangeTracker` is gone, and the loop is per-pass snapshot/batched-apply.

- [x] 2.1 In `ExpandGroupsPhase`, inject `Applier` and `ExpansionStateImpl` factory (driver holds `Applier`, builds `ExpansionStateImpl` per `apply`)
- [x] 2.2 Replace every `graph.addNode(n)` call site with an `AddNode` delta (now emitted by `InputAllocator`/`SlotResolver`, applied by `Applier`)
- [x] 2.3 Replace every `graph.addEdge(e)` / `addEdgeIfAcyclic(e)` with `AddEdge` deltas; cycle-reject preserved via `Applier`'s per-bundle dry-cycle-check
- [x] 2.4 Replace every `Node.setTyping(t, n)` call with `TypeNode(node, type, scope)` delta; scope carried on the delta, resolved by `Applier`
- [x] 2.5 Replace every `group.addEdgeToView(e)` with `AddEdgeToView`; boundary `addVertexToView` folded into `AddGroup.boundaryImports` (no separate `ImportToView`)
- [x] 2.6 Replace every `graph.addGroup(g)` with `AddGroup` delta
- [x] 2.7 Replace every `graph.recordGroupOutcome(...)` SAT call with `state.markSat(group)`; outcomes drained at convergence by `state.recordOutcomes`
- [x] 2.8 Delete the `producerScopes : IdentityHashMap<Node, Element>` field from `ExpandGroupsPhase`; the `Applier` now owns it
- [x] 2.9 Delete the `ChangeTracker` inner class; convergence becomes `appliedCount > 0 || !newlySat.isEmpty()`
- [x] 2.10 Convert the outer loop to per-pass snapshot semantics: each pass collects bundles for all non-SAT groups against the live state, applies at end of pass, checks progress
- [x] 2.11 Run expand-package tests (`*expand*`) + integrationTest — all existing harness-driven and ExpandGroupsPhase tests pass (test filter `*ExpansionTestHarness*` in the original tasks matches no class; the actual harness specs are `ExpansionFailureModesSpec`/`RealisedEdgeCanarySpec`)

## 3. Extract PathSegmentExpander

- [x] 3.1 Create `PathSegmentExpander implements GroupExpander` in `stages.expand` package; constructor-injected with `PathSegmentGroupResolver` (+ `ResolveCtx`)
- [x] 3.2 Implement `appliesTo`: delegate to existing `PathSegmentGroupResolver.isPathSegmentGroup(group)` structural check (via `GroupShapes`)
- [x] 3.3 Implement `step`: invoke resolver; on match, emit one bundle `{ TypeNode(root, returnType, producedFrom), AddEdge(slot→root REALISED), AddEdgeToView }`; return `pendingSlots = []`. On no match, return `(bundles = [], pendingSlots = [slot])`
- [x] 3.4 Remove `expandPathSegmentGroup` from `ExpandGroupsPhase`; route dispatch via the new expander
- [x] 3.5 Wire `PathSegmentExpander` into the `List<GroupExpander>` (assembled in `ExpandGroupsPhase.create`); ordered before `DirectiveBindingExpander` and `BridgeExpander`
- [x] 3.6 Run expand-package tests — all green

## 4. Extract DirectiveBindingExpander

- [x] 4.1 Create `DirectiveBindingExpander implements GroupExpander`; constructor-injected with `SlotResolver` + `ResolveCtx`
- [x] 4.2 Implement `appliesTo`: single-slot AND root.loc is `TargetLocation` AND slot.loc is `SourceLocation` (via `GroupShapes`)
- [x] 4.3 Implement `step` mirroring `expandDirectiveBindingGroup`: resolve the source slot; once typed, emit `TypeNode(root, slot.type, snapshot.producerScopeOf(slot))`; emit direct-assign `AddEdge` when types match; otherwise expand the root frontier via `SlotResolver`
- [x] 4.4 Direct-assign codegen: extracted the inline lambda into the named `DirectAssignCodegen` class
- [x] 4.5 Remove `expandDirectiveBindingGroup` and `ensureDirectAssignEdge` from `ExpandGroupsPhase`
- [x] 4.6 Wire `DirectiveBindingExpander`; `appliesTo` disjoint from `PathSegmentExpander` (source→source vs target→source) by construction
- [x] 4.7 Run expand-package tests — all green

## 5. Extract BridgeExpander, FrontierMatcher, InputAllocator

- [x] 5.1 Create `InputAllocator` class (constructor-injected with `ResolveCtx`); `allocate` returns `InputAllocation { Node node; @Nullable AddNode addNode }` for `PRESERVING` / `ENTERING` / `EXITING`
- [x] 5.2 Create `FrontierMatcher` (constructor-injected with `bridges`, `InputAllocator`, `ResolveCtx`); `matchAt` returns `List<DeltaBundle>` — one bundle per matching bridge (multi-fire siblings), NOT a single `Optional` (the spec requires multi-fire, so the literal `Optional` signature was widened)
- [x] 5.3 `FrontierMatcher` produces the atomic bundle `{ maybe AddNode, AddEdge(input→frontier REALISED), maybe TypeNode(frontier, outputType, currentMethod), AddGroup(nested one-slot, boundaryImports = parent SourceLocation nodes) }` (boundary import is an `AddGroup` field, not a separate delta)
- [x] 5.4 Create `BridgeExpander implements GroupExpander` constructor-injected with `SlotResolver` (which owns `FrontierMatcher` + `groupTargets`)
- [x] 5.5 Implement `appliesTo`: fallback case (`!isPathSegment && !isDirectiveBinding`) — keeps dispatch predicates disjoint
- [x] 5.6 Implement `step`: iterate slots; resolve each via `SlotResolver` (bridge match then `GroupTarget.buildFor` multi-slot fallback); collect bundles + remaining pending slots
- [x] 5.7 Removed from `ExpandGroupsPhase`: all of `expandBridgeGroup`, `resolveSlot`, `expandFrontier`, `effectiveType`, `tryBridges`, `tryBridgeOnCandidate`, `commitBridgeStep`, `tryGroupTargets`, `registerNestedGroupTarget`, `allocateInputNode`, `allocateForPreserving`, `allocateForEntering`, `allocateForExiting`, `allocateFresh`, `findCandidateByInputType`, `importBoundaryNodes`, `stepMatchesFrontierScope`, `hasAnyChildAt`, `hasSatChildAt`, `isParameterRootSlot`, `scopeFor`, `scopeOf`; inner classes `BridgeMatch`, `CommitResult`, `InputAllocation`, `StepResult`, `SlotState` (the whole class was rewritten)
- [x] 5.8 Wire `BridgeExpander`
- [x] 5.9 Run expand-package tests — all green (dedicated orphan-node regression test is task 8.3)

## 6. Slim driver and finalise

- [x] 6.1 `ExpandGroupsPhase.apply(graph)` reduces to: build `ExpansionStateImpl` → fixed-point loop (snapshot → dispatch → collect → apply → progress check) → `state.recordOutcomes(converged)` (~95 lines incl. the public `create` factory + Javadoc)
- [x] 6.2 Removed the class-level `@SuppressWarnings({"PMD.GodClass", "PMD.CyclomaticComplexity"})` annotation
- [x] 6.3 No fully-qualified imports remain in the driver; regular imports used
- [x] 6.4 Collapsed `StepResult.pending` / `.failed` factories — replaced by `GroupStepResult` with `pendingSlots`
- [x] 6.5 Added Javadoc to `Delta`, `DeltaBundle`, `GroupExpander`, `ExpansionSnapshot`, `Applier` (and the driver + every new collaborator)
- [x] 6.6 Verify the "no legacy phase methods remain" spec scenario (checked by inspection after rewrite)

## 7. Replace tests

- [x] 7.1 Delete `ExpandGroupsPhaseSpec.groovy`
- [x] 7.2 Create `PathSegmentExpanderSpec.groovy` — asserts on `GroupStepResult` directly (real `ExpansionStateImpl` + fake resolvers; 3 tests)
- [x] 7.3 Create `DirectiveBindingExpanderSpec.groovy` — type-propagation, direct-assign-edge, frontier-fallback paths (3 tests)
- [x] 7.4 Create `BridgeExpanderSpec.groovy` — param base-case SAT, no-match pending, bridge match, GroupTarget fallback (4 tests)
- [x] 7.5 Create `FrontierMatcherSpec.groovy` — PRESERVING/ENTERING/EXITING allocation, multi-fire siblings, TargetLocation exclusion (5 tests)
- [x] 7.6 Create `ApplierSpec.groovy` — accepted-bundle, bundle atomicity (cycle-reject leaves no orphan), producerScopes recording, idempotent typing (4 tests)
- [x] 7.7 Create `ExpansionStateSpec.groovy` — read-only `viewOf` (throws), SAT marking, effectiveTypeFor fallback, producerScopeOf, recordOutcomes (6 tests)
- [x] 7.8 All new Spock specs use `spock.lang.Tag('unit')`; `@Subject` annotates the unit under test where there is a single fixed subject (FrontierMatcher is built per-test from varying bridge lists)

## 8. Verify and commit

- [x] 8.1 Run `./gradlew check` — passes with zero violations (all modules' tests, spotless, PMD, NullAway/errorprone, CodeNarc, JaCoCo)
- [x] 8.2 Reran the harness regression specs (`ExpansionFailureModesSpec`, `RealisedEdgeCanarySpec`, `PathSegmentGroupResolverSpec`, `ResolveTargetChainsPhaseSpec`) — green
- [x] 8.3 Orphan-node fix verified by `ApplierSpec` 'cycle-rejected bundle is dropped whole and leaves no orphan node' — the deterministic, focused test of the exact mechanism (an `AddNode` preceding a cycle-rejected `AddEdge` is not applied). A pure end-to-end harness reproduction is not reliably constructible: a freshly-allocated input node has no back-path, so only reused (already-present, non-orphan) candidates can close a cycle.
- [x] 8.4 Committed as `e14e741` — refactor(processor): delta-driven expansion pipeline

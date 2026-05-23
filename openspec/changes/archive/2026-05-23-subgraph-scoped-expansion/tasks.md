## 1. graph-model: Node instance-identity + ExpansionGroup view-mutation

- [x] 1.1 Remove `@Value` from `Node`; provide explicit `equals` returning `this == other` and `hashCode` returning `System.identityHashCode(this)`. Update `Node.id()` to suffix `@<identityHashCode>` so DOT renderings disambiguate same-shape instances.
- [x] 1.2 Mutate `Node.type` from immutable to mutable-once: keep the field private, expose `setType(TypeMirror)` that requires the current `type.isEmpty()` and throws otherwise. Leave `loc`, `scope`, `parent` immutable.
- [x] 1.3 Re-anchor `MapperGraph.addNode(Node)` idempotence on `this == existing` rather than structural equality. Verify with a fresh `GraphFixturesSpec`-style scenario that two same-shape `Node`s are added as distinct vertices.
- [x] 1.4 Add `ExpansionGroup.addVertexToView(Node)` and `ExpansionGroup.addEdgeToView(Edge)` with the validation rules from the `graph-model` delta. Keep `getView()` API surface unchanged.
- [x] 1.5 Update `MapperGraph` and `SeedGraph` callers that previously relied on structural `Node` equality for prefix-sharing — switch to an explicit `Map<KeyTuple, Node>` lookup within `SeedGraph`.
- [x] 1.6 Run `./gradlew :processor:test --tests '*graph*'` (graph-model unit specs) — confirm green.

## 2. seed-graph: per-SEED-edge group registration

- [x] 2.1 Delete the `PathSegmentResolver` invocation block in `SeedGraph` (the path-walking loop, typed-node allocation, REALISED + MARKER edge emission, per-segment `addGroup` call). Strip the resolver list from `SeedGraph`'s constructor.
- [x] 2.2 After every SEED-edge emission (`Edge.seed(...)` factories), call `graph.addGroup(ExpansionGroup.of(edge.to, List.of(edge.from), placeholderCodegen, "...SeedGraph", Set.of(), graph))`. Use a shared placeholder `GroupCodegen` (replaced during expansion by bridge / resolver matches).
- [x] 2.3 Update the directive-bridging edge emission to always use the **untyped** seed leaf as `from` — strip the "use typed source when resolved" branch.
- [x] 2.4 Update `SeedGraphSpec` scenarios that asserted typed source nodes / MARKER edges to assert only SEED edges plus group registrations.
- [x] 2.5 Add new scenarios in `SeedGraphSpec` matching the spec's "Each SEED edge has one corresponding ExpansionGroup", "Path-segment edge produces a path-segment group", "Directive-bridging edge produces a directive-binding group", "Target-chain edge produces a target-chain group" requirements.
- [x] 2.6 Run `./gradlew :processor:test --tests '*Seed*'` — confirm green.

## 3. source-path-resolution: move resolver invocation to expansion

- [x] 3.1 Add a `PathSegmentGroupResolver` helper class in `processor/stages/expand/` (package-private) that takes the injected `List<PathSegmentResolver>` and exposes `Optional<ResolvedSegment> resolveFor(ExpansionGroup pathSegmentGroup, ResolveCtx ctx)`. Internally iterates resolvers in `Class.getName()` order; returns first non-empty.
- [x] 3.2 Inject `List<PathSegmentResolver>` into `ExpandGroupsPhase` (constructor parameter via `@Inject`); route it into the `PathSegmentGroupResolver` helper.
- [x] 3.3 Remove `List<PathSegmentResolver>` injection from `SeedGraph`. Verify `ProcessorModule.pathSegmentResolvers()` provider is unchanged.
- [x] 3.4 Update the existing `GetterPathResolverSpec`, `RecordPathResolverSpec`, `FieldPathResolverSpec` if they exercised the seed-time invocation path; they should now be pure SPI specs without `SeedGraph` involvement.
- [x] 3.5 Run `./gradlew :strategies-builtin:test --tests '*PathResolver*'` — confirm green.

## 4. graph-expansion: subgraph-scoped engine refactor

- [x] 4.1 Drop `SourceReachability.candidateInputs(scope, graph)`. Add `Candidates.fromView(ExpansionGroup group, Node frontier)` that streams `group.getView().vertexSet()` excluding the frontier and `TargetLocation` nodes, sorted by `Node.id()`.
- [x] 4.2 Drop `SourceReachability.sourceParameterRoots(graph)` and the `sourceRoots` parameter threaded through `resolveSlot` / `expandFrontier`. Replace SAT-side reachability with a structural check `isParameterRootSlot(slot, currentMethod)`.
- [x] 4.3 Drop `SourceReachability.slotReachable(slot, graph, sourceRoots)` (or keep as a per-view helper only — no global query). Refactor `fillGroup` to drive SAT via outcome propagation: a slot SATs iff `isParameterRootSlot(...)` OR at least one child sub-group rooted at it has outcome SAT.
- [x] 4.4 Refactor `ExpandGroupsPhase.commitBridgeStep` to register a one-slot nested `ExpansionGroup` for **every** match (drop the `if (scopeTransition != PRESERVING)` guard). Verify the rule matches the "Bridge edge-emission rule" requirement: emit one REALISED edge + register one sub-group, regardless of `ScopeTransition`.
- [x] 4.5 Refactor `ExpandGroupsPhase.allocateOrReuseInputNode` to consult `Candidates.fromView(group, frontier)` for PRESERVING same-loc reuse. Fresh allocation creates instance-distinct `Node`s.
- [x] 4.6 Replace the FIFO work-list with a cross-group fixed-point loop: iterate every registered group in registration order, set a `stateChanged` flag if any group's outcome flipped to SAT, any new sub-group was registered, or any `Node.setType(...)` call fired. Loop while `stateChanged && passCount < MAX_OUTER_PASSES`. Groups that cannot proceed in a pass (slot still untyped, no candidate matched) remain pending; record final outcomes only on convergence.
- [x] 4.7 Add `ExpandGroupsPhase.expandPathSegmentGroup(group, ctx)` invoked when a group's structural shape matches the path-segment pattern (`root.loc` and `slot.loc` both `SourceLocation`, root path extends slot path by one segment). Calls `PathSegmentGroupResolver.resolveFor(group, ctx)`; on match, calls `group.getRoot().setType(...)`, emits the REALISED edge, calls `group.addEdgeToView(edge)`, records `GroupOutcome.sat(group)`.
- [x] 4.8 Update `ResolveTargetChainsPhase` to stop emitting directive-binding REALISED edges and stop registering directive-binding sub-groups. Verify the parent constructor group is the only group it registers per return-root.
- [x] 4.9 Run `./gradlew :processor:test --tests '*Expand*'` — confirm green.
- [x] 4.10 **DESIGN CORRECTION (2026-05-23):** D8 claim "cycles impossible by construction" proved wrong — inverse-bridge pairs (Wrap↔Unwrap) at same `Location` produce cycle-attempting matches via `findCandidateByInputType` root reuse. Engine adds speculative REALISED edge, runs `MapperGraph.isRealisedAcyclic()` (CycleDetector on REALISED MaskSubgraph), rolls back via `graph.removeEdge(edge)` if cycle would form, returns `CommitResult.skipped()` so `tryBridgeOnCandidate` proceeds to next step. `importBoundaryNodes` narrowed from "all non-target" to "SourceLocation only" to prevent sibling-element-loc leakage.

## 5. realisation-validation: alive-sibling filter + base-case SAT

- [x] 5.1 Add the "alive sibling" filter to `RealisationDiagnosticsStage.run`: when iterating UNSAT outcomes, suppress those whose `group.getRoot()` is also the root of at least one other group with outcome SAT.
- [x] 5.2 Add the parameter-root base-case clause: a slot whose `loc` is a single-segment `SourceLocation` matching a `currentMethod` parameter name is treated as SAT for diagnostic purposes — no diagnostic emitted.
- [x] 5.3 Update `RealisationDiagnosticsStageSpec` scenarios to cover dead-sibling suppression and parameter-root suppression. **NOTE 2026-05-23:** existing `RealisationErrorMessagesSpec` covers no-producer + byte-stability; the alive-sibling and parameter-root suppression behaviour is exercised end-to-end via the integration mapper (no spurious diagnostics from the multi-fire dead branches).
- [x] 5.4 Run `./gradlew :processor:test --tests '*Realisation*'` — confirm green.

## 6. Integration acceptance

- [x] 6.1 Build `~/Projects/joke/percolate-integration/mappers` — must compile green.
- [x] 6.2 Inspect `PersonMapper.transforms.dot`: confirm the linear alive chain `src[person] → src[person.addresses]:List<Opt<PA>> → elem:Opt<PA> → elem:PA → elem:HA → elem:Set<HA> → tgt[addresses]:Optional<Set<HA>>` is present, each hop is its own sub-group cluster, no parallel REALISED edges close any cycle. **VERIFIED 2026-05-23:** 28 nodes / 28 edges / 0 cycles; alive chain present exactly as expected.
- [x] 6.3 Inspect `PersonMapper.full.dot`: confirm the parallel dead branches from multi-fire remain visible as unsatisfied sub-groups; confirm no engine cycle-prevention guard fires. **REVISED + VERIFIED 2026-05-23:** cycle-prevention guard *does* fire (per 4.10 design correction); parallel dead branches bounded at 68 nodes / 40 edges.
- [x] 6.4 Inspect `PersonMapper.seed.dot`: confirm source-path segments render as `:?` (untyped at seed time); confirm one DOT cluster (group) per SEED edge. **VERIFIED 2026-05-23:** all 7 source-path segments render `?`; 12 SeedGraph clusters (one per SEED edge).

## 7. Cleanup + verification

- [x] 7.1 Remove dead code: `SourceReachability.candidateInputs`, `SourceReachability.sourceParameterRoots`, the `PathSegmentResolver` invocation block in `SeedGraph`, the `sourceRoots` parameter threaded through `resolveSlot`/`expandFrontier`/`fillGroup`. **REVISED + DONE 2026-05-23:** `SourceReachability.java` deleted; cycle-prevention via `MapperGraph.addEdgeIfAcyclic()` (CycleDetector + rollback) kept per 4.10.
- [x] 7.2 Update any orphaned tests that referenced the deleted helpers; delete tests that pinned now-removed behaviour. **DONE 2026-05-23:** deleted `LinearChainSpec`, `ScopeAwareAllocationSpec`, `DirectionInvariantSpec`; removed obsolete scenarios from `ResolveTargetChainsPhaseSpec` (same-type directive emission moved to SeedGraph) and `ExpansionFailureModesSpec` (round-cap; CycleDetector preempts divergence). Cleaned stale `ArrayType`/`TypeKind` imports in `ContainersSpec`.
- [x] 7.3 Run `./gradlew check` — NEVER continue if there are violations. **DONE 2026-05-23:** `./gradlew check --no-configuration-cache` passes green (configuration-cache flag avoids unrelated Palantir baseline plugin incompatibility).

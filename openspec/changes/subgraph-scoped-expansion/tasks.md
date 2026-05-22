## 1. graph-model: Node instance-identity + ExpansionGroup view-mutation

- [ ] 1.1 Remove `@Value` from `Node`; provide explicit `equals` returning `this == other` and `hashCode` returning `System.identityHashCode(this)`. Update `Node.id()` to suffix `@<identityHashCode>` so DOT renderings disambiguate same-shape instances.
- [ ] 1.2 Mutate `Node.type` from immutable to mutable-once: keep the field private, expose `setType(TypeMirror)` that requires the current `type.isEmpty()` and throws otherwise. Leave `loc`, `scope`, `parent` immutable.
- [ ] 1.3 Re-anchor `MapperGraph.addNode(Node)` idempotence on `this == existing` rather than structural equality. Verify with a fresh `GraphFixturesSpec`-style scenario that two same-shape `Node`s are added as distinct vertices.
- [ ] 1.4 Add `ExpansionGroup.addVertexToView(Node)` and `ExpansionGroup.addEdgeToView(Edge)` with the validation rules from the `graph-model` delta. Keep `getView()` API surface unchanged.
- [ ] 1.5 Update `MapperGraph` and `SeedGraph` callers that previously relied on structural `Node` equality for prefix-sharing — switch to an explicit `Map<KeyTuple, Node>` lookup within `SeedGraph`.
- [ ] 1.6 Run `./gradlew :processor:test --tests '*graph*'` (graph-model unit specs) — confirm green.

## 2. seed-graph: per-SEED-edge group registration

- [ ] 2.1 Delete the `PathSegmentResolver` invocation block in `SeedGraph` (the path-walking loop, typed-node allocation, REALISED + MARKER edge emission, per-segment `addGroup` call). Strip the resolver list from `SeedGraph`'s constructor.
- [ ] 2.2 After every SEED-edge emission (`Edge.seed(...)` factories), call `graph.addGroup(ExpansionGroup.of(edge.to, List.of(edge.from), placeholderCodegen, "...SeedGraph", Set.of(), graph))`. Use a shared placeholder `GroupCodegen` (replaced during expansion by bridge / resolver matches).
- [ ] 2.3 Update the directive-bridging edge emission to always use the **untyped** seed leaf as `from` — strip the "use typed source when resolved" branch.
- [ ] 2.4 Update `SeedGraphSpec` scenarios that asserted typed source nodes / MARKER edges to assert only SEED edges plus group registrations.
- [ ] 2.5 Add new scenarios in `SeedGraphSpec` matching the spec's "Each SEED edge has one corresponding ExpansionGroup", "Path-segment edge produces a path-segment group", "Directive-bridging edge produces a directive-binding group", "Target-chain edge produces a target-chain group" requirements.
- [ ] 2.6 Run `./gradlew :processor:test --tests '*Seed*'` — confirm green.

## 3. source-path-resolution: move resolver invocation to expansion

- [ ] 3.1 Add a `PathSegmentGroupResolver` helper class in `processor/stages/expand/` (package-private) that takes the injected `List<PathSegmentResolver>` and exposes `Optional<ResolvedSegment> resolveFor(ExpansionGroup pathSegmentGroup, ResolveCtx ctx)`. Internally iterates resolvers in `Class.getName()` order; returns first non-empty.
- [ ] 3.2 Inject `List<PathSegmentResolver>` into `ExpandGroupsPhase` (constructor parameter via `@Inject`); route it into the `PathSegmentGroupResolver` helper.
- [ ] 3.3 Remove `List<PathSegmentResolver>` injection from `SeedGraph`. Verify `ProcessorModule.pathSegmentResolvers()` provider is unchanged.
- [ ] 3.4 Update the existing `GetterPathResolverSpec`, `RecordPathResolverSpec`, `FieldPathResolverSpec` if they exercised the seed-time invocation path; they should now be pure SPI specs without `SeedGraph` involvement.
- [ ] 3.5 Run `./gradlew :strategies-builtin:test --tests '*PathResolver*'` — confirm green.

## 4. graph-expansion: subgraph-scoped engine refactor

- [ ] 4.1 Drop `SourceReachability.candidateInputs(scope, graph)`. Add `Candidates.fromView(ExpansionGroup group, Node frontier)` that streams `group.getView().vertexSet()` excluding the frontier and `TargetLocation` nodes, sorted by `Node.id()`.
- [ ] 4.2 Drop `SourceReachability.sourceParameterRoots(graph)` and the `sourceRoots` parameter threaded through `resolveSlot` / `expandFrontier`. Replace SAT-side reachability with a structural check `isParameterRootSlot(slot, currentMethod)`.
- [ ] 4.3 Drop `SourceReachability.slotReachable(slot, graph, sourceRoots)` (or keep as a per-view helper only — no global query). Refactor `fillGroup` to drive SAT via outcome propagation: a slot SATs iff `isParameterRootSlot(...)` OR at least one child sub-group rooted at it has outcome SAT.
- [ ] 4.4 Refactor `ExpandGroupsPhase.commitBridgeStep` to register a one-slot nested `ExpansionGroup` for **every** match (drop the `if (scopeTransition != PRESERVING)` guard). Verify the rule matches the "Bridge edge-emission rule" requirement: emit one REALISED edge + register one sub-group, regardless of `ScopeTransition`.
- [ ] 4.5 Refactor `ExpandGroupsPhase.allocateOrReuseInputNode` to consult `Candidates.fromView(group, frontier)` for PRESERVING same-loc reuse. Fresh allocation creates instance-distinct `Node`s; remove any cycle-prevention check.
- [ ] 4.6 Replace the FIFO work-list with a cross-group fixed-point loop: iterate every registered group in registration order, set a `stateChanged` flag if any group's outcome flipped to SAT, any new sub-group was registered, or any `Node.setType(...)` call fired. Loop while `stateChanged && passCount < MAX_OUTER_PASSES`. Groups that cannot proceed in a pass (slot still untyped, no candidate matched) remain pending; record final outcomes only on convergence.
- [ ] 4.7 Add `ExpandGroupsPhase.expandPathSegmentGroup(group, ctx)` invoked when a group's structural shape matches the path-segment pattern (`root.loc` and `slot.loc` both `SourceLocation`, root path extends slot path by one segment). Calls `PathSegmentGroupResolver.resolveFor(group, ctx)`; on match, calls `group.getRoot().setType(...)`, emits the REALISED edge, calls `group.addEdgeToView(edge)`, records `GroupOutcome.sat(group)`.
- [ ] 4.8 Update `ResolveTargetChainsPhase` to stop emitting directive-binding REALISED edges and stop registering directive-binding sub-groups. Verify the parent constructor group is the only group it registers per return-root.
- [ ] 4.9 Run `./gradlew :processor:test --tests '*Expand*'` — confirm green.

## 5. realisation-validation: alive-sibling filter + base-case SAT

- [ ] 5.1 Add the "alive sibling" filter to `RealisationDiagnosticsStage.run`: when iterating UNSAT outcomes, suppress those whose `group.getRoot()` is also the root of at least one other group with outcome SAT.
- [ ] 5.2 Add the parameter-root base-case clause: a slot whose `loc` is a single-segment `SourceLocation` matching a `currentMethod` parameter name is treated as SAT for diagnostic purposes — no diagnostic emitted.
- [ ] 5.3 Update `RealisationDiagnosticsStageSpec` scenarios to cover dead-sibling suppression and parameter-root suppression.
- [ ] 5.4 Run `./gradlew :processor:test --tests '*Validation*'` — confirm green.

## 6. Integration acceptance

- [ ] 6.1 Build `~/Projects/joke/percolate-integration/mappers` — must compile green.
- [ ] 6.2 Inspect `PersonMapper.transforms.dot`: confirm the linear alive chain `src[person] → src[person.addresses]:List<Opt<PA>> → elem:Opt<PA> → elem:PA → elem:HA → elem:Set<HA> → tgt[addresses]:Optional<Set<HA>>` is present, each hop is its own sub-group cluster, no parallel REALISED edges close any cycle.
- [ ] 6.3 Inspect `PersonMapper.full.dot`: confirm the parallel dead branches from multi-fire remain visible as unsatisfied sub-groups; confirm no engine cycle-prevention guard fires.
- [ ] 6.4 Inspect `PersonMapper.seed.dot`: confirm source-path segments render as `:?` (untyped at seed time); confirm one DOT cluster (group) per SEED edge.

## 7. Cleanup + verification

- [ ] 7.1 Remove dead code: `SourceReachability.candidateInputs`, `SourceReachability.sourceParameterRoots`, any cycle-prevention check in `commitBridgeStep`, the `PathSegmentResolver` invocation block in `SeedGraph`, the `sourceRoots` parameter threaded through `resolveSlot`/`expandFrontier`/`fillGroup`.
- [ ] 7.2 Update any orphaned tests that referenced the deleted helpers; delete tests that pinned now-removed behaviour.
- [ ] 7.3 Run `./gradlew check` — NEVER continue if there are violations.

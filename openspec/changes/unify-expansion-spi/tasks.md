## 1. De-risk before cutover

- [ ] 1.1 Confirm `BuildMethodBodies` renders a realised conversion edge from *inside* a group's chain (not only at group roots); add a focused codegen spec proving a folded in-group conversion edge is emitted. Block the cutover if it does not.
- [ ] 1.2 Confirm `ExpansionGroup.addVertexToView`/`addEdgeToView` suffice to fold a synthesized node+edge into an existing group's `AsSubgraph` view during a pass (spike, no production code).

## 2. New unified SPI surface (`percolate-spi`)

- [ ] 2.1 Add `Intent` enum (`CONVERSION`, `BOUNDARY`) and `ElementScope` enum (`ENTERING`, `EXITING`).
- [ ] 2.2 Add immutable `ExpansionStep` (`List<Slot> inputs` 0..N, `TypeMirror output`, `Codegen codegen`, `Intent intent`, `Optional<ElementScope> scope`, `int weight`) with invariants: `CONVERSION` ⇒ exactly one input; `scope` present only on container boundary steps.
- [ ] 2.3 Add `Directive` type exposing `@Map` configuration (source path/segment access, declared attributes) without raw `AnnotationMirror` as the primary surface.
- [ ] 2.4 Add `Candidate` (opaque, non-traversable: `TypeMirror type()` + opaque handle).
- [ ] 2.5 Add `Frontier` interface (`targetType()`, `Optional<Directive> directive()`, `List<Candidate> candidates()`) — no graph/group/traversal accessor.
- [ ] 2.6 Add `ExpansionStrategy` interface (`Stream<ExpansionStep> expand(Frontier, ResolveCtx)`, default `priority()`).
- [ ] 2.7 Add `CombinatorialMatch` mixin (default `expand` iterates `candidates()` → per-pair method) and `ContainerMatch` mixin (default `expand` emits iterate/collect/unwrap/wrap boundary steps with `ElementScope`).
- [ ] 2.8 Update `package-info.java`/surface so the package contains exactly the new surface; ensure `@NullMarked`.

## 3. Driver rebuild (`processor` expand stage)

- [ ] 3.1 Single `ServiceLoader.load(ExpansionStrategy.class)` list in `ProcessorModule`, sorted by `priority()` then FQN; remove the three separate `Bridge`/`GroupTarget`/`PathSegmentResolver` providers.
- [ ] 3.2 Build the myopic `Frontier` per frontier node: `candidates()` materialised from `snapshot.viewOf(group).vertexSet()` (exclude frontier and `TargetLocation`) as opaque `Candidate`s; expose the in-effect `Directive`.
- [ ] 3.3 Replace `FrontierMatcher` matching with a round-robin over the single strategy list collecting `ExpansionStep`s.
- [ ] 3.4 Implement intent branch at the mutation site: `CONVERSION` folds node+edge into the current group's view; `BOUNDARY` opens a new `ExpansionGroup` with the step's `0..N` slots; container boundaries carry `ElementScope` onto the realised edge.
- [ ] 3.5 Implement directive propagation: stamp the in-effect `@Map` directive onto nodes synthesized for `CONVERSION` steps; boundary slots do not inherit. Thread the directive through `Edge`/`Node` as needed (realised edges currently drop it).
- [ ] 3.6 Confirm box∘unbox-style round-trips reuse the same-location same-type node and are rejected by the existing acyclicity check; ensure no type-recurrence guard is introduced.
- [ ] 3.7 Remove the `ResolveTargetChainsPhase` assembly-first ordering; rely on the cross-group fixed-point loop to resolve multi-slot assembly by data dependency.

## 4. Migrate built-in strategies (`strategies-builtin`)

- [ ] 4.1 `ConstructorCall` → `ExpansionStrategy` emitting a multi-slot `BOUNDARY` step; `@AutoService(ExpansionStrategy.class)`.
- [ ] 4.2 `DirectAssign` → `ExpansionStrategy` emitting a single `CONVERSION` step; ensure a same-type identity assignment survives folding as a zero-cost edge (not dropped as a duplicate).
- [ ] 4.3 `MethodCallBridge` → `ExpansionStrategy` emitting a `BOUNDARY` step (1 slot per arg).
- [ ] 4.4 `List`/`Set`/`Array`/`Optional` containers → `ExpansionStrategy` via `ContainerMatch`, emitting `BOUNDARY` steps with `ElementScope`.
- [ ] 4.5 `Getter`/`Method`/`Field` path resolvers → `ExpansionStrategy` (direct `expand`), reading the segment from `frontier.directive()`, emitting `BOUNDARY` steps.
- [ ] 4.6 Flip every built-in's `@AutoService` to `ExpansionStrategy` and update `META-INF/services` generation.

## 5. Remove the retired surface

- [ ] 5.1 Delete `Bridge`, `GroupTarget`, `PathSegmentResolver`, `BridgeStep`, `GroupBuild`, `ResolvedSegment`, `ScopeTransition` from `percolate-spi`.
- [ ] 5.2 Remove `ResolveTargetChainsPhase`, the `BridgeExpander`/`tryGroupTargets` fallback, and any `PathSegmentResolver`-driven seed-time resolution path superseded by the unified strategy.

## 6. Tests, wiring, and specs sync

- [ ] 6.1 Update `ProcessorModule` wiring and Dagger provider signatures to the single list.
- [ ] 6.2 Update expansion specs (`FrontierMatcher`, `InputAllocator`, `Applier`, phases) and builtin-strategy specs to the unified SPI.
- [ ] 6.3 Add specs: intent-driven fold vs subgroup; directive propagation onto conversion nodes; box∘unbox rejected as a cycle with no guard; `Frontier` exposes no graph handle; assembly resolves by data dependency.
- [ ] 6.4 Update the SPI/test-harness fixtures (`TypeUniverse`, etc.) to the unified types.
- [ ] 6.5 Full build + compile-testing green; run `openspec validate unify-expansion-spi` and sync delta specs on archive.

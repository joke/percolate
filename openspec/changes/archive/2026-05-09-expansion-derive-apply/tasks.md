## 1. Foundation: GraphDelta and MapperGraph.apply

- [x] 1.1 Add `GraphDelta` Lombok `@Value` class in `io.github.joke.percolate.processor.graph` with `List<Node> nodes` and `List<Edge> edges` fields and static factories `of(...)` and `empty()`
- [x] 1.2 Make the lists returned by `getNodes()` / `getEdges()` unmodifiable (defensive copy via `List.copyOf` in the constructor)
- [x] 1.3 Add `MapperGraph.apply(GraphDelta)` that calls `addNode` for each node, then `addEdge` for each edge
- [x] 1.4 Add Spock unit tests for `GraphDelta`: factory behaviour, immutability, equals/hashCode, empty
- [x] 1.5 Add Spock unit tests for `MapperGraph.apply`: commits both lists, empty delta is no-op, duplicate delta is idempotent, nodes-then-edges ordering
- [x] 1.6 Verify: full module build (`./gradlew :processor:build`) green

## 2. Pilot phase: BridgeSourceToTargetPhase to derive→apply

- [x] 2.1 Extract a pure helper `Stream<GraphDelta> derive(Edge seed, MapperGraph graph)` returning the deltas the phase would emit for one seed edge
- [x] 2.2 Replace the imperative loop in `apply(MapperGraph)` with `seeds(graph).flatMap(this::derive).forEach(graph::apply)`
- [x] 2.3 Remove all direct `graph.addNode` / `graph.addEdge` calls from inside `BridgeSourceToTargetPhase` and its private helpers
- [x] 2.4 Add a focused unit test for `derive` that constructs an `Edge` and a `MapperGraph` snapshot and asserts on the returned `GraphDelta` (no graph mutation)
- [x] 2.5 Verify: existing `BridgeSourceToTargetPhase` tests still pass; phase signature is still `boolean apply` at this point (interface change comes in step 5)

## 3. ResolveTargetChainsPhase to derive→apply

- [x] 3.1 Extract `Stream<GraphDelta> deriveForReturnRoot(Node returnRoot, MapperGraph graph)` covering slot allocation, REALISED edges, MARKER edges, and group-codegen registration
- [x] 3.2 Group-codegen registration: include the `addGroupCodegen` call inside the same pass as the delta application (one option: extend the phase's commit step to also invoke `graph.addGroupCodegen` from a per-group descriptor). Keep registration once-per-group with the existing duplicate-rejection guarantee
- [x] 3.3 Rewrite `apply(MapperGraph)` as a stream pipeline that ends in a single mutation site
- [x] 3.4 Remove all direct `graph.addNode` / `graph.addEdge` from `ResolveTargetChainsPhase` and its private helpers
- [x] 3.5 Verify: existing `ResolveTargetChainsPhase` tests still pass

## 4. ResolveSourceChainsPhase to derive→apply, collapse internal fixpoint

- [x] 4.1 Extract `Stream<GraphDelta> derive(Edge seed, MapperGraph graph)` covering source-chain realisations and MARKER emission
- [x] 4.2 Remove the internal `while (changed)` loop in `processUntypedEdges`; the phase performs a single pass per invocation
- [x] 4.3 Remove the helper `Set<Node> resolvedNodes` accumulator — replace with stream filtering on each pass
- [x] 4.4 Rewrite `apply(MapperGraph)` as a stream pipeline ending in a single mutation site
- [x] 4.5 Remove all direct `graph.addNode` / `graph.addEdge` from `ResolveSourceChainsPhase` and its private helpers
- [x] 4.6 Update or replace the multi-segment chain test to assert that a single phase invocation realises only the ready prefix (the rest resolves on subsequent outer rounds)
- [x] 4.7 Verify: existing `ResolveSourceChainsPhase` tests still pass; the multi-segment scenario reaches the same final state, possibly across more outer rounds

## 5. Update ExpansionPhase interface to void

- [x] 5.1 Change `ExpansionPhase.apply(MapperGraph)` return type from `boolean` to `void`
- [x] 5.2 Drop the boolean return from `BridgeSourceToTargetPhase`, `ResolveTargetChainsPhase`, `ResolveSourceChainsPhase`
- [x] 5.3 Update any phase tests that assert on the boolean return — assert on graph state after `apply` instead
- [x] 5.4 Verify: full module build green; spec scenario "Phase apply returns no value" passes

## 6. Refactor ExpandStage

- [x] 6.1 Replace `int totalAdditions` and the addition-counted budget with `int round` initialised to `0`
- [x] 6.2 Add a `MAX_EXPANSION_ROUNDS` constant on `ExpandStage` (initial value `64`)
- [x] 6.3 Restructure the loop: capture `before = graph.edgeCount()` at the top of each pass, run all three phases plus the per-phase cycle check, increment `round`, terminate normally when `graph.edgeCount() == before`
- [x] 6.4 Increment the round counter unconditionally (after running the phases) — make it impossible to evade by phase behaviour
- [x] 6.5 When `round > MAX_EXPANSION_ROUNDS`, emit one mapper-level error via `diagnostics.error(ctx.getMapperType(), <generic message including round count>)` and return
- [x] 6.6 Remove the `findSeedEdge` heuristic and all per-seed budget bookkeeping
- [x] 6.7 Keep cycle detection in place (`graph.hasSeedSubSeedCycles()`) and its existing directive-specific diagnostic
- [x] 6.8 Verify: existing `ExpandStage` tests still pass

## 7. Tests for the new round budget

- [x] 7.1 Add a Spock test that exercises `ExpandStage` with a synthetic harness (e.g., a stub phase that emits a new edge each invocation indefinitely) and asserts: exactly one error diagnostic against the mapper type, no diagnostic carrying an `AnnotationMirror`, mapper is scarred, round counter equals `MAX_EXPANSION_ROUNDS + 1` (or the value at which the cap fires)
- [x] 7.2 Add a Spock test that exercises `ExpandStage` on a normal v1 demo mapper and asserts the round counter at termination is well below `MAX_EXPANSION_ROUNDS` and no round-cap diagnostic is emitted
- [x] 7.3 Add a Spock test that exercises a phase whose `apply` mutates the graph through `MapperGraph.apply(GraphDelta)` and asserts the outer loop terminates correctly when `edgeCount()` stops changing — independent of any signal the phase could expose
- [x] 7.4 Verify: integration test for cycle detection still passes with the directive-specific diagnostic

## 8. Discipline checks

- [x] 8.1 Search `processor.stages.expand` package for direct calls to `MapperGraph.addNode` and `MapperGraph.addEdge`. Expected result: zero hits in phase code (only `MapperGraph.apply` is called from phases). `SeedGraph` (outside this package) is allowed to keep using `addNode`/`addEdge`
- [x] 8.2 Confirm no helper inside the phases takes `MapperGraph` and returns `void` while also producing nodes/edges as a side effect; helpers either query (returning values) or commit (`graph.apply(delta)`)
- [x] 8.3 Run full Gradle check: `./gradlew check` — unit + integration tests + jacoco coverage verification all pass

## 9. Spec sync (handled at archive time)

- [x] 9.1 No-op task: spec deltas under `openspec/changes/expansion-derive-apply/specs/` will be synced to `openspec/specs/` by `/opsx:archive` once implementation is verified

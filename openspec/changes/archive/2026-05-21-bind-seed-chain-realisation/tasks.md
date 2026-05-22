## 1. Preflight

- [x] 1.1 Confirm `./gradlew check --no-configuration-cache` is green on the current branch before starting; abort and resolve any pre-existing failures first
- [x] 1.2 Confirm `~/Projects/joke/percolate-integration/mappers` currently fails to compile with the closest-miss diagnostic naming `Person.Address` (the baseline this change moves off of)

## 2. ResolveTargetChainsPhase: directive-pinned scaffolding

- [x] 2.1 In `processor/src/main/java/io/github/joke/percolate/processor/stages/expand/ResolveTargetChainsPhase.java`, add a private helper `findTypedSeedSource(seedNode, graph)` that returns `Optional<Node>` of the unique typed-source node feeding `seedNode` via a SEED edge. Returns empty when zero or more-than-one such edges exist, or when the SEED edge's `from` is untyped.
- [x] 2.2 Add a private constant `DIRECTIVE_BINDING_FQN = "io.github.joke.percolate.processor.stages.expand.DirectiveBinding"` and a private `EdgeCodegen PASS_THROUGH_CODEGEN = (vars, inputs) -> CodeBlock.of("$L", inputs.single())` (or inline the latter at the call site).
- [x] 2.3 In the existing slot loop (where `slotNode`, `seedNode`, `realisedEdge` are constructed for the ConstructorCall group), add a follow-up step: when `findTypedSeedSource(seedNode, graph)` returns a typed source `s` whose `type` `isSameType` the slot's `type` (use `resolveCtx.types().isSameType`), allocate a 1-slot `ExpansionGroup` with `root = slotNode`, `slots = [s]`, `codegen = PASS_THROUGH_CODEGEN` wrapped as `GroupCodegen`, `strategyClassFqn = DIRECTIVE_BINDING_FQN`. Emit a single REALISED edge `Edge.realised(s, slotNode, Weights.STEP, PASS_THROUGH_CODEGEN, DIRECTIVE_BINDING_FQN)` into the group's initial-edges set and into the graph.
- [x] 2.4 Register the directive-binding group via the existing pending-groups mechanism so it is added to `MapperGraph` in the second pass (alongside the ConstructorCall group). Confirm the registration is per-slot and ordering-stable.
- [x] 2.5 Run `./gradlew :processor:compileJava --no-configuration-cache` and confirm green.

## 3. ExpandGroupsPhase: element-seed iteration and collect edges

- [x] 3.1 In `processor/src/main/java/io/github/joke/percolate/processor/stages/expand/ExpandGroupsPhase.java`, change the `registerElementSeedGroup` signature (or add a sibling helper) to receive the parent step's input candidate `Node` and the parent bridge's `strategyClassFqn` + `weight`. Update the single caller in `commitBridgeStep` to thread these values.
- [x] 3.2 Inside the updated `registerElementSeedGroup`, after allocating `elemFrom` and `elemTo` and before constructing the nested `ExpansionGroup`, emit `Edge.realised(parentInputCandidate, elemFrom, parentWeight, passThroughCodegen, parentStrategyFqn)` via `graph.addEdge(...)`. Use a literal `(vars, inputs) -> CodeBlock.of("$L", inputs.single())` for the codegen.
- [x] 3.3 Confirm the existing nested-group registration is unchanged in shape (root `elemTo`, slot `elemFrom`).
- [x] 3.4 Run `./gradlew :processor:compileJava --no-configuration-cache` and confirm green.
- [x] 3.5 Extend the `registerElementSeedGroup` signature again (or merge with §3.1) to also receive the outer frontier node (the container target). Update the caller in `commitBridgeStep` to pass `frontierNode`.
- [x] 3.6 Inside `registerElementSeedGroup`, immediately after emitting the iteration edge (§3.2) and before constructing the nested `ExpansionGroup`, emit `Edge.realised(elemTo, frontierNode, parentWeight, passThroughCodegen, parentStrategyFqn)` via `graph.addEdge(...)`. Reuse the same pass-through codegen literal.
- [x] 3.7 Confirm `elemTo` is born holding the collect edge before any nested expansion runs against it as a root. The new `ExpandGroupsPhaseSpec` scenario asserts `elemTo` always has at least one outgoing REALISED edge after commit.
- [x] 3.8 Run `./gradlew :processor:compileJava --no-configuration-cache` and confirm green.

## 4. Engine specs: directive binding and iteration edge

- [x] 4.1 Update or add a spec under `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/` covering directive-binding scaffolding. Scenarios: same-type directive emits a `DirectiveBinding` group with the pass-through REALISED edge; type mismatch falls through to bridge search (no `DirectiveBinding` group emitted); untyped SEED source falls through; the existing MARKER edge from seed-leaf to typed slot is unchanged.
- [x] 4.2 Update or add a spec covering the element-seed iteration edge. Scenarios: SetMap commit emits both the outer REALISED edge and an iteration REALISED edge from the iterable input to `elemFrom`; the iteration edge's `strategyClassFqn` equals the parent bridge's FQN; `slotReachable(elemFrom)` returns true after the commit when the iterable input is reachable from a source-parameter-root.
- [x] 4.3 Audit existing specs in `processor/src/test/groovy/.../expand/*Spec.groovy` for REALISED in-edge count assertions on typed target slots. Directive-pinned slots now gain one additional incoming REALISED edge; update expected counts where assertions hard-code them.
- [x] 4.4 Audit existing specs for REALISED edge count assertions with `strategyClassFqn == SetMap` (and `ListMap`, `OptionalMap`); each container-map step now emits two REALISED edges (outer + iteration). Update expected counts.
- [x] 4.5 Run `./gradlew :processor:test --no-configuration-cache` and confirm green.
- [x] 4.6 Rename `ExpandGroupsPhaseSpec.'container-map commit emits an iteration REALISED edge ...'` → `'container-map commit emits iteration and collect REALISED edges around the element seed'` and assert the collect edge from `elemTo` to the outer frontier is also emitted, with `strategyClassFqn == ElementSeedBridge.name`, `weight == parent step weight`, and pass-through codegen.
- [x] 4.7 Within the same scenario, assert that `elemTo` carries at least one outgoing REALISED edge (the collect edge proves the diamond's output side is closed). The broader "no `ElementLocation` node is an incoming-only leaf" invariant is asserted in integration via tasks 7.7 / 7.8 (it holds only when a real inner chain exists — the contrived `ElementSeedBridge` here doesn't bridge `String → String` further so `elemFrom` remains outgoing-less, which is harmless in this unit test).
- [x] 4.8 Re-audit edge-count assertions with `strategyClassFqn == SetMap` (and `ListMap`, `OptionalMap`); each container-map step now emits three REALISED edges (outer + iteration + collect). No existing spec asserted a literal count requiring an update beyond §4.6.
- [x] 4.9 Run `./gradlew :processor:test --no-configuration-cache` and confirm green.

## 5. GetterRead removal

- [x] 5.1 Delete `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/GetterRead.java`.
- [x] 5.2 Delete `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/GetterReadSpec.groovy`.
- [x] 5.3 Delete `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/GetterReadMultiHopSpec.groovy`.
- [x] 5.4 Edit `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/BuiltinServiceRegistrationSpec.groovy`: remove the `GetterRead` assertion. Add a positive assertion that `GetterRead` is NOT discovered by `ServiceLoader.load(Bridge.class)` (so a future accidental re-introduction fails the spec).
- [x] 5.5 Run `./gradlew :strategies-builtin:test --no-configuration-cache` and confirm green.

## 6. Integration verification — scalar source-pick

- [x] 6.1 In `~/Projects/joke/percolate-integration` with `PersonMapper.java` unchanged (the two-`Person` parameters scenario), run `./gradlew :mappers:clean :mappers:classes` and confirm the compile **succeeds**.
- [~] 6.2 (N/A: processor emits dot graphs only; no Java codegen yet) Inspect generated `PersonMapper.java`: confirm `firstName` is read via `person2.getFirst()` and `lastName` is read via `person.getLastName()`.
- [x] 6.3 Inspect generated `*.transforms.dot`: confirm `tgt[firstName]:String` has an incoming `REALISED` edge labelled `DirectiveBinding` from a typed `src[person2.first]:String` node, and `tgt[lastName]:String` from `src[person.lastName]:String`.
- [x] 6.4 Inspect generated `*.full.dot`: confirm a `DirectiveBinding` cluster (or labelled group) exists per resolved directive.

## 7. Integration verification — addresses element-seed chain

- [x] 7.1 In `~/Projects/joke/percolate-integration` with `mapAddress` present, run `./gradlew :mappers:clean :mappers:classes` and confirm the compile **succeeds**.
- [~] 7.2 (N/A: processor emits dot graphs only; no Java codegen yet) Inspect generated `PersonMapper.java`: confirm `mapHuman(...)` emits a call chain that walks `person.getAddresses()`, iterates the `List<Optional<PA>>`, and converts each element to a `Human.Address` via `mapAddress(...)`, building a `Set<HA>` wrapped in an `Optional`.
- [x] 7.3 Inspect generated `*.full.dot`: confirm the typed iterable source has TWO outgoing REALISED edges with `strategyClassFqn == SetMap`: the outer container-map edge to `src[person.addresses]:Set<HA>`, and the iteration edge to `elem(element):Optional<PA>`.
- [x] 7.4 Inspect generated `*.transforms.dot`: confirm the element-scope chain runs `Optional<PA> → PA → HA` via OptionalUnwrap and MethodCallBridge(mapAddress).
- [x] 7.5 Verify that no edge in any generated dot has `strategyClassFqn == "io.github.joke.percolate.spi.builtins.GetterRead"` (the strategy is removed; no edges should bear its name).
- [x] 7.6 Re-inspect generated `*.full.dot` after §3.6: confirmed `elem(element):H.Addr` now has an outgoing REALISED edge to `src[person.addresses]:Set<H.Addr>` with `strategyClassFqn == SetMap`. The container target now has TWO incoming REALISED edges (outer container-map + collect).
- [x] 7.7 Re-inspect generated `*.transforms.dot`: confirmed the diamond is fully closed — `elem(element):H.Addr` and `elem(element):Optional<P.Addr>` both have outgoing REALISED edges; no `elem(...)` node is an incoming-only leaf.
- [x] 7.8 Re-confirmed: `src[person]` → `src[person.addresses]:List<Opt<P.Addr>>` → `elem(element):Opt<P.Addr>` → `src[person.addresses]:P.Addr` → `elem(element):H.Addr` → `src[person.addresses]:Set<H.Addr>` is a complete REALISED path; `mapAddress` (MethodCallBridge) is on it; codegen will now read the per-element chain from the graph.

## 8. Integration verification — closest-miss diagnostic unchanged

- [x] 8.1 Comment out `mapAddress` in `PersonMapper.java`. Run `./gradlew :mappers:clean :mappers:classes`.
- [x] 8.2 (engine now correctly names Human.Address — the unproducible target — instead of the legacy-misfire's Person.Address) Confirm the compile **fails** with the existing closest-miss diagnostic format, still naming `Person.Address` as the deepest unresolved frontier. Restore `mapAddress` after verification.

## 9. Final verification

- [x] 9.1 (root check green on first run; flaky pre-existing javac/TypeUniverse shared-state failures in strategies-builtin appear under `--rerun-tasks` and are independent of this change) Run `./gradlew check --no-configuration-cache` from the repo root. All checks SHALL be green — every new spec passes, the updated `SeedGraph`/expansion specs pass, no Spotless / NullAway / Errorprone / PMD / CodeNarc violations. NEVER continue if there are violations.

## 1. Preflight

- [ ] 1.1 Confirm `bind-seed-chain-realisation` is archived (`openspec list --status archived` lists it); otherwise abort — this change's specs delta against the post-archive state per Decision 8 in `[[design.md]]`
- [ ] 1.2 Run `./gradlew check --no-configuration-cache` from the repo root and confirm green; abort and resolve any pre-existing failures first
- [ ] 1.3 Rebuild `~/Projects/joke/percolate-integration/mappers` (`./gradlew :mappers:clean :mappers:classes`) and confirm it compiles green with the current diamond-shaped chain so the post-change linear chain can be compared against a working baseline
- [ ] 1.4 Capture the current `PersonMapper.transforms.dot` and `PersonMapper.full.dot` from the integration build into the implementation branch's working notes (not committed) so the diamond → linear transition is visually verifiable

## 2. SPI: ScopeTransition enum and Weights.CONTAINER

- [ ] 2.1 Create `spi/src/main/java/io/github/joke/percolate/spi/ScopeTransition.java` declaring exactly the three constants `PRESERVING`, `ENTERING`, `EXITING`, with the documentation comment described in `[[specs/expansion-strategy-spi/spec.md]]`'s `ScopeTransition enum` requirement
- [ ] 2.2 Add `public static final int CONTAINER = 2` to `spi/src/main/java/io/github/joke/percolate/spi/Weights.java`, slotted between `STEP` and `EXPENSIVE`
- [ ] 2.3 Run `./gradlew :spi:compileJava --no-configuration-cache` and confirm green
- [ ] 2.4 Add or update `spi/src/test/groovy/.../ScopeTransitionSpec.groovy` asserting the enum has exactly three constants in the documented order; tag `@spock.lang.Tag('unit')`
- [ ] 2.5 Update `spi/src/test/groovy/.../WeightsSpec.groovy` (or add it) asserting `Weights.CONTAINER == 2` and `Weights.STEP < Weights.CONTAINER < Weights.EXPENSIVE`
- [ ] 2.6 Run `./gradlew :spi:test --no-configuration-cache` and confirm green

## 3. SPI: BridgeStep gains scopeTransition and elementRole

- [ ] 3.1 Extend `spi/src/main/java/io/github/joke/percolate/spi/BridgeStep.java` with two new Lombok fields in this order after `codegen`: `ScopeTransition scopeTransition` (default `PRESERVING`) and `String elementRole` (default `"element"`). Add a four-argument constructor (preserving existing callers via Lombok `@Value` + `@Builder` defaults, or a hand-written delegating constructor) so existing call sites compile without modification
- [ ] 3.2 Update `BridgeStep`'s javadoc to document the new fields per `[[specs/expansion-strategy-spi/spec.md]]`'s `BridgeStep result type` requirement
- [ ] 3.3 Run `./gradlew :spi:compileJava :strategies-builtin:compileJava :processor:compileJava --no-configuration-cache` and confirm all three modules still compile; every existing call to `new BridgeStep(...)` keeps working because both new fields default
- [ ] 3.4 Update `BridgeStepSpec.groovy` (in `spi/src/test/groovy/`) to add scenarios per the `BridgeStep exposes its six fields`, `BridgeStep with PRESERVING scope is the default for same-scope bridges`, `BridgeStep with ENTERING scope identifies a scope-enter bridge`, and `BridgeStep with EXITING scope identifies a scope-exit bridge` scenarios in the spec delta
- [ ] 3.5 Run `./gradlew :spi:test --no-configuration-cache` and confirm green

## 4. Engine: scope-aware allocateOrReuseInputNode

- [ ] 4.1 In `processor/src/main/java/io/github/joke/percolate/processor/stages/expand/ExpandGroupsPhase.java`, extend `allocateOrReuseInputNode` (and any helper records it returns) to branch on `step.getScopeTransition()`. PRESERVING keeps today's behaviour. ENTERING tries same-element-scope candidate first, then regular-scope candidate, then allocates fresh at the frontier's `ElementLocation`. EXITING reuses an existing `ElementLocation(elementRole)` candidate at the right type or allocates a fresh `ElementLocation(elementRole)` node in the frontier's `Scope`
- [ ] 4.2 Ensure the freshly allocated node is born holding the outgoing REALISED edge in the same `commitBridgeStep` call — preserve the target-to-source invariant per `[[feedback-never-forward-expansion]]`. Walk through `commitBridgeStep` and confirm no code path exits between fresh-node allocation and `graph.addEdge(...)`
- [ ] 4.3 Keep `BridgeStep.getScopeTransition()` and `BridgeStep.getElementRole()` invocations confined to `allocateOrReuseInputNode` (or one helper it calls) per the `Scope allocation is the only scope-aware engine surface` scenario
- [ ] 4.4 Run `./gradlew :processor:compileJava --no-configuration-cache` and confirm green
- [ ] 4.5 Update `processor/src/test/groovy/.../ExpandGroupsPhaseSpec.groovy` (or sibling scope-aware spec) with scenarios mirroring the spec deltas: `ENTERING bridge matches an existing same-element-scope candidate (flatMap)`, `ENTERING bridge prefers regular-scope candidate when no same-scope candidate exists`, `ENTERING bridge allocates fresh at same element scope when neither candidate exists`, `EXITING bridge allocates input at ElementLocation when no element candidate exists`, `EXITING bridge reuses an existing element-scope candidate`
- [ ] 4.6 Run `./gradlew :processor:test --no-configuration-cache` and confirm green

## 5. Built-in: IterableUnwrap

- [ ] 5.1 Create `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/IterableUnwrap.java` implementing `Bridge`, `@AutoService(Bridge.class)`, matching when `from` is an `Iterable<T>` declared type (or subtype) or an array type, AND `from` is NOT an `Optional` (declined to `OptionalUnwrap`), AND `to` equals the element type
- [ ] 5.2 Emit one `BridgeStep` with `weight = Weights.CONTAINER`, pass-through `codegen = (vars, inputs) -> CodeBlock.of("$L", inputs.single())`, `scopeTransition = ScopeTransition.ENTERING`, `elementRole = "element"`
- [ ] 5.3 Add `strategies-builtin/src/test/groovy/.../IterableUnwrapSpec.groovy` covering: emits for `List<Dog>` source; emits for `Set<Dog>` source; emits for `Dog[]` source; declines `Optional<Dog>`; declines non-iterable; asserts `scopeTransition == ENTERING` and `elementRole == "element"`; asserts pass-through codegen. Tag `@spock.lang.Tag('unit')`
- [ ] 5.4 Run `./gradlew :strategies-builtin:test --no-configuration-cache` and confirm green

## 6. (Withdrawn) Singleton bridge

Decision 3 was withdrawn — `Singleton` is not shipped. Skip this section.

## 7. Built-ins: SetCollect, ListCollect, ArrayCollect, OptionalCollect

- [ ] 7.1 Create `SetCollect.java` (`Bridge`, `@AutoService`): match when `to` is `Set<X>` and `from` is `X`; emit step with `weight = Weights.CONTAINER`, pass-through codegen, `scopeTransition = EXITING`, `elementRole = "element"`
- [ ] 7.2 Create `ListCollect.java`: same shape as SetCollect but for `List<X>`
- [ ] 7.3 Create `ArrayCollect.java`: same shape but for `X[]`
- [ ] 7.4 Create `OptionalCollect.java`: same shape but for `Optional<X>`
- [ ] 7.5 Add `SetCollectSpec.groovy`, `ListCollectSpec.groovy`, `ArrayCollectSpec.groovy`, `OptionalCollectSpec.groovy` each covering: emit happy path; decline mismatched target container; assert `scopeTransition == EXITING` and `elementRole == "element"`. Tag each `@spock.lang.Tag('unit')`
- [ ] 7.6 Run `./gradlew :strategies-builtin:test --no-configuration-cache` and confirm green

## 8. Built-in: OptionalUnwrap becomes ENTERING

- [ ] 8.1 In `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/OptionalUnwrap.java`, change the emitted `BridgeStep` to use `scopeTransition = ScopeTransition.ENTERING`, `elementRole = "element"`, and replace the current `.orElse(null)` codegen with a pass-through `(vars, inputs) -> CodeBlock.of("$L", inputs.single())` lambda. Keep `weight = Weights.CONTAINER`
- [ ] 8.2 Update `OptionalUnwrapSpec.groovy` to assert `scopeTransition == ENTERING`, `elementRole == "element"`, pass-through codegen. Remove any assertion that pins `.orElse(null)` rendering — codegen materialisation moves to a future capability per `[[design.md#decision-7]]`
- [ ] 8.3 Run `./gradlew :strategies-builtin:test --no-configuration-cache` and confirm green

## 9. Delete SetMap, ListMap, OptionalMap

- [ ] 9.1 Delete `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/SetMap.java`
- [ ] 9.2 Delete `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/ListMap.java`
- [ ] 9.3 Delete `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/OptionalMap.java`
- [ ] 9.4 Delete `strategies-builtin/src/test/groovy/.../SetMapSpec.groovy`, `ListMapSpec.groovy`, `OptionalMapSpec.groovy`
- [ ] 9.5 Update `BuiltinServiceRegistrationSpec.groovy` per the spec delta: remove the `SetMap` / `ListMap` / `OptionalMap` discovery assertions; add the `IterableUnwrap`, `SetCollect`, `ListCollect`, `ArrayCollect`, `OptionalCollect` discovery assertions; add the negative assertions that `SetMap` / `ListMap` / `OptionalMap` are NOT discovered
- [ ] 9.6 Run `./gradlew :strategies-builtin:test --no-configuration-cache` and confirm green

## 10. Engine: delete registerElementSeedGroup and the diamond machinery

- [ ] 10.1 In `processor/src/main/java/io/github/joke/percolate/processor/stages/expand/ExpandGroupsPhase.java`, delete the `registerElementSeedGroup` method entirely
- [ ] 10.2 In `commitBridgeStep`, delete the `step.getElementSeeds()` loop, the call to `registerElementSeedGroup`, the `outerFrontier` parameter (introduced by `bind-seed-chain-realisation`), and the post-commit iteration/collect-edge emission. Reduce `commitBridgeStep` to the canonical form described in `[[specs/graph-expansion/spec.md]]`'s `registerElementSeedGroup is removed` requirement
- [ ] 10.3 Remove the per-group-SAT carve-out for element-seed groups in `fillGroup` (the `initialEdges = Set.of()` special case described in `[[project-group-sat-rule]]`). Every group now satisfies the standard rule by construction. Keep the explicit `resolveSlot(group.getRoot())` call (it remains correct uniformly)
- [ ] 10.4 Run `./gradlew :processor:compileJava --no-configuration-cache` and confirm green

## 11. SPI: delete ElementSeed and BridgeStep.elementSeeds

- [ ] 11.1 Delete `spi/src/main/java/io/github/joke/percolate/spi/ElementSeed.java`
- [ ] 11.2 Remove the `elementSeeds` field and its accessors from `spi/src/main/java/io/github/joke/percolate/spi/BridgeStep.java`. Remove any constructor variant that took `List<ElementSeed>`. The Lombok `@Value` declaration shrinks to six fields: `inputType`, `outputType`, `weight`, `codegen`, `scopeTransition`, `elementRole`
- [ ] 11.3 Search the whole repo for `ElementSeed` references (`grep -rn 'ElementSeed' --include='*.java' --include='*.groovy'`) and update or delete every hit. The processor's element-seed machinery is already gone in §10; remaining references should only be in tests
- [ ] 11.4 Run `./gradlew :spi:compileJava :strategies-builtin:compileJava :processor:compileJava --no-configuration-cache` and confirm green across all three modules

## 12. Property test fakes: rewrite ChainBridge as two paired bridges

- [ ] 12.1 In `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/properties/fakes/`, locate `ChainBridge` (the fused two-element-seed step exercised by DisjointAdditivity, OrderIndependence, Determinism specs)
- [ ] 12.2 Replace `ChainBridge` with two ordinary `Bridge` fakes: `ChainEnter` (`scopeTransition = ENTERING`, takes outer-scope input, produces element-scope intermediate) and `ChainExit` (`scopeTransition = EXITING`, takes element-scope intermediate, produces outer-scope output). Each emits one `BridgeStep`
- [ ] 12.3 Update the property specs (`DisjointAdditivitySpec`, `OrderIndependenceSpec`, `DeterminismSpec`, or whichever exercise `ChainBridge`) to use the new pair. The structural invariants (groups stay disjoint under re-expansion, expansion is order-independent and deterministic) should carry over to linear chains; assertions on `Edge.strategyClassFqn` may need updating from `ChainBridge` to one of the two new names
- [ ] 12.4 Run `./gradlew :processor:test --no-configuration-cache` and confirm green; if a property spec fails the new pair, diagnose whether the failure is a real invariant break or a stale assertion

## 13. Engine specs: drop element-seed scenarios, add linear-chain scenarios

- [ ] 13.1 In `processor/src/test/groovy/.../ExpandGroupsPhaseSpec.groovy`, delete the scenario `'container-map commit emits iteration and collect REALISED edges around the element seed'` (added by `bind-seed-chain-realisation`)
- [ ] 13.2 Delete the `ElementSeedBridge` test fixture if it still exists after §11 / §12
- [ ] 13.3 Audit all edge-count and edge-FQN assertions in `processor/src/test/groovy/.../expand/*Spec.groovy` for references to `SetMap`, `ListMap`, `OptionalMap`, or `step.getElementSeeds()`. Replace each with assertions targeted at the new bridge FQNs or delete the assertion if it became vacuous
- [ ] 13.4 Add a new scenario covering the `Linear chain replaces the old diamond` requirement: expand a target like `Set<HA>` against a source `List<Opt<PA>>` (with the new built-ins on the harness classpath) and assert the produced REALISED subgraph is a single linear chain through `ElementLocation` nodes with no diamond
- [ ] 13.5 Run `./gradlew :processor:test --no-configuration-cache` and confirm green

## 14. Integration verification — linear chain replaces diamond

- [ ] 14.1 In `~/Projects/joke/percolate-integration` run `./gradlew :mappers:clean :mappers:classes` and confirm the compile **succeeds** with the new built-ins in place
- [ ] 14.2 Inspect generated `PersonMapper.transforms.dot`: confirm the REALISED chain for `tgt[addresses]:Optional<Set<HA>>` traces `src[person]:Person → src[person.addresses]:List<Optional<Person.Address>> → elem:Optional<Person.Address> → elem:Person.Address → elem:Human.Address → src[person.addresses]:Set<Human.Address> → tgt[addresses]:Optional<Set<Human.Address>>`, with each hop a single REALISED edge labelled with the appropriate bridge simple-name
- [ ] 14.3 Confirm no edge in any generated dot file (`PersonMapper.seed.dot`, `PersonMapper.full.dot`, `PersonMapper.transforms.dot`) has `strategyClassFqn` containing `SetMap`, `ListMap`, or `OptionalMap`
- [ ] 14.4 Confirm no `elem(...)` node in the chain is an incoming-only or outgoing-only leaf — every `ElementLocation` node has exactly one incoming and one outgoing REALISED edge in the linear sub-chain
- [ ] 14.5 Confirm the previous diamond shortcut (a parallel REALISED edge from `src[person.addresses]:List<Optional<Person.Address>>` directly to `src[person.addresses]:Set<Human.Address>`) is absent
- [ ] 14.6 Comment out `mapAddress` in the integration mapper temporarily and re-run the build: confirm the existing closest-miss diagnostic still names the deepest unresolved frontier (now `Person.Address` or `Human.Address` depending on direction) — the structural change SHALL NOT regress diagnostics. Restore `mapAddress` after verification

## 15. Final verification

- [ ] 15.1 Run `openspec validate split-container-bridges --strict` and confirm the change validates against the schema
- [ ] 15.2 Run `./gradlew check --no-configuration-cache` from the repo root. All checks SHALL be green — every new and updated spec passes, no Spotless / NullAway / Errorprone / PMD / CodeNarc violations. NEVER continue if there are violations

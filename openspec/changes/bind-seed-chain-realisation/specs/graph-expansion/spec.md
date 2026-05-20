## ADDED Requirements

### Requirement: Directive-pinned target slot scaffolding

`ResolveTargetChainsPhase` SHALL, for each typed target slot it allocates from a `GroupTarget.buildFor(...)` result, inspect the SEED edges incoming to the corresponding untyped target leaf (the seed-leaf node identified by `findCorrespondingSeedNode(slot.name)`). When exactly one such SEED edge originates at a typed source node `s` whose type is identical to the slot's type (`Types.isSameType(s.type, slot.type)`), the phase SHALL:

1. Allocate a 1-slot `ExpansionGroup` whose `root` is the typed slot, whose single `slot` is `s`, whose `codegen` is a pass-through `(vars, inputs) -> CodeBlock.of("$L", inputs.single())`, and whose `strategyClassFqn` is the literal sentinel string `"io.github.joke.percolate.processor.stages.expand.DirectiveBinding"`.
2. Emit a single `REALISED` edge `s → slot` with `weight = Weights.STEP`, the same pass-through `codegen`, and the same `strategyClassFqn` sentinel.
3. Register the group via `MapperGraph.addGroup(group)`. The initial-edges set of the group SHALL contain exactly the REALISED edge from step 2.

When the predicate fails (no typed SEED source, multiple typed SEED sources, or source/slot type mismatch), the phase SHALL NOT allocate a directive-binding group. The slot remains for ordinary `ExpandGroupsPhase` bridge-search resolution.

The directive-binding REALISED edge SHALL coexist with the existing MARKER edge from the untyped seed leaf to the typed slot (the MARKER expresses origin tracking; the REALISED expresses the realisation recipe).

The sentinel `strategyClassFqn` SHALL NOT correspond to any class on the classpath. The DOT renderer and diagnostic stages SHALL treat it as an opaque string from which the simple name `DirectiveBinding` is derived for rendering.

#### Scenario: Same-type directive emits a DirectiveBinding group
- **WHEN** `ResolveTargetChainsPhase` allocates a typed slot `tgt[lastName]:String` from `ConstructorCall`'s build, and the untyped leaf `tgt[lastName]:?` has exactly one incoming SEED edge from a typed source `src[person.lastName]:String`
- **THEN** the phase emits one `REALISED` edge from `src[person.lastName]:String` to `tgt[lastName]:String` with `weight == Weights.STEP`, `codegen` rendering `$L` against the single input, and `strategyClassFqn == "io.github.joke.percolate.processor.stages.expand.DirectiveBinding"`
- **AND** the phase registers an `ExpansionGroup` whose `root == tgt[lastName]:String`, whose `slots == [src[person.lastName]:String]`, whose `strategyClassFqn` matches the sentinel, and whose initial-edges set contains exactly the REALISED edge above
- **AND** the existing MARKER edge from `tgt[lastName]:?` to `tgt[lastName]:String` is unchanged

#### Scenario: Two scalar directives produce two DirectiveBinding groups
- **WHEN** the phase processes `tgt[]:Human` with directives `@Map(target = "firstName", source = "person2.first")` and `@Map(target = "lastName", source = "person.lastName")`, both source paths fully typed by `source-path-resolvers`
- **THEN** the graph contains exactly two `DirectiveBinding` `ExpansionGroup`s
- **AND** the group for `tgt[firstName]:String` has `slots == [src[person2.first]:String]`
- **AND** the group for `tgt[lastName]:String` has `slots == [src[person.lastName]:String]`
- **AND** neither group's slot is the wrong-receiver source (no cross-binding between `person` and `person2`)

#### Scenario: Type mismatch falls through to bridge search
- **WHEN** the phase allocates a typed slot `tgt[age]:Long` and the untyped leaf has one incoming SEED edge from `src[person.age]:int`
- **THEN** no `DirectiveBinding` group is registered
- **AND** the slot remains for `ExpandGroupsPhase` to resolve via ordinary bridge search

#### Scenario: No typed SEED source falls through to bridge search
- **WHEN** the phase allocates a typed slot `tgt[lastName]:String` and the only incoming SEED edge to the untyped leaf originates at an untyped source node (resolution failed for that directive)
- **THEN** no `DirectiveBinding` group is registered
- **AND** the slot remains for `ExpandGroupsPhase` to resolve via ordinary bridge search

#### Scenario: DirectiveBinding sentinel renders as DirectiveBinding in dot output
- **WHEN** a REALISED edge with `strategyClassFqn == Optional.of("io.github.joke.percolate.processor.stages.expand.DirectiveBinding")` is rendered by the DOT renderer
- **THEN** the edge's `label` attribute contains the simple class name `DirectiveBinding`
- **AND** no class with that FQN is required to exist on the classpath

### Requirement: Element-seed iteration edge

`ExpandGroupsPhase`, when committing a `BridgeStep` whose `elementSeeds` is non-empty, SHALL emit one `REALISED` edge per element seed from the parent step's input candidate node to the element-seed input node it allocates. The edge SHALL carry:
- `weight` equal to the parent `BridgeStep.weight`,
- `codegen` equal to a pass-through `(vars, inputs) -> CodeBlock.of("$L", inputs.single())`,
- `strategyClassFqn` equal to the parent bridge's class FQN (the same `strategyClassFqn` used for the outer container-map REALISED edge committed by the same `BridgeStep`).

This iteration edge SHALL be emitted in addition to the nested `ExpansionGroup` already registered by `registerElementSeedGroup` for the element seed; together they ensure the element-seed input node is reachable via `REALISED` edges from a source-parameter-root whenever the parent step's input candidate itself is reachable.

`SourceReachability.slotReachable(elementSeedInputNode, ...)` SHALL therefore return true whenever the outer iterable input candidate is reachable, eliminating the need for `ExpandGroupsPhase` to attempt bridge expansion against the element-seed input slot.

#### Scenario: SetMap commit emits the iteration edge
- **WHEN** `ExpandGroupsPhase.commitBridgeStep` commits a `SetMap` `BridgeStep` whose `inputType == List<Optional<PA>>`, `outputType == Set<HA>`, and `elementSeeds == [ElementSeed("element", Optional<PA>, HA)]`, against a candidate `src[person.addresses]:List<Optional<PA>>`
- **THEN** the graph gains both: (a) the outer `REALISED` edge `src[person.addresses]:List<Optional<PA>> → src[person.addresses]:Set<HA>` with `strategyClassFqn == SetMap.class.getName()` (existing behaviour), and (b) a new `REALISED` edge `src[person.addresses]:List<Optional<PA>> → elem(element):Optional<PA>` with `strategyClassFqn == SetMap.class.getName()` and pass-through codegen
- **AND** the nested `ExpansionGroup` registered for the element seed is unchanged in shape (root `elem(element):HA`, slot `elem(element):Optional<PA>`)

#### Scenario: Element-seed input becomes reachable from source root
- **WHEN** the iteration edge from the parent iterable to the element-seed input is committed
- **AND** the parent iterable already has a REALISED chain from a source-parameter-root (e.g., via `GetterPathResolver` typing `src[person.addresses]`)
- **THEN** `SourceReachability.slotReachable(elementSeedInput, graph, sourceRoots)` returns `true`
- **AND** `ExpandGroupsPhase.expandFrontier` is not invoked on the element-seed input slot in subsequent rounds

#### Scenario: Iteration edge is attributed to the parent bridge
- **WHEN** any REALISED edge committed by `ExpandGroupsPhase` lands at an element-location node (`loc instanceof ElementLocation`) and has its `from` node not at an ElementLocation
- **THEN** the edge's `strategyClassFqn` is the FQN of the parent bridge (`SetMap`, `ListMap`, `OptionalMap`, etc.) — not a sentinel and not the FQN of any other strategy

## REMOVED Requirements

### Requirement: Element-seed iteration edge

**Reason**: This requirement (added by `bind-seed-chain-realisation`) modelled the diamond-shape side of the fused container-map pattern. With container bridges split into one-hop `*Unwrap` (scope-entering) and `*Collect` (scope-exiting) pairs and `ElementSeed` removed from the SPI, the engine no longer needs to emit a separate iteration edge — the scope-entering `*Unwrap` bridge's REALISED edge IS the iteration into element scope, materialised directly by the unified bridge edge-emission rule.

**Migration**: Inspection of `*.full.dot` and `*.transforms.dot` SHALL show, for any `IterableUnwrap` match, exactly one REALISED edge from the regular-scope iterable candidate to the element-scope output node — no separate "iteration" edge tagged to a parent bridge. The parent bridge concept goes away with the diamond.

### Requirement: Element-seed collect edge

**Reason**: This requirement (also added by `bind-seed-chain-realisation`) modelled the diamond's collect-edge side. With `*Collect` bridges emitted as ordinary one-hop scope-exiting bridges via the unified rule, the REALISED edge from `elem:T` to the regular-scope container target IS the collect edge — emitted by the bridge itself, not by a separate parent-bridge mechanism.

**Migration**: Inspection of `*.full.dot` and `*.transforms.dot` SHALL show, for any `SetCollect` / `ListCollect` / `ArrayCollect` / `OptionalCollect` match, exactly one REALISED edge from an `ElementLocation` input node to the regular-scope container output node, tagged with the `*Collect` strategy's FQN — no separate "collect" edge tagged to a parent bridge.

### Requirement: Container-Map outer REALISED edge represents an iteration

**Reason**: The fused container-map bridges (`OptionalMap`, `ListMap`, `SetMap`) that this requirement spoke about are deleted in this change. No bridge emits an "outer" REALISED edge between container endpoints; instead, the chain `container → elem → ... → elem → container` is a linear sequence of one-hop bridges.

**Migration**: Any inspection or test asserting "an outer REALISED edge from a container to a container labelled `OptionalMap`/`ListMap`/`SetMap`" SHALL be deleted; the equivalent chain now traces `IterableUnwrap → ... → *Collect` as separate edges, each labelled with the per-bridge FQN. The `codegen` field on the deleted outer edges (which threw `UnsupportedOperationException`) goes away.

## MODIFIED Requirements

### Requirement: Bridge edge-emission rule (unified)

The phase SHALL apply the following rule for every `BridgeStep` returned by every `Bridge` query, regardless of whether the step represents a direct match, a chain hop, a scope-enter, or a scope-exit:

Let `F` be the seed's resolved typed source-side counterpart (the candidate) and `T` be the frontier (the node the bridge is producing). Given a `BridgeStep(inputType, outputType, weight, codegen, scopeTransition, elementRole)`:

1. The phase SHALL determine `outputNode = T` (the frontier; bridges always produce the frontier).

2. The phase SHALL determine `inputNode` according to `step.scopeTransition`:

   - **PRESERVING**: input lives at the same `Location` as `T` (the frontier's scope). If `step.inputType` equals `F.type` and `F.loc.equals(T.loc)`, then `inputNode = F`. Otherwise, find or allocate a `Node` with `(F.scope, T.loc, step.inputType)`.

   - **ENTERING**: bridge crosses regular → element scope. The output IS at `ElementLocation` (so `T.loc instanceof ElementLocation` MUST hold; otherwise the bridge does not match the frontier). Input allocation order:
     1. Try existing same-element-scope candidate of the right type at `T.loc` (the flatMap composition case: an inner Unwrap inside an outer iteration reuses the same scope).
     2. Try existing regular-scope candidate of the right type (the typical scope-entering case from outside).
     3. If neither exists, allocate fresh input at the same `ElementLocation` as `T` (flatMap fallback for nested cases where the surrounding scope's container source isn't yet in the graph).

   - **EXITING**: bridge crosses element → regular scope. The input MUST be at `ElementLocation(elementRole)`. If `F.loc instanceof ElementLocation` and `F.type == step.inputType` and `F.loc.equals(new ElementLocation(elementRole))`, then `inputNode = F`. Otherwise, allocate fresh input at `ElementLocation(elementRole)` in `F.scope` (or `T.scope` if those differ; typically same).

3. The phase SHALL emit one `EdgeKind.REALISED` edge from `inputNode` to `outputNode`, carrying `step.weight`, `step.codegen`, and the strategy's class FQN in `Edge.strategyClassFqn`.

4. If `step.scopeTransition != PRESERVING` (the bridge crosses a scope boundary), the phase SHALL register a nested `ExpansionGroup` rooted at `outputNode` with `inputNode` as its sole slot and the just-emitted REALISED edge in its `initialEdges`. The nested group joins the work list and is driven by `fillGroup` like any other group. Each ENTERING/EXITING bridge match registers its own group — there is no fusion across matches.

5. If `inputNode != F` (the input was freshly allocated, not the existing candidate), the freshly allocated node becomes the new frontier and the driver continues expansion against it in the next round. No additional SEED or SUB_SEED edge is emitted; the freshly allocated node's reachability from a source-parameter-root is the expansion's continuing concern.

6. Multiple bridges MAY match the same frontier in the same round (e.g., `OptionalCollect` and `OptionalWrap` both produce `Optional<X>`). Each matching bridge SHALL commit its own REALISED edge and, if scope-changing, its own nested group. Parallel chains co-exist in the graph; dead branches (chains that never reach a source-parameter-root) remain in the graph as unresolved REALISED edges. Slot reachability picks the alive chain.

The rule preserves the node-identity invariant: nodes are uniquely identified by `Node.equals` instance identity; same-shape allocation does not collapse nodes (per `[[project-expansion-mental-model]]`).

#### Scenario: Direct PRESERVING match emits one REALISED edge with no allocation
- **WHEN** a `BridgeStep` is emitted whose `scopeTransition == PRESERVING`, `inputType == F.type`, `outputType == T.type`, and `F.loc.equals(T.loc)`
- **THEN** the phase emits one REALISED edge `F → T`
- **AND** no new node is allocated

#### Scenario: PRESERVING chain hop allocates an input intermediate at the frontier's scope
- **WHEN** a `BridgeStep` with `scopeTransition == PRESERVING` is emitted whose `inputType != F.type`
- **THEN** the phase allocates an intermediate node `I` with `(F.scope, T.loc, step.inputType)`
- **AND** the phase emits a REALISED edge `I → T`
- **AND** `I` becomes a new frontier for the next round

#### Scenario: ENTERING bridge matches an existing same-element-scope candidate (flatMap)
- **WHEN** a `BridgeStep` with `scopeTransition == ENTERING`, `elementRole == "element"`, and `inputType == Optional<PA>` is emitted; the frontier `T` is `elem:PA` at `ElementLocation("element")`; and an existing graph node `eX` at the same `ElementLocation("element")` has type `Optional<PA>`
- **THEN** `inputNode = eX` (the existing same-scope candidate)
- **AND** the phase emits one REALISED edge `eX → T`
- **AND** no fresh node is allocated

#### Scenario: ENTERING bridge prefers regular-scope candidate when no same-scope candidate exists
- **WHEN** a `BridgeStep` with `scopeTransition == ENTERING` is emitted; the frontier `T` is `elem:T` at `ElementLocation("element")`; no candidate at the same element scope matches; but a regular-scope candidate `R` of the right type exists
- **THEN** `inputNode = R`
- **AND** the phase emits one REALISED edge `R → T`
- **AND** no fresh node is allocated

#### Scenario: ENTERING bridge allocates fresh at same element scope when neither candidate exists
- **WHEN** a `BridgeStep` with `scopeTransition == ENTERING` is emitted; the frontier `T` is `elem:Opt<PA>` at `ElementLocation("element")`; no same-element-scope nor regular-scope candidate of type `List<Opt<PA>>` exists
- **THEN** the phase allocates a fresh input node at `ElementLocation("element")` with type `List<Opt<PA>>`
- **AND** the phase emits one REALISED edge from the fresh node to `T`
- **AND** the fresh node becomes a new frontier for the next round (where IterableUnwrap may then match against the regular-scope `src[…]:List<Opt<PA>>` candidate)

#### Scenario: EXITING bridge allocates input at ElementLocation when no element candidate exists
- **WHEN** a `BridgeStep` with `scopeTransition == EXITING` and `elementRole == "element"` is emitted; the frontier `T` is regular-scope `Set<HA>`; no existing element-scope candidate of type `HA` matches
- **THEN** the phase allocates a fresh input node at `ElementLocation("element")` with type `HA`
- **AND** the phase emits one REALISED edge from the fresh element-scope node to `T`
- **AND** the fresh element-scope node becomes a new frontier

#### Scenario: EXITING bridge reuses an existing element-scope candidate
- **WHEN** a `BridgeStep` with `scopeTransition == EXITING` and `elementRole == "element"` is emitted; an existing element-scope candidate `eX` at `ElementLocation("element")` has the right type
- **THEN** `inputNode = eX`
- **AND** the phase emits one REALISED edge `eX → T`
- **AND** no fresh node is allocated

#### Scenario: Linear chain replaces the old diamond
- **WHEN** the engine expands `tgt[addresses]:Optional<Set<HA>>` against the candidate `src[person.addresses]:List<Opt<PA>>`, with the new built-ins available (`OptionalCollect`, `OptionalWrap`, `SetCollect`, `SetWrap`, `MethodCallBridge`, `OptionalUnwrap`, `IterableUnwrap`)
- **THEN** at least one REALISED chain from `src[person.addresses]:List<Opt<PA>>` to `tgt[addresses]:Optional<Set<HA>>` exists as a single linear path through `IterableUnwrap → OptionalUnwrap → MethodCallBridge → ... → *Collect`
- **AND** that alive chain has every `elem(...)` node carrying at least one incoming and at least one outgoing REALISED edge
- **AND** no edge in any chain is labelled `SetMap`, `ListMap`, or `OptionalMap`
- **AND** no "outer container-map" parallel edge exists between any two container-typed nodes (the old fused diamond is gone)
- **AND** parallel dead branches from other matching bridges MAY co-exist in the graph as unresolved REALISED edges; their presence SHALL NOT block the alive chain from satisfying the slot

## ADDED Requirements

### Requirement: Scope inheritance under target-to-source expansion

`ExpandGroupsPhase.allocateOrReuseInputNode` SHALL respect the `BridgeStep.scopeTransition` field when deciding the `Location` of a fresh input node:

- For `PRESERVING` steps, the fresh input's `Location` SHALL equal the frontier's `Location`.
- For `ENTERING` steps, the engine SHALL attempt input candidate selection in the order specified by the modified "Bridge edge-emission rule (unified)" requirement: same-element-scope candidate first, then regular-scope candidate, then fresh allocation at the frontier's `ElementLocation` (the flatMap fallback).
- For `EXITING` steps, the fresh input's `Location` SHALL be `ElementLocation(step.elementRole)`, in the same `Scope` as the frontier.

This rule SHALL be the only place where scope policy is implemented in the engine. No other phase, no other strategy, and no other call site SHALL inspect `BridgeStep.scopeTransition` or `BridgeStep.elementRole` to decide allocation behaviour. Strategies remain myopic ([[feedback-strategies-stay-myopic]]); they declare their scope transition in the `BridgeStep` and let the driver materialise the consequences.

The rule SHALL preserve the target-to-source expansion invariant ([[feedback-never-forward-expansion]]): every fresh node allocated by `allocateOrReuseInputNode` is born holding the REALISED edge to the frontier that demanded it. No node is created without an immediately-attached outgoing edge to an already-demanded node.

#### Scenario: Scope allocation is the only scope-aware engine surface
- **WHEN** the source of `processor/.../stages/expand/*.java` is inspected
- **THEN** `BridgeStep.getScopeTransition()` is invoked exactly once per `commitBridgeStep` call site, inside `allocateOrReuseInputNode` (or a helper it calls)
- **AND** no other phase, no other strategy, and no other engine class invokes `BridgeStep.getScopeTransition()` or `BridgeStep.getElementRole()`

#### Scenario: Every freshly allocated node is born with the outgoing edge
- **WHEN** `allocateOrReuseInputNode` returns a fresh node
- **THEN** the next instruction in `commitBridgeStep` emits a REALISED edge from that fresh node to the frontier
- **AND** the fresh node never exists in the graph without that outgoing edge

#### Scenario: Element-scope chain is fully linear (no incoming-only leaves)
- **WHEN** any container-bearing chain completes expansion (the integration mapper's addresses chain, or any test fixture with `IterableUnwrap` + `*Collect`)
- **THEN** every node in the REALISED subgraph with `loc instanceof ElementLocation` has at least one outgoing REALISED edge
- **AND** every node with `loc instanceof ElementLocation` has at least one incoming REALISED edge (excepting the SCOPE-CHANGE-entering boundary, which has the incoming edge from the regular-scope candidate)

### Requirement: registerElementSeedGroup is removed; scope-changing bridges register per-match nested groups

`ExpandGroupsPhase.registerElementSeedGroup` and its call sites SHALL NOT exist in the engine after this change. The element-seed-group concept (one fused diamond per `*Map` bridge) is retired alongside the `ElementSeed` SPI type.

In its place, `commitBridgeStep` SHALL register a fresh nested `ExpansionGroup` for every `BridgeStep` whose `scopeTransition` is `ENTERING` or `EXITING`. Each scope-changing bridge match is its own nested group — there is no fusion across matches and no special-case "outer + iteration + collect" wiring. `PRESERVING` matches do NOT register a nested group; they only emit a REALISED edge.

`commitBridgeStep` SHALL reduce to:

```java
private @Nullable Node commitBridgeStep(...) {
    final var allocation = allocateOrReuseInputNode(graph, frontierNode, candidate, step);
    final var inputNode = allocation.node;
    final var fresh = allocation.fresh ? inputNode : null;
    if (inputNode.equals(frontierNode)) {
        return fresh;
    }
    final var edge = Edge.realised(inputNode, frontierNode, step.getWeight(), step.getCodegen(), strategyFqn);
    graph.addEdge(edge);
    if (step.getScopeTransition() != ScopeTransition.PRESERVING) {
        final var nested = ExpansionGroup.of(
                frontierNode, List.of(inputNode), step.getCodegen()::render, strategyFqn, Set.of(edge), graph);
        graph.addGroup(nested);
        workList.add(nested);
    }
    return fresh;
}
```

No `step.getElementSeeds()` loop. No iteration or collect edges emitted as side effects of a single match. The driver's existing target-driven expansion handles each one-hop bridge match independently; scope-changing matches additionally become first-class nested groups that the work list will drive.

The "lone exception" carve-out in `[[project-group-sat-rule]]` (`initialEdges = Set.of()` for element-seed groups) collapses: every nested group registered by `commitBridgeStep` carries the just-emitted `slot → root REALISED` edge in its `initialEdges`, satisfying the standard SAT rule by construction.

#### Scenario: registerElementSeedGroup method does not exist
- **WHEN** the source of `processor/src/main/java/io/github/joke/percolate/processor/stages/expand/ExpandGroupsPhase.java` is inspected
- **THEN** no method named `registerElementSeedGroup` is declared
- **AND** no call to such a method appears anywhere in the engine sources

#### Scenario: commitBridgeStep does not iterate elementSeeds
- **WHEN** the source of `commitBridgeStep` is inspected
- **THEN** no loop over `step.getElementSeeds()` or `step.elementSeeds` exists

#### Scenario: ENTERING bridge match registers a nested ExpansionGroup
- **WHEN** `commitBridgeStep` runs for a `BridgeStep` whose `scopeTransition == ENTERING`
- **THEN** the phase adds an `ExpansionGroup` rooted at the frontier with the just-allocated input node as its sole slot to the graph
- **AND** the new group joins the work list
- **AND** the group's `initialEdges` contains the just-emitted REALISED edge

#### Scenario: EXITING bridge match registers a nested ExpansionGroup
- **WHEN** `commitBridgeStep` runs for a `BridgeStep` whose `scopeTransition == EXITING`
- **THEN** the phase adds an `ExpansionGroup` rooted at the frontier with the just-allocated input node as its sole slot to the graph
- **AND** the new group joins the work list
- **AND** the group's `initialEdges` contains the just-emitted REALISED edge

#### Scenario: PRESERVING bridge match does NOT register a nested ExpansionGroup
- **WHEN** `commitBridgeStep` runs for a `BridgeStep` whose `scopeTransition == PRESERVING`
- **THEN** the phase emits only the REALISED edge
- **AND** no new `ExpansionGroup` is added to the graph or work list

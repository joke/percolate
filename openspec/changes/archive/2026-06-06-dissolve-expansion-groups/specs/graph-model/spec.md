## MODIFIED Requirements

### Requirement: ExpansionGroup value type

The processor SHALL define a final type `ExpansionGroup` in
`io.github.joke.percolate.processor.graph` that is a **logical grouping label only** — it holds no
graph state, carries no codegen, and is NEVER traversed by code generation. It SHALL expose exactly:

- `GroupId id` — a thin value-type identity for the group (not a raw `String`), used for membership
  filtering and for the engine's SAT bookkeeping.
- `Node root` — the fan-in target node this group's demand produces.

Group **membership** is NOT stored on `ExpansionGroup`. A `Node` carries the set of groups it belongs
to (see "Node carries group-membership labels"); a group's **view** is derived on demand as a
`org.jgrapht.graph.MaskSubgraph` over `parent.underlyingGraph()`:

```
vertexMask = v -> !v.groups().contains(this.id)
edgeMask   = e -> e.getKind() != EdgeKind.REALISED
```

so the view shows exactly the group's tagged nodes and the `REALISED` edges between them. Because the
vertex mask hides edges with a masked endpoint, edge membership follows vertex membership; no edge
carries a group tag.

`ExpansionGroup` SHALL NOT expose `slots`, `getCodegen`, `strategyClassFqn`, `addVertexToView`,
`addEdgeToView`, `slotMetadata`, `expectedTypeFor`, `consumerContractFor`, `conversionFrontiers`, or
an `AsSubgraph` view. The `of(...)` factory `initialEdges` parameter and `validateInitialEdge` are
removed. "Adding a node to a group" is a single tag mutation on the `Node`, performed only by the
`Applier`.

The slots of a group (its demand's inputs) and its SAT status are derived: the input nodes are the
`from` endpoints of the `root`'s incoming REALISED edges within the view; SAT is recorded
engine-side, not on the group.

#### Scenario: ExpansionGroup exposes only id and root
- **WHEN** the public surface of `ExpansionGroup` is inspected
- **THEN** it exposes `getId()` and `getRoot()` and a derived `view()` (a `MaskSubgraph`)
- **AND** it exposes no `getSlots`, `getCodegen`, `getStrategyClassFqn`, `addVertexToView`,
  `addEdgeToView`, `slotMetadata`, `expectedTypeFor`, `consumerContractFor`, or `conversionFrontiers`

#### Scenario: Group view is a MaskSubgraph derived from node tags
- **WHEN** a group with `id = G` and `root = r` is constructed and nodes `r`, `a` are tagged with `G`
  and a `REALISED` edge `a → r` is added to the underlying graph
- **THEN** `group.view().vertexSet()` equals `{r, a}`
- **AND** `group.view().edgeSet()` contains the `a → r` REALISED edge
- **AND** a node not tagged with `G` is absent from `group.view().vertexSet()`

#### Scenario: Group view excludes non-REALISED edges and cross-group leak on shared nodes
- **WHEN** node `person.address` is tagged with both group `A` (where it is `root`) and group `B`
  (where it is an input), and `REALISED` edges `person → person.address` (in A) and
  `person.address → person.address.street` (in B) exist
- **THEN** `A.view().edgeSet()` contains `person → person.address` and NOT
  `person.address → person.address.street`
- **AND** `B.view()` contains the converse — no REALISED edge leaks across the shared boundary node

## ADDED Requirements

### Requirement: Node carries group-membership labels

`Node` SHALL carry an insertion-ordered, mutable set of `GroupId` labels recording the
`ExpansionGroup`s the node belongs to, exposed as `Set<GroupId> groups()`. A `Node` MAY belong to
many groups simultaneously (e.g. `person.address` is the `root` of one source-descent group and an
input of another). Group membership SHALL be mutated **only** by the `Applier` (the single mutation
site). The membership set SHALL be insertion-ordered for deterministic iteration. Group membership
SHALL NOT participate in `Node.equals`/`hashCode` (which remain instance-identity).

#### Scenario: A node can belong to multiple groups
- **WHEN** node `person.address` is tagged with group ids `G1` (as root) and `G2` (as input)
- **THEN** `node.groups()` contains both `G1` and `G2`
- **AND** iteration order is the order in which they were added

#### Scenario: Membership does not affect node identity
- **WHEN** two field-equal `Node` instances have different `groups()` sets
- **THEN** they remain unequal under `equals` purely by instance identity, and membership is not
  consulted

### Requirement: Edge carries the consumer Slot contract

A `REALISED` `Edge` SHALL carry the consumer `Slot` for the input it wires (the declared input type
and the `AnnotatedConstruct producedFrom` consumer contract). Code generation SHALL read a slot's
consumer contract from the **consuming edge's** `Slot` (the operand edge), not from any
`ExpansionGroup`. For an n-ary producer, each operand edge in the fan-in carries its own `Slot`.

#### Scenario: REALISED edge exposes its consumer Slot
- **WHEN** a `REALISED` operand edge feeding a constructor parameter is inspected
- **THEN** it exposes the consumer `Slot` carrying the declared parameter type and the
  `AnnotatedConstruct producedFrom`
- **AND** code generation derives the consumer contract from that edge, not from a group

### Requirement: MapperGraph variable identity

`MapperGraph` SHALL expose a get-or-create `variableFor(Scope scope, Location location)` that returns
the single canonical `Node` for `(scope, location)`, creating it (untyped) on first request. It is
used by `SeedStage` for seed-time structural variables so that shared path prefixes reuse one node
without transient caches. Expansion-minted nodes (per-`(name, type)` divergent leaves, conversion
intermediates) SHALL NOT route through `variableFor` — they are fresh instances, preserving the
instance-identity rule that prevents cross-sub-group cycles.

#### Scenario: Shared seed prefix resolves to one variable
- **WHEN** `SeedStage` requests `variableFor(scope, SourceLocation(["person"]))` for two directives
  `person.address` and `person.lastName`
- **THEN** both requests return the same `Node` instance

#### Scenario: Expansion-minted divergent leaves stay distinct
- **WHEN** expansion mints two leaves at the same `(scope, location)` but with different required
  types (`int` and `long`) via the assembly path
- **THEN** they are distinct `Node` instances and were not obtained via `variableFor`

## REMOVED Requirements

### Requirement: GroupCodegen interface
**Reason**: A group is a non-traversable label and carries no codegen. The n-ary producer's render
logic moves onto the producing `REALISED` edges (`EdgeCodegen`, whose `render(VarNames,
IncomingValues)` signature is identical), and code generation reconstructs the n-ary call from the
output node's fan-in.
**Migration**: Replace `GroupCodegen` usages with edge-carried `Codegen`/`EdgeCodegen`; the generator
gathers a node's incoming REALISED plan edges and renders once from their `IncomingValues` instead of
reading `group.getCodegen()`.

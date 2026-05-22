## MODIFIED Requirements

### Requirement: EdgeKind enum

The processor SHALL define an enum `EdgeKind` in `io.github.joke.percolate.processor.graph` with exactly three values:

- `SEED` — framing edges, ∞-weight. Either user-directive seeds emitted by `SeedGraph` (with non-empty `Edge.directive`) or strategy-emitted nested seeds that originated under earlier iterations of this change (with non-empty `Edge.strategyClassFqn`; under the new model these are emitted only by `SeedGraph` for completeness, since mid-expansion strategy work is expressed via `ExpansionGroup` rather than new SEED edges).
- `REALISED` — transformation edges produced by `Bridge` and `GroupTarget` strategies during expansion. These are the codegen substrate.
- `MARKER` — `realises` edges linking an untyped seed-graph node to its typed counterpart. Weight `Weights.NOOP`.

`EdgeKind` SHALL be referenced by `Edge.kind` and SHALL participate in `Edge` equality, hash, and comparison.

The earlier `SUB_SEED` and `ELEMENT_SEED` values are removed — they were forward-expansion driver artifacts. Per-group target-driven expansion expresses nested expansion work via `ExpansionGroup` registration, not via new SEED edge variants.

#### Scenario: EdgeKind has exactly three values

- **WHEN** `EdgeKind.values()` is invoked
- **THEN** the result contains exactly `SEED`, `REALISED`, `MARKER` in that declaration order

#### Scenario: SUB_SEED and ELEMENT_SEED are not present

- **WHEN** the source of `EdgeKind` is inspected
- **THEN** no `SUB_SEED` constant is declared
- **AND** no `ELEMENT_SEED` constant is declared

### Requirement: Edge value type

The processor SHALL define a Lombok `@Value` class `Edge` in `io.github.joke.percolate.processor.graph` with the following fields:

- `Node from`
- `Node to`
- `int weight` — uses the scale documented in `Weights`. `SEED` edges use `Weights.SENTINEL_UNREALISED` (∞). `REALISED` edges use a value in `{0, 1, 2, 3}`. `MARKER` edges use `Weights.NOOP`.
- `EdgeKind kind`
- `Optional<AnnotationMirror> directive` — populated on user-directive `SEED` edges emitted by `SeedGraph`. Empty on REALISED, MARKER, and strategy-emitted SEED edges.
- `Optional<EdgeCodegen> codegen` — populated on `REALISED` edges. Empty otherwise.
- `Optional<String> strategyClassFqn` — fully-qualified class name of the strategy that emitted the edge. Populated on `REALISED`, `MARKER`, and strategy-emitted nested `SEED` edges. Empty on user-directive `SEED` edges (emitted by `SeedGraph`, which is not a strategy).

`Edge` SHALL NOT carry a `groupId` field. Group membership of a `REALISED` edge is determined by membership in an `ExpansionGroup.view().edgeSet()` (see the `ExpansionGroup value type` requirement). This is consistent with the `MapperGraph` append-only invariant — groups are registered when their edges are added; the registry persists for the lifetime of the graph.

`Edge` SHALL be annotated `@Value @EqualsAndHashCode(exclude = {"codegen", "strategyClassFqn"})` so equality is structural over `(from, to, weight, kind, directive)`. `Edge` SHALL implement `Comparable<Edge>` ordered by `(from.id(), to.id(), weight, kind, directive-presence)`.

`Edge` SHALL provide exactly three static factory methods. The all-args constructor SHALL be package-private:

- `Edge.seed(Node from, Node to, Optional<AnnotationMirror> directive, Optional<String> strategyClassFqn)` — produces a SEED edge with `kind = SEED`, `weight = Weights.SENTINEL_UNREALISED`, the supplied `directive` and `strategyClassFqn`, empty `codegen`. User-directive seeds pass non-empty `directive` and empty `strategyClassFqn`.
- `Edge.realised(Node from, Node to, int weight, EdgeCodegen codegen, String strategyClassFqn)` — produces a REALISED edge with `kind = REALISED`, the supplied fields, empty `directive`. No `groupId` parameter.
- `Edge.marker(Node from, Node to, String strategyClassFqn)` — produces a MARKER edge with `kind = MARKER`, `weight = Weights.NOOP`, the supplied `strategyClassFqn`, empty `directive`, empty `codegen`.

The forward-expansion factories `Edge.subSeed(...)` and `Edge.elementSeed(...)` are removed.

#### Scenario: User directive SEED carries the mirror and empty strategyClassFqn

- **WHEN** `Edge.seed(from, to, Optional.of(directiveMirror), Optional.empty())` is invoked
- **THEN** the resulting edge has `kind == EdgeKind.SEED`, `weight == Weights.SENTINEL_UNREALISED`, non-empty `directive`, empty `strategyClassFqn`, empty `codegen`

#### Scenario: REALISED edge factory populates codegen and strategyClassFqn; no groupId field

- **WHEN** `Edge.realised(from, to, Weights.STEP, <closure>, "com.example.GetterRead")` is invoked
- **THEN** the resulting edge has `kind == EdgeKind.REALISED`, `weight == 1`, empty `directive`, non-empty `codegen`, non-empty `strategyClassFqn`
- **AND** no `getGroupId()` accessor exists on the `Edge` class

#### Scenario: MARKER edge has weight zero and no codegen

- **WHEN** `Edge.marker(seedNode, realisedNode, "com.example.GetterRead")` is invoked
- **THEN** the resulting edge has `kind == EdgeKind.MARKER`, `weight == 0`, empty `directive`, empty `codegen`, non-empty `strategyClassFqn`

#### Scenario: subSeed and elementSeed factories are absent

- **WHEN** the source of `Edge` is inspected
- **THEN** no `subSeed(...)` factory method exists
- **AND** no `elementSeed(...)` factory method exists

#### Scenario: groupId field is absent

- **WHEN** the source of `Edge` is inspected
- **THEN** no `groupId` field is declared
- **AND** no `getGroupId()` accessor exists

### Requirement: Node value type

The processor SHALL define a `Node` class in `io.github.joke.percolate.processor.graph` whose **identity is JVM object identity**:

- `equals` SHALL return `this == other` (reference equality).
- `hashCode` SHALL return `System.identityHashCode(this)`.

`Node` SHALL expose the following fields as **presentation attributes** consumed by the renderer and the diagnostic formatter, but **not participating in identity**:

- `Optional<TypeMirror> type` — the typed counterpart, populated when type resolution has completed for this node.
- `Location loc` — `SourceLocation`, `TargetLocation`, or `ElementLocation`. Carries the access-path/target-path/element-role label.
- `Scope scope` — `MapperScope` or `MethodScope`. Element-scope nodes are NOT a separate `Scope` — element nodes have `scope = <originating method's scope>` and `loc instanceof ElementLocation`.

The forward-expansion `Node.parent` field is removed. The structural "this element node belongs to that container" relationship is encoded by the element node's membership in a nested `ExpansionGroup` rooted at the container's output node.

`Node` SHALL expose a method `id()` returning a stable-per-run debug string of the form `"node@<identityHashCode>"`. `id()` SHALL NOT participate in equality or hashing.

Two `Node` instances with identical `type`, `loc`, `scope` values are distinct nodes (instance identity, no structural dedup). The forward-expansion `find-or-allocate` pattern is replaced by "always allocate fresh" in the expansion driver.

#### Scenario: Node equality is reference equality

- **WHEN** two `Node` instances are constructed with identical `type`, `loc`, `scope` field values
- **THEN** `a.equals(b)` returns `false`
- **AND** `a.hashCode() != b.hashCode()` in general (reference-based)

#### Scenario: Node has no parent field

- **WHEN** the source of `Node` is inspected
- **THEN** no `parent` field is declared
- **AND** no `getParent()` accessor exists

#### Scenario: Node.id() returns a stable-per-run debug string

- **WHEN** `node.id()` is invoked twice on the same `Node` instance within a single JVM run
- **THEN** both invocations return the same string
- **AND** the string has the form `"node@<digits>"`

### Requirement: RealisedSubgraph view

The processor SHALL define a class `RealisedSubgraph` in `io.github.joke.percolate.processor.graph` exposing a read-only view over a `MapperGraph` filtered to:

- **edges:** only edges with `kind == EdgeKind.REALISED` (excludes `SEED` and `MARKER`)
- **nodes:** only nodes incident on at least one `REALISED` edge

`RealisedSubgraph` SHALL expose `nodes()`, `edges()`, and `nodesByScope(Scope)` with `Stream<Node>` / `Stream<Edge>` return types in deterministic ascending order (by `id()` for nodes, natural `Edge.compareTo` for edges).

`RealisedSubgraph` SHALL be implemented over JGraphT `MaskSubgraph` — no graph copy is performed.

#### Scenario: RealisedSubgraph filter excludes SEED and MARKER edges

- **WHEN** a `MapperGraph` is constructed with one `SEED` edge, one `REALISED` edge, and one `MARKER` edge
- **THEN** `realisedSubgraph().edges().count() == 1`
- **AND** the single edge has `kind == EdgeKind.REALISED`

#### Scenario: RealisedSubgraph includes only nodes incident on REALISED edges

- **WHEN** a `MapperGraph` is constructed with a `REALISED` edge between nodes `A` and `B`, plus a third unconnected node `C`
- **THEN** `realisedSubgraph().nodes()` contains `A` and `B` and SHALL NOT contain `C`

## ADDED Requirements

### Requirement: ExpansionGroup value type

The processor SHALL define a final class `ExpansionGroup` in `io.github.joke.percolate.processor.graph` with the following fields:

- `Node root` — the fan-in target node the group's codegen produces. Always typed.
- `List<Node> slots` — the direct input slot nodes the group's codegen reads. Each slot is typed. Order is preserved from the strategy's `GroupBuild`.
- `GroupCodegen codegen` — the codegen function combining slot values into the root value.
- `String strategyClassFqn` — the FQN of the strategy that emitted the group (deterministic identification).
- `AsSubgraph<Node, Edge> view` — a JGraphT subgraph view containing the group's root, slot nodes, and slot-incoming `REALISED` edges. Backed by the parent `MapperGraph.underlyingGraph()`.

The class SHALL provide a static factory:

```
ExpansionGroup of(Node root, List<Node> slots, GroupCodegen codegen,
                  String strategyClassFqn, Set<Edge> initialEdges, MapperGraph parent)
```

The factory SHALL validate that `root` and every `slot` are nodes of `parent.underlyingGraph()`, and that every edge in `initialEdges` is in `parent.underlyingGraph()` and has `kind == REALISED` with an endpoint matching `(slot, root)` for some slot. The constructed `view` is an `AsSubgraph` initialised with `({root} ∪ slots, initialEdges)`.

The group's view SHALL NOT auto-grow when REALISED edges are added to the underlying graph outside the group's direct slot-incoming chain. Deeper source-side chain edges (e.g., a `GetterRead` edge from `src[person]:Person` to a slot's input chain) live in the underlying graph but are NOT members of the group's view. This keeps each group focused on its codegen's direct inputs.

`ExpansionGroup` SHALL expose:

- `Node getRoot()`, `List<Node> getSlots()`, `GroupCodegen getCodegen()`, `String getStrategyClassFqn()`, `AsSubgraph<Node, Edge> getView()` (or read-only equivalents)
- `boolean contains(Edge e)` — convenience for `view.containsEdge(e)`

#### Scenario: Factory constructs a view containing root, slots, and initial edges

- **WHEN** `ExpansionGroup.of(root, [slot1, slot2], codegen, "com.example.ConstructorCall", {edge_slot1_to_root, edge_slot2_to_root}, parent)` is invoked
- **THEN** the resulting `ExpansionGroup` has a view whose vertex set is `{root, slot1, slot2}` and edge set is `{edge_slot1_to_root, edge_slot2_to_root}`

#### Scenario: View does not auto-grow when chain edges are added outside the slot-incoming chain

- **WHEN** the underlying graph adds a REALISED edge from `src[person]:Person` to `slot1` (a slot of `group`)
- **THEN** `group.getView().edgeSet()` does NOT contain the new edge (it's a chain edge, not a slot-incoming edge of this group)
- **AND** the new edge IS present in `parent.underlyingGraph().edgeSet()`

#### Scenario: View shares vertex identity with the underlying graph

- **WHEN** `group.getRoot()` is invoked and the same `Node` instance is looked up in `parent.underlyingGraph().vertexSet()`
- **THEN** both references are the same object (`==`)

### Requirement: MapperGraph group registry

`MapperGraph` SHALL maintain a registry of `ExpansionGroup`s emitted during expansion. The registry SHALL be append-only — groups are added but never removed, mirroring the node/edge invariant.

`MapperGraph` SHALL expose:

- `void addGroup(ExpansionGroup group)` — appends `group` to the registry; throws `IllegalArgumentException` if any of `group`'s root, slots, or initial edges are not present in `underlyingGraph()`.
- `Stream<ExpansionGroup> groups()` — returns the registered groups in registration order.

`MapperGraph` SHALL NOT carry a `groupCodegens : Map<String, GroupCodegen>` map. The codegen for a group is `ExpansionGroup.getCodegen()`. Group identity is the `ExpansionGroup` object itself; no string `groupId` key is required.

The earlier `addGroupCodegen(String, GroupCodegen)` and `groupCodegen(String)` methods, and the `GroupRegistration` value type, are removed.

#### Scenario: addGroup appends to the registry

- **WHEN** `mapperGraph.addGroup(g1)` is invoked and then `mapperGraph.addGroup(g2)` is invoked
- **THEN** `mapperGraph.groups()` returns a stream containing `g1` then `g2` in that order

#### Scenario: addGroup validates membership

- **WHEN** `mapperGraph.addGroup(group)` is invoked with a `group` whose root is a `Node` instance not in `mapperGraph.underlyingGraph().vertexSet()`
- **THEN** `IllegalArgumentException` is thrown
- **AND** the registry is unchanged

#### Scenario: addGroup is append-only

- **WHEN** the public surface of `MapperGraph` is inspected
- **THEN** no method removes a registered `ExpansionGroup`

#### Scenario: groupCodegens map is absent

- **WHEN** the source of `MapperGraph` is inspected
- **THEN** no `groupCodegens` field is declared
- **AND** no `addGroupCodegen(...)` method exists
- **AND** no `groupCodegen(String)` method exists

### Requirement: MapperGraph is append-only after construction

After the discover and seed/expand stages have populated a `MapperGraph`, no stage SHALL remove nodes, edges, or registered `ExpansionGroup`s from it. `MapperGraph` exposes no removal methods; the invariant is structural rather than enforced at runtime.

Filtering for any downstream consumer (validation, dumping, future codegen) SHALL be expressed as a view (`RealisedSubgraph`, `transformsView`, an `ExpansionGroup.getView()`, or another `MaskSubgraph`) rather than as a destructive mutation.

`MARKER` edges and any other expansion artifacts SHALL remain in the underlying graph after expansion. The decision whether to render them is made at the view / renderer layer.

#### Scenario: MapperGraph exposes no node, edge, or group removal

- **WHEN** the public surface of `MapperGraph` is inspected
- **THEN** no method removes a node, edge, or `ExpansionGroup`
- **AND** no method takes a `Node`, `Edge`, or `ExpansionGroup` argument with the intent to delete it

#### Scenario: Greedy commit produces no dead REALISED edges

- **WHEN** `ExpandGroupsPhase` completes for a mapper whose every group is SAT
- **THEN** every `REALISED` edge in `MapperGraph.edges()` is on a path from some source-parameter-root to some slot of some registered `ExpansionGroup`
- **AND** the transforms view contains no orphan `REALISED` edges

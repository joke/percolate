## MODIFIED Requirements

### Requirement: Node value type

The processor SHALL define a class `Node` in `io.github.joke.percolate.processor.graph` with the following fields and contract:

- `Optional<TypeMirror> type` — the type of the value the node represents; empty when the type is not yet known. The type is **mutable** after construction (assigned in place when a `PathSegmentResolver` types a path-segment group's root during expansion); a `setType(TypeMirror)` accessor SHALL be provided that updates `type` from `Optional.empty()` to `Optional.of(...)` and SHALL throw if the type is already present.
- `Location loc` — where the node sits in the mapping (`SourceLocation`, `TargetLocation`, or `ElementLocation` for phantom container element nodes). Immutable.
- `Scope scope` — the method or mapper-level scope this node belongs to. Immutable.
- `Optional<Node> parent` — set ONLY for nodes whose `loc` is `ElementLocation`; empty for every other node. Immutable.
- `String id()` — a deterministic identity string for debug rendering and sorted iteration:
  - if `loc` is `ElementLocation`: `id()` = `parent.orElseThrow().id() + "::" + loc.segment() + "::" + typeEncode() + "@" + System.identityHashCode(this)`.
  - otherwise: `id()` = `scope.encode() + "::" + loc.segment() + "::" + typeEncode() + "@" + System.identityHashCode(this)`.
  - `typeEncode()` is the qualified type name when `type` is present, or `"?"` when absent.

The `@<identityHashCode>` suffix disambiguates same-shape instances in DOT output and sorted iteration. It is the visible reflection of the underlying instance-identity rule.

`Node.equals(Object)` SHALL return `this == other`. `Node.hashCode()` SHALL return `System.identityHashCode(this)`. Two `Node` instances with field-equal `(loc, type, scope, parent)` are distinct nodes if they are different objects in memory.

The `Node` class SHALL implement `Comparable<Node>` via `id()` so that sorted iteration of nodes is well-defined.

Instance-identity is the load-bearing structural property that makes cross-sub-group cycles impossible during expansion: a `Node` freshly allocated by one sub-group's bridge expansion is a distinct object from any same-shape `Node` allocated by a sibling sub-group, so candidate scans within a sub-group's view cannot pick up downstream instances even when their `(scope, loc, type)` would match.

`SeedGraph` is responsible for emitting **one `Node` instance per directive-segment-prefix key** via its own internal deduplication (a `Map<KeyTuple, Node>` populated as the seed is walked). Idempotence at the graph level (`MapperGraph.addNode(node)` being a no-op on `node == existingNode`) means `SeedGraph`'s own dedup is the convergence mechanism for seed nodes; bridge expansion creates fresh distinct instances.

#### Scenario: Two field-equal Nodes are distinct under instance identity
- **WHEN** two `Node` instances are constructed with field-equal `loc`, `type`, `scope`, and `parent`
- **THEN** they compare unequal under `equals`
- **AND** their `hashCode`s differ
- **AND** their `id()` strings differ in the `@<identityHashCode>` suffix

#### Scenario: SeedGraph dedup yields a single Node instance per directive prefix
- **WHEN** two directives `@Map(source = "person.addresses")` and `@Map(source = "person.lastName")` are processed by `SeedGraph` for the same method
- **THEN** exactly one `Node` instance exists for `SourceLocation(["person"])` in the resulting graph
- **AND** both directives' downstream SEED edges share that single `Node` object

#### Scenario: setType updates an untyped Node in place
- **WHEN** a `Node` constructed with `type = Optional.empty()` has `setType(<List<Opt<PA>>>)` invoked
- **THEN** `getType()` returns `Optional.of(<List<Opt<PA>>>)`
- **AND** subsequent `setType(...)` calls on the same `Node` throw

#### Scenario: Source-rooted node id includes identity suffix
- **WHEN** a `Node` is constructed with `loc = SourceLocation(["person"])`, `type = Optional.of(<Person>)`, `scope = MethodScope(<map(Person)>)`, `parent = Optional.empty()`
- **THEN** `id()` is the string `"v::map(Person)::person@" + identityHashCode` (or the equivalent documented encoding)
- **AND** repeated invocations of `id()` return the same string

#### Scenario: Target-rooted node id with unknown type
- **WHEN** a `Node` is constructed with `loc = TargetLocation(["lastName"])`, `type = Optional.empty()`, `scope = MethodScope(<map(Person)>)`, `parent = Optional.empty()`
- **THEN** `id()` includes the `?` marker for the unknown type so that later type discovery (via `setType`) does not collide

#### Scenario: Phantom node id derives from parent, role, and type
- **WHEN** a phantom `Node` is constructed with `loc = ElementLocation("element")`, `type = Optional.of(<String>)`, `scope` matching its parent, and `parent = Optional.of(<container node>)`
- **THEN** `id()` starts with the parent's `id()` prefix, followed by `::elem(element)::String@<identityHashCode>`

#### Scenario: Phantom node without parent throws
- **WHEN** a `Node` is constructed with `loc = ElementLocation` and `parent = Optional.empty()`, then `id()` is invoked
- **THEN** an unchecked exception is thrown

#### Scenario: Two phantoms with different parents are distinct
- **WHEN** two `Node` instances are constructed with the same `loc = ElementLocation("element")`, same `type`, same `scope`, but different `parent` values
- **THEN** they compare unequal under `equals`

#### Scenario: Two phantoms with same parent and role but different types are distinct
- **WHEN** two phantom `Node` instances are constructed with the same `loc = ElementLocation("element")`, same `scope`, same `parent`, but different `type` values
- **THEN** they compare unequal under `equals` (which is true vacuously under instance identity, but the contract holds without relying on it)

#### Scenario: Two phantoms with same parent and type but different roles are distinct
- **WHEN** two phantom `Node` instances are constructed with `loc = ElementLocation("key")` and `loc = ElementLocation("value")` respectively, same `scope`, same `parent`, same `type`
- **THEN** they compare unequal under `equals`

### Requirement: ExpansionGroup value type

The processor SHALL define a final class `ExpansionGroup` in `io.github.joke.percolate.processor.graph` with the following fields:

- `Node root` — the fan-in target node the group's codegen produces. Always typed at construction for non-path-segment groups; typed in-place by `PathSegmentResolver` invocation for path-segment groups (root starts untyped).
- `List<Node> slots` — the direct input slot nodes the group's codegen reads. Order is preserved from the strategy's `GroupBuild`.
- `GroupCodegen codegen` — the codegen function combining slot values into the root value.
- `String strategyClassFqn` — the FQN of the strategy that emitted the group.
- `AsSubgraph<Node, Edge> view` — a JGraphT subgraph view containing the group's root, slot nodes, and slot-incoming `REALISED` edges. Backed by the parent `MapperGraph.underlyingGraph()`.

The class SHALL provide a static factory:

```
ExpansionGroup of(Node root, List<Node> slots, GroupCodegen codegen,
                  String strategyClassFqn, Set<Edge> initialEdges, MapperGraph parent)
```

The factory SHALL validate that `root` and every `slot` are nodes of `parent.underlyingGraph()`, and that every edge in `initialEdges` is in `parent.underlyingGraph()` and has `kind == REALISED`. The constructed `view` is an `AsSubgraph` initialised with `({root} ∪ slots, initialEdges)`.

`ExpansionGroup` SHALL expose controlled view mutators used by `ExpandGroupsPhase` during expansion:

- `addVertexToView(Node n)` — adds `n` to `view.vertexSet()`. Validates that `n` is a member of `parent.underlyingGraph()`. Idempotent on instance-equal vertices.
- `addEdgeToView(Edge e)` — adds `e` to `view.edgeSet()`. Validates that `e` is a member of `parent.underlyingGraph()`, has `kind == REALISED`, and that both endpoints are in `view.vertexSet()`. Idempotent on instance-equal edges.

These mutators allow the engine to grow a group's view when a boundary-import is needed (a child sub-group's slot equals an existing node in the parent's view by instance identity — adding that vertex makes the sub-group's view non-empty for the slot).

`ExpansionGroup` SHALL expose: `getRoot()`, `getSlots()`, `getCodegen()`, `getStrategyClassFqn()`, `getView()`, `contains(Edge e)`, `addVertexToView(Node)`, `addEdgeToView(Edge)`.

#### Scenario: of() rejects a root not in the underlying graph
- **WHEN** `ExpansionGroup.of(root, slots, codegen, "com.example.X", initialEdges, parent)` is invoked with a `root` not added to `parent.underlyingGraph()`
- **THEN** an `IllegalArgumentException` is thrown

#### Scenario: of() rejects an initialEdges set containing a non-REALISED edge
- **WHEN** `ExpansionGroup.of(...)` is invoked with `initialEdges` containing an edge whose `kind != REALISED`
- **THEN** an `IllegalArgumentException` is thrown

#### Scenario: View is scoped to root, slots, and initialEdges at construction
- **WHEN** `ExpansionGroup.of(root, [slotA, slotB], codegen, "com.example.X", {edgeA, edgeB}, parent)` is invoked successfully
- **THEN** `group.getView().vertexSet()` equals `{root, slotA, slotB}`
- **AND** `group.getView().edgeSet()` equals `{edgeA, edgeB}`

#### Scenario: addVertexToView grows the view
- **WHEN** `group.addVertexToView(n)` is invoked with `n` already added to `parent.underlyingGraph()` but not in the view
- **THEN** `group.getView().vertexSet()` contains `n` after the call

#### Scenario: addVertexToView rejects a node not in the underlying graph
- **WHEN** `group.addVertexToView(n)` is invoked with `n` not added to `parent.underlyingGraph()`
- **THEN** an `IllegalArgumentException` is thrown

#### Scenario: addEdgeToView rejects an edge with endpoints outside the view
- **WHEN** `group.addEdgeToView(e)` is invoked and one of `e.from` / `e.to` is not in `group.getView().vertexSet()`
- **THEN** an `IllegalArgumentException` is thrown

#### Scenario: addEdgeToView rejects a non-REALISED edge
- **WHEN** `group.addEdgeToView(e)` is invoked with `e.kind != REALISED`
- **THEN** an `IllegalArgumentException` is thrown

#### Scenario: contains(edge) reports view membership
- **WHEN** `group.contains(e)` is invoked with an edge `e` that is in `group.getView().edgeSet()`
- **THEN** the call returns `true`
- **WHEN** `group.contains(e)` is invoked with an edge `e` that is not in `group.getView().edgeSet()`
- **THEN** the call returns `false`

# Seed Graph Spec

## Purpose

This spec defines the SeedGraph stage that constructs an initial `MapperGraph` from discovered mapper method mappings, producing nodes and edges that represent source parameters, return types, and directive-seeded paths.

## Requirements

### Requirement: SeedGraph registers one ExpansionGroup per SEED edge

For every SEED edge `e: from → to` emitted by `SeedGraph` (path-segment edges, directive-bridging edges, and target-chain edges), `SeedGraph` SHALL register one one-slot `ExpansionGroup` with `root = to`, `slots = [from]`, `strategyClassFqn = "io.github.joke.percolate.processor.stages.seed.SeedGraph"`, `codegen = a placeholder GroupCodegen` (replaced during expansion as bridges/resolvers fire). `initialEdges` SHALL be empty — the SEED edge itself is NOT a member of the group's view; the group represents an unresolved producer relationship pending expansion.

The set of groups registered by `SeedGraph` decomposes into three structural kinds:

- **Path-segment groups**: both `root.loc` and `slot.loc` are `SourceLocation`s; `root.loc.path` is `slot.loc.path` extended by exactly one segment. These are expanded by `PathSegmentResolver`s during `ExpandGroupsPhase`.
- **Directive-binding groups**: `root.loc` is a `TargetLocation` and `slot.loc` is a `SourceLocation` (the deepest source leaf). These are expanded by `Bridge`s during `ExpandGroupsPhase` to fill the transformation chain between the source leaf and the target leaf.
- **Target-chain groups**: both `root.loc` and `slot.loc` are `TargetLocation`s; `slot.loc.path` is `root.loc.path` extended by one segment. These are expanded by `GroupTarget`s matching at the deeper target node (or are SAT-by-construction once `ResolveTargetChainsPhase` allocates typed slots for them).

`SeedGraph` SHALL NOT pre-classify groups by these kinds (the classification is derived from node `Location`s at expansion time). The structural shape of each group is sufficient for `ExpandGroupsPhase` to dispatch.

The group kind is determined by node `Location`s, not by a `groupKind` field on `ExpansionGroup`.

#### Scenario: Each SEED edge has one corresponding ExpansionGroup
- **WHEN** `SeedGraph.apply(...)` emits `n` SEED edges
- **THEN** `MapperGraph.groups()` contains exactly `n` `ExpansionGroup`s registered by `SeedGraph`, one per SEED edge
- **AND** for each SEED edge `e`, exactly one group exists with `root = e.to` and `slots = [e.from]`

#### Scenario: Path-segment edge produces a path-segment group
- **WHEN** `SeedGraph.apply(...)` emits a SEED edge `src[person] → src[person.addresses]:?`
- **THEN** the corresponding `ExpansionGroup` has `root = src[person.addresses]:?`, `slots = [src[person]:Person]`
- **AND** the group's structural shape (root.loc and slot.loc both `SourceLocation`, root path is slot path + one segment) identifies it as a path-segment group for `ExpandGroupsPhase` dispatch

#### Scenario: Directive-bridging edge produces a directive-binding group
- **WHEN** `SeedGraph.apply(...)` emits a directive-bridging SEED edge `src[person.addresses]:? → tgt[addresses]:?`
- **THEN** the corresponding `ExpansionGroup` has `root = tgt[addresses]:?`, `slots = [src[person.addresses]:?]`
- **AND** the structural shape (root.loc `TargetLocation`, slot.loc `SourceLocation`) identifies it as a directive-binding group

#### Scenario: Target-chain edge produces a target-chain group
- **WHEN** `SeedGraph.apply(...)` emits a target-chain SEED edge `tgt[addresses] → tgt[]:Human`
- **THEN** the corresponding `ExpansionGroup` has `root = tgt[]:Human`, `slots = [tgt[addresses]]`
- **AND** the structural shape (root.loc and slot.loc both `TargetLocation`, root path is slot path - one segment) identifies it as a target-chain group

### Requirement: SeedGraph emits only SEED-kind edges
`SeedGraph` SHALL emit SEED edges for every directive's path-segment chain, every directive-bridging edge, and every target-chain edge. No REALISED, MARKER, `SUB_SEED`, or `ELEMENT_SEED` edges SHALL be emitted by `SeedGraph` — the only edge kind produced at seed time is `EdgeKind.SEED`.

`SeedGraph` SHALL register one `ExpansionGroup` per SEED edge (see "SeedGraph registers one ExpansionGroup per SEED edge").

For every edge emitted by `SeedGraph`, the following metadata fields SHALL be empty:
- `codegen == Optional.empty()`,
- `strategyClassFqn == Optional.empty()` (`SeedGraph` is not a strategy; the registered `ExpansionGroup`s carry `strategyClassFqn`, the edges do not).

`SeedGraph` SHALL produce a fresh `MapperGraph` per invocation. It SHALL NOT mutate any state outside the returned graph.

`SeedGraph` SHALL NOT invoke `PathSegmentResolver`s. Source-path resolution is the responsibility of `ExpandGroupsPhase` (see `source-path-resolution`).

#### Scenario: All seed-emitted edges have kind SEED
- **WHEN** `SeedGraph.apply(...)` produces a graph for any non-empty mapper
- **THEN** `graph.edges().allMatch(e -> e.getKind() == EdgeKind.SEED)` returns `true`

#### Scenario: No REALISED, MARKER, or other edge kinds are emitted at seed time
- **WHEN** `SeedGraph.apply(...)` produces a graph for any non-empty mapper
- **THEN** the graph contains only SEED edges; no `REALISED` / `MARKER` / `SUB_SEED` / `ELEMENT_SEED` edges exist

#### Scenario: Seed edges have empty codegen and strategyClassFqn
- **WHEN** `SeedGraph.apply(...)` produces a graph for any non-empty mapper
- **THEN** every edge has `codegen.isEmpty()` and `strategyClassFqn.isEmpty()`

#### Scenario: Empty mapper produces an empty graph
- **WHEN** `SeedGraph.apply(...)` is invoked with a `MapperMappings` whose `methods` list is empty
- **THEN** the returned `MapperGraph` contains zero nodes, zero edges, and zero groups

#### Scenario: Each method seeds its own scope
- **WHEN** `SeedGraph.apply(...)` is invoked with a `MapperMappings` containing two methods `map(Person)` and `map(Address)`
- **THEN** every node and every edge in the returned graph has `Scope = MethodScope(<that method>)`
- **AND** no node or edge has scope `MapperScope` (this is reserved for future use)

### Requirement: Parameter root nodes
For every parameter `p` of every method `M` in the input `MapperMappings`, `SeedGraph` SHALL emit exactly one parameter-root node:
- `loc = SourceLocation([<paramName>])`
- `type = Optional.of(<param type>)`
- `scope = MethodScope(<M>)`

#### Scenario: Single-parameter method seeds one parameter-root node
- **WHEN** `SeedGraph.apply(...)` is invoked for a method `Human map(Person person)`
- **THEN** the returned graph contains a node with `loc = SourceLocation(["person"])`, non-empty `type`, and `scope = MethodScope(<map(Person)>)`

#### Scenario: Multi-parameter method seeds one node per parameter
- **WHEN** `SeedGraph.apply(...)` is invoked for a method `Foo combine(Bar bar, Baz baz)`
- **THEN** the returned graph contains exactly two parameter-root nodes, one with `loc = SourceLocation(["bar"])` and one with `loc = SourceLocation(["baz"])`, both scoped to `MethodScope(<combine(Bar,Baz)>)`

### Requirement: Return-type root node
For every method `M` in the input `MapperMappings`, `SeedGraph` SHALL emit exactly one return-type root node:
- `loc = TargetLocation([])`
- `type = Optional.of(<M's return type>)`
- `scope = MethodScope(<M>)`

#### Scenario: Return-type root is emitted
- **WHEN** `SeedGraph.apply(...)` is invoked for a method `Human map(Person person)`
- **THEN** the returned graph contains a node with `loc = TargetLocation([])`, `type` equal to `<Human>`, and `scope = MethodScope(<map(Person)>)`

### Requirement: Directive-seeded source chains
For every `MappingDirective` on every method `M`, `SeedGraph` SHALL split the directive's `source` string on `.` to produce segments `s1, s2, ..., sk`. It SHALL emit (idempotently):
- one node per non-empty prefix `[s1, ..., si]` (for `i = 1..k`) with `loc = SourceLocation([s1, ..., si])`, `type = Optional.empty()`, `scope = MethodScope(<M>)`, `parent = Optional.empty()`,
- one edge constructed via `Edge.seed(...)` from the parameter-root node (the parameter named in the directive's source first-segment) to the node for `[s1]`, carrying the `@Map` mirror,
- for each `i = 1..k-1`, one edge constructed via `Edge.seed(...)` from the node for `[s1, ..., si]` to the node for `[s1, ..., si+1]`, carrying the `@Map` mirror.

All such edges SHALL therefore have `kind == EdgeKind.SEED` and `weight == Weights.SENTINEL_UNREALISED` (factory-enforced).

The first segment of every directive's source MUST match a parameter name of the method; directives whose first source segment does not name any parameter are rejected by `ValidateSourceParameters` before this stage runs. `SeedGraph` assumes all directives have valid source parameters.

#### Scenario: Single-segment source seeds one source node and one SEED edge
- **WHEN** `SeedGraph.apply(...)` is invoked for a method `Human map(Person person)` with `@Map(target = "person", source = "person")`
- **THEN** the returned graph contains a node with `loc = SourceLocation(["person"])`, empty `type`, scoped to that method, empty `parent`
- **AND** an edge with `from = parameter-root("person")`, `to = the source node`, `kind = EdgeKind.SEED`, `weight = Weights.SENTINEL_UNREALISED`, `directive` carrying the `@Map` mirror

#### Scenario: Dotted source seeds a chain of SEED edges
- **WHEN** `SeedGraph.apply(...)` is invoked for a directive `@Map(target = "x", source = "person.address.street")`
- **THEN** the returned graph contains source nodes for paths `["person.address"]`, `["person.address.street"]` (each as its full multi-segment path) — implementation MAY choose to use the parameter-root node directly for the single-segment "person" prefix
- **AND** edges chain them: parameter-root → `[person, address]` → `[person, address, street]`, every edge with `kind = SEED`, `weight = Weights.SENTINEL_UNREALISED`, each carrying the `@Map` mirror

### Requirement: Directive-seeded target chains
For every `MappingDirective` on every method `M`, `SeedGraph` SHALL split the directive's `target` string on `.` to produce segments `t1, t2, ..., tk`. It SHALL emit (idempotently):
- one node per non-empty prefix `[t1, ..., ti]` (for `i = 1..k`) with `loc = TargetLocation([t1, ..., ti])`, `type = Optional.empty()`, `scope = MethodScope(<M>)`, `parent = Optional.empty()`,
- one edge constructed via `Edge.seed(...)` from the deepest target node (`[t1, ..., tk]`) to the next-shallower target node, then to the next, until reaching the return-type root node, each carrying the `@Map` mirror.

All such edges SHALL therefore have `kind == EdgeKind.SEED` and `weight == Weights.SENTINEL_UNREALISED` (factory-enforced).

In other words, chains flow OUTWARD from the deepest target slot to the return-type root.

#### Scenario: Single-segment target seeds one target node and one SEED edge to the root
- **WHEN** `SeedGraph.apply(...)` is invoked for a method `Human map(Person person)` with `@Map(target = "lastName", source = "lastName")`
- **THEN** the returned graph contains a node with `loc = TargetLocation(["lastName"])`, empty `type`, scoped to that method, empty `parent`
- **AND** an edge from the target node `["lastName"]` to the return-type root `[]`, `kind = EdgeKind.SEED`, `weight = Weights.SENTINEL_UNREALISED`, carrying the `@Map` mirror

#### Scenario: Dotted target seeds a chain of SEED edges
- **WHEN** `SeedGraph.apply(...)` is invoked for a directive `@Map(target = "address.line1", source = "x")`
- **THEN** the returned graph contains target nodes for `["address"]` and `["address","line1"]`
- **AND** edges chain them: `["address","line1"]` → `["address"]` → return-type root, every edge with `kind = SEED`, `weight = Weights.SENTINEL_UNREALISED`, each carrying the `@Map` mirror

### Requirement: Directive-bridging edge

For every `MappingDirective` on every method `M`, `SeedGraph` SHALL emit exactly one edge constructed via `Edge.seed(...)` bridging the directive's deepest **untyped** source node to the deepest target node (the node for the full target path), carrying the `@Map` mirror.

The bridging edge's `from` is **always the untyped source leaf** (`SourceLocation([s1, ..., sk]):?` for a k-segment source path). The previous "use the typed source if path resolution succeeded" preference is removed — `SeedGraph` no longer types source paths, so no typed alternative exists at seed time.

The bridging edge SHALL therefore have `kind == EdgeKind.SEED` and `weight == Weights.SENTINEL_UNREALISED` (factory-enforced). One directive-binding `ExpansionGroup` SHALL be registered per bridging edge with `root = tgt[full-target-path]:?`, `slot = src[full-source-path]:?` (both untyped at this stage).

#### Scenario: One bridging SEED edge per directive, always from untyped leaf
- **WHEN** `SeedGraph.apply(...)` is invoked for `@Map(target = "name", source = "first")` on `Human map(Person person)`
- **THEN** the returned graph contains exactly one edge with `from = source-node(["first"]):?` (untyped leaf) and `to = target-node(["name"]):?`, `kind = EdgeKind.SEED`, `weight = Weights.SENTINEL_UNREALISED`, carrying the `@Map` mirror
- **AND** the corresponding directive-binding `ExpansionGroup` is registered

#### Scenario: Multi-segment source still bridges from the untyped leaf
- **WHEN** `SeedGraph.apply(...)` is invoked for `@Map(target = "lastName", source = "person.lastName")` on `Human map(Person person)`
- **THEN** the bridging edge's `from` is the untyped seed leaf `src[person.lastName]:?`
- **AND** the bridging edge's `to` is the untyped target leaf `tgt[lastName]:?`
- **AND** the corresponding directive-binding `ExpansionGroup` has `root = tgt[lastName]:?` and `slot = src[person.lastName]:?`

#### Scenario: Two directives produce two bridging SEED edges and two directive-binding groups
- **WHEN** `SeedGraph.apply(...)` is invoked for two directives on the same method
- **THEN** the returned graph contains exactly two bridging edges, one per directive
- **AND** exactly two directive-binding `ExpansionGroup`s are registered, one per directive

### Requirement: Directed acyclicity at seed time
For every `MapperGraph` produced by `SeedGraph`, the underlying directed graph SHALL be acyclic (a DAG). The introduction of `EdgeKind` and the weight flip to `Weights.SENTINEL_UNREALISED` does NOT alter graph topology — only edge weights and labels. The acyclicity invariant therefore continues to hold for the seed stage. This is a documented invariant SHALL be asserted by tests.

Note: the seed graph is not necessarily a forest in the undirected sense — multiple directives on the same method share common nodes (parameter roots, return-type root), so paths converge creating undirected cycles while remaining directed-acyclic.

#### Scenario: Seed graph is acyclic after the weight and kind flip
- **WHEN** `SeedGraph.apply(...)` produces a graph for any non-empty mapper
- **THEN** `MapperGraph.isAcyclic()` returns `true`
- **AND** all edges have `kind == EdgeKind.SEED`
- **AND** all edges have `weight == Weights.SENTINEL_UNREALISED`

### Requirement: Idempotent node and edge addition
When two `MappingDirective`s on the same method share path prefixes, `SeedGraph` SHALL NOT emit duplicate nodes or duplicate edges for those prefixes. Equality is structural (per `Node.equals` and `Edge.equals`).

#### Scenario: Shared prefix is not duplicated
- **WHEN** `SeedGraph.apply(...)` is invoked for two directives both targeting paths under `address.*` (e.g., `address.street` and `address.city`)
- **THEN** exactly one node exists for `TargetLocation(["address"])` in the resulting graph
- **AND** exactly one edge connects that node to the return-type root

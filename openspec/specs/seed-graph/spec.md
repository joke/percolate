# Seed Graph Spec

## Purpose

This spec defines the SeedGraph stage that constructs an initial `MapperGraph` from discovered mapper method mappings, producing nodes and edges that represent source parameters, return types, and directive-seeded paths.

## Requirements

### Requirement: SeedGraph registers one ExpansionGroup per SEED edge

For every **source-side** SEED edge `e: from → to` emitted by `SeedGraph` — path-segment edges and directive-bridging edges — `SeedGraph` SHALL register one one-slot `ExpansionGroup` with `root = to`, `slots = [from]`, `strategyClassFqn = "io.github.joke.percolate.processor.stages.seed.SeedGraph"`, `codegen = a placeholder GroupCodegen` (replaced during expansion as resolvers/strategies fire). `initialEdges` SHALL be empty — the SEED edge itself is NOT a member of the group's view; the group represents an unresolved producer relationship pending expansion.

The **target side is consolidated, not per-edge.** Target-chain SEED edges (`tgt[child] → tgt[parent]`) do NOT each register a group. Instead, for every parent target node that has child target leaves, `SeedGraph.registerAssemblyGroups` SHALL register exactly **one umbrella assembly `ExpansionGroup`** with `root = the parent target node`, `slots = all of that parent's child target leaves`, the same placeholder codegen and `strategyClassFqn`. This umbrella is produced during expansion by an `AssemblyStrategy` (e.g. `ConstructorCall`) bound to those child leaves.

The set of groups registered by `SeedGraph` decomposes into three structural kinds:

- **Path-segment groups**: both `root.loc` and `slot.loc` are `SourceLocation`s; `root.loc.path` is `slot.loc.path` extended by exactly one segment. One per source path-segment edge. These are expanded by the `SourceDescentExpander` during `ExpandGroupsPhase`.
- **Directive-binding groups**: `root.loc` is a `TargetLocation` and `slot.loc` is a `SourceLocation` (the deepest source leaf). One per directive-bridging edge. These are expanded by the `DirectiveBindingExpander` during `ExpandGroupsPhase` to fill the transformation chain between the source leaf and the target leaf.
- **Assembly (umbrella) groups**: `root.loc` is a `TargetLocation` with one or more child target leaves; `slots` are **all** of that node's child target leaves. One per parent target node, registered by `registerAssemblyGroups`. These are expanded by the `AssemblyExpander`, which runs the strategy round at the root so an `AssemblyStrategy` (`ConstructorCall`) can over-emit a multi-slot BOUNDARY binding the child leaves.

`SeedGraph` SHALL NOT pre-classify groups by these kinds (the classification is derived from node `Location`s and slot shape at expansion time via `GroupShapes`). The structural shape of each group is sufficient for `ExpandGroupsPhase` to dispatch to the correct `GroupExpander`.

The group kind is determined by node `Location`s and slot shape, not by a `groupKind` field on `ExpansionGroup`.

#### Scenario: Source-side SEED edges each get a one-slot group; the target side gets one umbrella per parent
- **WHEN** `SeedGraph.apply(...)` emits `s` source-side SEED edges (path-segment + directive-bridging) and target chains under `p` distinct parent target nodes
- **THEN** `MapperGraph.groups()` contains exactly `s` one-slot groups (one per source-side edge, `root = e.to`, `slots = [e.from]`)
- **AND** it contains exactly `p` umbrella assembly groups (one per parent target node, `root = parent`, `slots = all child target leaves of that parent`)
- **AND** no group is registered per individual target-chain edge

#### Scenario: Path-segment edge produces a path-segment group
- **WHEN** `SeedGraph.apply(...)` emits a SEED edge `src[person] → src[person.addresses]:?`
- **THEN** the corresponding `ExpansionGroup` has `root = src[person.addresses]:?`, `slots = [src[person]:Person]`
- **AND** the group's structural shape (root.loc and slot.loc both `SourceLocation`, root path is slot path + one segment) identifies it as a path-segment group for `SourceDescentExpander` dispatch

#### Scenario: Directive-bridging edge produces a directive-binding group
- **WHEN** `SeedGraph.apply(...)` emits a directive-bridging SEED edge `src[person.addresses]:? → tgt[addresses]:?`
- **THEN** the corresponding `ExpansionGroup` has `root = tgt[addresses]:?`, `slots = [src[person.addresses]:?]`
- **AND** the structural shape (root.loc `TargetLocation`, slot.loc `SourceLocation`) identifies it as a directive-binding group for `DirectiveBindingExpander` dispatch

#### Scenario: A parent target node produces one umbrella assembly group over all its children
- **WHEN** `SeedGraph.apply(...)` seeds two directives `@Map(target = "address.street", …)` and `@Map(target = "address.zip", …)`, so `tgt[address]` has child leaves `tgt[address.street]` and `tgt[address.zip]`
- **THEN** exactly one umbrella assembly `ExpansionGroup` is registered with `root = tgt[address]` and `slots = [tgt[address.street], tgt[address.zip]]`
- **AND** no separate group is registered for the `tgt[address.street] → tgt[address]` or `tgt[address.zip] → tgt[address]` target-chain edges
- **AND** the group's shape (root a `TargetLocation` with child-leaf slots) identifies it as an assembly group for `AssemblyExpander` dispatch

### Requirement: SeedGraph emits only SEED-kind edges
`SeedGraph` SHALL emit SEED edges for every directive's path-segment chain, every directive-bridging edge, and every target-chain edge. No REALISED, MARKER, `SUB_SEED`, or `ELEMENT_SEED` edges SHALL be emitted by `SeedGraph` — the only edge kind produced at seed time is `EdgeKind.SEED`.

`SeedGraph` SHALL register one `ExpansionGroup` per SEED edge (see "SeedGraph registers one ExpansionGroup per SEED edge").

For every edge emitted by `SeedGraph`, the following metadata fields SHALL be empty:
- `codegen == Optional.empty()`,
- `strategyClassFqn == Optional.empty()` (`SeedGraph` is not a strategy; the registered `ExpansionGroup`s carry `strategyClassFqn`, the edges do not).

`SeedGraph` SHALL produce a fresh `MapperGraph` per invocation. It SHALL NOT mutate any state outside the returned graph.

`SeedGraph` SHALL NOT perform source-path resolution. Resolving source-path segments is the responsibility of `ExpandGroupsPhase`, via the path-resolver `ExpansionStrategy` implementations (see `source-path-resolution`).

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

### Requirement: Umbrella child leaves are name-keyed demands, not single-type targets

The umbrella assembly group's slots — the parent target node's child target leaves (see "SeedGraph registers one ExpansionGroup per SEED edge") — SHALL be **name-keyed demands**: each child leaf identifies one directive-declared target field by name and carries an empty `type` at seed time. A child leaf SHALL NOT be treated as a single-type target that exactly one producer pins.

When the target's accessible constructors disagree on a child's parameter type (type-divergent overloads such as `Address(int number, …)` and `Address(long number, …)`), the engine SHALL NOT force a single type onto the shared name-keyed leaf. Instead, per-`(name, required-type)` typed leaves and their directive-binding conversions are minted during expansion by the assembly path (see the `graph-expansion` capability), each fed from the **one shared source value** seeded for that directive.

`SeedGraph` SHALL therefore still emit exactly **one** name-keyed child leaf per distinct directive-declared target-field name, and exactly **one** directive-binding group per directive (the shared source→name binding). `SeedGraph` SHALL NOT pre-materialise per-type leaves, because constructor parameter types are not known at seed time. The pre-seeded umbrella shape is unchanged; only the contract of a child leaf — a name demand whose typing is deferred to per-constructor expansion, rather than a single-type pin target — is clarified by this requirement.

#### Scenario: A field name seeds exactly one name-keyed leaf regardless of constructor overloads
- **WHEN** `SeedGraph.apply(...)` seeds `@Map(target = "address.number", source = "person.address.number")` for a target `Human.Address` that declares both `Address(int number, String street)` and `Address(long number, String street)`
- **THEN** the umbrella assembly group for `tgt[address]` contains exactly one child leaf `tgt[address.number]` with empty `type`
- **AND** exactly one directive-binding group is registered with `root = tgt[address.number]:?` and `slot = src[person.address.number]:?`
- **AND** no per-type leaf (`tgt[address.number]:int`, `tgt[address.number]:long`) is created at seed time

#### Scenario: Type-divergent overloads do not collide on the seeded leaf
- **WHEN** `SeedGraph.apply(...)` seeds a directive whose declared target field is consumed by two type-divergent overloaded constructors
- **THEN** the seeded name-keyed leaf carries no type and is bound to no single constructor's parameter type at seed time
- **AND** resolving the divergent per-`(name, required-type)` typed leaves is deferred to the assembly path during expansion

# Seed Graph Spec

## Purpose

This spec defines the SeedGraph stage that constructs an initial `MapperGraph` from discovered mapper method mappings, producing nodes and edges that represent source parameters, return types, and directive-seeded paths.

## Requirements

### Requirement: SeedGraph emits only SEED-kind edges
`SeedGraph` SHALL emit SEED edges for every directive's untyped chain. When seed-time path resolution succeeds (per *Seed-time source-path typing via PathSegmentResolver*), `SeedGraph` SHALL additionally emit `REALISED` edges along the typed source chain and `MARKER` edges linking the untyped seed nodes to their typed counterparts. The directive-bridging edge is always SEED; no `SUB_SEED` edge is emitted by `SeedGraph` (the kind no longer exists in `EdgeKind`).

For every edge emitted by `SeedGraph`, the following metadata fields SHALL be empty:
- `codegen == Optional.empty()`,
- `strategyClassFqn == Optional.empty()` (`SeedGraph` is not a strategy).

`SeedGraph` SHALL produce a fresh `MapperGraph` per invocation. It SHALL NOT mutate any state outside the returned graph.

#### Scenario: All seed-emitted edges have kind SEED
- **WHEN** `SeedGraph.apply(...)` produces a graph for any non-empty mapper
- **THEN** `graph.edges().allMatch(e -> e.getKind() == EdgeKind.SEED)` returns `true`

#### Scenario: No realised, marker, or sub-seed edges are emitted at seed time
- **WHEN** `SeedGraph.apply(...)` produces a graph for any non-empty mapper
- **THEN** the graph contains directive SEED edges plus any seed-time-emitted REALISED/MARKER edges from path resolution; no `SUB_SEED` edges (the kind does not exist)

#### Scenario: Seed edges have empty codegen and strategyClassFqn
- **WHEN** `SeedGraph.apply(...)` produces a graph for any non-empty mapper
- **THEN** every edge has `codegen.isEmpty()` and `strategyClassFqn.isEmpty()`

#### Scenario: Empty mapper produces an empty graph
- **WHEN** `SeedGraph.apply(...)` is invoked with a `MapperMappings` whose `methods` list is empty
- **THEN** the returned `MapperGraph` contains zero nodes and zero edges

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

### Requirement: Seed-time source-path typing via PathSegmentResolver

For every `MappingDirective` `d` on every method `M` whose `source` path has more than one segment, `SeedGraph` SHALL invoke the seed-time path-walking algorithm defined in the `source-path-resolution` capability. The walk produces, for each successfully resolved non-root segment:

- one typed source node with `loc = SourceLocation([s1, ..., si])`, `type = Optional.of(resolvedReturnType)`, scope `MethodScope(M)`,
- one `REALISED` edge from the previous (parent) typed source node to the new typed node, carrying the resolver-provided `EdgeCodegen` and `weight`,
- one `MARKER` edge from the corresponding untyped seed-leaf source node (already created by *Directive-seeded source chains*) to the new typed node, for diagnostic origin tracking,
- one 1-slot `ExpansionGroup` registered via `graph.addGroup(...)` with `root = typedNode`, `slots = [parentTypedNode]`, `strategyClassFqn = resolver.getClass().getName()`.

Within a single method scope, two directives sharing a path prefix SHALL reuse the typed nodes and group already registered for the shared prefix (idempotent on equal-path keys).

`SeedGraph` SHALL inject the resolver list via constructor parameter; the wiring is `ProcessorModule.pathSegmentResolvers()` (defined in `expansion-strategy-spi`).

#### Scenario: Typed source chain coexists with the untyped chain
- **WHEN** `SeedGraph.apply(...)` is invoked for `@Map(target = "lastName", source = "person.lastName")` with a `GetterPathResolver` matching `Person.getLastName()`
- **THEN** the graph contains BOTH the untyped seed source node `src[person.lastName] : ?` AND the typed node `src[person.lastName] : String`
- **AND** the untyped node has a `MARKER` edge to the typed node
- **AND** the typed node has an incoming `REALISED` edge from `src[person] : Person`

#### Scenario: Resolution failure leaves the untyped chain unchanged
- **WHEN** `SeedGraph.apply(...)` is invoked for `@Map(target = "x", source = "person.unknown")` and no resolver matches `unknown` on `Person`
- **THEN** the typed source chain is not extended past `src[person]`
- **AND** the untyped seed source chain remains as the *Directive-seeded source chains* requirement defines

### Requirement: Directive-bridging edge

For every `MappingDirective` on every method `M`, `SeedGraph` SHALL emit exactly one edge constructed via `Edge.seed(...)` bridging the directive's deepest source node to the deepest target node (the node for the full target path), carrying the `@Map` mirror.

The choice of *deepest source node* SHALL be:

- the deepest **typed** source node produced by *Seed-time source-path typing via PathSegmentResolver* (i.e., the typed node corresponding to the full source path), when the full source path was successfully resolved;
- otherwise the deepest **untyped** source node (the existing seed-leaf for the full source path).

The bridging edge SHALL therefore have `kind == EdgeKind.SEED` and `weight == Weights.SENTINEL_UNREALISED` (factory-enforced).

#### Scenario: One bridging SEED edge per directive (untyped path resolution failure)
- **WHEN** `SeedGraph.apply(...)` is invoked for `@Map(target = "name", source = "first")` on `Human map(Person person)` and no resolver matches `first` (or path has one segment)
- **THEN** the returned graph contains exactly one edge with `from = source-node(["first"])` (untyped leaf) and `to = target-node(["name"])`, `kind = EdgeKind.SEED`, `weight = Weights.SENTINEL_UNREALISED`, carrying the `@Map` mirror

#### Scenario: Bridging edge originates from the typed source when resolution succeeds
- **WHEN** `SeedGraph.apply(...)` is invoked for `@Map(target = "lastName", source = "person.lastName")` on `Human map(Person person)` with a `GetterPathResolver` matching `Person.getLastName()`
- **THEN** the bridging edge's `from` is the typed source node `src[person.lastName] : String`, NOT the untyped seed leaf
- **AND** the bridging edge's `to` is the untyped target leaf `tgt[lastName] : ?`

#### Scenario: Two directives produce two bridging SEED edges
- **WHEN** `SeedGraph.apply(...)` is invoked for two directives on the same method
- **THEN** the returned graph contains exactly two bridging edges, one per directive, each with `kind = SEED`, `weight = Weights.SENTINEL_UNREALISED`, carrying its own `@Map` mirror
- **AND** each edge's `from` is the typed source node when the corresponding source path resolves, otherwise the untyped seed leaf

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

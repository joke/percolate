## ADDED Requirements

### Requirement: SeedGraph emits only SEED-kind edges
For every `MapperGraph` produced by `SeedGraph`, every `Edge` in the graph SHALL have `kind == EdgeKind.SEED`. `SeedGraph` SHALL NOT emit `REALISED`, `MARKER`, or `SUB_SEED` edges in this change. This is a pinned invariant of the seed stage that distinguishes Phase 1 output from Phase 2 expansion output.

For every edge emitted by `SeedGraph`, the following metadata fields SHALL be empty:
- `groupId == Optional.empty()`,
- `codegen == Optional.empty()`,
- `strategyClassFqn == Optional.empty()` (`SeedGraph` is not a strategy).

#### Scenario: All seed-emitted edges have kind SEED
- **WHEN** `SeedGraph.apply(...)` produces a graph for any non-empty mapper
- **THEN** `graph.edges().allMatch(e -> e.getKind() == EdgeKind.SEED)` returns `true`

#### Scenario: No realised, marker, or sub-seed edges are emitted at seed time
- **WHEN** `SeedGraph.apply(...)` produces a graph for any non-empty mapper
- **THEN** the graph contains zero edges of kind `REALISED`, zero of kind `MARKER`, and zero of kind `SUB_SEED`

#### Scenario: Seed edges have empty codegen, groupId, and strategyClassFqn
- **WHEN** `SeedGraph.apply(...)` produces a graph for any non-empty mapper
- **THEN** every edge has `codegen.isEmpty()`, `groupId.isEmpty()`, and `strategyClassFqn.isEmpty()`

## MODIFIED Requirements

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
For every `MappingDirective` on every method `M`, `SeedGraph` SHALL emit exactly one edge constructed via `Edge.seed(...)` bridging the deepest source node (the node for the full source path) to the deepest target node (the node for the full target path), carrying the `@Map` mirror.

The bridging edge SHALL therefore have `kind == EdgeKind.SEED` and `weight == Weights.SENTINEL_UNREALISED` (factory-enforced).

#### Scenario: One bridging SEED edge per directive
- **WHEN** `SeedGraph.apply(...)` is invoked for `@Map(target = "name", source = "first")` on `Human map(Person person)`
- **THEN** the returned graph contains exactly one edge with `from = source-node(["first"])` and `to = target-node(["name"])`, `kind = EdgeKind.SEED`, `weight = Weights.SENTINEL_UNREALISED`, carrying the `@Map` mirror

#### Scenario: Two directives produce two bridging SEED edges
- **WHEN** `SeedGraph.apply(...)` is invoked for two directives on the same method
- **THEN** the returned graph contains exactly two bridging edges, one per directive, each with `kind = SEED`, `weight = Weights.SENTINEL_UNREALISED`, carrying its own `@Map` mirror

### Requirement: Directed acyclicity at seed time
For every `MapperGraph` produced by `SeedGraph`, the underlying directed graph SHALL be acyclic (a DAG). The introduction of `EdgeKind` and the weight flip to `Weights.SENTINEL_UNREALISED` does NOT alter graph topology — only edge weights and labels. The acyclicity invariant therefore continues to hold for the seed stage. This is a documented invariant SHALL be asserted by tests.

Note: the seed graph is not necessarily a forest in the undirected sense — multiple directives on the same method share common nodes (parameter roots, return-type root), so paths converge creating undirected cycles while remaining directed-acyclic.

#### Scenario: Seed graph is acyclic after the weight and kind flip
- **WHEN** `SeedGraph.apply(...)` produces a graph for any non-empty mapper
- **THEN** `MapperGraph.isAcyclic()` returns `true`
- **AND** all edges have `kind == EdgeKind.SEED`
- **AND** all edges have `weight == Weights.SENTINEL_UNREALISED`

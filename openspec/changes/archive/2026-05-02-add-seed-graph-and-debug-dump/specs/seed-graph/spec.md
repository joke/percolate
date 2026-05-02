## ADDED Requirements

### Requirement: SeedGraph stage
The processor SHALL define a stage `SeedGraph` in package `io.github.joke.percolate.processor` that consumes a `MapperMappings` and returns a `MapperGraph`. The stage SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)` (Dagger-injected).

`SeedGraph` SHALL produce a fresh `MapperGraph` per invocation. It SHALL NOT mutate any state outside the returned graph.

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
- one node per non-empty prefix `[s1, ..., si]` (for `i = 1..k`) with `loc = SourceLocation([s1, ..., si])`, `type = Optional.empty()`, `scope = MethodScope(<M>)`,
- one edge from the parameter-root node (the parameter named in the directive's source first-segment) to the node for `[s1]`, weight `1`, `directive = Optional.of(<the @Map mirror>)`,
- for each `i = 1..k-1`, one edge from the node for `[s1, ..., si]` to the node for `[s1, ..., si+1]`, weight `1`, `directive = Optional.of(<the @Map mirror>)`.

The first segment of every directive's source MUST match a parameter name of the method; directives whose first source segment does not name any parameter are rejected by `ValidateSourceParameters` before this stage runs. `SeedGraph` assumes all directives have valid source parameters.

#### Scenario: Single-segment source seeds one source node and one edge
- **WHEN** `SeedGraph.apply(...)` is invoked for a method `Human map(Person person)` with `@Map(target = "person", source = "person")`
- **THEN** the returned graph contains a node with `loc = SourceLocation(["person"])`, empty `type`, scoped to that method
- **AND** an edge with `from = parameter-root("person")`, `to = the source node`, `weight = 1`, `directive` carrying the `@Map` mirror

#### Scenario: Dotted source seeds a chain
- **WHEN** `SeedGraph.apply(...)` is invoked for a directive `@Map(target = "x", source = "person.address.street")`
- **THEN** the returned graph contains source nodes for paths `["person.address"]`, `["person.address.street"]` (each as its full multi-segment path) — implementation MAY choose to use the parameter-root node directly for the single-segment "person" prefix
- **AND** edges chain them: parameter-root → `[person, address]` → `[person, address, street]`, each carrying the `@Map` mirror

### Requirement: Directive-seeded target chains
For every `MappingDirective` on every method `M`, `SeedGraph` SHALL split the directive's `target` string on `.` to produce segments `t1, t2, ..., tk`. It SHALL emit (idempotently):
- one node per non-empty prefix `[t1, ..., ti]` (for `i = 1..k`) with `loc = TargetLocation([t1, ..., ti])`, `type = Optional.empty()`, `scope = MethodScope(<M>)`,
- one edge from the deepest target node (`[t1, ..., tk]`) to the next-shallower target node, then to the next, until reaching the return-type root node. All edges weight `1`, `directive = Optional.of(<the @Map mirror>)`.

In other words, chains flow OUTWARD from the deepest target slot to the return-type root.

#### Scenario: Single-segment target seeds one target node and one edge to the root
- **WHEN** `SeedGraph.apply(...)` is invoked for a method `Human map(Person person)` with `@Map(target = "lastName", source = "lastName")`
- **THEN** the returned graph contains a node with `loc = TargetLocation(["lastName"])`, empty `type`, scoped to that method
- **AND** an edge from the target node `["lastName"]` to the return-type root `[]`, weight `1`, carrying the `@Map` mirror

#### Scenario: Dotted target seeds a chain
- **WHEN** `SeedGraph.apply(...)` is invoked for a directive `@Map(target = "address.line1", source = "x")`
- **THEN** the returned graph contains target nodes for `["address"]` and `["address","line1"]`
- **AND** edges chain them: `["address","line1"]` → `["address"]` → return-type root, each carrying the `@Map` mirror

### Requirement: Directive-bridging edge
For every `MappingDirective` on every method `M`, `SeedGraph` SHALL emit exactly one edge bridging the deepest source node (the node for the full source path) to the deepest target node (the node for the full target path). Weight `1`, `directive = Optional.of(<the @Map mirror>)`.

#### Scenario: One bridging edge per directive
- **WHEN** `SeedGraph.apply(...)` is invoked for `@Map(target = "name", source = "first")` on `Human map(Person person)`
- **THEN** the returned graph contains exactly one edge with `from = source-node(["first"])` and `to = target-node(["name"])`, weight `1`, carrying the `@Map` mirror

#### Scenario: Two directives produce two bridging edges
- **WHEN** `SeedGraph.apply(...)` is invoked for two directives on the same method
- **THEN** the returned graph contains exactly two bridging edges, one per directive, each carrying its own `@Map` mirror

### Requirement: Forest invariant at seed time
For every `MapperGraph` produced by `SeedGraph`, the underlying directed graph SHALL satisfy `org.jgrapht.GraphTests.isForest(...)` when treated as undirected. This is a documented invariant of the seed stage and SHALL be asserted by tests.

#### Scenario: Seed graph is a forest
- **WHEN** `SeedGraph.apply(...)` produces a graph for any non-empty mapper
- **THEN** `GraphTests.isForest(<undirected view of the graph>)` returns `true`

### Requirement: Idempotent node and edge addition
When two `MappingDirective`s on the same method share path prefixes, `SeedGraph` SHALL NOT emit duplicate nodes or duplicate edges for those prefixes. Equality is structural (per `Node.equals` and `Edge.equals`).

#### Scenario: Shared prefix is not duplicated
- **WHEN** `SeedGraph.apply(...)` is invoked for two directives both targeting paths under `address.*` (e.g., `address.street` and `address.city`)
- **THEN** exactly one node exists for `TargetLocation(["address"])` in the resulting graph
- **AND** exactly one edge connects that node to the return-type root

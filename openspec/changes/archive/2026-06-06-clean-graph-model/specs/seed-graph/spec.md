## MODIFIED Requirements

### Requirement: SeedGraph emits only SEED-kind edges
`SeedGraph` SHALL emit SEED edges for every directive's path-segment chain, every directive-bridging edge, and every target-chain edge. No REALISED edges SHALL be emitted at seed time — the only edge kind produced is `EdgeKind.SEED`. (`MARKER`, `SUB_SEED`, and `ELEMENT_SEED` no longer exist as `EdgeKind` values.)

`SeedGraph` SHALL register one `ExpansionGroup` per SEED edge (see "SeedGraph registers one ExpansionGroup per SEED edge").

For every edge emitted by `SeedGraph`, the following metadata fields SHALL be empty:
- `codegen == Optional.empty()`,
- `strategyClassFqn == Optional.empty()` (`SeedGraph` is not a strategy; the registered `ExpansionGroup`s are non-traversable labels carrying neither codegen nor `strategyClassFqn`).

`SeedGraph` SHALL produce a fresh `MapperGraph` per invocation. It SHALL NOT mutate any state outside the returned graph.

`SeedGraph` SHALL NOT perform source-path resolution. Resolving source-path segments is the responsibility of `ExpandGroupsPhase`, via the path-resolver `ExpansionStrategy` implementations (see `source-path-resolution`).

#### Scenario: All seed-emitted edges have kind SEED
- **WHEN** `SeedGraph.apply(...)` produces a graph for any non-empty mapper
- **THEN** `graph.edges().allMatch(e -> e.getKind() == EdgeKind.SEED)` returns `true`

#### Scenario: No REALISED edges are emitted at seed time
- **WHEN** `SeedGraph.apply(...)` produces a graph for any non-empty mapper
- **THEN** the graph contains only SEED edges; no `REALISED` edge exists

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

### Requirement: Idempotent node and edge addition
When two `MappingDirective`s on the same method share path prefixes, `SeedStage` SHALL NOT emit duplicate nodes or duplicate edges for those prefixes. Non-duplication is achieved **producer-side**, by `SeedStage` itself, not by structural equality inside the graph (`Node.equals` and `Edge.equals` are now instance-identity):

- `SeedStage` owns a `(scope, location) → Node` canonical map (a per-`apply` helper). A structural variable is created once and reused on every later request for the same `(scope, location)`.
- Because each canonical node has exactly one structural producer edge (the edge from its prefix parent), a chain segment edge — and its source-side demand — is emitted **only when the canonical node was freshly created**. A reused node means the prefix is already seeded, so neither the edge nor its demand is re-emitted.
- The directive-bridging edge is emitted once per `MappingDirective`; the degenerate case of two directives sharing both source and target is guarded by a single `getAllEdges(from, to)` existence check, not by graph value-dedup.

`MapperGraph.addEdge` SHALL therefore not be relied upon to reject duplicates; `SeedStage` SHALL never offer one.

#### Scenario: Shared prefix is not duplicated
- **WHEN** `SeedStage.apply(...)` is invoked for two directives both targeting paths under `address.*` (e.g., `address.street` and `address.city`)
- **THEN** exactly one node exists for `TargetLocation(["address"])` in the resulting graph
- **AND** exactly one edge connects that node to the return-type root
- **AND** exactly one demand group is registered for that shared `address → root` segment

#### Scenario: Non-duplication does not rely on graph-level structural equality
- **WHEN** the seed stage's chain-building logic is inspected
- **THEN** segment edge and demand emission is gated on "canonical node freshly created"
- **AND** it does not depend on `MapperGraph.addEdge` returning `false` for a structurally-equal edge

# Seed Graph Spec

## Purpose

This spec defines the SeedGraph stage that constructs an initial `MapperGraph` from discovered mapper method mappings, producing nodes and edges that represent source parameters, return types, and directive-seeded paths.

## Requirements

### Requirement: SeedGraph registers one ExpansionGroup per SEED edge

For every **source-side** SEED edge `e: from → to` emitted by the seed stage — path-segment edges and directive-bridging edges — the seed stage SHALL register one source-side demand by tagging `to` (the demand `root`) and `from` (the demand input) with one shared `GroupId`, and adding a label-only `ExpansionGroup{id, root = to}` to the graph. The seed stage SHALL NOT attach any codegen to a group (no placeholder `GroupCodegen`); a group is a non-traversable label (see `graph-model`). The SEED edge itself is NOT a member of the group's view — the view is the `MaskSubgraph` derived from the shared `GroupId` and shows only `REALISED` edges produced during expansion.

The **target side is consolidated, not per-edge.** Target-chain SEED edges (`tgt[child] → tgt[parent]`) do NOT each register a group. Instead, for every parent target node that has child target leaves, the seed stage SHALL register exactly **one umbrella assembly demand**: it tags the parent (`root`) and all of its child target leaves with one shared `GroupId` and adds the label-only `ExpansionGroup{id, root = parent}`. Source-side and umbrella registration SHALL go through **one** unified `registerDemand(root, inputs)` operation (the source case has one input, the umbrella case has all child leaves); there SHALL be no separate `targetChildren` post-pass.

The set of groups registered by the seed stage decomposes into three structural kinds, derived from node `Location`s and tagged-input shape at expansion time via `GroupShapes` (the seed stage SHALL NOT store a `groupKind` field):

- **Path-segment groups**: both `root.loc` and the input `loc` are `SourceLocation`s; `root.loc.path` is the input path extended by one segment. Expanded by the `SourceDescentExpander`.
- **Directive-binding groups**: `root.loc` is a `TargetLocation` and the input `loc` is a `SourceLocation` (the deepest source variable). Expanded by the `DirectiveBindingExpander`.
- **Assembly (umbrella) groups**: `root.loc` is a `TargetLocation` with one or more child target leaves as inputs. Expanded by the `AssemblyExpander`.

#### Scenario: Source-side edges each get a one-input demand; the target side gets one umbrella per parent
- **WHEN** the seed stage emits `s` source-side SEED edges (path-segment + directive-bridging) and target chains under `p` distinct parent target nodes
- **THEN** the graph contains exactly `s` one-input demand groups (one per source-side edge, `root = e.to`, the single tagged input is `e.from`)
- **AND** it contains exactly `p` umbrella assembly groups (one per parent target node, `root = parent`, tagged inputs = all child target leaves of that parent)
- **AND** no group is registered per individual target-chain edge

#### Scenario: Groups carry no codegen
- **WHEN** any `ExpansionGroup` registered by the seed stage is inspected
- **THEN** it exposes only `getId()` and `getRoot()` and exposes no codegen
- **AND** its inputs are derived from the nodes tagged with its `GroupId`, not stored as a `slots` list

#### Scenario: A parent target node produces one umbrella assembly group over all its children
- **WHEN** the seed stage seeds `@Map(target = "address.street", …)` and `@Map(target = "address.zip", …)`, so `tgt[address]` has child leaves `tgt[address.street]` and `tgt[address.zip]`
- **THEN** exactly one umbrella assembly group is registered with `root = tgt[address]` and tagged inputs `tgt[address.street]`, `tgt[address.zip]`
- **AND** no separate group is registered for the `tgt[address.street] → tgt[address]` or `tgt[address.zip] → tgt[address]` target-chain edges

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

For every `MappingDirective` on every method `M`, the seed stage SHALL emit exactly one edge constructed via `Edge.seed(...)` bridging the directive's **deepest source variable** to the deepest target node (the node for the full target path), carrying the `@Map` mirror.

The bridging edge's `from` is the deepest source variable: for a multi-segment source path it is the untyped source leaf (`SourceLocation([s1, ..., sk]):?`); for a **single-segment** source whose segment names a parameter, it is the **typed parameter-root node** itself. A parameter is a real typed variable, so a single-segment source binds directly to it — no untyped twin node is minted. (This replaces the prior "the bridging `from` is always the untyped source leaf" rule, which is inconsistent with the variable model.)

The bridging edge SHALL therefore have `kind == EdgeKind.SEED` and `weight == Weights.SENTINEL_UNREALISED` (factory-enforced). One directive-binding demand SHALL be registered per bridging edge with `root = tgt[full-target-path]:?` and its single tagged input being the bridging edge's `from`.

#### Scenario: Single-segment source binds from the typed parameter root
- **WHEN** the seed stage is invoked for `@Map(target = "name", source = "person")` on `Human map(Person person)`
- **THEN** the bridging edge's `from` is the typed parameter-root node `src[person]:Person`
- **AND** no untyped `src[person]:?` twin node is created
- **AND** the directive-binding demand has `root = tgt[name]:?` and its tagged input is the parameter root

#### Scenario: Multi-segment source still bridges from the untyped leaf
- **WHEN** the seed stage is invoked for `@Map(target = "lastName", source = "person.lastName")` on `Human map(Person person)`
- **THEN** the bridging edge's `from` is the untyped seed leaf `src[person.lastName]:?`
- **AND** the bridging edge's `to` is the untyped target leaf `tgt[lastName]:?`

### Requirement: Directed acyclicity at seed time
For every `MapperGraph` produced by `SeedGraph`, the underlying directed graph SHALL be acyclic (a DAG). The introduction of `EdgeKind` and the weight flip to `Weights.SENTINEL_UNREALISED` does NOT alter graph topology — only edge weights and labels. The acyclicity invariant therefore continues to hold for the seed stage. This is a documented invariant SHALL be asserted by tests.

Note: the seed graph is not necessarily a forest in the undirected sense — multiple directives on the same method share common nodes (parameter roots, return-type root), so paths converge creating undirected cycles while remaining directed-acyclic.

#### Scenario: Seed graph is acyclic after the weight and kind flip
- **WHEN** `SeedGraph.apply(...)` produces a graph for any non-empty mapper
- **THEN** `MapperGraph.isAcyclic()` returns `true`
- **AND** all edges have `kind == EdgeKind.SEED`
- **AND** all edges have `weight == Weights.SENTINEL_UNREALISED`

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

### Requirement: Seed stage assumes valid source parameters and drops no directive silently

The seed stage SHALL treat `ValidateSourceParameters` as a hard precondition: by the time it runs, every directive's first source segment names a method parameter. The seed stage SHALL NOT contain a fallback branch minting an orphan source node for a non-parameter first segment, and SHALL NOT silently drop a directive with an empty source path. Any directive reaching the seed stage SHALL be fully seeded; an empty or invalid source at seed time is a precondition violation, not a silent no-op.

#### Scenario: No orphan source chain for a non-parameter first segment
- **WHEN** the seed stage source of `buildSourceChain` (or its successor) is inspected
- **THEN** there is no branch that mints a source node when the first segment does not name a parameter

#### Scenario: No silent directive drop
- **WHEN** the seed stage processes the directives of a validated mapper
- **THEN** every directive produces its source chain, target chain, and bridging edge
- **AND** no directive is skipped without a diagnostic

### Requirement: Stage classes follow the *Stage naming convention

Every processor pipeline stage that `implements Stage` SHALL have a class name ending in `Stage`. In particular the seed stage SHALL be named `SeedStage` (renamed from `SeedGraph`), reflecting that it is a stage that initially populates the single `MapperGraph` and does **not** own a separate "seeded graph" artifact. Internal `*Phase` orchestration classes (which do not `implement Stage`) are exempt.

#### Scenario: All Stage implementations end in Stage
- **WHEN** every class implementing `Stage` under `processor/src/main/java/.../stages/` is inspected
- **THEN** each class name ends with the suffix `Stage`
- **AND** the seed stage class is named `SeedStage`

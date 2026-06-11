## MODIFIED Requirements

### Requirement: Directive-seeded source chains
This requirement applies only to **source-bearing** directives. A **constant** directive declares no `source` and SHALL NOT seed any source chain or parameter-root edge (see "Constant directives seed a constant-value node and bridging edge").

For every source-bearing `MappingDirective` on every method `M`, `SeedGraph` SHALL split the directive's `source` string on `.` to produce segments `s1, s2, ..., sk`. It SHALL emit (idempotently):
- one node per non-empty prefix `[s1, ..., si]` (for `i = 1..k`) with `loc = SourceLocation([s1, ..., si])`, `type = Optional.empty()`, `scope = MethodScope(<M>)`, `parent = Optional.empty()`,
- one edge constructed via `Edge.seed(...)` from the parameter-root node (the parameter named in the directive's source first-segment) to the node for `[s1]`, carrying the `@Map` mirror,
- for each `i = 1..k-1`, one edge constructed via `Edge.seed(...)` from the node for `[s1, ..., si]` to the node for `[s1, ..., si+1]`, carrying the `@Map` mirror.

All such edges SHALL therefore have `kind == EdgeKind.SEED` and `weight == Weights.SENTINEL_UNREALISED` (factory-enforced).

The first segment of every source-bearing directive's source MUST match a parameter name of the method; directives whose first source segment does not name any parameter are rejected by `ValidateSourceParameters` before this stage runs. `SeedGraph` assumes all source-bearing directives have valid source parameters.

#### Scenario: Single-segment source seeds one source node and one SEED edge
- **WHEN** `SeedGraph.apply(...)` is invoked for a method `Human map(Person person)` with `@Map(target = "person", source = "person")`
- **THEN** the returned graph contains a node with `loc = SourceLocation(["person"])`, empty `type`, scoped to that method, empty `parent`
- **AND** an edge with `from = parameter-root("person")`, `to = the source node`, `kind = EdgeKind.SEED`, `weight = Weights.SENTINEL_UNREALISED`, `directive` carrying the `@Map` mirror

#### Scenario: Dotted source seeds a chain of SEED edges
- **WHEN** `SeedGraph.apply(...)` is invoked for a directive `@Map(target = "x", source = "person.address.street")`
- **THEN** the returned graph contains source nodes for paths `["person.address"]`, `["person.address.street"]` (each as its full multi-segment path) — implementation MAY choose to use the parameter-root node directly for the single-segment "person" prefix
- **AND** edges chain them: parameter-root → `[person, address]` → `[person, address, street]`, every edge with `kind = SEED`, `weight = Weights.SENTINEL_UNREALISED`, each carrying the `@Map` mirror

#### Scenario: Constant directive seeds no source chain
- **WHEN** `SeedGraph.apply(...)` is invoked for a directive `@Map(target = "status", constant = "ACTIVE")`
- **THEN** the returned graph contains no `SourceLocation` node and no parameter-root edge for that directive

## ADDED Requirements

### Requirement: Constant directives seed a constant-value node and bridging edge

For every **constant** `MappingDirective` on every method `M`, `SeedGraph` SHALL:
- build the directive's target chain exactly as for any other directive (one node per target prefix, chained outward to the return-type root),
- plant one **constant-value node** carrying the directive's raw `constant` string, with `type = Optional.empty()`, `scope = MethodScope(<M>)`, `parent = Optional.empty()`, and a location that distinguishes it as a constant value (not a `SourceLocation`),
- emit exactly one bridging edge via `Edge.seed(...)` from the constant-value node to the deepest target node (the node for the full target path), carrying the `@Map` mirror, with `kind == EdgeKind.SEED` and `weight == Weights.SENTINEL_UNREALISED`,
- register one directive-binding demand with `root = tgt[full-target-path]:?` whose single tagged input is the constant-value node.

The constant-value node SHALL be **untyped at seed time**; its type is resolved later by the `ConstantValue` strategy from the demanded target type (see the `constant-values` capability). The seed stage SHALL NOT attempt to coerce or type the literal.

#### Scenario: Constant directive seeds a constant-value node bridged to the target
- **WHEN** `SeedGraph.apply(...)` is invoked for a method `Human map(Person person)` with `@Map(target = "status", constant = "ACTIVE")`
- **THEN** the returned graph contains an untyped constant-value node carrying `"ACTIVE"`, scoped to that method
- **AND** a bridging edge from the constant-value node to the target node `["status"]` with `kind = EdgeKind.SEED` and `weight = Weights.SENTINEL_UNREALISED`, carrying the `@Map` mirror
- **AND** a directive-binding demand with `root = tgt[status]:?` whose single tagged input is the constant-value node

#### Scenario: Constant directive still seeds its target chain to the root
- **WHEN** `SeedGraph.apply(...)` is invoked for a directive `@Map(target = "address.zip", constant = "00000")`
- **THEN** the returned graph contains target nodes for `["address"]` and `["address","zip"]` chained outward to the return-type root, each edge `kind = SEED`
- **AND** the bridging edge's `to` is the deepest target node `["address","zip"]`

#### Scenario: Seed graph with a constant remains acyclic
- **WHEN** `SeedGraph.apply(...)` produces a graph for a mapper containing a constant directive
- **THEN** `MapperGraph.isAcyclic()` returns `true`

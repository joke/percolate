## ADDED Requirements

### Requirement: Seed-time source-path typing via PathSegmentResolver

For every `MappingDirective` `d` on every method `M` whose `source` path has more than one segment, `SeedGraph` SHALL invoke the seed-time path-walking algorithm defined in the `source-path-resolution` capability. The walk produces, for each successfully resolved non-root segment:

- one typed source node with `loc = SourceLocation([s1, ..., si])`, `type = Optional.of(resolvedReturnType)`, scope `MethodScope(M)`,
- one `REALISED` edge from the previous (parent) typed source node to the new typed node, carrying the resolver-provided `EdgeCodegen` and `weight`,
- one `MARKER` edge from the corresponding untyped seed-leaf source node (already created by *Directive-seeded source chains*) to the new typed node, for diagnostic origin tracking,
- one 1-slot `ExpansionGroup` registered via `graph.addGroup(...)` with `root = typedNode`, `slots = [parentTypedNode]`, `strategyClassFqn = resolver.getClass().getName()`.

Within a single method scope, two directives sharing a path prefix SHALL reuse the typed nodes and group already registered for the shared prefix (idempotent on equal-path keys).

`SeedGraph` SHALL inject the resolver list via constructor parameter; the wiring is `ProcessorModule.pathSegmentResolvers()` (defined in the `source-path-resolution` capability).

#### Scenario: Typed source chain coexists with the untyped chain
- **WHEN** `SeedGraph.apply(...)` is invoked for `@Map(target = "lastName", source = "person.lastName")` with a `GetterPathResolver` matching `Person.getLastName()`
- **THEN** the graph contains BOTH the untyped seed source node `src[person.lastName] : ?` (per *Directive-seeded source chains*) AND the typed node `src[person.lastName] : String`
- **AND** the untyped node has a `MARKER` edge to the typed node
- **AND** the typed node has an incoming `REALISED` edge from `src[person] : Person`

#### Scenario: Resolution failure leaves the untyped chain unchanged
- **WHEN** `SeedGraph.apply(...)` is invoked for `@Map(target = "x", source = "person.unknown")` and no resolver matches `unknown` on `Person`
- **THEN** the typed source chain is not extended past `src[person]`
- **AND** the untyped seed source chain remains exactly as the *Directive-seeded source chains* requirement defines

## MODIFIED Requirements

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
- **AND** the edge has `kind = EdgeKind.SEED`, `weight = Weights.SENTINEL_UNREALISED`, carrying the `@Map` mirror

#### Scenario: Two directives produce two bridging SEED edges
- **WHEN** `SeedGraph.apply(...)` is invoked for two directives on the same method
- **THEN** the returned graph contains exactly two bridging edges, one per directive, each with `kind = SEED`, `weight = Weights.SENTINEL_UNREALISED`, carrying its own `@Map` mirror
- **AND** each edge's `from` is the typed source node when the corresponding source path resolves, otherwise the untyped seed leaf

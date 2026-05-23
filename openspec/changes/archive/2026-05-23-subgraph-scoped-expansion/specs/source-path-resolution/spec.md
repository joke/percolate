## ADDED Requirements

### Requirement: Expansion-time path-segment-group resolution

`ExpandGroupsPhase` SHALL invoke registered `PathSegmentResolver`s when it encounters a path-segment group during work-list processing. A group is recognised as a path-segment group by its structural shape (see `graph-expansion` capability — "Path-segment-group resolution via PathSegmentResolver" requirement): both `root.loc` and `slot.loc` are `SourceLocation`s, and `root.loc.path` extends `slot.loc.path` by exactly one segment.

The resolver invocation rule:

1. The slot MUST be typed (`slot.type.isPresent()`). If the slot is not yet typed, the path-segment group is not ready — the work-list's topological ordering guarantees the slot's own path-segment group has been processed first.
2. Iterate the injected `pathSegmentResolvers` in `Class.getName()` ascending order. For each resolver, call `resolve(slot.type.get(), appendedSegment, ctx)`. Take the first non-empty `Optional<ResolvedSegment>` — call it `rs`.
3. If no resolver matches, record `GroupOutcome.unsatNoPlan(group, slot)` and return. The root remains untyped.
4. On match:
   - Set the root's type via `root.setType(rs.getReturnType())` (in-place; instance identity preserved — see `graph-model`).
   - Emit a REALISED edge from `slot` to `root` with `weight = rs.getWeight()`, `codegen = rs.getCodegen()`, `strategyClassFqn = resolver.getClass().getName()`.
   - Add the edge to the path-segment group's view via `group.addEdgeToView(edge)` so the group's `view.edgeSet()` reflects the just-emitted REALISED edge.
   - Record `GroupOutcome.sat(group)`.

`PathSegmentResolver` implementations SHALL NOT be invoked outside `ExpandGroupsPhase`'s path-segment-group expansion path. `SeedGraph` SHALL NOT invoke them (see `seed-graph` capability).

The graph traversal direction stays target-driven: `ExpandGroupsPhase` starts at the group's untyped root and asks "what produces this?" The resolver answers by inspecting the typed slot's type and the appended segment name (resolver-internal type inference is forward; engine-level traversal is backward). Per `feedback_never_forward_expansion.md`, this distinction is preserved.

#### Scenario: Two-segment source path resolves at expansion time
- **WHEN** a path-segment group `(root=src[person.lastName]:?, slot=src[person]:Person)` is drained from the work-list
- **AND** `GetterPathResolver.resolve(Person, "lastName", ctx)` returns `Optional.of(ResolvedSegment(String, codegen, Weights.STEP))`
- **THEN** the root's type is set to `Optional.of(String)` in place
- **AND** a REALISED edge `src[person] → src[person.lastName]:String` is emitted with the resolver's codegen
- **AND** the group's outcome is SAT

#### Scenario: Three-segment path resolves in topological layers
- **WHEN** two path-segment groups exist: `g_addr = (root=src[person.address]:?, slot=src[person]:Person)` and `g_street = (root=src[person.address.street]:?, slot=src[person.address]:?)`
- **AND** the work-list processes `g_addr` first (topological order)
- **THEN** `g_addr` SATs first, typing `src[person.address]` to `Address`
- **AND** when `g_street` is processed, its slot is now typed, so `GetterPathResolver.resolve(Address, "street", ctx)` is invoked
- **AND** `g_street` SATs, typing `src[person.address.street]` to `String`

#### Scenario: No resolver matches; the path-segment group is UNSAT_NO_PLAN
- **WHEN** a path-segment group `(root=src[person.weirdSegment]:?, slot=src[person]:Person)` is drained
- **AND** no registered resolver matches `(Person, "weirdSegment", ctx)`
- **THEN** the group records `unsatNoPlan(group, slot)`
- **AND** the root remains untyped
- **AND** subsequent directive-binding groups depending on this root remain UNSAT

#### Scenario: Two directives sharing a prefix share the typed Node by instance identity
- **WHEN** two directives `@Map(source = "person.address.street")` and `@Map(source = "person.address.city")` are processed
- **THEN** `SeedGraph` registers one shared `src[person.address]:?` Node instance (via its prefix-sharing dedup)
- **AND** exactly one path-segment group is registered for the `Address`-segment (slot `src[person]`)
- **AND** when that group SATs, the shared Node's type is set in place — both downstream directives see the typed node

#### Scenario: Single-segment source path needs no resolver invocation
- **WHEN** a directive `@Map(target = "x", source = "person")` is processed and `person` is a parameter
- **THEN** no path-segment group is registered (the SEED edge is `src[person] → tgt[x]`, which is a directive-binding group, not a path-segment group)
- **AND** no resolver is invoked

## REMOVED Requirements

### Requirement: Seed-time path-walking algorithm

**Reason:** Path resolution moves from a `SeedGraph` pre-pass to `ExpandGroupsPhase` (see "Expansion-time path-segment-group resolution" under ADDED). The motivation is structural: under per-sub-group expansion, path-segment groups are processed in topological order over the boundary DAG and resolvers fire naturally as each group becomes ready. The seed-time pre-pass was originally introduced to bake types into the seed graph as a workaround for global candidate scans; once expansion is sub-group-scoped, the workaround is unnecessary and the duplication (seed-time path-walking + expansion-time bridge walks for everything else) collapses to one mechanism.

**Migration:** `PathSegmentResolver` implementations are unchanged. The injection target moves from `SeedGraph` (constructor parameter list) to `ExpandGroupsPhase` (or its helper). `ProcessorModule.pathSegmentResolvers()` provider is unchanged. The seed-time walk loop is deleted; the equivalent logic moves to `ExpandGroupsPhase.fillGroup(...)` keyed on the structural recognition of path-segment groups (per `graph-expansion`).

Existing tests that asserted "typed node `src[person.X]:Y` exists after `SeedGraph.apply(...)`" need to be re-anchored to assert against `MapperGraph` state after `ExpandStage.run(ctx)` instead.

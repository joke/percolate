## ADDED Requirements

### Requirement: Single round-robin strategy invocation per pass

Each expansion pass SHALL try the one `List<ExpansionStrategy>` as a single round at every open frontier — there SHALL be no per-kind ordering (no "match strategies, then assembly strategies, then path resolvers"). The driver SHALL build a `Frontier` for the frontier node and invoke `expand(frontier, ctx)` on each strategy in list order (sorted by `priority()` then FQN), collecting the emitted `ExpansionStep`s.

The return-assembly (a multi-slot `BOUNDARY` step) SHALL be resolved by data dependency within the cross-group fixed-point loop, not by a dedicated earlier phase. Target→source direction bounds the search, dead-end sub-groups are pruned, and the node budget bounds transient growth, so convergence holds without an explicit assembly-first phase.

#### Scenario: strategies are tried as one unordered round
- **WHEN** the driver expands a frontier
- **THEN** every strategy in `List<ExpansionStrategy>` is offered the frontier in one round
- **AND** no strategy kind is gated to run before another

#### Scenario: assembly resolves by data dependency
- **WHEN** a return value requires a multi-slot assembly whose slots are produced in later passes
- **THEN** the assembly `BOUNDARY` step is committed and its slots resolve in subsequent passes via the fixed-point loop
- **AND** the result is identical regardless of which pass first emitted the assembly step

### Requirement: Intent-driven fold versus subgroup at the single mutation site

The driver SHALL branch on `ExpansionStep.intent` alone to decide graph shape:

- For `intent == CONVERSION`: the driver SHALL add the synthesized input node and the realised edge into the **current** group's view (the same `AsSubgraph` the frontier belongs to). It SHALL NOT open a new `ExpansionGroup`. The new input node becomes a frontier resolved within that same view.
- For `intent == BOUNDARY`: the driver SHALL open a new `ExpansionGroup` rooted at the frontier whose `slots` are the step's `inputs` (`0..N`), with the realised slot→frontier edges as its initial edges. Container boundaries carry the step's `ElementScope` onto the realised edge.

All mutation SHALL continue to flow through the `Applier` as atomic `DeltaBundle`s.

#### Scenario: CONVERSION folds into the current group
- **WHEN** a strategy emits an `ExpansionStep` with `intent == CONVERSION` at frontier `F` in group `G`
- **THEN** the synthesized input node and the realised input→`F` edge are added to `G`'s view
- **AND** no new `ExpansionGroup` is created for the step

#### Scenario: BOUNDARY opens a subgroup with the step's slots
- **WHEN** a strategy emits an `ExpansionStep` with `intent == BOUNDARY` and `N` inputs at frontier `F`
- **THEN** a new `ExpansionGroup` rooted at `F` with those `N` slots is created
- **AND** its initial view contains `F`, the slot nodes, and the realised slot→`F` edges

### Requirement: Conversion folding makes round-trips structural cycles

Because consecutive `CONVERSION` steps share one group view, a conversion that re-derives a type already present at the frontier's location SHALL reuse the existing same-location, same-type node rather than minting a fresh one. A no-progress round-trip (e.g. box∘unbox) therefore closes a cycle in the REALISED projection and SHALL be rejected by the existing acyclicity check in "DeltaBundle atomicity". No type-recurrence or no-progress guard SHALL exist; the cycle check is sufficient.

#### Scenario: box-then-unbox round-trip is rejected as a cycle
- **WHEN** a `CONVERSION` chain at one location would re-derive a type already present at that location (a box∘unbox round-trip)
- **THEN** the second step reuses the existing same-location node
- **AND** the resulting realised edge closes a cycle that the applier's acyclicity check rejects
- **AND** no separate type-recurrence guard is consulted

### Requirement: Directive propagation onto synthesized conversion nodes

The driver SHALL thread the in-effect `@Map` directive onto nodes it synthesizes as the input of a `CONVERSION` step, so a downstream strategy reading `frontier.directive()` sees the originating binding's configuration (source path/segment, patterns, default values). Nodes synthesized as the slots of a `BOUNDARY` step SHALL NOT inherit the parent's directive (a boundary crosses to a new value). Strategies SHALL NOT search for the directive; the driver supplies it via `Frontier`.

#### Scenario: a synthesized conversion node inherits the originating directive
- **WHEN** a frontier carrying directive `D` is resolved by a `CONVERSION` step that synthesizes input node `X`
- **THEN** the `Frontier` later built for `X` returns `D` from `directive()`

#### Scenario: boundary slots do not inherit the parent directive
- **WHEN** a frontier carrying directive `D` is resolved by a `BOUNDARY` step that synthesizes slot nodes
- **THEN** the `Frontier` built for a slot node does not return `D` from `directive()` solely by inheritance

## MODIFIED Requirements

### Requirement: Candidate search scoped to current group's view

The driver SHALL source candidate input nodes from the current group's view (`snapshot.viewOf(group).vertexSet()`), excluding the frontier itself and excluding any node whose `Location` is a `TargetLocation`, and expose them to strategies only as the flat `frontier.candidates()` snapshot. No global scan over `snapshot.allNodes()` or any equivalent SHALL occur during candidate search, and no strategy SHALL receive the view or the graph itself.

The view-scoped search is the structural fix for sibling-leak cross-group cycles and multi-parameter directive ambiguity. Combined with `Node` instance-identity (see `graph-model`) and narrow boundary import (only `SourceLocation` nodes inherit from the parent group's view), it prevents *sibling-derived nodes* from being picked up as candidates. Cycle-closing matches (e.g. an inverse container step reusing the current group's root) can still be emitted; they are rejected at applier-time via the cycle check in "DeltaBundle atomicity".

#### Scenario: Candidates come only from the current group's view
- **WHEN** the driver builds the `Frontier` for a frontier node in `group`
- **THEN** `frontier.candidates()` contains only nodes from `snapshot.viewOf(group).vertexSet()`
- **AND** excludes the frontier
- **AND** excludes any node whose `Location` is a `TargetLocation`
- **AND** no candidate is sourced from `snapshot.allNodes()` or `MapperGraph.nodes()` directly

#### Scenario: Sibling sub-group nodes are invisible
- **WHEN** the underlying `MapperGraph` contains a `Node` `X` that is a member of some sibling sub-group `S` but not of `snapshot.viewOf(currentGroup).vertexSet()`
- **AND** the driver builds the `Frontier` for a node in `currentGroup`
- **THEN** `X` is not among `frontier.candidates()`

## REMOVED Requirements

### Requirement: Bridge edge-emission rule
**Reason**: Replaced by the intent-driven emission rule. The `PRESERVING`/`ENTERING`/`EXITING` scope-transition branching is gone; a step now folds (`CONVERSION`) or opens a subgroup (`BOUNDARY`), with element scope carried by `ExpansionStep.scope`.
**Migration**: Emit `ExpansionStep`s; the driver applies "Intent-driven fold versus subgroup at the single mutation site" and "Conversion folding makes round-trips structural cycles".

### Requirement: Every bridge match spawns a one-slot nested ExpansionGroup
**Reason**: Only `BOUNDARY` steps spawn groups now; `CONVERSION` steps fold into the current group. The "every match is its own sub-group" rule no longer holds.
**Migration**: A unary boundary (getter, call, container) is a 1-slot `BOUNDARY` group; a scalar conversion folds in place.

### Requirement: GroupTarget matches register nested groups via tryGroupTargets
**Reason**: Assembly is no longer a separate fallback after bridge matching; it is an ordinary `BOUNDARY` `ExpansionStep` with `N` slots emitted from `expand(...)` in the single round.
**Migration**: Emit a multi-slot `BOUNDARY` step from an `ExpansionStrategy`; the driver opens the multi-slot group via "Intent-driven fold versus subgroup".

### Requirement: ResolveTargetChainsPhase scaffolds target chains and ExpansionGroups
**Reason**: The dedicated assembly-first phase is removed; assembly resolves by data dependency in the fixed-point loop (see "Single round-robin strategy invocation per pass").
**Migration**: Remove the phase ordering; rely on the fixed-point loop to resolve the return assembly when its inputs become available.

### Requirement: Path-segment-group resolution via PathSegmentResolver
**Reason**: Path-segment resolution is now an `ExpansionStrategy` emitting a `BOUNDARY` step; the `segment` is read from `frontier.directive()` rather than a parameter, and there is no separate resolver-driven phase.
**Migration**: Implement the segment resolver as an `ExpansionStrategy` and emit a `BOUNDARY` step for the segment access.

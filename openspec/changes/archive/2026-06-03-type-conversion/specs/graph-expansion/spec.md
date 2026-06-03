## MODIFIED Requirements

### Requirement: Intent-driven fold versus subgroup at the single mutation site

The driver SHALL branch on `ExpansionStep.intent` alone to decide graph shape:

- For `intent == CONVERSION`: the driver SHALL obtain the input node by **same-type-within-view dedup** — if a node whose type is the same (`Types.isSameType`) as the step's single input type already exists in the **current** group's view (excluding the frontier itself and `TargetLocation` nodes), it SHALL reuse that node; otherwise it SHALL **synthesize** a fresh type-keyed node for the input type. It SHALL add the input node (when synthesized) and the realised input→frontier edge into the current group's view (the same `AsSubgraph` the frontier belongs to). It SHALL NOT open a new `ExpansionGroup`. A synthesized input node SHALL be an **expandable frontier** — offered to strategies via `matchAt` in later passes so its own producers are discovered — but SHALL NOT be added to the group's AND-required slot set (so an unreachable synthesized node is a retained dead end, not a blocker).
- For `intent == BOUNDARY`: the driver SHALL open a new `ExpansionGroup` rooted at the frontier whose `slots` are the step's `inputs` (`0..N`), with the realised slot→frontier edges as its initial edges. Container boundaries carry the step's `ElementScope` onto the realised edge.

All mutation SHALL continue to flow through the `Applier` as atomic `DeltaBundle`s.

#### Scenario: CONVERSION reuses an existing in-view node of the input type
- **WHEN** a strategy emits a `CONVERSION` step at frontier `F` in group `G` and a node of the step's input type already exists in `G`'s view
- **THEN** the realised input→`F` edge is folded from that existing node
- **AND** no new node is synthesized and no new `ExpansionGroup` is created

#### Scenario: CONVERSION synthesizes the input node when none of the type exists
- **WHEN** a strategy emits a `CONVERSION` step at frontier `F` in group `G` and no node of the step's input type exists in `G`'s view
- **THEN** a fresh type-keyed input node is synthesized and added to `G`'s view with the realised input→`F` edge
- **AND** the synthesized node is an expandable frontier offered to strategies in a later pass
- **AND** the synthesized node is NOT added to `G`'s AND-required slot set
- **AND** no new `ExpansionGroup` is created for the step

#### Scenario: BOUNDARY opens a subgroup with the step's slots
- **WHEN** a strategy emits an `ExpansionStep` with `intent == BOUNDARY` and `N` inputs at frontier `F`
- **THEN** a new `ExpansionGroup` rooted at `F` with those `N` slots is created
- **AND** its initial view contains `F`, the slot nodes, and the realised slot→`F` edges

### Requirement: Conversion folding makes round-trips structural cycles

Because consecutive `CONVERSION` steps share one group view, the input-node dedup is keyed on **type within the group's view** (not location): a conversion whose input type already has a node in the current view SHALL reuse that node rather than minting a fresh one. A no-progress round-trip (e.g. box∘unbox, which re-derives a type already present) therefore folds its edge onto the existing same-type node and closes a cycle in the REALISED projection, which SHALL be rejected by the existing acyclicity check in "DeltaBundle atomicity". No type-recurrence or no-progress guard SHALL exist; the cycle check is sufficient.

#### Scenario: box-then-unbox round-trip is rejected as a cycle
- **WHEN** a `CONVERSION` chain would re-derive a type already present in the group's view (a box∘unbox round-trip)
- **THEN** the second step reuses the existing same-type node
- **AND** the resulting realised edge closes a cycle that the applier's acyclicity check rejects
- **AND** no separate type-recurrence guard is consulted

## ADDED Requirements

### Requirement: Conversion-chain satisfaction is base-case reachability

A node satisfied via realised **conversion** edges SHALL satisfy iff at least one incoming conversion edge's **source node is itself satisfied** (a base case, a node with a SAT child sub-group, or another conversion node satisfied by this same rule — transitively to a base case). Mere presence of an incoming conversion edge SHALL NOT satisfy a node; the edge's source must be satisfied. A group SATs iff every one of its **fixed slots** is satisfied (by base case, SAT child sub-group, or conversion-chain reachability); synthesized conversion nodes that never become reachable are retained dead ends and SHALL NOT block group SAT.

This generalizes per-slot satisfaction from "has one incoming realised edge" to base-case reachability through realised conversion edges, so a chain `X→Y→Z` SATs only once a complete realised path from a base case exists.

#### Scenario: a conversion chain SATs only when complete
- **WHEN** a slot `Z` is fed by a synthesized conversion node `Y` which is fed by a base-case source `X` (`X→Y→Z`)
- **THEN** `Z` is satisfied only after `Y` is satisfied, which holds only because `X` is a base case
- **AND** before `Y` is reachable, `Z` is NOT satisfied despite having the incoming `Y→Z` edge

#### Scenario: an unreachable conversion node does not block group SAT
- **WHEN** a target-driven conversion synthesizes an alternative input node that never acquires a producing source (a dead end)
- **THEN** that node remains in the graph unsatisfied
- **AND** the group still SATs once its fixed slots are reachable via some other realised conversion path

### Requirement: Conversion expansion is type-keyed, bounded, and stops at SAT

`CONVERSION` input-node dedup SHALL key on type within the current group's view (at most one node per type per view); `BOUNDARY` slot nodes SHALL remain keyed on logical identity (slot role). Because the lossless primitive conversion lattice is finite, type-keyed synthesis SHALL produce a bounded per-group type-DAG. The existing stop-at-SAT behaviour SHALL be unchanged — once a group is SAT it is not expanded in later passes — which is sound for conversions because expansion is breadth-by-hops and conversion weights are uniform (`Weights.STEP`): the shortest (cheapest) realised path is already present when the group SATs, and deeper paths are strictly more expensive. Path selection among the retained alternatives SHALL remain the plan-view's cheapest-realised-path responsibility, not the expander's.

#### Scenario: distinct logical boundary slots of the same type are not merged
- **WHEN** a `BOUNDARY` step opens a subgroup with two slots whose declared types are equal but whose logical roles differ
- **THEN** two distinct slot nodes are created (logical-identity keying), not one

#### Scenario: a SAT conversion group is not expanded further
- **WHEN** a group reaches SAT via a complete realised conversion path in a pass
- **THEN** the group is excluded from subsequent passes
- **AND** the shorter/cheaper competing paths discovered up to that pass remain in the graph for the plan-view to select among

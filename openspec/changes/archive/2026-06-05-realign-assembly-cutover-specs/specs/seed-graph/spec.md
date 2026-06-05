## MODIFIED Requirements

### Requirement: SeedGraph registers one ExpansionGroup per SEED edge

For every **source-side** SEED edge `e: from → to` emitted by `SeedGraph` — path-segment edges and directive-bridging edges — `SeedGraph` SHALL register one one-slot `ExpansionGroup` with `root = to`, `slots = [from]`, `strategyClassFqn = "io.github.joke.percolate.processor.stages.seed.SeedGraph"`, `codegen = a placeholder GroupCodegen` (replaced during expansion as resolvers/strategies fire). `initialEdges` SHALL be empty — the SEED edge itself is NOT a member of the group's view; the group represents an unresolved producer relationship pending expansion.

The **target side is consolidated, not per-edge.** Target-chain SEED edges (`tgt[child] → tgt[parent]`) do NOT each register a group. Instead, for every parent target node that has child target leaves, `SeedGraph.registerAssemblyGroups` SHALL register exactly **one umbrella assembly `ExpansionGroup`** with `root = the parent target node`, `slots = all of that parent's child target leaves`, the same placeholder codegen and `strategyClassFqn`. This umbrella is produced during expansion by an `AssemblyStrategy` (e.g. `ConstructorCall`) bound to those child leaves.

The set of groups registered by `SeedGraph` decomposes into three structural kinds:

- **Path-segment groups**: both `root.loc` and `slot.loc` are `SourceLocation`s; `root.loc.path` is `slot.loc.path` extended by exactly one segment. One per source path-segment edge. These are expanded by the `SourceDescentExpander` during `ExpandGroupsPhase`.
- **Directive-binding groups**: `root.loc` is a `TargetLocation` and `slot.loc` is a `SourceLocation` (the deepest source leaf). One per directive-bridging edge. These are expanded by the `DirectiveBindingExpander` during `ExpandGroupsPhase` to fill the transformation chain between the source leaf and the target leaf.
- **Assembly (umbrella) groups**: `root.loc` is a `TargetLocation` with one or more child target leaves; `slots` are **all** of that node's child target leaves. One per parent target node, registered by `registerAssemblyGroups`. These are expanded by the `AssemblyExpander`, which runs the strategy round at the root so an `AssemblyStrategy` (`ConstructorCall`) can over-emit a multi-slot BOUNDARY binding the child leaves.

`SeedGraph` SHALL NOT pre-classify groups by these kinds (the classification is derived from node `Location`s and slot shape at expansion time via `GroupShapes`). The structural shape of each group is sufficient for `ExpandGroupsPhase` to dispatch to the correct `GroupExpander`.

The group kind is determined by node `Location`s and slot shape, not by a `groupKind` field on `ExpansionGroup`.

#### Scenario: Source-side SEED edges each get a one-slot group; the target side gets one umbrella per parent
- **WHEN** `SeedGraph.apply(...)` emits `s` source-side SEED edges (path-segment + directive-bridging) and target chains under `p` distinct parent target nodes
- **THEN** `MapperGraph.groups()` contains exactly `s` one-slot groups (one per source-side edge, `root = e.to`, `slots = [e.from]`)
- **AND** it contains exactly `p` umbrella assembly groups (one per parent target node, `root = parent`, `slots = all child target leaves of that parent`)
- **AND** no group is registered per individual target-chain edge

#### Scenario: Path-segment edge produces a path-segment group
- **WHEN** `SeedGraph.apply(...)` emits a SEED edge `src[person] → src[person.addresses]:?`
- **THEN** the corresponding `ExpansionGroup` has `root = src[person.addresses]:?`, `slots = [src[person]:Person]`
- **AND** the group's structural shape (root.loc and slot.loc both `SourceLocation`, root path is slot path + one segment) identifies it as a path-segment group for `SourceDescentExpander` dispatch

#### Scenario: Directive-bridging edge produces a directive-binding group
- **WHEN** `SeedGraph.apply(...)` emits a directive-bridging SEED edge `src[person.addresses]:? → tgt[addresses]:?`
- **THEN** the corresponding `ExpansionGroup` has `root = tgt[addresses]:?`, `slots = [src[person.addresses]:?]`
- **AND** the structural shape (root.loc `TargetLocation`, slot.loc `SourceLocation`) identifies it as a directive-binding group for `DirectiveBindingExpander` dispatch

#### Scenario: A parent target node produces one umbrella assembly group over all its children
- **WHEN** `SeedGraph.apply(...)` seeds two directives `@Map(target = "address.street", …)` and `@Map(target = "address.zip", …)`, so `tgt[address]` has child leaves `tgt[address.street]` and `tgt[address.zip]`
- **THEN** exactly one umbrella assembly `ExpansionGroup` is registered with `root = tgt[address]` and `slots = [tgt[address.street], tgt[address.zip]]`
- **AND** no separate group is registered for the `tgt[address.street] → tgt[address]` or `tgt[address.zip] → tgt[address]` target-chain edges
- **AND** the group's shape (root a `TargetLocation` with child-leaf slots) identifies it as an assembly group for `AssemblyExpander` dispatch

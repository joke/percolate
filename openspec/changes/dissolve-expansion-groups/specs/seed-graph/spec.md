## MODIFIED Requirements

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

## ADDED Requirements

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

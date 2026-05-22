## Why

The current built-in container bridges (`SetMap`, `ListMap`, `OptionalMap`) fuse three logically distinct operations — iterate a container, map each element, collect into a target container — into one `BridgeStep` carrying an `ElementSeed` declaration. The driver then materialises a four-edge diamond per bridge match (outer container-map edge + iteration edge + inner per-element chain + collect edge), with the outer edge serving as a load-bearing connectivity scaffold so the parent group can SAT without synchronising on the nested element-seed group's processing. The result is asymmetric with the Optional pair (`OptionalUnwrap`/`OptionalWrap`, each one hop), forces a special-case carve-out in `[[project-group-sat-rule]]` (`initialEdges = Set.of()`), and produces a structurally redundant outer edge whose codegen has no real implementation. Mirroring the Optional pair at every container arity eliminates the diamond, the synchronisation gap, the `ElementSeed` SPI, the carve-out, and the redundant edge in one stroke; every REALISED edge becomes one real operation and the realisation chain reads as a linear recipe.

## What Changes

- **BREAKING (internal built-in only)**: delete `SetMap`, `ListMap`, `OptionalMap` and their `@AutoService(Bridge.class)` registrations. The fused container-map step pattern is retired.
- **BREAKING (SPI)**: remove `ElementSeed` from `io.github.joke.percolate.spi`. Remove `BridgeStep.elementSeeds` and the corresponding constructor parameter. `BridgeStep` becomes a pure single-hop record (`inputType`, `outputType`, `weight`, `codegen`).
- Add four new one-hop bridges in `strategies-builtin`:
  - `IterableUnwrap`: produces `T` (in element scope) from any `Iterable<T>` / array. Mirrors `OptionalUnwrap`.
  - `SetCollect`: produces `Set<T>` from `T` (in element scope). Mirrors `OptionalWrap`.
  - `ListCollect`: produces `List<T>` from `T` (in element scope).
  - `ArrayCollect`: produces `T[]` from `T` (in element scope).
- Drop `registerElementSeedGroup`, the iteration/collect edge emission, and the element-seed-group special case from `ExpandGroupsPhase`. The driver's existing per-group greedy expansion handles linear chains directly. Each scope-changing (`ENTERING`/`EXITING`) bridge match registers its own one-slot nested `ExpansionGroup` in place of the fused diamond — preserving the subgraph-as-unit-of-work model but at one hop per group. The "lone exception" carve-out in `[[project-group-sat-rule]]` becomes obsolete — every group satisfies the standard SAT rule by construction.
- `tryBridges` commits every matching bridge at each frontier (not first-match-only). Parallel chains co-exist in the graph; dead branches lie as unresolved nested groups; slot reachability picks the alive chain. This unblocks chains like `tgt[addresses]:Optional<Set<HA>>` where both `OptionalCollect` and `OptionalWrap` are valid producers but only one of the two onward chains reaches a source-parameter-root.
- Element scope continues to be declared by node `ElementLocation`. The scope-entering bridge (`IterableUnwrap`) emits its `BridgeStep` with an output node at `ElementLocation`; the scope-exiting bridges (`*Collect`) consume an input node at `ElementLocation`. The driver continues to allocate nodes at the location the bridge declares. No new "scope tracking" SPI is needed.
- Update integration acceptance: `~/Projects/joke/percolate-integration/mappers` compiles green; `PersonMapper.transforms.dot` shows a linear chain `src[person] → src[person.addresses]:List<Opt<PA>> → elem:Opt<PA> → elem:PA → elem:HA → src[person.addresses]:Set<HA> → tgt[addresses]:Optional<Set<HA>> → tgt[]:Human` with no diamond, no incoming-only `elem(...)` leaves, no edges labelled `SetMap` / `ListMap` / `OptionalMap`, and no edges attributed to any deleted strategy FQN.

## Capabilities

### New Capabilities

None. Each of the four new bridges is added to the existing `builtin-strategy-unit-tests` and `expansion-strategy-spi` capability surfaces.

### Modified Capabilities

- `expansion-strategy-spi`: removes `ElementSeed` from the SPI; removes `BridgeStep.elementSeeds` from the `BridgeStep` value type's listed fields; removes the `Bridge`-must-declare-element-seeds requirement; removes the "iteration edge" and "collect edge" requirements that `ExpandGroupsPhase` emits in response to element seeds.
- `graph-expansion`: removes the "Element-seed iteration edge" and "Element-seed collect edge" requirements (introduced by `bind-seed-chain-realisation`); removes `registerElementSeedGroup` from the listed `ExpandGroupsPhase` responsibilities; removes the per-group-SAT carve-out for element-seed nested groups (no such groups exist after this change).
- `builtin-strategy-unit-tests`: drops `SetMap`, `ListMap`, `OptionalMap` from the required-specs list; adds `IterableUnwrap`, `SetCollect`, `ListCollect`, `ArrayCollect`. Net count is unchanged at four container-related specs.
- `container-expansion`: replaces the "container-map" framing (one bridge declaring an `ElementSeed`) with the "iterate-map-collect" framing (three orthogonal bridges chained linearly). Updates the worked example for `Set<Human.Address>` from `List<Optional<Person.Address>>`.
- `graph-debug-output`: drops the `SetMap`/`ListMap`/`OptionalMap` strategy-FQN-to-label scenarios; adds the four new bridges' labels. Removes the "outer container-map edge + iteration edge + collect edge" diamond rendering scenarios — the rendering now follows the standard linear-chain rules.

## Impact

- **Affected code**:
  - `spi/src/main/java/io/github/joke/percolate/spi/ElementSeed.java` deleted.
  - `spi/src/main/java/io/github/joke/percolate/spi/BridgeStep.java` updated to drop `elementSeeds` field and the corresponding constructor.
  - `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/{SetMap,ListMap,OptionalMap}.java` deleted.
  - `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/{IterableUnwrap,SetCollect,ListCollect,ArrayCollect}.java` added.
  - `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/{SetMapSpec,ListMapSpec,OptionalMapSpec}.groovy` deleted; four new specs added.
  - `processor/src/main/java/io/github/joke/percolate/processor/stages/expand/ExpandGroupsPhase.java`: drop `registerElementSeedGroup`, the `step.getElementSeeds()` loop in `commitBridgeStep`, the second `outerFrontier` parameter threaded by `bind-seed-chain-realisation`, and the post-commit collect-edge emission. `commitBridgeStep` reduces to "add outer REALISED edge per step; recurse via the work list."
  - `processor/src/test/groovy/.../expand/ExpandGroupsPhaseSpec.groovy`: drop the `'container-map commit emits iteration and collect REALISED edges around the element seed'` scenario. The element seed test fixture (`ElementSeedBridge`) is deleted.
- **Affected behaviour**:
  - `*.transforms.dot` and `*.full.dot` no longer contain diamonds. Every chain is linear. `elem(role)` nodes appear as intermediate nodes with one incoming and one outgoing REALISED edge each.
  - No edge in any generated dot bears `strategyClassFqn` equal to `SetMap`, `ListMap`, or `OptionalMap`.
  - Strategy authors writing their own `Bridge` lose the `ElementSeed` mechanism; the supported way to introduce element-scope per-arity transformations is to author an Unwrap bridge (produces element-scope output) and a Collect bridge (consumes element-scope input). For 1:1 element conversions (today's `OptionalMap` use case), the existing `OptionalUnwrap` + element-converting bridge + `OptionalWrap` chain already covers it without a dedicated `OptionalMap`.
- **Affected teams**: processor engine maintainers; built-in strategy maintainers. Downstream users authoring their own container bridges via `ElementSeed` lose that mechanism; migration is to split into an Unwrap/Collect pair, which is a one-time mechanical refactor per bridge.
- **Risk**: medium. The change touches the realisation engine's structural model and the SPI surface. Mitigation: the `bind-seed-chain-realisation` collect edge work landed a working diamond first, so the integration acceptance is well-understood; the new chain shape can be verified against the same `PersonMapper` mapper. Reverting is a single revert of this change (no schema migration, no persisted state).
- **Dependencies**: requires `bind-seed-chain-realisation` to be archived first (this change supersedes its "Element-seed iteration edge" and "Element-seed collect edge" requirements). Coordinates with `source-path-resolvers` (typed source chains stay; no change to seed-time path resolution).

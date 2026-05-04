## Why

Phase 1 (`add-seed-graph-and-debug-dump`) shipped the seed graph as a passive
data structure. Phase 1.5 (`align-graph-for-expansion`) shipped the schema for
realised edges, markers, sub-seeds, group coordination, and codegen closures —
but no edge of those kinds exists yet. Without an expansion phase the seed
graph cannot drive validation or codegen, and the user's `@Map` directives
stay unrealised. This change closes the loop: it consumes the seed graph,
populates `REALISED` + `MARKER` edges via strategies, emits Tier-2 / Tier-3
diagnostics when realisation fails, and establishes the strategy SPI that
future expansion work (setters, builders, containers, conversions,
cross-mapper composition, codegen) extends without touching the platform.

## What Changes

- **NEW** `ExpandStage` containing three sequential phases —
  `ResolveSourceChainsPhase` (runs `SourceStep` strategies),
  `ResolveTargetChainsPhase` (runs `GroupTarget` strategies),
  `BridgeSourceToTargetPhase` (runs `Bridge` strategies). Each phase is
  a pure transformation of `MapperGraph` and is testable in isolation.
- **NEW** `ValidateRealisationStage` containing two sequential phases —
  `ValidateMarkersPhase` (Tier-2: every SEED node must have ≥ 1 outgoing
  MARKER edge) and `ValidatePathsPhase` (Tier-3: bidirectional gap walk
  over the realised subgraph; failures report both shoulders and the
  missing type pair). Tier-2 scars the failing mapper so Tier-3 skips it.
- **NEW** strategy SPI in package `io.github.joke.percolate.processor.spi`:
  - Three small interfaces — `SourceStep`, `GroupTarget`, `Bridge` —
    matching three of the four seed-edge flavors. `TargetStep` is
    deferred (no `SetterWrite` in v1).
  - Result types `Step`, `BridgeStep`, `Slot`, `GroupBuild` — immutable
    data carrying type, weight, and a codegen lambda. Strategies see no
    graph internals (no `Edge`, no `Node`, no markers, no sub-directives,
    no `groupId`, no `MapperGraph`).
  - `ResolveCtx` exposes `Types` and `Elements` only.
  - Strategies declare `default int priority()` for future codegen-time
    tiebreaking.
  - Uniform registration via `ServiceLoader<…>` + `@AutoService` for
    both built-ins and user-supplied strategies. Loaded once per round,
    sorted lexicographically by FQN.
- **NEW** built-in strategies, all `@AutoService`-registered:
  - `GetterRead` (`SourceStep`, weight `Weights.STEP`).
  - `ConstructorCall` (`GroupTarget`, exact match against the
    directive-target set sharing a return root, weight `Weights.STEP`
    per arg).
  - `DirectAssign` (`Bridge`, weight `Weights.NOOP`).
- **NEW** driver guards: JGraphT `CycleDetector` over the
  SEED + SUB_SEED subgraph runs after each phase; per-seed expansion
  budget of 100 (hardcoded in v1; configurable later via
  `processor-options`).
- **NEW** `DumpExpandedGraph` stage writes `<MapperFQN>.expanded.dot`
  whenever `-Apercolate.debug.graphs=true`, including on Tier-2 / Tier-3
  failure. The full graph (SEED + REALISED + MARKER + SUB_SEED) renders
  so failing expansion is debuggable post-mortem.
- **BREAKING** internal: `Pipeline` refactors from a hard-wired
  straight-line into `List<Stage>` Dagger injection. The stage list grows
  by three (`ExpandStage`, `ValidateRealisationStage`,
  `DumpExpandedGraph`). No external-API impact — the processor module is
  internal.
- **BREAKING** internal: `MARKER` edge emission and `SUB_SEED` edge
  emission are now driver responsibilities. Strategies never produce
  them. (No production code path was producing markers yet.)
- **NEW** dependency: Google `auto-service` (annotation + processor)
  added to the processor module's compile and annotation-processor
  configurations.

**Out of scope, deferred to follow-up changes:**
- `TargetStep` SPI interface and `SetterWrite` / `BuilderWrite`
  strategies. v1 limitation: dotted target paths
  (e.g., `@Map(target = "address.street", …)`) fail Tier-2 with a
  clear diagnostic.
- `OptionalWrap`, container `extract`/`collect` (`ContainerKind` SPI),
  conversion strategies (`String ↔ LocalDateTime`, boxing pairs, …).
- `MethodCallStrategy` for routable mapper methods and cross-mapper
  composition.
- Sub-directive emission by built-in strategies. The driver supports the
  flow forward-compat (cycle/budget guards are in place); no v1 strategy
  emits them.
- Codegen. Dijkstra path selection over `realisedSubgraph()` and
  JavaPoet emission land as Phase 3.

## Capabilities

### New Capabilities

- `graph-expansion`: the `ExpandStage` and its three phases. Owns flavor
  classification, driver-side normalisation of same-side `?→?` edges,
  the bridge-readiness predicate, marker emission, parallel-edge
  emission for multi-match results, sub-directive emission, cycle
  detection, and the per-seed expansion budget.
- `expansion-strategy-spi`: the strategy-author surface — three
  interfaces, four result types, `ResolveCtx`, the
  `ServiceLoader` + `@AutoService` registration mechanism, and the
  three v1 built-in strategies (`GetterRead`, `ConstructorCall`,
  `DirectAssign`).
- `realisation-validation`: the `ValidateRealisationStage` covering
  Tier-2 (markers walk; scars on failure) and Tier-3 (bidirectional gap
  walk producing diagnostics that show both shoulders and the missing
  type pair).

### Modified Capabilities

- `processor`: pipeline restructures from straight-line to `List<Stage>`
  Dagger injection; the stage list grows by three new stages.
- `graph-debug-output`: the DOT renderer now emits styled REALISED,
  MARKER, and SUB_SEED edges (the alignment change shipped the styling
  table; this change generates edges of those kinds). A new
  `<MapperFQN>.expanded.dot` file is produced whenever the debug option
  is on.

## Impact

**Affected code** (processor module only): new packages
`processor.spi`, `processor.expand`, `processor.validate`; refactor of
`Pipeline`; extension of `DotRenderer`; new `DumpExpandedGraph` stage.
Tests grow with per-phase Spock specs, per-strategy unit specs, and
end-to-end Compile Testing integration covering trivial bean-mapping
realisation. Estimated ~2.5 KLoC including tests.

**Affected APIs:** the new `processor.spi` package becomes a stable
surface for future strategy authors. Internal packages remain internal.

**Dependencies:** adds Google `auto-service` annotation + annotation
processor. JGraphT `CycleDetector` already declared and used.

**Affected teams:** processor maintainers — the only consumers of the
processor's internal packages — own this change end-to-end. Future
strategy authors (downstream consumers) gain a stable
`processor.spi` extension point.

**v1 limitation (documented):** dotted target paths fail Tier-2 with a
clear diagnostic. They land when `TargetStep` and `SetterWrite` arrive
in a follow-up change. Single-segment target paths plus exact-match
constructors cover the v1 demo (the value-object / Lombok
`@AllArgsConstructor` shape).

**Migration:** no rollback concern beyond reverting the change set.
Generated output is unaffected — codegen is still future. The debug-DOT
goldens grow with new `expanded.dot` files; existing `seed.dot` goldens
are unchanged.

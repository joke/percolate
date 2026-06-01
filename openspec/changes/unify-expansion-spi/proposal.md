## Why

The expansion engine has three strategy SPIs (`Bridge`, `GroupTarget`, `PathSegmentResolver`), each with a different method signature, loaded into three separate lists and driven by phase-ordered, kind-specific code. The split is organised by *discovery mode* (combinatorial `from→to`, target-directed tails, segment name) — but those parameters are all derivable from one local context (the frontier's type, its `@Map` directive, and the in-scope candidates). The split that actually matters for graph construction — *does a step cross a flow-identity boundary (open a subgroup) or just convert a value in place (fold)?* — is **not expressed anywhere**. Because it is missing, conversion steps each spawn a subgroup, which hides the box∘unbox round-trip from the `CycleDetector` and forces unsound type-recurrence work-arounds; and per-binding configuration that developers must put on `@Map` (date/parse patterns, default values) cannot reach the strategies that need it.

## What Changes

- **BREAKING (SPI):** Collapse the three strategy interfaces into **one** `ExpansionStrategy` with a single method `Stream<ExpansionStep> expand(Frontier, ResolveCtx)`. All strategies register under one `@AutoService(ExpansionStrategy.class)` / `ServiceLoader` list and are tried together each round — no `MatchStrategy`-then-`Assembly` ordering.
- **BREAKING (SPI):** Collapse the result types (`BridgeStep`, `GroupBuild`, `ResolvedSegment`) into one `ExpansionStep`: `0..N` input slots + output + codegen + an **intent** (`CONVERSION` = fold in place, `BOUNDARY` = open a subgroup) + optional element-scope for containers. A subgroup *is* a boundary step's slots; a 0-slot boundary is a terminal producer (e.g. a default value); a 1-slot conversion folds.
- Introduce a **myopic** `Frontier` decision context: `targetType()`, `directive()` (the in-effect `@Map` info — source path/segment, patterns, default values), and `candidates()` (a flat, non-traversable snapshot of in-scope source values). A strategy **never** sees the graph.
- The engine branches on the **intent bit only**: `CONVERSION` adds node+edge into the current subgroup so a coercion chain shares one view; `BOUNDARY` opens a subgroup rooted at the frontier. This makes a future box∘unbox round-trip a structural cycle the existing `CycleDetector` rejects — so **no type-recurrence guard is ever needed**.
- **Driver-side directive propagation:** the scaffolding threads the originating `@Map` directive onto synthesized conversion nodes, so downstream strategies read patterns / defaults / segments from local context. (Realised edges currently drop the directive.)
- Remove the explicit `ResolveTargetChainsPhase`-before-groups phase ordering; the fixed-point loop resolves assembly by data dependency (target→source already bounds the search; dead ends are pruned; the node budget bounds transients).
- Provide optional **mixin interfaces** (default `expand()` methods) for the common patterns — combinatorial `from→to` matching and container scope-crossing — so authors of simple conversions or containers write no candidate loop, without reintroducing kind-ordering.

This change delivers the *mechanism* and migrates the existing built-ins (`ConstructorCall`, `DirectAssign`, `MethodCallBridge`, the containers, the `*PathResolver`s). The scalar coercion bridges (boxing/widening) and `String` ↔ scalar conversions are reintroduced afterwards on this clean base by the regenerated `type-conversion` change.

## Capabilities

### New Capabilities
<!-- None: this change restructures existing strategy/expansion capabilities rather than introducing a new behavioural area. -->

### Modified Capabilities
- `expansion-strategy-spi`: replace the three interfaces (`Bridge`, `GroupTarget`, `PathSegmentResolver`) and three result types with one `ExpansionStrategy` interface, one `ExpansionStep` result type carrying the `CONVERSION`/`BOUNDARY` intent and `0..N` slots, the myopic `Frontier` context, and the mixin helper interfaces; single-list ServiceLoader registration.
- `source-path-resolution`: `PathSegmentResolver` is absorbed into `ExpansionStrategy`; the `segment` parameter is dropped in favour of reading the segment from `Frontier.directive()` + position. Seed-time path typing semantics are preserved, expressed via boundary steps.
- `graph-expansion`: single round-robin strategy invocation per fixed-point pass (no phase/kind ordering); intent-driven fold-vs-subgroup at the single mutation site; driver-side `@Map` directive propagation onto synthesized nodes; removal of the `ResolveTargetChainsPhase` ordering guarantee.
- `container-expansion`: container iterate/collect/wrap/unwrap steps are expressed as `BOUNDARY` `ExpansionStep`s carrying element-scope (`ENTERING`/`EXITING`), produced via the container mixin rather than a distinct `Bridge` base.

## Impact

- **`percolate-spi`**: breaking interface/result-type changes (`ExpansionStrategy`, `ExpansionStep`, `Frontier`, `Intent`, mixins); removal of `Bridge`, `GroupTarget`, `PathSegmentResolver`, `BridgeStep`, `GroupBuild`, `ResolvedSegment` (or their reframing).
- **`percolate-strategies-builtin`**: every built-in (`ConstructorCall`, `DirectAssign`, `MethodCallBridge`, `List/Set/Array/Optional` containers, `Getter/Field/Method` path resolvers) migrates to the unified SPI; `@AutoService` target changes to `ExpansionStrategy`.
- **`percolate-processor`** (`stages/expand`, `stages/seed`): `FrontierMatcher`, `InputAllocator`, `Applier`, the expansion phases, and `ProcessorModule` strategy wiring are rewritten around one list + intent + directive propagation; `Edge` carries the propagated directive.
- **Tests**: expansion specs, builtin-strategy specs, and the test harness fixtures adapt to the unified SPI.
- **Affected maintainers**: single maintainer (no cross-team coordination).

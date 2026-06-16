## Why

Expansion is meant to be a single uniform demand work-list, but it isn't: source-path descent
(`ExpandStage.descend`/`resolveAccessor`) is a **second** engine — eager, forward (param→leaf), with
its own strategy dispatch and its own memo — and `SeedStage` pre-creates parameter/return roots,
mutating the graph via `graph.valueFor(...)` directly, the lone carve-out to the "Applier is the sole
mutation site" invariant. The existing `graph-expansion` / `expansion-strategy-spi` specs already
*require* demand-driven descent ("an accessor strategy pulls its parent … no eager whole-path
materialization"), so today's code violates its own intent. Folding descent into the work-list makes
the seed stage's job evaporate: once a source path is just target→source demands bottoming at a
parameter, the graph can start empty and grow entirely by demand. The same pass removes a dead,
dangerous SPI coupling (`ResolveCtx.currentMethod()`/`mapperType()` + the backing `ThreadLocal`).

## What Changes

- **Fold source-path descent into the demand work-list.** A source path `a.b.c` becomes
  target→source demands: `SourceLocation[a,b,c] ← accessor ← SourceLocation[a,b] ← accessor ←
  SourceLocation[a]` (single segment = the parameter). Delete the eager `descend`, `resolveAccessor`,
  `paramRoot`, and the `descended` memo; the work-list's own visited-set and strategy dispatch drive
  it. **BREAKING** internally (no public API).
- **Promote `Location.Role` into a resolution mode the single work-list dispatches on**, set when a
  demand is created: `FREE` (target / conversion intermediate → full strategy set), `ACCESS`
  (multi-segment source → accessor strategies only; peel last segment, re-demand the prefix; assembly
  must not fire), `LEAF` (single-segment source = parameter, and element root → base case, no
  expansion), `CONSTANT`. Mode is derivable from the existing `Location` kind + segment count.
- **Tighten the cost base case to `LEAF` only.** A producerless `LEAF` is `Cost.ZERO`; *any* other
  producerless value — including a multi-segment source demand whose accessor never matched — is
  `Cost.INFINITE`. (Today's "any supply value is a base case" would make a failed accessor chain
  vacuously reachable; it is masked only because eager descent always produced it.)
- **Delete `SeedStage`.** Expansion self-seeds one return-type demand per abstract method at entry;
  the graph starts empty; parameter `LEAF`s materialise lazily on first reference. The goal-spec
  derivation moves to the discovery phase (it is a pure reshaping of discovered `@Map` directives).
- **Restore the Applier as the sole mutation site.** Every vertex — including the initial root
  demands — is born through a delta via the `Applier`; no stage calls `valueFor` directly.
- **Remove `ResolveCtx.currentMethod()` and `mapperType()`** (dead in production; only
  `callableMethods()` is read) and **eliminate the `ThreadLocal<MapperContext>`** by constructing
  `ResolveCtx` per-mapper. **BREAKING** SPI surface change.

Out of scope (explicit follow-ups, not bundled): port-carries-intent / variable-vs-application
unification; unifying method vs child-scope seeding; nested `@Map` into container elements.

## Capabilities

### New Capabilities
- _(none — this change refactors existing capabilities)_

### Modified Capabilities
- `seed-graph`: **removed in full** — the seed stage is deleted; the graph starts empty and is grown
  entirely by demand.
- `graph-expansion`: the single work-list dispatches on a demand's resolution mode; expansion
  self-seeds root demands from an empty graph; all mutation (incl. root demands) flows through the
  Applier.
- `source-path-resolution`: descent is the ordinary work-list over `ACCESS` demands; the
  single-segment parameter base case is a lazily-materialised `LEAF`, not a pre-seeded root.
- `graph-model`: `Location` exposes a resolution mode (`FREE`/`ACCESS`/`LEAF`/`CONSTANT`) driving the
  work-list and the base-case rule; `Scope`s are no longer "produced by the seed stage".
- `plan-extraction`: the cost base case is a `LEAF` value only; every other producerless value is
  unreachable (`Cost.INFINITE`).
- `expansion-strategy-spi`: `ResolveCtx` exposes only `types()`, `elements()`, `callableMethods()`;
  `mapperType()`/`currentMethod()` are removed and the backing `ThreadLocal` is gone.
- `mapping-discovery`: the per-level declared-bindings goal spec is derived during discovery from the
  discovered `@Map` directives.
- `graph-debug-output`: the pre-expansion `seed` dump is removed (with no seed stage the graph starts
  empty, so there is no pre-expansion snapshot); the remaining `full`/`transforms`/`plan` dumps all run
  after expansion.

## Impact

- **Code:** `ExpandStage`, `DemandView`, `Applier`, `MapperGraph`, `Location` (+ `SourceLocation` /
  `TargetLocation` / `ElementLocation`), `ExtractedPlan`, `GoalSpec`, `ProcessorModule` (drop
  `seedStage` from the pipeline + the `ThreadLocal` + `CompileResolveCtx`), `Pipeline`, `ResolveCtx`,
  `MethodCallBridge`. **Deleted:** `SeedStage`.
- **Tests/harness:** `HarnessResolveCtx`, `ResolveCtxBuilder` (+ spec), inline `ResolveCtx` fakes in
  `ContainersSpec` / `ContainerSpec` drop the removed accessors; the existing Spock/jqwik suite is the
  behavioural oracle (byte-identical codegen not required, but the suite must stay green).
- **Invariants preserved:** strategies stay myopic (Demand only, never the graph); WHEN/WHERE in the
  driver, HOW in declarations; expansion never walks forward (the deleted `descend` was the last
  forward mechanism); no silent sourcing (supply stays directive-rooted).
- **Pre-existing debt (not fixed here):** several specs still describe pre-VOG types
  (`Frontier`/`ExpansionStep`/`Slot`/`Intent`/`EdgeCodegen`); this change touches only the
  requirements it changes and leaves a broader "sync specs to VOG reality" pass for later.

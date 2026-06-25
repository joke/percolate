## Why

The expansion engine asserts a single strategy-invocation site, yet source-path descent quietly runs a
second one. `AccessorResolver` dispatches strategies inside a forward "typing" shadow-walk to *predict*
each parent's type, then `expandAccess` emits the accessors **backward** (child segment before parent)
and selects among matching accessors with `findFirst()` over the **unsorted `ServiceLoader` list** —
silently ignoring the `getter < method < field` cost ordering the engine relies on everywhere else.

That forward typing walk exists *only because* emission is backward: when you build the child accessor
before its parent, the parent's `Value` does not exist yet, so its type cannot be read and must be
predicted — and prediction means a second strategy dispatch. The `graph-expansion` spec already forbids
this ("exactly one strategy-invocation site… no `descend`/`resolveAccessor` component") and
`source-path-resolution` already claims the typing helper performs "no strategy dispatch." The code
diverges from both. This change makes the code honor that intent.

## What Changes

- **Forward, target-bound descent replaces backward emission + forward typing.** A directive's named
  source path is walked root→leaf: each segment lands a typed accessor `Value`, and the next segment
  reads its parent's type off the `Value` just landed. Types fall out of landing, so the forward typing
  shadow-walk is deleted.
- **The second strategy-invocation site collapses into the driver's single call site.** Accessors are
  over-emitted and cost-pruned like every other strategy output. `findFirst` accessor selection is
  removed; an ambiguous segment (e.g. both `getX()` and a public field `x`) now resolves by **weight**
  (getter wins) instead of by ServiceLoader order — a determinism fix that changes generated output for
  such segments (behavior change).
- **The strategy demand contract splits into produce vs descend.** A *produce* demand carries a known
  output type and the strategy declares its inputs; a *descend* demand carries a known parent type plus
  a segment and the strategy discovers the output type. Accessors answer the descend shape instead of
  punning `Demand.targetType()` as the parent.
- **Driver walks, strategy decides.** The driver owns descent orchestration (segment sequencing, graph
  mutation, holding the directive's remaining path); the per-segment accessor stays a myopic, graph-blind,
  pluggable strategy. Walking is not, and cannot be, a strategy.
- **`AccessorResolver` is dissolved entirely** — `typing()` becomes a land-time lookup off the parent
  `Value`; `resolveAccessor()` becomes the driver's descend dispatch. `SourceCandidates` is untouched
  (it owns graph binding/materialisation and can never be a strategy).
- **Descent roots reuse the existing `Scope.inputDecls` abstraction unchanged.** A root is always a scope
  input, concretely typed by the time the scope exists — from a method signature, or from grounding-at-land
  for a child (element) scope. No scope-kind branch is added; `never_forward` is preserved because a
  *named, target-given* path is not a speculative source sweep.

## Capabilities

### New Capabilities
<!-- none: this change reshapes existing expansion behavior, it introduces no new capability -->

### Modified Capabilities
- `graph-expansion`: the `ACCESS` demand changes from backward parent re-demand to **forward target-bound
  descent**; the "single strategy-invocation site / no `resolveAccessor` component" requirement becomes
  literally true; the driver gains an explicit produce-vs-descend dispatch; the `never_forward` requirement
  is clarified to permit walking a directive-given named path root→leaf.
- `source-path-resolution`: descent is forward and types fall out of landing (the forward, non-mutating
  typing helper that secretly dispatched strategies is removed); a segment with multiple matching accessors
  resolves by cost (over-emit + prune), never by `findFirst`/registration order.
- `expansion-strategy-spi`: the `Demand` decision context distinguishes a produce demand from a descend
  demand; an accessor strategy reads a parent type + segment from a descend demand rather than a
  parent-punned `targetType()`.

## Impact

- **percolate-processor**: `ExpandStage` (forward descend dispatch + the walk loop, descent-obligation
  work item), `AccessorResolver` **removed**, `SourceCandidates` unchanged.
- **percolate-spi**: the `Demand` surface (produce | descend); the `Accessor` archetype base adapts to the
  descend demand.
- **percolate-strategies-builtin**: `GetterPathResolver` / `MethodPathResolver` / `FieldPathResolver`
  adapt to the descend demand (matching/weight logic unchanged).
- **Tests**: `source-path-resolution` and `graph-expansion` harness; deep (3+ segment) paths and
  getter-vs-field segment ambiguity — both currently uncovered — gain explicit coverage.
- **Teams**: expansion-engine / codegen maintainers. No annotation-surface or user-API change.

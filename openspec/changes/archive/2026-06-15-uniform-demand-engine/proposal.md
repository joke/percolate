## Why

The bipartite cutover made the graph model uniform, but the expansion **driver** is not: it still
hard-codes three supply modes (`assembly` / `bridge` / container-child) behind an `if/else`, hand-
builds productions the SPI is supposed to own (`crossNonNull` emits a `requireNonNull` Operation
inside the driver; the default-value `coalesce` is the same shape), runs an **eager, forward** source
descent (`SourceDescent.materialize` walks parameter-root → deepest accessor — the one push-shaped
component in a target-to-source engine), and threads a hand-curated candidate list through each
demand. Each of these is engine code that knows a *how*, contradicting the project's own rule that
strategies own the how and the driver owns only when/where. The result is that adding a new crossing,
a new accessor shape, or a new container still means editing the engine.

The container SPI carries a parallel non-uniformity: `SequenceContainer` vs `WrapperContainer` (and
`ContainerCodegen` vs `WrapperCodegen`) are two bases whose only real difference is "does it
`collect`?" — a distinction the stream-pipeline model already made structural.

Collapsing both makes the engine a pure work-list and pushes **all** how-knowledge into strategies,
so new productions (crossings, accessors, conversions, containers) become "add a strategy," never
"edit the engine." The project is in evaluation with no releases — the structural fix is cheapest now.

## What Changes

- **The driver becomes a pure demand work-list.** One loop: a `Value` demand → `run(all strategies)`
  → land each `OperationSpec` as an atomic `AddOperation` → enqueue each port as a new demand. The
  `assembly` / `bridge` branch is removed (gating already prevents misfires: assembly strategies gate
  on the declared-children goal spec, conversions on a candidate type match), and `bindPort`'s
  match-or-synthesize matchmaking is removed (a port is just a demand).
- **Nullness crossings become strategies.** `requireNonNull` (partial) and `coalesce` (total, when the
  demand's directive declares a default) are emitted by ordinary conversion strategies that fire on a
  `(nullable candidate, non-null demand)` pair, reading the binding/slot name from the demand context
  for the message. `crossNonNull` and the engine-side coalesce emission are deleted.
- **Source accessors become demand-driven (pull).** A directive-bound demand carrying
  `source = "in.address.street"` is satisfied by demanding the source `Value` at
  `SourceLocation(in.address.street)`, which an accessor strategy produces from `in.address`, and so
  on down to the parameter root — *the location pins the source, so the directive's choice is
  preserved without curated candidates*. The eager `SourceDescent` component is deleted; accessors are
  ordinary Operations produced on demand.
- **Candidates are uniform.** A demand's candidate set is the in-scope source `Value`s (parameter
  roots and already-materialized accessors), not a per-demand hand-curated list; directive selection
  is carried by the demanded `SourceLocation`, not by candidate filtering.
- **One `Container` SPI (Thread C).** `SequenceContainer` / `WrapperContainer` collapse into a single
  `Container` base declaring up to four optional kind-local operations (`iterate`, `collect`, `wrap`,
  `unwrap`) plus `matches` / `element`; `ContainerCodegen` / `WrapperCodegen` collapse into one handle
  family. Container **kind is emergent** — a sequence supplies `collect`, a presence wrapper leaves it
  empty. The generic `StreamMap` (`map`/`flatMap`) is unchanged.
- **BREAKING (internal SPI):** the container base/handle split is removed (one `Container` base);
  `Demand` candidate semantics change (in-scope source values, not a curated list); new built-in
  crossing/accessor strategies replace engine-emitted productions. No public `@Map` surface change.
- **Not changing:** the bipartite graph model, scopes, deltas, the single `Applier`, the
  goal-spec/declared-bindings gate, `OperationSpec`/`ExpansionStrategy` core shapes, plan extraction.
  The behavioural contract stays **compiles + semantically equivalent** generated output (the
  integration suite arbitrates) — the pull engine may factor a pipeline differently but maps the same.

## Capabilities

### New Capabilities

<!-- none — this restructures existing expansion/SPI behaviour; no new capability is introduced -->

### Modified Capabilities

Only capabilities whose **requirements** change are listed. On review, several specs already describe
the target model aspirationally (their *implementation* took shortcuts); those are implementation-only
alignment — see Impact — and need no delta: `source-path-resolution` already states descent is
demand-driven; `nullability`'s crossing requirement already describes the explicit Operation (silent
on emitter); `expansion-test-harness` already exposes the bipartite/demand surface.

- `graph-expansion`: ADD that the driver is a single uniform work-list that builds no Operations
  itself (no assembly/bridge branch, no `bindPort` matchmaking) — every Operation, including nullness
  crossings and accessors, comes from a strategy; a port is just a demand.
- `expansion-strategy-spi`: `Demand` candidate semantics become "in-scope source values" (not a
  curated per-demand list) and the demand exposes the binding/slot name; ADD that nullness crossings
  and source accessors are ordinary `ExpansionStrategy`s on the existing loader.
- `default-values`: the `coalesce` crossing is emitted by a **strategy** reading the demand's directive
  default (was "the engine SHALL emit"); selection vs `requireNonNull` stays the totality rule.
- `container-codegen-spi` (Thread C): `SequenceContainer`/`WrapperContainer` collapse into one
  `Container` base whose kind is emergent (collect ⇒ sequence); `ContainerCodegen`/`WrapperCodegen`
  collapse into one optional-operation handle family.
- `container-expansion` (Thread C): a container supplies `collect` iff it is a sequence (kind emergent
  from the operation's presence), not via a separate base type; the stream-pipeline composition is
  unchanged.

## Impact

- **`processor` module** — rewrite `stages/expand/ExpandStage.Driver` to a single demand loop; delete
  `stages/expand/SourceDescent` and the driver's `crossNonNull`/`bindPort`; route accessor and
  crossing productions through strategies; `Applier`, deltas, and `MapperGraph` unchanged.
- **`spi` module** — collapse the container bases/handles (`SequenceContainer`, `WrapperContainer`,
  `ContainerCodegen`, `WrapperCodegen`) into `Container` + one handle family; adjust `Demand`
  (candidate semantics + binding name). `OperationSpec`, `ExpansionStrategy`, `Port`, `ChildScopeSpec`
  unchanged.
- **`strategies-builtin` module** — new built-in strategies for the nullness crossings and a
  demand-driven accessor surface; rework the four container classes onto the single `Container` base.
- **Tests** — strategy unit specs for the new crossing/accessor strategies; container specs onto the
  one base; expansion/harness specs to the uniform surface; integration suite stays green with
  semantically-equivalent generated output.
- **Sequencing** — depends on `unify-sat-into-cost-semiring` landing first (simpler reachability/cost
  fold to build on); this change does not itself touch SAT/cost.
- **Affected teams** — single-maintainer project; `processor`, `spi`, `strategies-builtin`.
- **Dependencies** — none added; JGraphT substrate unchanged.

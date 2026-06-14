## Context

The bipartite model is uniform, but `ExpandStage.Driver` is not. `expand(demand)` branches on
`goalSpec.declaredChildren(path).isEmpty()` into `expandAssembly` vs `expandBridge`; `expandBridge`
eagerly runs a forward `SourceDescent.materialize` (parameter root → deepest accessor) before querying
strategies, hand-builds the `requireNonNull` crossing in `crossNonNull`, threads a curated
`List<Value>` of candidates through each `DemandItem`, and does match-or-synthesize matchmaking in
`bindPort`. Each is engine code that owns a *how*, against the project rule that strategies own the how.
In parallel, the container SPI carries a `SequenceContainer`/`WrapperContainer` (and
`ContainerCodegen`/`WrapperCodegen`) split whose only real difference is whether the container
`collect`s.

Crucially, the **specs already describe the clean target**: `graph-expansion`'s "Demand work-list over
Values" is already the uniform pull model; `source-path-resolution` already says descent is
demand-driven; `nullability` already states the crossing is an explicit Operation. So this change is
mostly bringing the *implementation* in line with existing requirements, plus two genuine requirement
deltas (candidate semantics; one Container base) and the architectural rule that crossings/accessors
are strategies. Sequenced after `unify-sat-into-cost-semiring` (already landed), so it builds on the
single cost-fold engine.

## Goals / Non-Goals

**Goals:**
- `ExpandStage.Driver` becomes a pure work-list: `demand → run(all strategies) → land Operations →
  enqueue ports`. No assembly/bridge branch, no `bindPort`, no `crossNonNull`, no `SourceDescent`.
- Every plan Operation comes from an `ExpansionStrategy` — crossings (`requireNonNull`/`coalesce`) and
  source accessors included.
- `Demand.candidates()` are the in-scope source Values; directive source selection is carried by the
  demanded `SourceLocation`, not curated lists.
- One `Container` SPI base; kind emergent from which operations are supplied (Thread C).
- Generated output stays **compiles + semantically equivalent** (the integration suite arbitrates).

**Non-Goals:**
- The graph model, scopes, deltas, `Applier`, plan extraction / cost fold — untouched.
- The goal-spec/declared-bindings gate semantics — unchanged (only its invocation site moves into the
  uniform round).
- Byte-identical output (the pull engine may factor a pipeline differently — equivalence is the bar).

## Decisions

### D1 — The driver is a pure work-list; gating replaces the branch

`expand(demand)` runs one `run(allStrategies, demand)` round. Assembly and conversion strategies
coexist in one loader list; misfires are prevented at emission time, not by routing: assembly
strategies gate on `Demand.declaredChildren()` (a constructor is a candidate iff its parameter-name set
equals the declared set), conversions on a candidate type match. Each accepted match lands as an
atomic `AddOperation`; each port is enqueued as a demand. *Alternative rejected:* keeping the
assembly/bridge `if/else` — it is redundant once gating is structural and adds a second place to reason
about supply.

### D2 — Nullness crossings are strategies

`[requireNonNull]` (partial) and `[coalesce]` (total, when the demand's directive declares a default)
become a `CombinatorialMatch`-style strategy firing on a `(nullable candidate, non-null demand)` pair.
The message's slot name comes from the demand context (a new `Demand` accessor — the binding/slot
name). `crossNonNull` and the engine-side coalesce emission are deleted. Totality dominance in
extraction already prefers `[coalesce]` over `[requireNonNull]`, so no driver either/or remains.
*Alternative rejected:* leaving crossings engine-resident — it is the clearest violation of "strategies
own the how" and blocks adding new crossing kinds without engine edits.

### D3 — Source accessors are demand-driven (pull); `SourceDescent` is deleted

A directive-bound demand carrying `source = "p.address.street"` is satisfied by demanding the source
`Value` at `SourceLocation("p.address.street")`; an accessor strategy produces it from
`SourceLocation("p.address")`, recursing to the parameter root (a base case). **The location pins the
source**, so the directive's specific selection is preserved without curated candidates — this is the
key to dropping `SourceDescent` without silently binding the wrong same-typed source. *Alternative
rejected:* eager forward materialization — it is the one push-shaped step in a target-to-source engine
and forces the curated-candidate threading.

### D4 — Candidates are the in-scope source Values

`Demand.candidates()` is `graph.valuesIn(scope)` filtered to source-derived Values (parameter roots +
already-materialized accessors), not a per-demand list. Conversion strategies match a candidate by
type; directive-bound assignment is driven by the demanded `SourceLocation` (D3), so "any String in
scope" can never shadow the directive's chosen source. The driver no longer assembles a `DemandItem`
candidate list.

### D5 — One `Container` base; kind is emergent (Thread C)

`SequenceContainer`/`WrapperContainer` collapse into a single `Container` base supplying the optional
kind-local operations it has: `iterate`, `collect`, `wrap`, `unwrap`, and the same-kind scope-owning
`mapPresence`. `ContainerCodegen`/`WrapperCodegen` collapse into one handle family. Kind is emergent —
`collect` present ⇒ sequence; absent (with `wrap`/`unwrap`/`mapPresence`) ⇒ presence wrapper. The
generic element `map`/`flatMap` stays the `StreamMap` strategy's `ScopeCodegen`, not a container method.
*Alternative rejected:* a capability-flag enum on a kept split — the optional-operation set already *is*
the capability flag, so two base types are redundant.

## Risks / Trade-offs

- **[Output regression from the restructure]** → the integration suite (compiles + semantically
  equivalent) is the gate; an in-repo end-to-end case (`mapHuman`) and the strategy unit specs pin
  behaviour; diff a representative generated mapper before/after.
- **[Pull source descent — wrong-source binding]** → mitigated by D3: the demanded `SourceLocation`
  pins the source; a focused spec asserts two same-typed sources don't cross-bind.
- **[Worklist churn from per-segment accessor demands]** → bounded by `valueFor` dedup and visited-once;
  the path is finite. More demands than eager descent, but each is cheap and idempotent.
- **[Thread C breadth]** → C touches the SPI bases + all four built-ins; it is mechanically separable
  from A and can be staged second within this change if A lands first.

## Migration Plan

1. `Demand`: add the binding/slot-name accessor; change `candidates()` semantics to in-scope source
   values; update the driver to populate it from `graph.valuesIn(scope)`.
2. Add crossing strategies (`requireNonNull`/`coalesce`) and demand-driven accessor strategies; delete
   `crossNonNull` and `SourceDescent`.
3. Collapse `ExpandStage.Driver` to the uniform work-list (remove assembly/bridge branch, `bindPort`).
4. Thread C: introduce `Container` + unified handle; port the four built-ins; delete the two bases.
5. `./gradlew check` green; integration suite + `mapHuman` end-to-end green; diff a generated mapper.

Annotation-processor only; rollback is reverting. No public `@Map` or generated-code contract change.

## Open Questions

- Whether A and C ship as one commit or two (C is mechanically independent). Default: one change, two
  commits, A first.
- Whether the accessor-strategy surface fully subsumes the current path resolvers or wraps them — to be
  settled during 2.x; the existing `GetterPathResolver`/`MethodPathResolver`/`FieldPathResolver`
  matching logic carries over either way.

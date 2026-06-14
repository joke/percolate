## Context

Satisfaction, cost, and totality are one bottom-up fold over the same AND/OR (bipartite) graph,
computed in three places today:

- `HornSat.propagate` — a boolean reachability fixpoint, recomputed from scratch at the end of
  `ExpandStage` (`HornSat.java:25`, called from `ExpandStage.java:92`), memoized as a vertex set on
  `MapperGraph` (`markSat`/`isSat`/`clearSat`/`satVertices`, `MapperGraph.java:38,116`).
- `ExtractedPlan.costOf` — `min` over producers / `weight + Σ ports` (`ExtractedPlan.java:83,94`).
- `ExtractedPlan.partialOf` — the transitive partial-operation count (`ExtractedPlan.java:111,124`).

`ExtractedPlan.cheapestProducer` already selects with one lexicographic comparison
(`ExtractedPlan.java:78`):

```java
.min(Comparator.comparingInt(this::partialOf)      // totality  (most significant)
        .thenComparingDouble(this::costOf)          // weight
        .thenComparing(Operation::id));             // deterministic tie-break
```

and already encodes `unreachable ⟺ +∞` via `UNREACHABLE`/`UNREACHABLE_PARTIAL`. So `HornSat` is a
redundant third pass: the selection comparator *is* the reachability predicate. The supply-root base
case is also duplicated — `HornSat.isSupplyRoot` (`:62`) and `ExtractedPlan.isSupplyRoot` (`:141`) are
identical `instanceof` checks, mirrored again as the `instanceof TargetLocation` gate in the driver
(`ExpandStage.java:130`). Expansion itself does not consult SAT (it over-emits and drains a
visited-once work-list), so SAT exists only for three post-expansion consumers: `ExtractedPlan`,
`RealisationDiagnosticsStage`, and the full debug dump.

## Goals / Non-Goals

**Goals:**
- One bottom-up minimum-cost-hyperpath fold computes reachability, cost, totality, and the chosen
  producer together; `HornSat` and the `MapperGraph` SAT store are deleted.
- Selection is expressed over an explicit, comparable `Cost` so that a future preference is a new
  component, not a new pass (the extensibility this change exists for).
- The supply-root / demand / element / constant distinction lives in one place: `Location.role()`.
- **No change to which producer wins**, hence no change to generated mapper output (the integration
  suite is the contract).

**Non-Goals:**
- The pure-pull / uniform-demand driver restructure (`uniform-demand-engine`) — separate change.
- Caching one `ExtractedPlan` across the validate and generate stages — a possible follow-up
  optimisation; this change keeps the simplest "each consumer builds its own" shape.
- Any new selection preference itself — this change only makes the *extension point* first-class.

## Decisions

### D1 — One minimum-cost-hyperpath fold over an explicit `Cost`

Model selection as Knuth's minimum-cost hyperpath over a totally-ordered cost domain. Introduce a
comparable `Cost` value type that is either `Cost.INFINITE` (unreachable) or a finite vector
`(partials : int, weight : double)`, ordered lexicographically with `partials` most significant
(totality dominance). The semiring shape:

- `⊕` (at a `Value`, OR): `min` over producer costs.
- `⊗` (at an `Operation`, AND): if any operand is `INFINITE` the result is `INFINITE`; otherwise
  componentwise combine — `partials` add (the Operation's own `+1` when `partial`), `weight`s add
  (the Operation's own `weight` plus the sum over ports and the child return-root).
- `Cost.ZERO = (0, 0.0)` at supply roots and zero-port Operations; producerless non-supply Values are
  `INFINITE`.

The chosen producer of a Value is `argmin` over its producers' `Cost`, with `Operation.id()` as the
**selection-local** deterministic tie-break (it is not propagated — it breaks ties only among the
direct producers of one Value, exactly as today). `cheapestProducer` returns empty when the minimum is
`INFINITE`. This is one memoized DFS (the structure already in `ExtractedPlan`), collapsing the three
recursions into a single `cost(Value) : Cost`.

*Why `Cost` as a type, not the existing two memo maps + comparator chain:* the comparator chain hides
the vector; adding a preference edits `min(...)` and adds a memo map. A first-class `Cost.compareTo`
makes the ordered vector the visible, single extension point — a new component is one field and one
`compareTo` slot, and `⊕`/`⊗` and the DFS are untouched. *(Chosen over the merged-comparator option.)*

### D2 — Reachability is a derived query; delete the SAT store

`reachable(v) ⟺ cost(v) < Cost.INFINITE`. `ExtractedPlan` exposes `reachable(Value)` (and `cost(Value)`)
backed by the same fold. `MapperGraph` loses `markSat`/`isSat`/`clearSat`/`satVertices`;
`HornSat` is deleted; `ExpandStage` drops the `HornSat.propagate` call. The three consumers move to the
plan:

- `RealisationDiagnosticsStage` (validate) builds an `ExtractedPlan` and reports a method whose
  return-root is `!reachable` (cost `INFINITE`). It needs cost even when the plan walk is empty — the
  fold populates the memo from the return root regardless of whether any plan was extracted.
- The full debug dump annotates each vertex reachable/unreachable from `cost`.
- The harness exposes reachability/cost instead of a SAT predicate.

*Why inline in `ExtractedPlan` rather than a standalone `CostModel` + post-expand stage:* fewer types
and no new pipeline stage; `ExtractedPlan` is already the fold's home. *(Chosen over the
standalone-stage option; the cost is that validate and generate each build a plan — see Non-Goals /
Open Questions.)*

### D3 — Selection equivalence (output unchanged)

The producer chosen for every Value is unchanged because the comparison key is byte-for-byte the same
ordering `(partials, weight, id)`. The only mechanical change is at the selection site: the current
`producersOf(value).filter(isSat).min(cmp)` becomes `producersOf(value).min(byCost)` with an
`INFINITE ⇒ empty` guard. For a Value whose producers are all unreachable, both forms return empty
(today: no SAT producer; new: min cost is `INFINITE`). For a mix, the finite-cost producer wins in
both (a SAT producer is exactly a finite-cost one). Therefore generated mappers are expected
byte-identical; the integration suite arbitrates and is the acceptance gate.

### D4 — Well-foundedness preserved by the cost cycle guard

`HornSat`'s monotone-fixpoint well-foundedness (a Value never SATs through a cycle containing itself,
`HornSat.java:18`) is preserved by the existing cost cycle guard: `cost(Value)` writes `INFINITE` into
its memo before recursing (`ExtractedPlan.java:88`), so a producer that depends back on the in-progress
Value sees `INFINITE` and is never chosen. A box∘unbox zero-weight cycle between `x:int` and
`x:Integer` with no acyclic derivation therefore costs `INFINITE` and is unreachable — the same verdict
`HornSat` gives. This is the well-foundedness gate the prior change closed (task 9.1); it is retained,
now as the sole mechanism. A dedicated test pins the zero-weight cycle case.

### D5 — `Location.role()` replaces `instanceof` dispatch (Thread D)

`Location` gains `role() : Role` (`SUPPLY` / `DEMAND` / `ELEMENT` / `CONSTANT`). The base-case predicate
in the fold and the demand gate in the driver consult `role()` instead of `instanceof`. This removes
the duplicated `isSupplyRoot` (one copy died with `HornSat`; the other moves to a single
`role() == SUPPLY || role() == ELEMENT` check) and the driver's `instanceof TargetLocation`. Pure
mechanical refactor, no behaviour change.

*Why fold it in here:* deleting `HornSat` already removes one of the two duplicated supply-root checks,
so consolidating the predicate now avoids leaving a lone `instanceof` orphan.

## Risks / Trade-offs

- **[Output regression from the selection rewrite]** → D3 keys on the identical ordering; the full
  integration suite (the established compiles + semantically-equivalent contract) must stay green, and
  a focused `ExtractedPlanSpec` case asserts mixed reachable/unreachable producer selection matches the
  old `filter(isSat).min` result.
- **[Recompute cost]** validate and generate each build an `ExtractedPlan`, recomputing the fold
  (the chosen "inline" shape). The graphs are per-mapper and small; memoisation makes one build linear
  in vertices+edges. If it ever matters, cache the plan on `MapperContext` after validate — explicitly
  a follow-up, not this change.
- **[Conceptual: one overloaded `Cost` vs two ideas]** "reachable" and "cheapest" now read as one thing
  (`cost < ∞`). Mitigated by the `reachable(Value)` query name and `Cost.INFINITE` being explicit, so
  call sites read intent-fully.
- **[`double` weight cost / ties]** unchanged from today; `Cost` keeps the existing `double` weight and
  the `Operation.id()` selection tie-break, so determinism is preserved.

## Migration Plan

1. Add `Cost` + `Location.role()` (additive).
2. Reimplement `ExtractedPlan` over `Cost`; add `reachable`/`cost`; keep `chosenProducer` semantics.
3. Repoint `RealisationDiagnosticsStage`, the dump, and the harness at `reachable`/`cost`.
4. Delete `HornSat` and the `MapperGraph` SAT store; drop the `HornSat.propagate` call in `ExpandStage`.
5. Replace `instanceof` supply-root/target checks with `role()`.
6. `./gradlew check` + integration suite green (byte-diff a representative generated mapper before/after).

No deploy/runtime surface (annotation processor); rollback is reverting the change. No public `@Map`
or generated-code contract change.

## Open Questions

- None blocking. The only deferred decision is whether to cache one `ExtractedPlan` across validate and
  generate (recorded as a Non-Goal / follow-up); the change works without it.

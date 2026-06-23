## Context

The expansion driver (`ExpandStage`) is a single uniform demand work-list over one flat bipartite
`MapperGraph`. Scope is a **coordinate carried on every `Value`** (`MapperScope` → `MethodScope` →
`ChildScope`), not a graph the driver switches into; confinement during search is the
`SourceCandidates.sourceValues` filter `graph.valuesIn(scope)` plus the `addDep` "no `Dep` crosses a
scope boundary" invariant. The `scopeView`/`MaskSubgraph` is currently unused (separate concern).

Structurally a scope is already *"base-case input roots + one `FREE` output-root demand."* The
`graph-expansion` spec already mandates that **method parameters are materialised lazily** ("an unused
parameter is never materialised"), and the cost model already treats every `LEAF` (a method parameter
*or* an element root) as a base case uniformly. So the engine is ~90% scope-agnostic already.

The residual asymmetry is narrow and incidental:

- A **method scope's** input roots (parameters) are *lazy* — derived from the `ExecutableElement` and
  materialised on first reference, behind two `instanceof MethodScope` branches in `SourceCandidates`
  (`paramTypes`, `matchingParam`) and one in `AccessorResolver` (path-root typing).
- A **child scope's** input root (the element) is *eagerly force-minted* by `MapperGraph.mintChildRoots`
  when the owning Operation lands — **only** because the element lambda's bound-variable name is sourced
  from that materialised param-root `Value` (`HoistPlan.lambdaName(paramRoot)`; `ScopeCodegen.weave`'s
  `var`). If the element is unused (mapped to a constant), the `Value` exists purely to name `a` in
  `stream.map(a -> <constant>)`.

So one codegen coupling (lambda name ⇐ materialised `Value`) forces one engine asymmetry (eager child
minting), which forces three `instanceof MethodScope` branches. Remove the coupling and all of it
collapses.

## Goals / Non-Goals

**Goals:**

- The driver is **scope-type-agnostic**: source-binding, grounding, and accessor-root typing consult a
  single uniform per-scope input declaration with no `instanceof MethodScope`.
- Element input roots become **lazy**, exactly like method parameters already are (one materialisation
  policy for all scopes).
- Generated output is **byte-identical** for every realisable mapper.

**Non-Goals:**

- **Lexical capture** (a child seeing its ancestors' bindings). The declaration model makes it a small
  later generalisation, but it is a real capability change with codegen implications — deferred.
- Touching the **dedup key** (scope stays in it — load-bearing; see D5).
- Changing **`SelfCallGuard`** semantics (stays method-scope-only — see D4).
- The **`scopeView`/`MaskSubgraph`** read-surface question.
- Any SPI / `ScopeCodegen` signature change, or any change to `MaskSubgraph`-based views.

## Decisions

### D1 — The unifying principle: output root is an eager demand; input roots are lazy sources

Every scope obeys one shape:

```
  scope := { lazy input roots (LEAF sources) }  +  one eager output root (FREE demand)
```

The **output root drives** the work-list, so it is seeded eagerly — a `MethodScope`'s return root by
`ExpandStage.seedMethod`, a `ChildScope`'s return root when the owning Operation lands
(`enqueue(child.getReturnRoot())`). The **input roots are sources**, consumed only by reuse, so they are
**declared, not minted** — materialised lazily on the first port that matches one. The only reason the
two scope kinds looked different is that `ChildScope` minted its input eagerly; once that stops, they are
identical.

### D2 — `Scope.inputDecls()`: a lazy, scope-relative declaration of base-case inputs

Add `Scope.inputDecls() : Stream<InputDecl>`, where

```
  InputDecl = (Location location, TypeMirror type, Nullability nullness)   // an AddValue minus the scope
```

- `MethodScope.inputDecls()` → one per parameter: `(SourceLocation(paramName), paramType, nullness)`.
- `ChildScope.inputDecls()` → its single element decl: `(ElementLocation(name), elementIn, elementInNullness)`.
- `MapperScope.inputDecls()` → empty (the "empty list" case falls out for free).

`SourceCandidates` and `AccessorResolver` consult `inputDecls()` uniformly:

- `sourceTypes(scope)` = `inputDecls().map(type)` ∪ already-materialised in-scope sources — so
  grounding-by-match sees the element-in **type** from the declaration *without* materialising a `Value`.
- `matchingSource(scope, port)` = an existing in-scope source, else the first `InputDecl` whose
  `(type, nullness)` matches `port`, **materialised on demand** via `applier.apply(new AddValue(scope,
  decl.location, decl.type, decl.nullness))`. Idempotent through the `valueFor` dedup index, so repeat
  materialisation returns the same `Value`.
- `AccessorResolver` resolves a path's root segment against `inputDecls()` instead of the
  `ExecutableElement` parameters.

The declaration lives **on the scope** (the data is already there: `MethodScope` holds the
`ExecutableElement`; `ChildScope` retains its `ChildScopeSpec`/element decl). *Alternative considered:* a
`Scope → inputs` provider held by `SourceCandidates`. Rejected — it re-externalises data that already
lives on the scope and re-introduces a lookup keyed by scope type.

### D3 — Split the binding from the source Value (the load-bearing move)

An input root fuses two facets today; separate them:

```
  binding      — a scope-visible NAME (param name / lambda var). A codegen fact. Always present.
  source Value — a graph vertex ports reuse. Materialised LAZILY, only when reused.
```

The binding is fully described by the `InputDecl` (its `Location.slotName()` + `type`); the unique name
is still allocated at codegen by the `NameAllocator`. So **`HoistPlan.lambdaName` takes the `ChildScope`'s
`InputDecl`, not a materialised param-root `Value`** — an unused element still binds `a -> <constant>`
with `a` named from the declaration. The element source `Value` is then materialised lazily and only if
something in the child plan actually reuses the element.

*Alternative considered:* keep `mintChildRoots` force-minting **only** the child's single input at land
time (everything else lazy). Simpler (no codegen change) but leaves exactly the asymmetry this change
exists to remove, and keeps `ChildScope` minting a `Value` that may be dead. Rejected.

### D4 — `SelfCallGuard` stays method-scope-only; this is the one *intentional* scope branch

A self-call is a **named** method invocation — meaningless for an anonymous element transform — so
`SelfCallGuard`'s `instanceof MethodScope` is **semantic**, not an incidental seam, and stays. The
existing `graph-expansion` requirement *"A method never calls itself on its own whole parameter"* already
(a) refuses only when the bound argument is the scope's **own parameter-root**, and (b) exempts child
scopes, so recursive container mapping — `src.getChildren().stream().map(e -> mapCat(e))` — already works.
This change must **preserve** that: the guard remains the single deliberate scope-type discriminant, and
the existing `Tree`/`mapCat` scenarios are the regression bar. (This retires earlier open question "does
recursion make the guard too narrow?" — the spec already answers no.)

### D5 — Scope stays in the dedup key (out of scope, stated for the record)

`Value` identity is `(scope, location, type, nullness)`. Locations are **scope-relative** —
`ElementLocation("element")` and `TargetLocation([])` recur verbatim in every scope — so scope is the
namespace that keeps sibling element plans (same element type, different operations) from collapsing into
one shared vertex (which would also breach the no-cross-scope-edge invariant). Not touched here.

### D6 — `InputDecl` is internal to the processor

The SPI never sees scopes or graph internals. `InputDecl` lives in `processor.graph` (or `stages.expand`);
`ScopeCodegen.weave(operand, var, body)` keeps its exact signature — only the *source* of `var` moves
from a `Value` to a declaration.

## Risks / Trade-offs

- **[A consumer assumed the element param-root is always present]** → audit everything that reads
  `valuesIn(scope)` or the child param-root: `ValidateConstantDefaultLegalityStage`, debug-dump output,
  `HoistPlan` name reservation. → Mitigation: each iterates what's present, so a lazily-absent unused
  input is a no-op; the green suite plus an explicit audit task guards it.
- **[`.dot` shape changes]** → an unused element param-root no longer appears as an orphan `LEAF` (now
  matching how an unused method parameter already doesn't). → Mitigation: acceptable and more consistent;
  refresh any golden `.dot` snapshots in `graph-debug-output` tests.
- **[Lambda variable names drift]** → names now allocate from the `InputDecl` rather than the materialised
  `Value`. → Mitigation: the base name derives from the same `(slotName, type)`, so allocation is
  identical; the regression suite asserts generated text byte-for-byte.
- **[Idempotency relies on the dedup index]** → lazy materialisation may be requested more than once. →
  Mitigation: `valueFor` is get-or-create; already the contract `Applier` depends on.

## Migration Plan

Additive and revertible:

1. Add `InputDecl` and `Scope.inputDecls()`; implement on `MethodScope` / `ChildScope` / `MapperScope`.
2. Route `SourceCandidates` (`sourceTypes`, `matchingSource`) and `AccessorResolver` through
   `inputDecls()`; delete the `instanceof MethodScope` branches.
3. `HoistPlan.lambdaName` (and the `ScopeCodegen` `var` source) take the `ChildScope` `InputDecl`.
4. Stop force-minting the element param-root in `MapperGraph.mintChildRoots` (keep the eager
   **return**-root); materialise the input lazily like any parameter.

Generated output is identical for realisable mappers, so the existing green suite (containers, reactor,
constants, recursive `Tree`/self-call) is the no-regression guard. Rollback = revert the commit.

## Open Questions

- **Home of `inputDecls()`** — on the `Scope` interface (data locality; leaning this way) vs a
  `SourceCandidates`-side collaborator. Decide in the specs phase.
- **Does anything need the child *return*-root to remain eager beyond being the demand?** Expected no, but
  confirm while wiring D1 (the return root stays eager regardless; this is just a sanity check).
- **Lexical capture (deferred):** once inputs are scope-exposed declarations, a child's `inputDecls()`
  including its ancestors' bindings would let an element lambda reference enclosing method parameters — a
  capability extension with codegen (captured-variable) implications. Recorded as the natural follow-on,
  not in scope.

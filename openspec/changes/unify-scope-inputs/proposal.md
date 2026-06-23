## Why

The expansion driver treats a method's root scope and a container element's child scope as two
different things, even though they are structurally the same "plan": a set of base-case input roots
plus one `FREE` output-root demand. The difference is **incidental, not essential** — method
parameters are materialised lazily and sourced through `instanceof MethodScope` branches
(`SourceCandidates.paramTypes` / `matchingParam`, `AccessorResolver`), while a child scope's element
root is force-minted eagerly (`MapperGraph.mintChildRoots`) **only** because the element lambda's
bound-variable name is fused to that materialised `Value`. Closing this seam makes the engine
genuinely scope-agnostic ("the driver does not care which it is"), deletes three
`instanceof MethodScope` branches, and lays clean groundwork for later recursion work (lexical
capture).

## What Changes

- Introduce a **lazy per-scope input declaration** — `Scope.inputDecls() : Stream<InputDecl>` (or an
  equivalent collaborator), where `InputDecl = (bindingName, type, nullness)`. `MethodScope` derives
  it from the method's parameters; `ChildScope` yields its single element declaration; `MapperScope`
  yields none (the empty-list case falls out for free).
- `SourceCandidates` and `AccessorResolver` consult `Scope.inputDecls()` **uniformly** instead of
  branching on `instanceof MethodScope`. A matched declaration is materialised as a `LEAF` source
  `Value` on demand through the idempotent dedup index (`valueFor` get-or-create) — preserving today's
  "an unused input is never materialised" behaviour and **extending it to element roots**.
- **Split an input root's two fused facets**: the **binding** (a scope-visible name — a method
  parameter name or a lambda variable; a codegen fact that always exists for a child scope) from the
  **source `Value`** (a graph vertex ports reuse; materialised lazily only when reused).
  `HoistPlan.lambdaName` SHALL source the element lambda's bound variable from the `ChildScope`'s input
  declaration, not from a materialised param-root `Value`, so an unused element still binds its lambda
  variable (`a -> <constant>`).
- **Relax `ChildScope` root minting**: the element param-root `Value` is no longer force-minted when
  the owning Operation lands; it is materialised lazily like any other input. The child **return-root**
  (the `FREE` demand the owning Operation folds for cost) is unchanged.
- **Explicitly out of scope (and preserved):**
  - Scope stays in the `Value` dedup key — locations are scope-relative (`elem(element)`, `tgt[]`
    repeat in every scope), so scope is the load-bearing namespace that keeps sibling element plans
    distinct. Not touched.
  - `SelfCallGuard` stays **method-scope-only**: a self-call is a *named* method invocation — a
    semantic rule, not an incidental seam. The existing `graph-expansion` requirement *"A method never
    calls itself on its own whole parameter"* already exempts child scopes and keeps recursive element
    mapping (`mapCat`) working; the unification MUST preserve that the guard remains the one
    intentional scope-type branch.
  - The `scopeView` / `MaskSubgraph` read-surface question is a separate concern; not addressed here.

## Capabilities

### New Capabilities

(none — this is a refactor of existing expansion and codegen behaviour; generated mapper output is
unchanged.)

### Modified Capabilities

- `graph-expansion`: generalise lazy parameter materialisation into a uniform per-scope input
  declaration consumed by source-binding and grounding; element roots become lazily materialised too;
  retire the `instanceof MethodScope` source/grounding branches; affirm `SelfCallGuard` remains
  method-scope-only.
- `graph-model`: the `Scope` interface gains a lazy `inputDecls()` declaration; `ChildScope` no longer
  force-mints its element param-root (the binding is split from the source `Value`). The
  no-`Dep`-crosses-scope invariant and the dedup key are unchanged.
- `source-path-resolution`: `AccessorResolver` resolves an access path's root segment via the uniform
  input declaration rather than an `instanceof MethodScope` parameter lookup.
- `code-generation`: the container element lambda's bound-variable name is sourced from the scope's
  input declaration (the binding), independent of whether a source `Value` was materialised.

## Impact

- **Affected code (`processor`):** `graph/Scope` (+ `MethodScope`, `ChildScope`, `MapperScope`),
  `graph/MapperGraph` (`mintChildRoots`), `stages/expand/SourceCandidates`,
  `stages/expand/AccessorResolver`, `stages/expand/ExpandStage` (scope-owning-Operation landing),
  `stages/generate/HoistPlan` (and where `ScopeCodegen`'s `var` is sourced).
- **Behaviour:** no change to generated mapper output for any realisable mapper — the existing green
  suite (containers, reactor, constants, recursive `Tree` / self-call) is the no-regression guard.
  Graph / `.dot` shape changes only in that an unused element param-root may no longer appear as an
  orphan `LEAF` — matching how an unused method parameter already does not.
- **SPI:** none expected. `ScopeCodegen.weave(operand, var, body)` keeps its signature; only the
  source of `var` moves. `InputDecl` is internal to the processor (the SPI never sees scopes) — confirm
  in design.
- **No** dependency, processor-option, or public-API changes.
- **Recorded follow-ons (not resolved here):**
  - (a) Recursive container mapping is *already* supported and **constrains** this design (the guard
    must stay method-only); verify with the existing `Tree` / `mapCat` scenarios rather than treating
    it as open.
  - (b) **Lexical capture** — once inputs are scope-exposed declarations, having a child's
    `inputDecls()` include its ancestors' bindings would let an element lambda reference enclosing
    method parameters. A real capability extension with codegen implications; deliberately deferred.

## Affected Teams

Single-maintainer project (compiler/engine internals only); no cross-team or external-consumer impact.

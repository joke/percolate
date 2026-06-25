## Context

Source-path descent (`@Map(source="p.a.b.c")`) is realised today by two helpers in the expansion stage:

- `AccessorResolver` — a forward, memoized `typing()` walk that *predicts* each prefix's type, plus a
  `resolveAccessor()` that dispatches every `ExpansionStrategy` and keeps the first match by `findFirst()`.
- `ExpandStage.expandAccess` — emits the accessor for the **last** segment, then re-demands the **parent**
  path (backward), pulling the predicted parent type out of `typing()` so it can construct the parent
  `AddValue` (which cannot exist untyped).

This produces **two walks in opposite directions**: a forward typing walk feeding a backward emission
walk. The forward walk exists *only* because emission is backward — building a child accessor before its
parent means the parent `Value` (hence its type) does not yet exist, so the type must be predicted, and
prediction is a **second strategy-invocation site**. That site also selects by `findFirst()` over the
unsorted `ServiceLoader` list, so the `getter(1) < method(2) < field(3)` weight ordering is ignored on
the descent path.

Both the `graph-expansion` spec ("exactly one strategy-invocation site… no `resolveAccessor` component")
and the `source-path-resolution` spec ("type resolution performs… no strategy dispatch") already describe
the intended state. The code diverges from its own specs. This change closes that gap.

Constraints: Java 11 (no `sealed`, no pattern-matching `instanceof`); the locked `never_forward`
invariant; strategies stay myopic and graph-blind; the driver + `Applier` own all graph mutation; built-in
strategies are the first customers of the same SPI a third party uses.

## Goals / Non-Goals

**Goals:**
- Make the driver the **sole** invoker of strategies; delete the second dispatch site.
- Walk source paths **forward and target-bound** (root→leaf), so a segment's parent type is *read off the
  `Value` landed for the previous segment* — never predicted.
- Let accessor ambiguity resolve by **over-emit + cost-prune** (getter beats field), never `findFirst`.
- Give the strategy author surface an honest **produce vs descend** distinction instead of punning
  `targetType()` as the parent.
- Keep the descent-root handling **scope-kind-agnostic**, reusing the `Scope.inputDecls` abstraction
  unchanged for method and child scopes.
- Dissolve `AccessorResolver`; leave `SourceCandidates` untouched.

**Non-Goals:**
- No change to accessor matching rules, accessibility checks, weights, or codegen rendering.
- No change to the annotation surface or any user-facing API.
- No change to grounding-by-match, `SourceProjection`, or container/element-mapping mechanics beyond
  consuming their already-concrete scope roots.
- No new accessor *kinds* (records/fluent/field stay as-is); extensibility is preserved, not extended.

## Decisions

### D1 — Forward, target-bound descent replaces backward emit + forward typing

A directive's named path `[p, a, b, c]` is materialised root→leaf. The root `p` is the scope input
(`LEAF`). For each subsequent segment the driver reads the **already-landed** parent `Value`'s type,
dispatches the accessor strategy for that `(parentType, segment)`, lands the resulting accessor
`Operation` producing a typed child `Value`, and proceeds to the next segment. The type needed at each
step is a *lookup* off the previous `Value`, so the forward `typing()` shadow-walk has nothing left to
compute and is deleted.

*Why over the current backward scheme:* the two-walks structure is purely an artifact of emitting
child-before-parent. Aligning emission with data flow (root→leaf) collapses the two walks into one and
removes the only reason a type ever had to be predicted.

*Alternative — keep backward, move `typing` into the driver:* technically makes the call site singular but
keeps two invocation *semantics* (predict-by-`findFirst` vs land-by-cost) and the determinism bug. Rejected
as lipstick.

### D2 — Driver walks, strategy decides; the walk is an inline forward descent

Per-segment accessing is a myopic, graph-blind, pluggable **strategy** (it already is: `GetterPathResolver`
et al.). The **walk** — sequencing segments, reading which `Value`s exist, landing new ones, and holding
the directive's remaining path — is **driver orchestration** and cannot be a strategy: it touches the graph
and carries the directive, both forbidden to strategies. The driver runs the walk **inline** at the point a
directive-bound target is resolved: a loop over the directive's segments that, for each segment, dispatches
the accessor `descend` strategy set against the parent type read off the `Value` landed for the previous
segment, lands the accessor(s), and advances; when the path is exhausted the leaf `Value` is the ordinary
materialised source preferred for the target's port. (Implementation-equivalent to a `(parentValue,
remainingSegments)` work-list obligation, but kept inline at the existing pinned-source site for minimal
blast radius — see Decisions/Open Questions.)

*Why not emergent from node-to-node demand propagation:* if accessors were ordinary producers (output =
child, input port = parent), recursion would flow child→parent (backward), reviving the typing shadow. To
flow forward emergently a `Value` would have to know "descend `b,c` next" — directive info that may never
live on a `Value`. So the walk must be an explicit driver obligation.

### D3 — Produce vs descend expressed as two SPI dispatch methods (Java 11)

The two demands are genuinely different shapes: **produce** (output type known = the demand, inputs
declared) and **descend** (parent type + segment known, output type discovered). We surface this as two
methods on `ExpansionStrategy` rather than one punned method:

```
Stream<OperationSpec> expand(ProduceDemand demand, ResolveCtx ctx)     // producers
Stream<OperationSpec> descend(DescendDemand demand, ResolveCtx ctx)    // accessors (default: empty)
```

The driver dispatches a produce demand to `expand` and a descend obligation to `descend`; the driver
remains the **sole invoker**. `DescendDemand` exposes `parentType()` + `segment()` (no `targetType()` pun);
`ProduceDemand` keeps `targetType()`/`declaredChildren()`/`directive()` (the latter still carries
`defaultValue`/`constant` for `NullnessCrossing`/`ConstantValue`).

*Why two methods over a `sealed Demand` + `instanceof`:* Java 11 has neither `sealed` nor pattern-matching
`instanceof`. Two methods discriminate at the type level with no casts, keep each strategy's relevant demand
fully typed, and make "the driver dispatches by demand kind" explicit. The `Accessor` archetype base
implements `descend` and leaves `expand` empty; producers do the reverse.

**This amends the `target-driven-engine` "One uniform target-driven strategy surface" principle.** That
principle states every strategy answers exactly one question — "what produces this demanded target?". Forward
descent *cannot* be phrased that way: when the driver descends `.a` off a known parent, the child's type is
unknown until the accessor answers, so there is no demanded-target type to key on. Descent is therefore a
**second** strategy question ("what does reading `.segment` off this parent yield?"). Strategy **myopia is
preserved** (a descend strategy still reads only its demand, touches no graph, reads no candidate); what
changes is "one question" → "two questions (produce + descend)". This is a deliberate, scoped revision of
that principle for the accessor axis, not a relaxation of myopia.

*Alternative — keep one `expand(Demand)` with sub-interfaces and `instanceof` checks:* viable but pushes
casts into every strategy and re-admits the punning temptation. Rejected.

### D4 — Accessor selection by over-emit + cost-prune; delete `findFirst`

When several accessors match one `(parentType, segment)` (e.g. `getX()` and public field `x`), the driver
over-emits all into the same deduped child `Value` (same scope/location/type ⇒ one `Value`, several
`Operation`s) and global plan extraction prunes by weight — getter (1) before field (3). `findFirst` over
the `ServiceLoader` order is removed. This is a determinism fix; it changes generated output only for
segments that were previously resolved arbitrarily.

### D5 — Descent roots reuse `Scope.inputDecls`, concrete by scope birth, no scope-kind branch

A descent always roots at the path's first segment, which is a `Scope` `InputDecl`. `unify-scope-inputs`
already unified this: `MethodScope.inputDecls` types the root from the signature; `ChildScope.inputDecls`
returns the element `InputDecl` set at `ChildScope.initialise` when the owning op lands. Because grounding
runs *before* land and land is what *creates* the child scope, a child scope cannot exist before its root
type is concrete (grounding-at-land). Inductively this holds at every nesting depth. The forward walk's
"read the parent type off the root" is therefore "read the root `InputDecl`'s type" — uniformly concrete,
no `instanceof MethodScope`, no waiting.

### D6 — `AccessorResolver` dissolves; `SourceCandidates` is untouched

`typing()` → land-time lookup off the parent `Value`. `resolveAccessor()` → the driver's `descend`
dispatch. The `isReuseOnly`/same-type accessor-shape filter is no longer a spec-shape sniff at a side
helper — a descend demand *is* the accessor request. `SourceCandidates` reads and mutates the graph
(materialises inputs as `LEAF`s, binds ports), so it can never be a strategy and stays exactly as the
driver's binding/materialisation machinery; `sourceTypes()` keeps feeding grounding unchanged.

### D8 — Inline descent at the pinned-source site; directive-preference binds the descended leaf

Today a directive-bound target binds to an **eagerly-created, pinned** leaf `Value` created typed by the
forward `typing()` walk. Forward descent deletes the `typing()` walk: the same site (`pinnedSource`) instead
**descends the path forward**, landing real accessor `Operation`s and returning the leaf `Value` produced by
the last segment. Because `pinnedSource` already runs in `expandFree` *before* the target's ports are bound,
the descended leaf is present and concrete when `sourceForPort` consults it, and the existing pinned-source
preference routes the target to *its own* directive's leaf over a same-typed sibling — directive-preference
is preserved with no change to the binding rule. Descent stays bounded by the directive's named segments (it
never walks members no directive names), so "supply is directive-rooted only" is upheld. The same site serves
method and child scopes uniformly because it roots at `scope.inputDecls` (D5).

*Why inline rather than an eager per-scope seed + work-list obligation:* both produce identical observable
behavior, but inline reuses the existing pinned-source ordering (descend-before-bind already holds) and adds
no new work-item kind or child-scope-birth seeding path — strictly less blast radius. The eager/obligation
form remains a valid alternative if a future need (e.g. sharing a descended path across many targets) makes
per-scope seeding worthwhile.

### D7 — `never_forward` reconciliation

`never_forward` forbids a *speculative source sweep* — enumerating sources and exploring forward what they
produce. Forward descent walks a **named, target-given** path, bounded by the directive's segments, seeded
by a target binding, materialising only those segments and stopping. It discovers nothing by sweeping. The
spec language is clarified to permit root→leaf walking of a directive-given path while keeping the global
search target-seeded. (The user-blessed term for this is **target-bound**.)

## Risks / Trade-offs

- **Touching the locked `never_forward` invariant** → the spec delta encodes the target-bound/sweep
  distinction explicitly (D7); descent stays target-seeded and path-bounded, materialising only named
  segments. Reviewed against `feedback_never_forward_expansion`.
- **Behavior change for getter-vs-field ambiguity** → previously arbitrary (`ServiceLoader` order), now
  deterministic by weight (getter). New tests pin the ordering; documented as a determinism fix.
- **Heterogeneous work-list (typed-Value demands + descend obligations)** → the obligation is a small
  explicit record carrying only `(parentValue, remainingSegments)`; the driver dispatches by item kind, no
  state smeared onto `Value`s.
- **SPI method split is a breaking change for strategy authors** → all in-repo strategies (builtins,
  reactor) migrate in this change; the `Accessor` base absorbs the `descend`/`expand` split so individual
  resolvers change minimally. External authors re-point one method.
- **Amends a recently-locked principle** ("One uniform target-driven strategy surface", `target-driven-engine`)
  → the change scopes the revision to the accessor axis and preserves myopia/candidate-freedom; the spec delta
  rewrites that requirement to "two strategy questions" rather than silently contradicting it. Reviewed against
  `project_target_driven_engine` and `feedback_strategies_stay_myopic`.
- **Over-emit forking when a segment has accessors of differing output types** → handled by standard
  over-emit + prune (each output type is its own deduped `Value`; the branch the target cannot use is
  pruned). Rare; no special path.
- **Directive-preference must survive deleting `typing()`** (D8) → the `pinnedSource` site now *descends* the
  path forward (real accessor ops) instead of `typing()`+create, and returns the leaf; because `pinnedSource`
  already runs before the port binds, the existing pinned preference still routes the target to its own
  directive's leaf over a same-typed sibling. Mitigation: a regression test with two same-typed sources
  distinguished only by path.

## Migration Plan

Internal refactor of one processor stage + the `Demand` SPI surface + the three built-in resolvers; no data
or persisted-state migration. Cutover bar: compiles, `./gradlew check` green, all existing
`source-path-resolution` / `graph-expansion` scenarios pass with semantically equivalent generated code
(not byte-identical for ambiguous segments, which now deterministically prefer the getter), plus new
coverage for deep (3+ segment) paths and getter-vs-field ambiguity. The change is one logical unit; rollback
is a single revert.

## Open Questions

_All resolved 2026-06-23 (user-confirmed):_

- **Work-item representation — RESOLVED (revised during apply):** no new work-item kind — the walk runs
  **inline** at the `pinnedSource` site when a directive-bound target is resolved (D2/D8). The
  `DescendObligation` work-list form was the original lean but proved more invasive for no behavioral gain.
- **`descend` location — RESOLVED:** `descend` lives on `ExpansionStrategy` (default-empty); the driver stays
  kind-free and never probes `instanceof Accessor`.
- **Naming — RESOLVED:** `ProduceDemand` / `DescendDemand` (as written in the specs).
- **Descent trigger (D8) — RESOLVED (revised during apply):** inline-lazy — descend a directive's path
  forward at `pinnedSource`, before the target's port binds. Equivalent to eager-per-scope seeding but with
  less blast radius; the descended leaf is concrete at bind time because `pinnedSource` runs before binding.

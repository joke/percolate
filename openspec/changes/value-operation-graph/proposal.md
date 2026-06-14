## Why

The resolution graph's single-kind model (`node = value`, `edge = function`) cannot natively express an n-ary producer (an AND), so the engine encodes function identity **three redundant ways**: `GroupId` tags on endpoint nodes, one shared `EdgeCodegen` instance across a fan-in's operand edges, and per-`(name, type)` duplicated leaf nodes as simulated input ports. Three `PlanView` pruning passes plus the disjoint-slots rule exist solely to keep those encodings consistent, and every recent structural defect (overloaded-constructor assembly, OR-sibling slot collisions, the spec'd-but-unimplemented conversion-synthesis machinery) is consistency maintenance between them. The dual model (`node = function`) just smears *value* identity instead — any single-kind graph reinvents the missing kind as labels. The project is in evaluation with no releases, so the structural fix is cheapest now, before conversion synthesis and datetime bridges build more machinery on the old encoding.

## What Changes

- Replace the single-kind graph with a **bipartite AND/OR graph**: two vertex kinds, `Value` (a typed variable; OR over its producers) and `Operation` (an n-ary production; AND over its ports), as a closed two-implementation hierarchy on the existing JGraphT `DirectedMultigraph`. Edges become pure dependencies; an edge into an `Operation` carries the port id it feeds.
- **Dissolve** `ExpansionGroup`, `GroupId`, `GroupOutcome`, `Node.groups()` tags, per-`(name,type)` leaf duplication, `consumerSlot`-on-edge, the shared-`EdgeCodegen`-instance convention, and all three `PlanView` pruning passes.
- **SAT becomes Horn unit propagation** on the existing work-list: a `Value` is SAT iff any producer is SAT; an `Operation` is SAT iff all ports are SAT; base cases are parameter roots and zero-port Operations (constants). Target-to-source expansion direction is unchanged.
- **Plan selection becomes bottom-up cost extraction** (min over producers at a `Value`, weight + sum over ports at an `Operation`), fixing the current Dijkstra cost oracle's min-through-AND underestimate. New `plan-extraction` capability.
- **Nullness joins the `Value` identity key** (JSpecify-consistent: `String!` and `String?` are different types). The single unsafe crossing `NULLABLE → NON_NULL` becomes an explicit Operation — `[requireNonNull]` (today's semantics, plan-visible) or `[coalesce]` when the directive declares a `defaultValue`. The `applyNullabilityContract` codegen weaving is deleted.
- **Containers compose through an explicit `Stream` intermediate**: container reshaping is a chain of kind-local plain Operations (`iterate`/`collect`/`wrap`/`unwrap`) over a `Stream<T>` `Value`; the per-element transform is a child scope (param root in, return root out — the same shape as a method body) owned by a generic, kind-free `map`/`flatMap`, in the same flat graph. Cross-kind and flatten (`List<Optional<A>> → Optional<Set<B>>`) emerge from OR-matching on `Stream` Values — no Operation knows two kinds. Invariant: no dependency edge crosses a scope boundary (the `Stream` stages are parent-scoped); the only parent/child coupling is the owning Operation. The `ElementScope` `ENTERING`/`EXITING` edge attribute is removed; wrap (`Optional.of`) is a plain Operation, structurally distinct from element-mapping.
- **Seeding produces a goal spec**: param-root and return-root `Value`s plus per-level **declared bindings** (`{target child name → directive}`, derived from dotted target paths). Assembly strategies interpret the goal (constructors: exact consumption — every declared child consumed, nothing undeclared invented). No-silent-sourcing becomes structural: there is no rule that invents source descent for an undirected port (`InputAllocator` fall-through removed).
- **Delta pipeline retained, deltas reshaped** to `AddValue` / `AddOperation` (an Operation delta lands atomically with its ports). The per-bundle `CycleDetector` rollback is expected to dissolve — Horn derivations are well-founded — verified in design.
- **BREAKING (internal SPI):** strategies emit Operation specs (codegen, weight, ordered typed ports) instead of edge-bundle `ExpansionStep`s; codegen moves from edges onto Operations (`EdgeCodegen` → operation codegen; the `render(VarNames, IncomingValues)` contract survives). This deliberately reopens the locked 2026-05-30 "codegen on edges" decision while preserving its substance (codegen determined solely by traversing the plan; container ops first-class on the carrier).
- Debug output: the three DOT dumps (seed/full/transforms) re-rendered in bipartite (Petri-style) form — boxes for Operations, ellipses for Values.
- **Not changing:** the public `@Map` annotation surface. The generated mapper output is the behavioural contract at the level of **compiles + semantically equivalent** (the integration suite arbitrates) — not byte-identical, since cost extraction may factor an equivalent pipeline differently.

## Capabilities

### New Capabilities

- `plan-extraction`: Cheapest-plan selection over the bipartite graph — the bottom-up cost recursion, OR resolution at Values, deterministic tie-breaking, shared-Value rendering semantics (inline per use, idempotency assumption), and the read-only plan view code generation consumes. Replaces the `PlanView` selection requirements currently living in `graph-expansion`/`code-generation`.

### Modified Capabilities

- `graph-model`: `Node`/`Edge` replaced by `Value` / `Operation` vertices and dependency edges with port ids; nullness in the `Value` identity key; scope tree with the no-edge-crosses-scope invariant; `ExpansionGroup`/`GroupId` removed.
- `graph-expansion`: group forest and `GroupOutcome` records replaced by a demand work-list over `Value`s with Horn-SAT propagation; frontier matching emits Operations; conversion chains are unary Operation chains over type-deduped intermediate Values (subsumes the unimplemented convert-bundle synthesis).
- `seed-graph`: seeding creates param/return-root Values and per-level declared-bindings goal specs; no `SEED` edges, no seed groups, no pre-created untyped target leaves.
- `code-generation`: the generator walks the extracted plan; producer identity and fan-in are structural (`Value.chosenProducer()`); nullability weaving removed; renders child scopes as lambda bodies.
- `nullability`: resolver machinery unchanged, but nullness becomes part of Value identity; the `NULLABLE → NON_NULL` crossing is an explicit Operation; acceptance rules (lenient `UNKNOWN`) preserved verbatim.
- `type-conversion`: conversion steps become unary Operations; reuse-or-synthesize and type-dedup fall out of the Value identity rule; reachability SAT falls out of Horn propagation.
- `container-expansion`: each container emits kind-local plain Operations (`iterate`/`collect`/`wrap`/`unwrap`) and wrappers a same-kind scope-owning `mapPresence`; a generic kind-free stream strategy emits scope-owning `map`/`flatMap` over `Stream<T>` Values; cross-kind/flatten emerge from OR-matching (no Operation knows two kinds). The strictly-linear REALISED-chain invariant is replaced by the scope-ownership invariant.
- `container-codegen-spi`: the one-class-per-container surface keeps candidacy + codegen-handle roles, but the handle attaches to Operations; `ElementScope` edge crossing removed; Wrap-vs-Collect asymmetry becomes structural.
- `expansion-strategy-spi`: `ExpansionStep` reshaped to an Operation spec (codegen, weight, ordered typed ports, optional child scope); `Slot` becomes the port contract; intents (`CONVERSION`/`BOUNDARY`) re-expressed.
- `source-path-resolution`: path resolvers emit accessor Operations per segment instead of scaffolding edges.
- `constant-values`: `ConstantValue` emits a zero-port Operation (legitimately vacuously SAT); coercion and non-null-by-construction semantics unchanged.
- `default-values`: `defaultValue` becomes the `[coalesce]` Operation on the nullness crossing, selected by the directive in the demand context; dead-default rejection semantics unchanged.
- `graph-debug-output`: DOT renderer draws the bipartite graph (Operation boxes, Value ellipses, port-labelled edges); same three dumps, same gating.
- `realisation-validation`: diagnostics walk unsatisfied demands (Value with no SAT producer / Operation with unsatisfied ports) instead of UNSAT `GroupOutcome`s; closest-miss becomes the deepest unsatisfied port chain.
- `expansion-test-harness`: harness substrate reshaped (no groups/outcomes; builds and asserts on Values/Operations and demand state).
- `builtin-strategy-unit-tests`: required assertions re-expressed against the Operation-spec surface (ports, weight, child scope) instead of `ExpansionStep` intent/`elementScope`/`Slot` metadata.

## Impact

- **`processor` module** — the bulk: `graph` package (Node, Edge, ExpansionGroup, GroupId, GroupOutcome, PlanView, TransformsView, DotRenderer), `stages/seed`, `stages/expand` (FrontierMatcher, Applier, expanders, deltas, snapshot), `stages/generate` (BuildMethodBodies), `stages/dump`. The pipeline stage list and `Pipeline`/`ProcessorModule` wiring shape are unchanged.
- **`spi` module** — `ExpansionStep`, `Slot`, `Codegen`/`EdgeCodegen`, `ElementScope`, container SPI surfaces; `Directive`, `Nullability`, resolver surfaces unchanged.
- **`strategies-builtin` module** — every strategy's emission reshaped to Operation specs; matching logic and weights carried over.
- **Tests** — harness fixtures and strategy unit specs updated to the new surfaces; DOT goldens regenerated; **integration tests must pass with compiles + semantically-equivalent generated output** (the behavioural contract for the whole change), plus an in-repo end-to-end spec for the cross-kind container (`mapHuman`) shape.
- **Affected teams** — single-maintainer project; all three modules above. No public API or generated-code impact for mapper users.
- **Dependencies** — none added; JGraphT 1.5.2 remains the graph substrate (`CycleDetector` usage expected to be removed, `MaskSubgraph` views retained).

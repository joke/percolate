## Context

The `full`/`transforms`/`plan` DOT dumps are the primary window into the bipartite Value/Operation
graph. Three things make them hard to read today:

1. **Operation labels are codegen lambda class names.** Strategies build their codegen as a lambda
   implementing `OperationCodegen`; `ExpandStage.land`/`expandAccess` set `Operation.label` and
   `Operation.strategyFqn` from `spec.getCodegen().getClass()` — yielding
   `DirectAssign$$Lambda/0x0000000012753658`. The enclosing-strategy prefix is meaningful; the
   `$$Lambda/0x…` suffix is noise (and a load-time hash, so it also varies run-to-run).
2. **Value labels are `TypeMirror.toString()`** — full FQNs plus inline JSpecify annotations
   (`io.github.joke.testing.Human.@org.jspecify.annotations.Nullable Address`), ignoring the Value's
   own clean `nullness` field.
3. **The `full` dump shows the over-emitted candidate set undifferentiated.** Expansion over-emits
   (e.g. ~20 conversion operations into one `tgt[number]`); the dump renders survivors and pruned
   candidates identically, even though `graph-debug-output` already *requires* the full dump to
   annotate reachability from extraction cost.

There are **no external strategy authors yet**, so the SPI surface the labels touch can still change
cheaply. `VarNames` (an empty marker threaded through every `render` call but used by nobody) and the
dead `LoopContainerCodegen` are collateral cleanup.

## Goals / Non-Goals

**Goals:**
- Operation nodes read as typed productions (`int→long`, `new Address(int, String)`, `getStreet()`).
- Value nodes read as simple-name types with JSpecify `?`/`!` nullness (`Optional<Set<Address?>>!`).
- The `full` dump visually separates what survived planning from the pruned over-emission.
- Tighten the small SPI surface now: required typed `label`, no `VarNames`, no `LoopContainerCodegen`,
  no dead `strategyFqn`.

**Non-Goals:**
- Byte-stable DOT output (the lambda-hash instability disappears as a side effect, but stability is
  not a goal).
- Any operations-as-data / central-renderer IR (explicitly rejected — see D2).
- Shared-Value hoisting / local-variable naming (the seam `VarNames` reserved; re-add when it lands).
- Changing generated mapper code — the Spock/jqwik suite is the behavioural oracle and stays green.

## Decisions

### D1 — Operation label is a required, strategy-supplied typed string on `OperationSpec`

Only the strategy holds the operand types at match time, so a typed label (`int→long`,
`new Address(int, String)`) must originate there. `OperationSpec` gains a **required** `label`;
`ExpandStage` sets `Operation.label` from `spec.label()` and the accessor handler supplies its own
(`getStreet()` / `.street` / `street()`). Conversions use the glyph arrow `→` (U+2192). Built-in
labels: `DirectAssign`→`assign`; `ConstructorCall`→`new Address(int, String)`;
`WidenPrimitive`→`int→long`; `PrimitiveWrapperConversion`→`int→Integer`; `ConstantValue`→`"ACTIVE"`;
`StreamMap`→`map`/`flatMap`; container ops→`collect`/`wrap`/`unwrap`/`iterate`;
`MethodCallBridge`→`mapAddress(…)`; `NullnessCrossing`→`requireNonNull`/`coalesce`.

*Alternatives.* (a) Strip the `$$Lambda…` suffix from the lambda class name — fragile (relies on JVM
lambda naming) and only yields the strategy name, not the typed form. (b) Driver attributes the
producing strategy class (`DirectAssign`) — stable but untyped; loses `int→long`. (c) Make `label`
**optional** with a driver fallback to the strategy's simple name — gentler SPI, but with no external
authors the required field is simpler and forces every built-in to read well. Chose required + typed.

### D2 — Keep codegen-on-operation; do **not** introduce an operations-as-data IR

The clean-sounding "operations are pure descriptors; a central renderer turns kind+types into code;
the label falls out for free" is the op-node IR that was evaluated and dropped (2026-05-30). A central
kind-dispatch renderer is a **closed set**: an external strategy author (Flux/Mono) could not ship a
new operation kind's codegen in their own jar. Codegen travelling *with* the operation (the
`OperationCodegen`/`ScopeCodegen` handle the composer calls polymorphically) is exactly what keeps the
SPI externally extensible — the property this change exists to protect. `render()` is already lazy
(only chosen operations render, in `BuildMethodBodies.Walk`), so there is no performance case for
unbundling; only the cheap closure allocation is eager. Therefore the architecture is unchanged and
the label is plain data alongside the existing handle, not a replacement for it.

### D3 — Drop `Operation.strategyFqn`

It held the codegen lambda's FQN and is read by nobody (only `MapperGraph` stores it). Remove the
field from `Operation`/`AddOperation`/`MapperGraph` and stop populating it in `ExpandStage`.

### D4 — Remove `VarNames` from the codegen surface

`OperationCodegen.render(VarNames, IncomingValues)` becomes `render(IncomingValues)`; the `VarNames`
type is deleted. It is an empty marker used by zero strategies, threaded through the whole render API
for nothing. It was a reserved seam for future shared-Value hoisting (kept out of scope by
`plan-extraction`); re-adding a parameter when hoisting lands is cheap, so YAGNI wins now.

### D5 — Value labels render from a `TypeMirror` walk with JSpecify `?`/`!`

`DotRenderer.valueLabel` stops calling `TypeMirror.toString()`. A small recursive walk emits the
**simple** name at each level and a nullness suffix per level: the **outer** level's nullness from the
Value's own `nullness` field (authoritative), each **nested** type-argument's nullness from its
annotation mirrors (`@Nullable` present → `?`, else `!`). Example:
`Optional<Set<Address?>>!`. A pure regex over the annotated `toString()` is rejected — the canonical
inline form `Owner.@Nullable Inner` is awkward to rewrite positionally and brittle under nesting.

### D6 — The `full` dump dims unreachable vertices by extraction cost

`DumpFullGraphStage` extracts the plan (as `DumpTransforms` already does) and passes a
reachability predicate to the renderer; `DotRenderer` renders **all** vertices but **dims** unreachable
ones (grey fill / dashed outline) rather than filtering them. This closes the existing-but-unmet
`graph-debug-output` scenario "Full dump annotates reachability from cost". The `transforms` (reachable
filter) and `plan` (in-plan filter) dumps are unchanged.

## Risks / Trade-offs

- **[Hand-composed labels drift in style across strategies]** → acceptable for a debug view; the
  conventions in D1 are the guide, and specs pin the salient ones (`int→long`, `new Address(int, String)`).
- **[The `TypeMirror` walk mishandles exotic types]** (wildcards, intersections, type variables) →
  fall back to the simple name without a suffix rather than throwing; the dump is best-effort, never a
  compile blocker.
- **[Removing `VarNames` now costs a re-add later]** → a single parameter; the seam is documented in
  `plan-extraction` and the design here, so the re-introduction point is known.
- **[Reachability for the full dump needs the extracted plan]** → it is the same `ExtractedPlan.extract`
  the transforms dump already runs; no new machinery.

## Migration Plan

Single-PR refactor, no runtime/data migration (compile-time processor). Suggested order: (1) SPI —
add `OperationSpec.label`, drop `VarNames` from `OperationCodegen.render`, delete `VarNames` and
`LoopContainerCodegen`; (2) strategies — supply typed labels, drop the `VarNames` lambda parameter;
(3) processor — set `Operation.label` from the spec, drop `strategyFqn`, update `BuildMethodBodies`
render call sites; (4) `DotRenderer` value-label walk + unreachable dimming; (5) `DumpFullGraphStage`
plan extraction. The Spock/jqwik suite gates each step. Rollback = revert the PR.

## Open Questions

- Exact glyphs for container ops in labels (`wrap`/`collect` vs `→Optional`/`→List`) — settle during
  apply; the conversion arrow `→` is fixed.
- Whether the dimmed style is grey-fill, dashed-outline, or both — a rendering detail to tune visually
  during apply.

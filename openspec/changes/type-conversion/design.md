## Context

Percolate resolves a mapping by composing `Bridge` emissions into a path from a source location to a target slot. Today `DirectAssign` is the only built-in that pairs types — it matches `isSameType(from, to)` and emits a passthrough. Every primitive↔wrapper or primitive→wider-primitive field therefore fails to resolve, even though the JLS guarantees the conversion and javac would perform it implicitly in an assignment context.

The expansion engine already composes multi-hop paths across *distinct* `Bridge` instances — this is exactly how the proposed datetime hub-and-spoke and the existing container chains (`OptionalWrap`, `ListCollect`, …) work. That capability is the lever: we add a few atomic conversion bridges and let the engine assemble the cross-products (`Integer → Long`, `int → Long`, …) for free.

Constraints that shaped this design:
- **Bridges only.** No SPI additions, no scaffolding/driver changes, no engine code, no new processor options. ([[project_container_processing]], [[feedback_strategies_stay_myopic]])
- **Strategies are myopic.** `ResolveCtx` deliberately does not expose nullability; bridges surface the matched construct and the engine resolves nullability opaquely at commit sites. A bridge cannot and must not branch on nullability.
- **Java 11 target**, Lombok, `@AutoService(Bridge.class)` registration, Spock specs per the `builtin-strategy-unit-tests` enumeration.

## Goals / Non-Goals

**Goals:**
- Resolve boxing (`int → Integer`), unboxing (`Integer → int`), and widening (`int → long`) with three atomic bridges.
- Let the engine compose them into boxed-widening chains (`int → Long`, `Integer → long`, `Integer → Long`) with no per-pair code.
- Cover the full JLS 5.1.2 widening lattice, including the three precision-losing IEEE legs (`int → float`, `long → float`, `long → double`), matching javac's implicit-assignment behaviour.
- Ship a tagged Spock spec per new bridge.

**Non-Goals:**
- Narrowing / lossy conversions (`long → int`, `double → int`, `Integer → Byte`). User-helper territory.
- Cross-wrapper conversions that are not a widening of the underlying primitive (e.g. there is no `Integer → Double` short-circuit beyond what unbox→widen→box already yields).
- `Number`-supertype or `Object` boxing targets.
- Any nullability diagnostic for unboxing a `@Nullable` source — see Open Questions.
- Engine, SPI, scaffolding, driver, or processor-option changes.

## Decisions

### D1 — Three atomic bridges, composition over enumeration

`BoxBridge`, `UnboxBridge`, `WidenBridge`. The engine composes them: `Integer → Long` = `UnboxBridge`(`Integer→int`) → `WidenBridge`(`int→long`) → `BoxBridge`(`long→Long`).

*Why over a single mega-bridge or per-pair classes:* one class per JLS conversion family keeps each bridge's `bridge()` trivial and independently testable, and avoids hand-enumerating the ~dozens of boxed-widening cross-products. Composition is a capability the engine already provides and the datetime proposal already relies on; reusing it is the whole point of "bridges only."

### D2 — `WidenBridge` carries the JLS lattice as a `TypeKind` adjacency map

A single `WidenBridge` holds a static `Map<TypeKind, Set<TypeKind>>` encoding JLS 5.1.2:

| from | widens to |
|---|---|
| `byte` | short, int, long, float, double |
| `short` | int, long, float, double |
| `char` | int, long, float, double |
| `int` | long, float, double |
| `long` | float, double |
| `float` | double |

`bridge(from, to, ctx)` matches iff both `from` and `to` are primitive and `to.getKind() ∈ map.get(from.getKind())`, then emits one step. `boolean` appears in no row (no widening). Note the asymmetry: `byte`/`short` do **not** widen to `char` (JLS), and `char` is a one-way source.

*Why a single class with a map* over six widening bridges: the lattice is data, not behaviour; one table is easier to audit against the JLS than six near-identical classes.

### D3 — Explicit codegen per family

- **Box**: `Integer.valueOf($L)` (per-primitive wrapper + factory). Uses the wrapper's value cache; static type is unambiguously the wrapper.
- **Unbox**: `$L.intValue()` (per-wrapper accessor: `intValue`, `longValue`, `charValue`, `booleanValue`, …).
- **Widen**: explicit cast `(long) $L`.

*Why explicit over relying on autobox/auto-widen:* a bridge emits an *expression* that the next step consumes; its static type must be exactly `outputType` regardless of the surrounding context (method argument, further composition, nested wrapper). `Integer.valueOf` / `.intValue()` / `(cast)` pin the static type. Bare `$L` would lean on assignment-context conversion that may not exist where the expression lands.

### D4 — Weights: each step is `Weights.STEP` (1)

Box/Unbox/Widen each emit `weight = STEP`. `DirectAssign` stays `NOOP` (0), so an exact-type match always beats a conversion. A composed `Integer → Long` costs 3; a user helper via `MethodCallBridge` (cost `METHOD`=1) can still out-compete a long chain, which is the right preference order. No new constant in `Weights`.

### D5 — Mechanical lookups via `ResolveCtx`

Wrapper↔primitive pairing uses `ctx.types().boxedClass(primitiveType)` / `unboxedType(declaredType)` and `ctx.elements()` where a `TypeElement` is needed for the wrapper. `TypeKind` drives the widening table. No reflection, no hardcoded `TypeMirror` construction.

## Risks / Trade-offs

- **Box∘Unbox cycle** (`int → Integer → int → …`) → the engine's `CycleDetector` rollback drops cycle-attempting matches; box/unbox being mutual inverses is the canonical case it handles. No guard needed in the bridges. ([[cycles_dropped_not_impossible]])
- **Search-space growth** from three new generally-applicable bridges → bounded: widening is a DAG (no narrowing reverse), and box/unbox only fire at primitive↔wrapper boundaries. Weights keep `DirectAssign` and short paths preferred.
- **Precision loss on `int→float` / `long→float` / `long→double`** → accepted by decision (full JLS set, javac parity). Documented as a known characteristic of widening, not a bug. Users needing exactness pick the exact target type.
- **Nullable-unbox NPE** → see Open Questions; explicitly not solved here.
- **Ambiguous paths** (e.g. `int → Long` could in principle route widen→box vs box→[no unbox-widen]) → only one valid lossless route exists per pair given the lattice + no narrowing, so no tie-break ambiguity is introduced.

## Migration Plan

Additive only. New bridges activate on the processor classpath via `@AutoService`. No config flag. Behavioural change: fields that previously produced *no* mapping path (and thus an unresolved-target diagnostic) will now resolve. Call out in release notes that primitive/wrapper/widening fields begin mapping automatically. Rollback = remove the three bridge classes; nothing else references them.

## Open Questions

- **Nullable-unbox handling.** Does `JspecifyNullabilityResolver` classify a primitive `TypeMirror` as `NON_NULL`? The documented algorithm is "type-use → scope walk → package-info → UNKNOWN"; a primitive bears no annotation, so it most likely resolves `UNKNOWN`, and `join(NULLABLE, UNKNOWN) = NULLABLE` — meaning **no `NON_NULL` conflict is raised** and `Integer → int` from a nullable source emits an NPE-prone `.intValue()` with no warning. This change does **not** fix that (it would require engine/nullability work, out of the "bridges only" scope). Decision: treat as a deferred gap owned by the `nullability` capability; the `type-conversion` spec should NOT assert any nullability behaviour for unboxing. Confirm during implementation whether a primitive really resolves `UNKNOWN`, and file a follow-up nullability change if a warning is wanted.
- **Naming.** `BoxBridge` / `UnboxBridge` / `WidenBridge` vs `AutoBox` / `AutoUnbox` / `WideningPrimitive` to match the existing terse builtin naming (`DirectAssign`, `OptionalWrap`). Cosmetic; settle in tasks.

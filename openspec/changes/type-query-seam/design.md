## Context

The engine and strategies read Java types by calling `javax.lang.model` (`Types`/`Elements`/`TypeMirror`)
**directly** — `ctx.types().isSameType(...)`, `((DeclaredType) m).getTypeArguments()`, `m.getKind()` — at
~127 sites (~47 in `strategies-builtin`, ~80 in `spi`, plus engine). Because those calls must be answered
consistently, ~48 of ~74 `@Tag('unit')` specs stand up a real javac substrate (`TypeUniverse`, a shared
static `JavacTask`). javac is thread-hostile, so the shared substrate races under threaded pitest, which
forced `@Isolated`, `threads = 1`, and `spock-pitest.groovy`. Only ~11 specs actually mock.

The distinct type-questions the whole codebase asks number only **~10–13**. This is the disease: not
`TypeMirror`, but the *direct wide coupling* to it. `ValidateNoDuplicateTargetsStageSpec` already proves the
cure — it mocks `Messager` and treats `ExecutableElement`/`AnnotationMirror` as never-stubbed opaque tokens,
because the stage never interrogates them.

This change supersedes `evict-javax-model`. That change tried to replace the *currency* (`TypeMirror` →
owned `TypeRef` value model); it proved unreachable because codegen needs genuinely compiler-backed mirrors
(three permanent exemptions → dual currency forever), and its real win — a per-spec javac
(`PrivateTypeUniverse`) — only relabelled the substrate rather than removing javac from the unit path.

## Goals / Non-Goals

**Goals:**

- One narrow, mockable **type-query seam** on `ResolveCtx` (the ~10–13 measured questions), with `TypeMirror`
  kept as an **opaque pass-through token** the engine/strategies never interrogate.
- Unit tests mock `ResolveCtx` and stub the 1–2 questions their subject asks — **no javac in the unit path**,
  parallel-safe by construction, so `@Isolated`/`threads = 1`/`spock-pitest.groovy` are deleted, not bridged.
- `javax.lang.model` confined by ArchUnit to the seam impl + discovery adapter + codegen emit + diagnostics +
  nullability resolver.
- `processor` and `spi` unit specs **rewritten from scratch** against mocks, with **pitest as the acceptance
  oracle** (threaded, deterministic, extended to `spi`).

**Non-Goals:**

- Rewriting the 20 `strategies-builtin` unit specs, shrinking the 16 e2e specs to doc examples, and the pitest
  rollout to `strategies-builtin`/`reactor` — all deferred to `features-as-documentation` (#3).
- Any owned type-value model. The seam abstracts *questions*, not *values*; `TypeMirror` remains the currency.
- Changing any engine algorithm (expansion, cost, weaving, port sourcing) — coupling shape only.

## Decisions

### D1 — A behavioural seam over `TypeMirror`, not an owned value model

| Option | Verdict |
|---|---|
| Owned `TypeRef`/`TypeSpace` sole currency (`evict-javax-model`) | Rejected — unreachable (codegen needs real mirrors → dual currency); huge; the tests don't even use it |
| Per-spec real javac (`PrivateTypeUniverse`) | Rejected as the *goal* — relabels the substrate; javac stays in the unit path; needs `SynchronizedElements` |
| Keep `@Isolated` + `threads = 1` | Rejected — the recurring bridge; blocks the pitest rollout |
| **Narrow mockable seam; `TypeMirror` an opaque token** | **Chosen** — removes javac from unit tests; the three exemptions evaporate; smallest completable surface |

Because we abstract the *questions* and keep the *values*, the codegen/discovery sites that genuinely need a
real mirror (`AssembleMapperType`, `BuildMethodBodies`, `CallableMethods.producing`) keep receiving one and
call `TypeName.get(...)`/`Types.isAssignable(...)` at the boundary — there is no exemption wall.

### D2 — `ResolveCtx` **is** the seam

`ResolveCtx` is already the SPI context handed to strategies, and the engine holds one too. Making it the seam
means **one abstraction serves both** and preserves the engine/strategy separation (neither gains graph
access; the seam is read-only type Q&A). `ResolveCtx.types()`/`elements()` are removed; the ~13 questions land
directly on `ResolveCtx`:

```mermaid
flowchart LR
    subgraph boundary["javax.lang.model allowed (ArchUnit boundary)"]
        IMPL["CompileResolveCtx<br/>(seam impl → real javac)"]
        ADAPT["discovery adapter"]
        EMIT["codegen emit<br/>TypeName.get(mirror)"]
        DIAG["diagnostics (Messager)"]
        NULL["nullability resolver"]
    end
    subgraph owned["javax.lang.model forbidden"]
        ENGINE["engine<br/>graph / expand / plan / weave"]
        STRATS["strategies<br/>builtin · reactor*"]
        HELP["Containers / TypeProbe<br/>(over the seam)"]
    end
    SEAM(["ResolveCtx seam<br/>~13 type-questions · TypeMirror = opaque token"])
    IMPL --> SEAM
    SEAM --> ENGINE
    SEAM --> STRATS
    SEAM --> HELP
    ENGINE -->|real mirror token| EMIT
    ENGINE -.Element.-> DIAG
```

### D3 — The measured surface (~13 questions)

`isSameType(a,b)` · `isAssignable(a,b)` · `erasure(t)` · `isPrimitive(t)`/`isArray(t)`/`isDeclared(t)` ·
`typeArgument(t,i)` / `typeArgumentCount(t)` · `arrayComponent(t)` · `declaredType(elem,args…)` ·
`arrayType(t)` · `boxed(t)`/`unboxed(t)` · `simpleName(t)`/`qualifiedName(t)`. Methods that *return* a type
return another opaque token. `Containers`/`TypeProbe`'s higher-level predicates (`isList`, `isOptional`,
`streamElement`, …) become **mockable over the seam** — either instances the mock returns, or seam methods —
carved so each unit test stubs 1–2 questions. The exact carving is validated by the spike (D7).

### D4 — Rewrite from scratch, pitest as oracle

The misaligned specs are not migrated (that preserves coverage we know is suspect — pitest has never run on
`spi`). Each is re-derived from "what branches must this unit guarantee?", asserting behaviour on a mocked
`ResolveCtx`, with **pitest ratcheting** the acceptance floor. Example-based Spock only — **no jqwik**.

### D5 — `javax.lang.model` confinement (ArchUnit)

One rule: `javax.lang.model` imports are allowed only in the seam impl, the discovery adapter, codegen
emission, diagnostics emission, and the nullability resolver. Everywhere else (engine graph/stages,
strategies, `Containers`/`TypeProbe`) is `javax.lang.model`-free. This is the same rule `evict-javax-model`
wanted, but now it *holds* — there are no engine-side exemptions to carve.

### D6 — Revert the owned model without git-revert conflicts

The owned-model commits interleave with the `PrivateTypeUniverse` test scaffold across 20 branch commits plus
the Phase-1 `spi …/types` package already on `main`. Rather than `git revert` the range (which conflicts), the
owned model is **forward-removed as ordinary edits** on this branch (which already carries the scaffold to
keep): delete `spi …/types` + `TestTypes` + the discovery adapter, un-dual-type `Port`/`OperationSpec`/
`Demand`, restore `PortType`/`PortTypes` and pre-fold `Grounding`, restore `TypeMirror`-keyed `Value.id()`/
`MapperGraph.valueKey`/`MethodScope`/`SelfCallGuard`. `PrivateTypeUniverse` is kept transitionally for the
specs deferred to #3; the shared static `TypeUniverse` is deleted once `processor`+`spi` specs are mock-only.

## Risks / Trade-offs

- **[Seam surface misses a question]** → the spike ports a real stage + strategy first; the e2e/doc compiles +
  `percolate-smoke` gate every commit; a missing question fails the real-compile path loudly.
- **[Higher-level predicate carving too coarse — a test still needs many stubs]** → the spike *measures* 1–2
  stubs per rewritten test as an explicit go/no-go criterion; adjust the carving before rollout.
- **[Forward-removing the owned model breaks a green build mid-way]** → done as its own phase gated by a full
  build on the restored baseline before any seam work begins.
- **[Rewritten specs under-assert (green but weak)]** → pitest ratchet is the gate; a weak spec fails to kill
  mutants and blocks `check`.
- **[Removing `types()`/`elements()` breaks a caller assumed to be on the boundary]** → ArchUnit surfaces every
  straggler; each is either routed through the seam or confirmed to be a boundary package.

## Migration Plan

```mermaid
flowchart TD
    P0["Phase 0 — scaffold & withdraw<br/>opsx change · supersede evict-javax-model · strike jqwik"]
    P1["Phase 1 — revert owned model<br/>forward-remove to javax-native baseline · keep PrivateTypeUniverse · build green"]
    P2["Phase 2 — SPIKE (go/no-go)<br/>seam on ResolveCtx · SourceCandidates + ListContainer · mock-rewrite their specs · pitest-clean"]
    P3["Phase 3 — seam across all production + ArchUnit confinement"]
    P4["Phase 4 — rewrite processor unit specs from scratch · restore threaded pitest · ratchet"]
    P5["Phase 5 — rewrite spi unit specs · delete TypeUniverse · pitest on spi"]
    P0 --> P1 --> P2 -->|go| P3 --> P4 --> P5
```

Gates: e2e/doc compiles + `percolate-smoke` green after Phases 1–5; pitest deterministic and ratcheting in
`processor` (Phase 4) and `spi` (Phase 5). Rollback at any phase = `git revert` (no persisted state).

## Open Questions

- Whether `Containers`/`TypeProbe` fold *onto* `ResolveCtx` or stay as separate injectable instances the mock
  returns — decided by the spike on the 1–2-stub criterion.
- Whether the `spi` pitest ratchet floor and thresholds live in `spi/build.gradle` or the shared root block —
  decided at Phase 5 with real scores in hand.

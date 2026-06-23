## Why

When a `@Mapper` and the Lombok-annotated domain types it maps (`@Value`/`@Data`/`@Getter`
DTOs) live in the **same** compilation unit, generation fails with `no plan for tgt[]: … has
no producer`. Root cause is not a missing `lombok-percolate-binding`: Lombok mutates javac's
shared symbol table, so its generated constructors/accessors **are** visible to other
processors — in a later round. The real gap is that `MapperStep.process()` returns
`ImmutableSet.of()` unconditionally, so it runs the whole pipeline to a hard error in the
**first** round it sees a `@Mapper` — before Lombok has injected the members that
`ConstructorCall`/`AccessorResolver` read. The processor gives up one round too early.

This blocks the common, legitimate layout of keeping DTOs and their mapper in one module, and
it bites *any* AST-modifying upstream processor, not just Lombok.

## What Changes

- **`MapperStep` becomes the sole round-aware component.** After running the per-mapper
  pipeline it inspects the outcome and:
  - **Contract errors fire eagerly** (malformed `@Map`, duplicate target, unknown source) — a
    typo is wrong in every round, so these are **never** deferred (detected via the existing
    `Diagnostics.hasErrorsFor(mapperType)`).
  - **Success** emits generated code (unchanged).
  - **A pure no-producer realisation failure** causes the mapper element to be **deferred** —
    returned from `process()` so `BasicAnnotationProcessor` re-resolves it **by name** in the
    next round, where the upstream-injected members are present.
- **`RealisationDiagnosticsStage` stops printing.** It **records** the unsatisfied-realisation
  outcome (the closest-miss/UNSAT walk) into `MapperContext` instead of emitting it in-stage.
- **The `no plan` diagnostic is round-gated.** `MapperStep` emits the recorded diagnostic only
  on the **final** round (`processingOver`), reclaiming the message from
  `BasicAnnotationProcessor`'s generic "could not be processed" give-up error.
- **Pipeline stages stay round-agnostic and idempotent.** No cross-round state is carried; the
  whole pipeline re-runs against the freshly re-resolved `TypeElement` each round. This is
  deliberate — javac `Element`s from a prior round are stale, so persisting a half-finished
  `MapperContext` is exactly the thing the framework forbids.
- Not breaking: no SPI change, no change to generated output for realisable mappers. The only
  observable behavioural shift is that co-module Lombok now succeeds, and diagnostics for a
  *genuinely* un-realisable mapper surface on the final round instead of round 1.

## Capabilities

### New Capabilities

None — this extends existing processor behaviour.

### Modified Capabilities

- `processor`: `MapperStep` gains the round-aware defer/emit decision — defer un-realisable
  mappers, never defer contract errors, detect the final round, and own the realisation
  diagnostic's emission. The `Stage`/`Pipeline` contract gains the requirement that stages are
  round-agnostic and idempotent (no cross-round `Element` retention).
- `realisation-validation`: `RealisationDiagnosticsStage` changes from emitting the
  `no plan`/UNSAT diagnostic directly to **recording** it into `MapperContext`; emission timing
  moves to `MapperStep` on `processingOver`.

> `diagnostics` is **used, not modified**: the existing per-round `reset()` and
> `hasErrorsFor(Element)` requirements are exactly what the defer decision relies on. No delta
> spec for it.

## Impact

- **Code:** `MapperStep` (round decision + final-round emission), `RealisationDiagnosticsStage`
  (record instead of print), `MapperContext` (carry the recorded realisation outcome). `Pipeline`
  and the discover/validate stages are unchanged in behaviour but newly *required* to be
  idempotent.
- **Affected teams:** processor/engine maintainers (round model); downstream mapper authors who
  co-locate Lombok models with mappers (the unblocked case). No public-API or SPI surface moves.
- **Generated output:** unchanged for realisable mappers.
- **Spikes (RESOLVED — standalone `BasicAnnotationProcessor` + real Lombok 1.18.46, one
  compilation unit; see design.md):**
  1. *Does a deferred round-1 reach a round where the upstream-injected members are present?*
     **GO.** Round 1 sees only the `[0]`-arg default ctor; after deferral, `BasicAnnotationProcessor`
     re-resolves the mapper **by name** in round 2, where Lombok's all-args ctor is visible. Compile
     succeeds. No binding artifact needed.
  2. *Where does `MapperStep` emit for a genuinely-broken mapper?* During implementation a second
     spike corrected the proposal's first guess (and an interim fixpoint design): **deferral alone
     does not create another round** — javac runs a further round only when files are generated or an
     AST-modifying processor (Lombok) is active. So a genuinely-broken mapper with no co-processor gets
     only round 1, and the `Step` is never invoked at `processingOver`. The emit therefore happens in
     `PercolateProcessor.postRound` (flush still-deferred outcomes on the final round).
     `BasicAnnotationProcessor`'s `MiscError` for the leftover deferral is **not** suppressible (its
     `process` is `final`), so it co-occurs with our `no plan` for that case — an accepted trade-off
     (the co-module Lombok goal realises in Lombok's round and is consumed cleanly). See design.md
     D3/D4 and Risks.

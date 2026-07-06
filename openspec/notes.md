# Testing & Documentation Architecture — Plan of Record

A reference for the multi-change effort that reshapes how percolate is tested and documented. Captured
2026-06-27 after the discussion that scrapped the `audit-e2e-test-placement` re-sort framing.

## The core insight

The test layers and the documentation are **not separate concerns**. A user-facing feature's end-to-end
test (a Google compile-test: input mapper → generated output) **is** its documentation example. So "what is
the e2e test for the method-call bridge?" and "what is the doc example for conversion bridges?" are one
artifact. The engine, by contrast, is tested like an external library — heavy unit tests, no strategies, no
fakes. This gives a clean three-layer model.

## The three layers

| Layer | What it tests | How | Where |
|---|---|---|---|
| **Engine** | the engine as an external library (stages, graph, plan, weaving, cost, realisation) | **unit tests**, coverage gate 60%→95%, **pitest on the unit suite only** | `processor`, isolated — no real strategy, no fakes |
| **Features** | each **user-facing feature** | **e2e compile-test = the doc example** (input mapper + generated output, single-sourced via `docTags`) | **co-located with the responsible strategy**, per feature |
| **Packaging** | the artifacts work as an annotation processor and are packaged correctly | `percolate-smoke` (one fixed mapper) | `percolate-smoke` — NOT feature coverage |

## Layer 1 — Engine as a library (change: `harden-engine-as-library`, FIRST)

- Test `processor` as if it were a shipped library: comprehensive **unit tests** at its own seams
  (construct graph/demand/plan state directly and assert stage output), no compilation, no strategies.
- **Coverage gate 60% → 95%** (root `build.gradle` jacoco `BRANCH COVEREDRATIO`, currently `0.6`). The gate
  already merges `test.exec` + `integrationTest.exec`; with integration removed it measures the unit suite.
- **pitest (mutation testing) on the UNIT suite only.** pitest is slow only against integration tests; on
  unit tests it is fast and it is what makes the unit suite genuinely trustworthy (kills mutants, not just
  touches lines). **Start the change with a pitest spike** to settle config + plugin + ratchet.
- **Remove the engine integration tests for now** — the fake-driven compile-testing specs in `processor`
  (`EngineWeavingFakeStrategySpec`, the narrowed `SelfSeedExpansionSpec`, `GenerateStageFailureModes…`,
  `DocTagsEmissionSpec`, …). Real engine↔strategy integration is covered by the feature e2e layer. If a
  genuine need for an engine integration test reappears, add it back deliberately.
- Consequence: `FakeStrategy` and the never-built "assembling fake" are unnecessary. The whole
  "test the engine through a compile with a fake" idea is dropped.
- `test` runs `@Tag('unit')`, `integrationTest` runs `@Tag('integration')` — the split already exists.

## Layer 2 — Features as documentation (change: `features-as-documentation`, SECOND)

Each user-facing feature becomes **one feature section + one compile-test that is its example**. The
compile-test (with `-Apercolate.docTags`) compiles the example mapper, asserts behaviour, and materialises
the generated output the section `include::`s. Implementation, test, and documentation for a feature live
**in one place — the module responsible for that feature:**

- `reactor` → reactive container feature (e2e + doc in `reactor`)
- `reactor-blocking` → its blocking-bridge feature (e2e + doc in `reactor-blocking`)
- `strategies-builtin` → the basic / universal / common features (e2e + doc in `strategies-builtin`)
- the central `docs` module keeps only the **spine** (intro, getting-started, the nav)

This subsumes the deferred "distribute docs into modules" restructure. The current
`strategies-builtin/.../e2e/` specs are **mostly deleted and reborn** as these feature e2e+doc pairs.

### Feature inventory (each currently has a strategy but NO doc example — each is a "free e2e")

- **Path access** — getters **and** fields (and record accessors); combined section "percolate reads
  getters, record accessors, and public fields"
- **Optionals** — no example today
- **Containers** — `List`, `Set`, nested containers, `List`→`Set` conversion
- **Constants** — no example today
- **Constructor call** — "how percolate builds beans"; **builder support** when it lands
- **Defaults & nullness** — JSpecify support, unmentioned today
- **Conversion bridge** — `MethodCallBridge`: a mapper calling another method (or a `default` method) for
  conversion within a chain
- **Reactive** — already started
- **Compile-time switches** — a manual section covering every processor option (`docTags`,
  `locals.final` / `locals.var`, `nullable.annotations`, `debug.graphs`), each with an example **and its
  generated output** showing the switch's effect. Each switch is therefore a free e2e=doc test. (So
  `docTags` has two test surfaces: the pure tag-wrapping helper **unit-tested in `spi`** in
  `harden-engine-as-library`, and the switch's user-facing effect as an **e2e=doc** here.)

### Documentation invariants

- Sections are named by the **user-facing feature**, NEVER the implementation class. No literal prose like
  "`FieldPathResolver` is responsible for supporting field mappers" — that is not the intent.
- Related strategies merge into one section (getters + fields → one "path access" section).
- Every shown code block is single-sourced: input from the compiled fixture, output from real generation
  (`docTags`), never hand-typed.

## Layer 3 — Packaging (`percolate-smoke`, unchanged in intent)

`percolate-smoke` exists ONLY to prove the published artifacts work as an annotation processor and are
packaged correctly. It is not a feature-coverage or integration layer.

## Sequencing

1. **`harden-engine-as-library`** — pitest spike → unit coverage to 95% → remove engine integration tests.
   ✅ DONE, archived 2026-07-03.
2. **`type-query-seam`** (supersedes `evict-javax-model`) — narrow the ~13-question type-query seam onto
   `ResolveCtx` (`TypeMirror` kept as an opaque token, no owned value model); revert the abandoned owned
   model; rewrite `processor`+`spi` unit specs mock-only; delete `TypeUniverse`; restore threaded pitest.
   Runs **before** `features-as-documentation` so the old `strategies-builtin` e2e compile-tests serve as the
   safety net for the cutover. ✅ DONE, archived 2026-07-05.
3. **`decompose-engine-stages`** — the honest completion of Layer 1: `harden-engine-as-library` hit the
   coverage/pitest numbers on a coarse seam, but the algorithm-bearing stages (`ExpandStage.Driver`,
   `Grounding`, `BuildMethodBodies`) were still "one entry point + a wall of private helpers," which is why
   `FakeResolveCtx`/`FakeType`/`PrivateTypeUniverse` kept surviving in `processor` despite `type-query-seam`.
   Decomposes them into single-method collaborators, adds the two co-enforced ArchUnit guards below, and is
   **upstream of** the `strategies-builtin` unit-spec migration and `features-as-documentation` (both were
   being built on the coarse-seam assumption this change corrects). See "Engine internal structure" below for
   the guard's metric and the audit backlog it leaves.
4. **`features-as-documentation`** — feature e2e=doc co-located per module; delete/replace the old
   `strategies-builtin` e2e; distribute pages into modules; central spine only.

## Open topics

- **Engine internal structure (change: `decompose-engine-stages`).** `ExpandStage.Driver` and `Grounding`
  are fully decomposed into single-method collaborators (injected, mockable); `BuildMethodBodies` is the
  codegen exemplar (its pure assembly logic split from the one irreducible `TypeName.get(mirror)` leaf,
  extracted to `TypeNameRenderer`); `AssembleMapperType` was evaluated and left alone — nearly its entire
  body *is* that leaf, so there is no separable logic to extract (over-atomization risk, not a gap).
  - **Two co-enforced ArchUnit guards**, in `ModuleBoundariesSpec` (`architecture-tests`): Rule A (no
    `private` methods — a `private` method is `invokespecial`-dispatched and no test double can intercept
    it) and Rule B (a size ceiling, so Rule A alone can't be satisfied by exposing a monolith's guts as
    package-private). **Rule B's metric: non-synthetic method count per class, ceiling 15.** Chosen because
    it's the simplest metric ArchUnit doesn't ship natively (no WMC/LOC condition in the fluent DSL, so it's
    a ~10-line custom `ArchCondition`) and it separates cleanly around the decomposed classes: the largest
    legitimate unit today, `BuildMethodBodies.Walk`, sits at 13 (a cohesive data/query class walking one
    plan, design.md's cohesion exception); the pre-decomposition monoliths this change eliminated were 21
    (`ExpandStage.Driver`) and 17 (`BuildMethodBodies`) *private* methods alone. Both rules were verified to
    actually fire (private method reintroduced, ceiling lowered — each failed as expected, both reverted)
    before trusting them green.
  - **Guard scope today: `processor.internal.stages.expand..` + `processor.internal.stages.generate..`
    only** (`DECOMPOSED_ENGINE_PACKAGES` in `ModuleBoundariesSpec`) — not the full `processor.internal..`
    tree the ADDED requirement's text names, because most of the tree still has legitimate `private`
    methods outside this change's scope (see the audit backlog below). Within `expand`, the pre-existing
    small collaborators `SourceCandidates`/`SelfCallGuard`/`BindingDirective` had their remaining `private`
    helpers widened too (trivial, zero behaviour change) so the *whole* package is clean rather than
    carving out exceptions — a stronger, simpler scope than a per-class allowlist.
  - **Audit backlog (litmus: is every method describable in one sentence without "and," individually
    isolable by a same-package spec?)** — each item below still fails that litmus and is out of scope for
    this change (Non-Goal: not a mass-split of every flagged stage). Widen `DECOMPOSED_ENGINE_PACKAGES` (and
    re-check the ceiling) as each lands:
    - `ValidateConstantDefaultLegalityStage` (195 lines, 1 entry / 11 private) — `processor.internal.stages.validate`
    - `RealisationDiagnosticsStage` (91/1/5) — same package
    - `ValidateSourceParametersStage`, `ValidateMappingShapeStage` — same package, not yet censused in detail
    - `GraphDumpWriter` — `processor.internal.stages.dump`
    - `processor.internal.stages.discover.*` (`DiscoverMappingsStage`, `DiscoverCallableMethodsStage`) — still
      construct `PrivateTypeUniverse` in their specs; unrelated to this change's Driver/Grounding/BuildMethodBodies
      scope, so left untouched
    - `processor.internal.graph.*` (`MapperGraph`, `ExtractedPlan`, `DotRenderer`, `Value`, `Dep`) — the value/view
      layer design.md calls "already small"; several still carry private algorithmic helpers (e.g.
      `ExtractedPlan.walk`/`cheapestProducer`, `DotRenderer`'s whole private rendering suite) that were never in
      the census table and are out of scope here
  - **`FakeType` was *not* fully deleted** (only `FakeResolveCtx` was): it retains one legitimate consumer,
    `ValidateConstantDefaultLegalityStageSpec`, which does genuine boundary-exempt structural `TypeMirror`
    inspection (`instanceof DeclaredType`/`ArrayType`) unrelated to the `ResolveCtx` seam. `PrivateTypeUniverse`
    similarly survives for `AssembleMapperTypeSpec` (the one class that *is* the compile-tested leaf) and for
    the `discover` specs above — both explicitly permitted by the ADDED requirement's own exception clause.

- **Evict `javax.lang.model` from engine + SPI (change: `evict-javax-model`, decided 2026-07-03).**
  **WITHDRAWN 2026-07-04 — superseded by `type-query-seam`.** The owned-`TypeRef`-currency approach proved
  unreachable (codegen needs genuinely compiler-backed mirrors → three permanent exemptions → dual currency
  forever), and its real win — a per-spec javac (`PrivateTypeUniverse`) — only relabelled the substrate rather
  than removing javac from the unit path. `type-query-seam` keeps `TypeMirror` as the currency and instead
  narrows a mockable ~13-question type-query seam onto `ResolveCtx`, so unit tests mock 1–2 questions instead
  of a compiler. The cleanup folded in below (delete `TypeUniverse`, restore threaded pitest, ArchUnit
  confinement) carries over to `type-query-seam`. Historical rationale retained for context:
  Supersedes the earlier "immutable fake `Types`/`Elements`, no production abstraction" plan. The recurring
  TypeUniverse trouble (races under threaded pitest, javac "Filling X during Y", `@Isolated`, `threads = 1`,
  `SynchronizedElements`, priming lists) is a symptom; the disease is the *currency*: a `TypeMirror` is a
  handle into a live javac session, not a value. Production already suffers it — `Value.id()` dedups by
  `TypeMirror::toString`, same string-keying in `MethodScope`/`SelfCallGuard`, nullability bolted beside the
  mirror because it can't live in it. **The fix:** an own immutable type model (`TypeRef` + an immutable
  universe snapshot passed via `ResolveCtx`, never ambient static state) as the sole engine+SPI currency;
  javax.lang.model dies at the discovery boundary (adapter — discovery already half-is one: `CallableMethods`,
  `MethodCandidate`, `MappingDirective`); an ArchUnit rule confines javax.lang.model to the adapter so the
  disease can't creep back. Measured surface is small (~8 `Types` + ~5 `Elements` methods). Type-system
  fidelity stays deliberately lawful-not-faithful (structural sameness, edge-walk assignability, invariant
  generics, boxing table) — real Java semantics remain the feature-e2e layer's job. Spike-gated: port one
  strategy + one stage to a prototype `TypeRef` before committing to the cutover.
  **Cleanup folded in:** `TypeUniverse`/`HarnessResolveCtx` javac substrate deleted; all 16 `@Isolated`
  removed; `processor/spock-pitest.groovy` and its `spock.configuration` jvmArg deleted; pitest `threads`
  back to `availableProcessors()`; `pitestTargetClasses`/`pitestTargetTests` property blocks removed;
  processor-specific `excludedClasses` moves from root `build.gradle` into `processor/build.gradle`; pitest
  rolled out to `spi`, `strategies-builtin`, `reactor`, `reactor-blocking`.

## Scrapped

- **`audit-e2e-test-placement`** — wrong frame (it re-sorted compile-testing e2e by contract and invented
  an "assembling fake"). Superseded entirely by the three-layer model above.

## Enabling machinery already in place

- **`enforce-module-separation`** — the `processor` `internal`/api split (the surface the engine unit tests
  cover) and the ArchUnit boundary (keeps engine tests out of strategy modules and vice versa).
- **`single-source-manual-examples`** — the opt-in `-Apercolate.docTags` generation, the `org.antora`
  Gradle plugin, and the antora-collector materialisation pipeline — the mechanism the feature e2e=doc
  layer reuses. (Its central-`docs/` page layout is what Layer 2 redistributes into modules.)

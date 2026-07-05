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
   safety net for the cutover.
3. **`features-as-documentation`** — feature e2e=doc co-located per module; delete/replace the old
   `strategies-builtin` e2e; distribute pages into modules; central spine only.

## Open topics

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

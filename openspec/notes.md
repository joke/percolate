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
2. **`features-as-documentation`** — feature e2e=doc co-located per module; delete/replace the old
   `strategies-builtin` e2e; distribute pages into modules; central spine only.

## Open topics

- **Redesign the engine-test type universe (its own change, after `harden-engine-as-library`).** The shared,
  all-`static` `TypeUniverse` javac singleton (`spi/src/testFixtures`) is **not thread-safe**, so the engine
  seam tests race it under threaded pitest — non-deterministic, *under-reported* mutation scores (a class
  swung 9%↔91% across identical clean runs; the race hid ~280 real kills). It has bitten the project
  repeatedly. **Bridge** (in `harden-engine-as-library`): mark the TypeUniverse-using specs `@Isolated` —
  serialises them, deterministic, ~zero cost since the unit suite isn't Spock-parallel today. **Real fix:** an
  immutable, test-only fake `Types`/`Elements` implementing only the javax-model operations the engine calls,
  with test-controllable type relationships — inherently thread-safe/parallel/fast, no javac "Filling"
  fragility. No production abstraction needed (the engine already consumes the `Types`/`Elements` interfaces
  via `ResolveCtx`); the fake is test-only. Engine *logic* is tested against a controlled oracle; real type
  *semantics* are the feature-e2e layer's job (real compiles). Sequence it **before** writing further engine
  seam tests.

## Scrapped

- **`audit-e2e-test-placement`** — wrong frame (it re-sorted compile-testing e2e by contract and invented
  an "assembling fake"). Superseded entirely by the three-layer model above.

## Enabling machinery already in place

- **`enforce-module-separation`** — the `processor` `internal`/api split (the surface the engine unit tests
  cover) and the ArchUnit boundary (keeps engine tests out of strategy modules and vice versa).
- **`single-source-manual-examples`** — the opt-in `-Apercolate.docTags` generation, the `org.antora`
  Gradle plugin, and the antora-collector materialisation pipeline — the mechanism the feature e2e=doc
  layer reuses. (Its central-`docs/` page layout is what Layer 2 redistributes into modules.)

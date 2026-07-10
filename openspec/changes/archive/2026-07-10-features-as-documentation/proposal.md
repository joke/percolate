## Why

The manual documents a handful of features with mostly hand-typed generated output, while `strategies-builtin/src/test/.../e2e/` holds 16 compile-tests of which only 2 actually back a doc page — the rest are engine-correctness fossils from before the `ResolveCtx` seam existed, which broke on cosmetic codegen churn without catching real regressions. This is the last step of the testing/documentation plan of record (`openspec/notes.md`), now unblocked: it inverts the relationship so **documentation drives a minimal end-to-end layer**, closes the example gaps (list mapping has no worked example today), and co-locates each feature's prose with the module that owns it.

## What Changes

- **Establish the driving principle: the manual's feature sections define the e2e set.** An e2e exists only to be a documented feature's example; it must pass the doc gates (is it a feature worth reading about, understandable, not too complicated, does it make a clear point). The e2e layer is deliberately **non-exhaustive** — one witness per user-facing *mechanism*, not per type combination, because the mechanisms are parametric over their type arguments (the `@ControllerAdvice`/`List<X>→List<Y>` argument). Exhaustiveness stays the unit + pitest layer's job.
- **e2e assert behaviour, not generated source text.** A doc-e2e instantiates the generated mapper and checks what it *does*; the generated source is materialised only for *display*. This cures the brittleness (cosmetic-change false alarms) and blindness (un-asserted regressions) that made the old string-matching e2e dead weight.
- **Sort the 16 builtin e2e specs two ways.** Genuine doc examples are kept/reworked as feature=doc pairs; engine-correctness specs (`TwoSameTypedSources`, `SourcePathAccessorAmbiguity`, `ContainerReturn`, `OverloadedConstructorAssembly`, `HoistAssembly`, the diagnostics specs, …) are **repatriated to `processor` unit tests** at the now-mockable seam, or dropped where already unit-covered. There is no third "regression e2e" bucket.
- **Close the example gaps with progressive, single-sourced chapters.** A worked example per container mechanism (`List<X>→List<Y>`, `Set→List`, `Stream→Set`, presence-in/out of a container), an Optionals chapter, a Defaults & nullness chapter, a Compile-time-switches reference (each processor option with its example and generated effect), and path access over fields/records — each shown with real generated output.
- **Enforce single-sourced generated output on every page**, completing the existing user-manual requirement that only `nested-paths` satisfies today; no page may hand-type a block claimed to be generated code.
- **Co-locate feature prose in its owning module.** `reactor` owns reactive, `strategies-builtin` the basics, `spi` the Extending page (with `reactor` as the real worked example of a custom strategy), `processor` the switches reference; `docs/` keeps only the spine (index, getting-started, mapper-structure, nav). Reader-invisible; cuts maintainer mental distance.
- **Permit `processor`/`spi` to host their own integration-tagged doc-e2e**, narrowing the engine's strategy-free constraint to its **unit** suite so Layer 1's isolation is intact while a feature these modules own can still be demonstrated end-to-end.

## Capabilities

### New Capabilities
<!-- None: this change reshapes existing capabilities rather than introducing a new one. -->

### Modified Capabilities
- `e2e-test-architecture`: e2e become documentation-driven and non-exhaustive (one witness per mechanism); assert behaviour over real generated output rather than source text; are positive-only, with engine-correctness and diagnostics repatriated to unit tests; and the processor's strategy-free rule narrows from "compile, runtime, or test" to the **unit** suite, so `processor`/`spi` may host an integration-tagged doc-e2e for a feature they own.
- `user-manual`: adds the missing chapters (progressive collections with a worked example per container mechanism, optionals, defaults & nullness, compile-time-switches reference, path access over fields/records); requires single-sourced generated output on **every** page; co-locates feature pages into their owning modules with `docs/` reduced to the spine; and makes the Extending section a compiled example using `reactor`.

## Impact

- **Docs tree:** feature pages move out of `docs/modules/ROOT/pages/` into their owning modules; `docs/` keeps the spine + `nav.adoc` (xrefs resolve across modules). `docs/antora.yml` gains collector `scan` entries for module-owned pages.
- **`strategies-builtin`:** the `e2e/` package is sorted — doc examples reworked to behavioural + single-sourced output, correctness specs repatriated/removed.
- **`processor`:** gains the repatriated engine-correctness unit tests; gains an integration-tagged switches doc-e2e (unit suite stays strategy-free and fake-free).
- **`spi`:** gains an integration-tagged Extending doc-e2e; `reactor` supplies the real custom-strategy example. `reactor-blocking` gains its blocking-bridge page.
- **Governance:** the `module-boundaries` ArchUnit guards are main-scope and already permit these test-scope edges — verified, not modified. `e2e-test-architecture` and `user-manual` specs are updated.

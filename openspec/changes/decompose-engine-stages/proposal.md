## Why

The algorithm-bearing classes in `processor/src/main/.../internal/stages` are "one public entry method + a wall of private helpers" god-classes — `ExpandStage.Driver` (392 lines, 1 entry / 21 private), `BuildMethodBodies` (250, 1/17), `Grounding` (232, 1/11), `ValidateConstantDefaultLegalityStage` (195, 1/11), `AssembleMapperType` (82, 1/4). This structure is untestable in isolation: the only addressable surface is the entry point, so every test must drive the whole pipeline through it — which forces sociable tests, which force the `FakeResolveCtx`/`FakeType` (engine) and `PrivateTypeUniverse` (codegen) substrates. **The structural problem and the test-substrate problem are one problem** — every fake and every javac substrate traces back to a stage that can only be entered through its front door. `harden-engine-as-library` met the coverage/pitest *metrics* (95% branch, 86% mutation) but not the *property* those metrics were meant to prove: individually addressable engine units. This change is that property's honest completion, and it is upstream of the deferred `strategies-builtin` unit-spec migration and of `features-as-documentation`, both of which were being built on it.

## What Changes

- Decompose the algorithm-bearing engine stages so **every method is describable in one sentence without "and"** and is individually isolable. `ExpandStage.Driver` and `Grounding` are worked in full; `BuildMethodBodies` is the codegen exemplar; the rest become an audit backlog gated by the same litmus (judgment per class, not a blind mass-split).
- Each private helper takes one of **three fates**: (a) a separable behavioral step becomes a new single-method collaborator (`land`→`PortBinder`/`PortSourceResolver`, the unify family→`Unifier`, `descendSegment`→`SourcePathDescender`, `widen`→`SourceWidener`, `instantiate`→`SpecInstantiator`); (b) a misplaced method is relocated to its rightful value class (`type`/`nullness`→`Value`, `childLocation`→`Location`, `splitPath`→the path type); (c) a genuinely atomic single-use helper is inlined. The result is a handful of collaborators plus relocations plus inlines — not a swarm of tiny classes.
- **BREAKING (engine-internal only):** no `private` methods in the engine `internal` packages. This is a JVM constraint, not a style choice — mock/spy proxies (even `mockito-inline`) cannot stub a `private` method (`invokespecial`, statically bound), so a private method is un-isolable by any test double.
- Add **two co-enforced ArchUnit guards** over the engine internal packages: a no-`private`-methods rule and a class-size / method-count cap. Neither alone suffices — the no-private rule on its own is satisfied by exposing a blob's guts; the pair leaves separable logic nowhere to hide but a new small class.
- Unit tests exercise each unit against a **mocked** `ResolveCtx` and **mocked collaborators**; **spies are reserved for irreducible self-recursion only** (`Grounding.ground` over `App` args, `unify→unifyApp→unify`). `FakeResolveCtx`/`FakeType` are deleted from `processor`; `PrivateTypeUniverse` shrinks to at most the compile-tested `TypeName.get(mirror)` codegen leaf.
- pitest stays bound to the **unit** suite only; ratchet floors are maintained. All graph mutation stays inside the driver package — strategies remain myopic.

## Capabilities

### New Capabilities

_None._ This change re-shapes engine internals and tightens existing test/structure requirements; it introduces no new user- or SPI-facing behavior.

### Modified Capabilities

- `engine-test-quality`: the engine is not merely unit-tested at a coarse seam but **decomposed so each unit is individually isolable**; unit tests mock the `ResolveCtx` seam and collaborators, use **no `FakeResolveCtx`/`FakeType`**, and use **spies only for irreducible self-recursion**.
- `module-boundaries`: adds the two co-enforced structural guards (no-`private` in engine internals; class-size/method-count cap) alongside the existing structural-naming, acyclicity, and `javax.lang.model` confinement rules.
- `expansion-test-harness`: `FakeResolveCtx`/`FakeType` are removed from `processor`; `PrivateTypeUniverse` is shrunk to (at most) the compile-tested codegen `TypeName.get(mirror)` leaf, or removed outright.

## Impact

- **Production:** `processor/src/main/.../internal/stages/expand` (`ExpandStage.Driver` → collaborators; `Grounding` → collaborators) and `internal/stages/generate` (`BuildMethodBodies`); minor method relocations onto `Value`/`Location`/the path type. No public API, runtime, or consumer-packaging change — this is an engine-internal refactor.
- **Tests:** rewrite the affected `processor` specs to mock-and-spy; delete `FakeResolveCtx`/`FakeType`; shrink/remove `PrivateTypeUniverse` in `spi/src/testFixtures`.
- **Enforcement:** two new ArchUnit rules in the `architecture-tests` module.
- **Gates:** jacoco 95% branch and threaded unit-only pitest are preserved in intent; ratchet floors maintained across cleared-history runs.
- **Teams:** none downstream — the surface is confined to the engine and its own tests; no other module's API or classpath changes.

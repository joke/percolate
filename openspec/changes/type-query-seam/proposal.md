## Why

The engine and strategies interrogate `javax.lang.model` (`Types`/`Elements`/`TypeMirror`) **directly** across
~127 scattered call sites, so ~48 of the ~74 `@Tag('unit')` specs are forced to stand up a real, shared,
thread-hostile javac substrate (`TypeUniverse`) — which races under threaded pitest and forced `@Isolated`,
`threads = 1`, and a serialised minion config. The "unit" label is largely fiction, and pitest — the tool that
would prove these tests actually cover the code — has never run on `spi`. The distinct type-questions the whole
codebase asks number only ~10–13: routing them through one narrow, mockable seam makes unit tests mock 1–2
methods instead of a compiler, removing the disease at its root rather than bridging it. (This supersedes
`evict-javax-model`, whose owned-`TypeRef`-currency approach proved unreachable — codegen needs genuinely
compiler-backed mirrors — and whose real win, a per-spec javac, only relabelled the substrate.)

## What Changes

- **New narrow type-query seam on `ResolveCtx`.** `ResolveCtx` exposes the ~13 purpose-built questions the
  engine and strategies actually ask (`isSameType`, `isAssignable`, `erasure`, `isPrimitive`/`isArray`/
  `isDeclared`, `typeArgument`/`typeArgumentCount`, `arrayComponent`, `declaredType`, `arrayType`,
  `boxed`/`unboxed`, `simpleName`/`qualifiedName`). `TypeMirror` stays as an **opaque pass-through token** —
  never interrogated by engine/strategy code, so a mock never stubs it.
- **BREAKING (SPI):** `ResolveCtx.types()`/`elements()` are removed. All engine and strategy type
  interrogation routes through the seam; `Containers`/`TypeProbe` become mockable over it. Pre-1.0, no
  external consumers.
- **`javax.lang.model` confined by ArchUnit** to the seam implementation, the discovery adapter, codegen
  emission, diagnostics, and the nullability resolver — nowhere else in engine or strategies.
- **Revert the abandoned owned model** (`evict-javax-model`'s `TypeRef`/`TypeSpace`/`TypeDecl`/discovery
  adapter/dual-typed `Port`/`OperationSpec`/`Demand`/`PortType`-fold/value-equality keying, plus the
  `spi …/types` package and `TestTypes` already on `main`) to the `javax.lang.model`-native baseline. The
  `PrivateTypeUniverse` test fixture is kept **only transitionally** for the specs deferred to change #3.
- **Rewrite `processor` and `spi` unit specs from scratch** against a mocked `ResolveCtx` (the existing
  `ValidateNoDuplicateTargetsStageSpec` pattern: mock collaborators, `javax.lang.model` values as
  never-stubbed opaque tokens, assert behaviour), rather than migrating suspect coverage. **No jqwik** —
  example-based Spock only.
- **Delete the shared static `TypeUniverse`** and the pitest workarounds; **apply threaded pitest to `spi`**
  (`threads = availableProcessors()`, no `@Isolated`, no `spock-pitest.groovy`, no `pitestTargetClasses/-Tests`
  blocks) with a ratchet floor — pitest becomes the acceptance oracle.
- **Spike-gated:** one engine stage (`SourceCandidates`) + one strategy (`ListContainer`) routed through the
  seam with their specs rewritten mock-only and pitest-clean, before the horizontal rollout.

## Capabilities

### New Capabilities

_None._ The seam is the evolution of an existing SPI surface (`ResolveCtx`), not a new subsystem.

### Modified Capabilities

- `expansion-strategy-spi`: `ResolveCtx` exposes the narrow type-query seam instead of `Types`/`Elements`;
  `Containers`/`TypeProbe` answer questions over it; a strategy is authorable treating `TypeMirror` as an
  opaque token it never interrogates.
- `expansion-test-harness`: the `TypeUniverse` shared-javac substrate requirement is removed; unit tests
  construct a **mocked `ResolveCtx`** (no javac, parallel-safe); the **jqwik property-test and configuration
  requirements are struck** in favour of example-based Spock.
- `module-boundaries`: new ArchUnit rule confining `javax.lang.model` imports to the seam implementation,
  discovery adapter, codegen emission, diagnostics, and nullability resolver.
- `engine-test-quality`: threaded pitest (`availableProcessors()`) with no `@Isolated`/serialisation, extended
  to `spi`, deterministic across cleared-history runs, module-specific config in the module.

## Impact

- **Modules:** `spi` (seam on `ResolveCtx`; owned-model removal; spec rewrite; pitest), `processor` (engine
  routed through the seam; `CompileResolveCtx` prod impl; owned-model + adapter removal; spec rewrite; pitest
  config restore), `strategies-builtin`/`reactor`/`reactor-blocking` (production routed through the seam only),
  `architecture-tests` (new rule), root + module `build.gradle`.
- **Affected parties:** solo maintainer; no external SPI consumers (pre-1.0) — last cheap moment for the seam break.
- **Safety net:** the e2e/doc compile-tests (`strategies-builtin`, `reactor*`) and `percolate-smoke` stay green
  throughout — they exercise the seam's real-javac implementation against real compiles.
- **Explicitly deferred to `features-as-documentation` (#3):** rewriting the 20 `strategies-builtin` unit specs
  against mocks, deleting `PrivateTypeUniverse`/`ResolveCtxBuilder`, shrinking the 16 e2e specs to genuine doc
  examples, and the pitest rollout to `strategies-builtin`/`reactor`.

# Proposal: evict-javax-model

## Why

`javax.lang.model` is the engine's and SPI's type currency, and a `TypeMirror` is not a value — it is a
handle into a live, lazy, thread-hostile javac session. Every recurring pain traces back to this one fact:
the shared-static `TypeUniverse` javac singleton races under threaded pitest (non-deterministic,
*under-reported* mutation scores), forcing `@Isolated` on 16 specs, `threads = 1`, a serialised
`spock-pitest.groovy` minion config, `SynchronizedElements`, and javac "Filling X during Y" priming lists.
Production suffers too: mirrors have no value equality, so `Value.id()` dedups the graph by
`TypeMirror::toString`, and `MethodScope`/`SelfCallGuard` string-key the same way. Bridges (`@Isolated`) have
been applied repeatedly; the problem keeps returning. This change removes the disease instead of the
symptom — and unblocks rolling threaded pitest out to every module, which the
`features-as-documentation` plan (docs-as-e2e + unit/mock + pitest) depends on.

## What Changes

- **New immutable type model as the sole engine + SPI type currency.** A `TypeRef` value model (declared /
  primitive / array / variable kinds, type arguments, value equality) plus an immutable **universe
  snapshot** that answers the type-system questions the engine actually asks (measured surface: ~8 `Types` +
  ~5 `Elements` methods — sameness, assignability, erasure, boxing, member enumeration). The snapshot is **a
  value passed through `ResolveCtx`, never ambient static state** — there is nothing shared to race on.
- **javax.lang.model dies at the discovery boundary.** Discovery becomes the adapter: it walks real
  `Element`s/`TypeMirror`s once per mapper round and materialises the snapshot + model objects
  (`CallableMethods`, `MethodCandidate`, `MappingDirective` already began this vocabulary). Codegen gains a
  `TypeRef → TypeName` emitter replacing `TypeName.get(mirror)`.
- **BREAKING (SPI):** `ResolveCtx` no longer exposes `Types`/`Elements`; `Demand`, `CallableMethods`,
  `Containers`, `TypeProbe`, `OperationSpec` et al. carry `TypeRef` instead of `TypeMirror`. All strategies
  (builtin, reactor, reactor-blocking) are ported. Pre-1.0, no external consumers.
- **ArchUnit confinement:** a new rule restricts `javax.lang.model` imports to the discovery/adapter (and
  processor-boundary) packages so the currency can never leak back into engine or strategies.
- **`TypeUniverse` is deleted** (not redesigned), along with `HarnessResolveCtx`'s javac substrate. Tests
  construct snapshots as plain immutable values (fixture classes may still be mirrored into the model by a
  small reflection-based test builder — decided in design).
- **Type-system fidelity is deliberately lawful, not faithful:** structural sameness, edge-walk
  assignability, invariant generics, fixed boxing table. Real Java type semantics remain the feature-e2e
  layer's job (real compiles), per the three-layer plan of record.
- **pitest restored and rolled out:** `threads = availableProcessors()`; all 16 `@Isolated` removed;
  `processor/spock-pitest.groovy` and its `spock.configuration` jvmArg deleted; the
  `pitestTargetClasses`/`pitestTargetTests` property blocks deleted; processor-specific `excludedClasses`
  move from the root `build.gradle` into `processor/build.gradle` (root keeps only module-agnostic
  mechanics); pitest applied to `spi`, `strategies-builtin`, `reactor`, `reactor-blocking`.
- **Spike-gated:** port one strategy + one stage to a prototype `TypeRef` first; go/no-go before the full
  cutover.
- **Sequenced before `features-as-documentation`:** the existing `strategies-builtin` e2e compile-tests are
  the safety net that validates the adapter against real javac before Layer 2 deletes them.

## Capabilities

### New Capabilities

- `type-model`: the immutable type currency — `TypeRef` values, the universe snapshot and its type algebra
  (sameness, assignability, erasure, boxing, generics invariance), member/callable views, construction from
  `javax.lang.model` at the discovery boundary, `TypeRef → TypeName` codegen emission, and the
  thread-safety/immutability guarantees.

### Modified Capabilities

- `expansion-strategy-spi`: `ResolveCtx` exposes the snapshot (not `Types`/`Elements`); `Demand`,
  `OperationSpec`, `TypeProbe` carry `TypeRef`; a strategy is authorable without importing
  `javax.lang.model`.
- `callable-method-discovery`: `CallableMethods.producing(TypeRef)`; the discovery stage is part of the
  adapter boundary that materialises the model.
- `container-expansion`: `Containers` helper predicates/extractors take `TypeRef` instead of `TypeMirror`.
- `graph-model`: `Value` typing is a `TypeRef`; identity/dedup by value equality, not
  `TypeMirror::toString` string keys (same for operation text-encodings).
- `graph-debug-output`: nullness/type rendering walks the `TypeRef` model, not a `TypeMirror`.
- `expansion-test-harness`: the `TypeUniverse`/`HarnessResolveCtx` javac-substrate requirements are replaced
  by snapshot-literal fixture construction (plain values, no javac, parallel-safe).
- `builtin-strategy-unit-tests`: the "single-substrate javac invariant" is replaced by "types are plain
  model values; `javax.lang.model` never appears in a strategy unit spec".
- `module-boundaries`: new ArchUnit rule — `javax.lang.model` imports confined to the adapter/boundary
  packages.
- `engine-test-quality`: mutation testing extended — parallel-safe suite (no `@Isolated`),
  `threads = availableProcessors()`, pitest applied to `spi`/`strategies-builtin`/`reactor`/
  `reactor-blocking`, module-specific pitest config lives in the module.

## Impact

- **Modules touched:** all production modules — `spi` (model + SPI signatures), `processor` (engine +
  adapter + codegen emitter), `strategies-builtin`, `reactor`, `reactor-blocking` (ported strategies),
  `architecture-tests` (new rule), root + module `build.gradle` (pitest config), plus every unit spec that
  consumed `TypeUniverse` (16 processor + ~30 strategies-builtin files).
- **Affected parties:** solo maintainer; no external SPI consumers yet (pre-1.0) — this is the last cheap
  moment for a currency break.
- **Safety net:** the old `strategies-builtin` e2e compile-tests (real javac end-to-end) stay alive through
  this change and gate the adapter's correctness; `percolate-smoke` gates packaging.
- **Risk concentration:** the discovery adapter's eager closure walk (accessor types, callable signatures,
  directive types; cycle-safe) and the `TypeRef → TypeName` emitter (nested types, arrays, type-use
  `@Nullable`) — both spike-validated and e2e-covered.
- **Explicitly out of scope:** wildcard/JLS-faithful subtyping (lawful-not-faithful algebra only), the
  `features-as-documentation` re-slice, de-leaking `percolate-dependencies`.

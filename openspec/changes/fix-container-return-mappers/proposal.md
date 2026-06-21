## Why

A direct container-return mapper — `List<Human.Address> mapAddresses(Set<Person.Address>)` delegating to a sibling `Human.Address mapAddress(Person.Address)` — fails to generate (7 × `no plan for tgt[]` in the integration build), even though the correct `stream().map(this::mapAddress).collect(...)` plan is fully present in the graph. Root-causing it (from the integration `.dot` dumps) surfaced **two independent engine bugs**, and the first of them (`self-bridge`) is the exact pre-existing quirk that forced `add-reactor-modules` to defer its `reactor-blocking` module (D9). Fixing both bugs unblocks both the JDK container case and the deferred reactive module, so they ship together.

## What Changes

- **Bug B — self-bridge.** The expansion driver currently lets a mapper method satisfy its **own** return-root: `DiscoverCallableMethodsStage` indexes every single-param method by return type via `getAllMembers`, including the method being generated, so `this.mapAddresses(address)` (weight `METHOD`) out-prices the real `collect∘map∘iterate` chain. **Fix:** exclude the method-under-generation from its own candidate set, as a per-scope exclusion at expansion time (driver / `CompileResolveCtx`) — never in the scope-independent index, and never excluding delegation to a *different* abstract method (`mapAddress`). Identity is the same `ExecutableElement` (Java forbids two abstract methods with an identical erased signature).
- **Bug A — type-blind return-root.** `TargetLocation.isReturnRoot()` is `path.getSegments().isEmpty()` (location only). Over-emission mints many *typed* candidate Values at the same root location `tgt[]` (List/Set/Optional/Stream/array/scalar × element type); every unreachable one is then reported as a return-root with `no plan`. **Fix:** the return root is the single *seeded* Value (identified by the seed, e.g. tagged at `seedReturnRoot` / matched on the method return type), not "any Value at the empty path." The three consumers — realisation diagnostics, plan extraction (root set), and codegen (return expression) — all key on that seeded identity.
- **Ship `reactor-blocking`** (deferred `add-reactor-modules` D9), now that Bug B no longer masks it: a pure opt-in SPI module with the upward async→sync crossings `block` / `blockOptional` / `single().block` / `collectList().block` / `toStream`, each weighted above any non-blocking alternative, reuse-only ports (the `unwrap` pattern). The boundary-direction rule is preserved by packaging: downward auto (`reactor`), upward opt-in only (`reactor-blocking`).
- **Fold in the postponed `add-reactor-modules` tests:** a direct container-return positive (generates `stream().map(elem).collect`), `reactor-blocking` positives per family, the high-weight "no eager block" guard, and confirmation that the `reactor`-only negatives still report "no producer" for an upward crossing without the blocking module.

## Capabilities

### New Capabilities
<!-- none — reactor-blocking is expressed as a modification of the existing reactor-containers spec, which today records it as deferred -->

### Modified Capabilities
- `graph-expansion`: a method never produces its own return-root (self-bridge exclusion, per-scope at expansion); the seeded return-root is a distinct type-aware identity, not "any Value at the empty path."
- `realisation-validation`: only the seeded return-root(s) are checked for reachability — over-emitted typed siblings at the root location never produce a spurious `no plan`.
- `plan-extraction`: the plan's roots are the seeded return Values, not every empty-path Value at the return location.
- `code-generation`: the method's return expression resolves from the seeded return-root, enabling direct container-return method bodies.
- `reactor-containers`: the opt-in `reactor-blocking` module ships (reversing D9's deferral); an upward async→sync crossing is satisfiable when, and only when, `reactor-blocking` is on the annotation-processor classpath.

## Impact

- **Engine (`processor`):** `DiscoverCallableMethodsStage` / `CompileResolveCtx` / `ExpandStage` driver (Bug B); `seedReturnRoot` + return-root identity threaded through `RealisationDiagnosticsStage`, `ExtractedPlan`, `BuildMethodBodies` (Bug A). `TargetLocation.isReturnRoot()` semantics revisited.
- **New module `reactor-blocking`:** `@AutoService` strategies, `implementation project(':spi')`, `reactor-core` pin; added to `settings.gradle`. No engine dependency.
- **Tests:** new engine specs (container-return positive, self-bridge negative, no-spurious-no-plan); new `reactor-blocking` Spock suites; the `reactor` boundary negatives retained.
- **Non-goal:** do **not** make the over-emitted candidate graph acyclic — box/unbox cycles are intentional; only the extracted plan must be a DAG (finite-cost pruning already guarantees this). Strategies stay myopic; all graph mutation stays in driver/Applier.
- **Affected teams:** percolate engine + reactor plugin maintainers (single-maintainer project).

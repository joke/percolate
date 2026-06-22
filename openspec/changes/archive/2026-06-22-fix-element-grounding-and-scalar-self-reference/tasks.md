## 1. FU1 — SPI: neutral call-target on OperationSpec (additive)

- [x] 1.1 Add an optional call-target field (the `ExecutableElement` a method-call production invokes) to `OperationSpec`, defaulting to absent
- [x] 1.2 Add the factory entry point(s) that set the call target (`callOf(...)`), keeping existing `of`/`ofPartial`/`mapping` source-compatible (call target absent)
- [x] 1.3 Update the `OperationSpec` Javadoc: the field is a neutral structural fact ("this op calls this method"), not a self-call marker; interpretation is the driver's concern

## 2. FU1 — Builtins: record the call target

- [x] 2.1 `MethodCallBridge.buildSpec` populates the call target from the `MethodCandidate.getMethod()` it already holds (`OperationSpec.callOf`)
- [x] 2.2 Kept the `declaredChildren` early-return — it is NOT redundant: it enforces "method calls apply only to leaf demands", a constraint the driver rule does not subsume (comment refined). The self-call aspect is now handled at bind time.

## 3. FU1 — Engine: per-binding self-call refusal in the driver

- [x] 3.1 `SelfCallGuard.refuses(...)` (a cohesive collaborator) refuses a binding whose call target is the current `MethodScope`'s method (matched by signature) **and** whose bound argument is that scope's parameter-root `Value`; the driver calls it in `land`
- [x] 3.2 A self-call on a sub-part (ACCESS `Value`) or a container element (child scope) is not refused; delegation to a different method is not refused (verified by tests 4.2/4.4 + the `@PendingFeature` shows the sub-part call IS landed, only blocked downstream)
- [x] 3.3 Removed the old scope-wide `SelfCallGuard` `CallableMethods` view + `selfCallGuard.resolveCtxFor(value)` wiring; restored the base `ResolveCtx`
- [x] 3.4 No `CallableMethods` / `ResolveCtx` SPI change; no `label` inspection in the rule

## 4. FU1 — Tests

- [x] 4.1 Scalar self-reference positive — **delivered** (no longer `@PendingFeature`): the per-binding self-call rule lands the sub-part self-call, and relaxing `AccessorResolver`'s filter (§9.1) admits the recursive same-type accessor (`getNext():NodeDto`), so the mapper generates the terminating `next`-walk. Green hard positive in `ContainerReturnEndToEndSpec`.
- [x] 4.2 Regression: `CatMapper` container-element recursion stays green
- [x] 4.3 Regression: container-return `List<DAO> mapMany(Set<DTO>)` still emits `stream().map(this::mapOne).collect(...)`, never `this.mapMany(src)`
- [x] 4.4 Positive: delegation to a *different* method returning the same type is landed
- [x] 4.5 Negative: a mapper satisfiable only by the whole-parameter self-call reports a clean "no plan"

## 5. FU2 — reactor-blocking: blocking SourceProjections

- [x] 5.1 `FluxToStream` also implements `SourceProjection` (`Flux<X> → Stream<X>`); `@AutoService({ExpansionStrategy.class, SourceProjection.class})`
- [x] 5.2 `MonoBlockOptional` also implements `SourceProjection` (`Mono<X> → Optional<X>`, the **total** bridge); shared `Blockings.view(...)` helper
- [x] 5.3 Each `project` returns empty for an unrecognised/raw source; names only the two requested kinds; contributes only grounding candidates (no graph access)

## 6. FU2 — Tests

- [x] 6.1 Positive (param-direct): `List<DAO> map(Flux<DTO> src)` generates `src.toStream().map(this::mapOne).collect(...)`
- [x] 6.2 Positive: `Mono<DTO> → Optional<DAO>` element transform generates via the total `blockOptional` view
- [x] 6.3 Guard: no eager-block weight inversion (existing lazy-wins test stays green)
- [x] 6.4 Negative: with only `reactor` present, an upward crossing still reports "no producer" (existing `ReactorBoundaryNegativeSpec`, green in full check)

## 7. FU2 — bean-field case + diagnostics (gated on .dot)

- [x] 7.1 Added the bean-field repro `Flux<DTO> src.people → List<DAO>` — it **generates** (no `.dot` needed)
- [x] 7.2 Materialisation-order tweak **NOT needed**: the bean-field reactive source is already materialised before element-map grounding (caveat resolved, no engine change)
- [x] 7.3 Bean-field positive is green; param-direct also green

## 8. Verify & commit

- [x] 8.1 `./gradlew check` runs fully green (all tests + PMD + spotless + NullAway + error-prone + codenarc + jacoco)
- [x] 8.2 Commit the change with `/commit-commands:commit`

## 9. Follow-up surfaced during implementation (needs a scope decision)

- [x] 9.1 **Decided: relax now (scope extended), and more cleanly than first framed.** `AccessorResolver.resolveAccessor`'s same-type-output filter was the second engine constraint blocking 4.1. Rather than the narrow "admit same-type accessors" tweak, the `output != parentType` test was replaced with the principled `!port.isReuseOnly()` discriminator: the old test was only ever a *proxy* for "exclude the reuse-only identity/nullness specs (DirectAssign, requireNonNull/coalesce)", and the reuse-only test does that directly while correctly admitting a recursive same-type accessor. Javadoc rewritten to document the real intent; full `./gradlew check` green. See design D6.

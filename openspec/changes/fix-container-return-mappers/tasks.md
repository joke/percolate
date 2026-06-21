## 1. Seeded return-root identity (foundation for Bug A and Bug B)

- [x] 1.1 At `seedReturnRoot` (`ExpandStage`), record each seeded return-root `Value` on the graph (a `returnRoots()` set / `isReturnRoot(Value)` mark), keyed per `MethodScope` — distinct from over-emitted same-location intermediates
- [x] 1.2 Expose the recorded seeded roots from `MapperGraph` so graph-only consumers (`ExtractedPlan`) can read them without a `Types` dependency

## 2. Bug A — thread the seeded return-root identity through the three consumers

- [x] 2.1 `ExtractedPlan`: root extraction at the seeded return `Value`s, not the `isReturnRoot()` location filter (line ~38) — a same-location `Stream<E>`/`Set<E>` is an ordinary intermediate, never an independent root
- [x] 2.2 `RealisationDiagnosticsStage`: walk only the seeded return roots; assert dead typed siblings at the return location produce no `no plan for tgt[]`
- [x] 2.3 `BuildMethodBodies`: render each method body from its seeded return root (line ~71)
- [x] 2.4 Resolve the `TargetLocation.isReturnRoot()` open question — **retained** as the location predicate (still used by `SelfSeedExpansionSpec` + semantically meaningful); the graph's seeded-root set is the authority for the three consumers

## 3. Bug B — narrow self-bridge exclusion

- [x] 3.1 Driver builds a per-scope `ResolveCtx` whose `CallableMethods` view excludes the current `MethodScope`'s `ExecutableElement`, applied **only** when expanding that scope's seeded return root (memoize per `Scope`); no `CallableMethods`/`ResolveCtx` SPI change
- [x] 3.2 Verify cross-method delegation (a *different* same-return-type method) and container-element self-recursion stay available; strategies remain myopic. **Refined:** exclusion is method-scope-wide (not return-root-only) — the degenerate self-call also appears wrapped at a field (`List.of(this.m(param))`); legitimate recursion survives via the element *child* scope. Scalar self-referential fields now report "no plan" (a strict improvement over the prior silent infinite-recursion; arg-aware support is a follow-up)

## 4. Engine tests (Spock + Google Compile Testing)

- [x] 4.1 Builtin container-return positive: `List<DAO> mapMany(Set<DTO>)` + `DAO mapOne(DTO)` generates `src.stream().map(this::mapOne).collect(...)` and compiles (`ContainerReturnEndToEndSpec`)
- [x] 4.2 Self-bridge negative: the generated body for a container-return method is never `this.mapMany(...)`
- [x] 4.3 Self-recursion preserved: a self-similar mapper recurses through the container element (`mapCat` via `List<Cat> children`)
- [x] 4.4 No-spurious-no-plan: a reachable seeded root with unreachable same-location typed siblings emits zero diagnostics

## 5. `reactor-blocking` module

- [x] 5.1 Scaffold the `reactor-blocking` Gradle module: `build.gradle`, `settings.gradle` include, `reactor-core` pin, `implementation project(':spi')`, `@AutoService`, no engine dependency
- [x] 5.2 Implement the upward strategies `block` (`Mono<T>→T`, partial), `blockOptional` (`Mono<T>→Optional<T>`), `single().block` (`Flux<T>→T`, partial), `collectList().block` (`Flux<T>→List<T>`), `toStream` (`Flux<T>→Stream<T>`) — reuse-only ports, each weighted strictly above any non-blocking alternative; the lossy ones (`block`/`single().block`) are **partial** so totality prefers the empty-/element-preserving total bridges
- [x] 5.3 Resolved the `toStream` open question — **kept**: it is lazily-streaming and semantically distinct from `collectList().block()` + `iterate` (which fully buffers first), unlike the truly-redundant `fromIterable`/`fromCallable`

## 6. Reactor tests (new + the postponed `add-reactor-modules` suites)

- [x] 6.1 `reactor-blocking` positives per family (`block`, `blockOptional`, `single().block`, `collectList().block`, `toStream`) with both `reactor` + `reactor-blocking` on the classpath (`ReactorBlockingEndToEndSpec`)
- [x] 6.2 "No eager block" guard: for `Mono<DTO>` field → `Mono<DAO>` field the lazy `mono.map(f)` is selected over the eager `Mono.just(f(mono.block()))` — body has `.map(`, no `.block(`
- [x] 6.3 `reactor`-only upward negatives still report "no producer" (boundary-direction rule) without the blocking module — existing `ReactorBoundaryNegativeSpec` still green
- [x] 6.4 Reactive direct container-return positive: `Flux<DAO> map(Flux<DTO>)` + `PersonDAO mapOne(PersonDTO)` generates `flux.map(e -> mapOne(e))`, never `this.map(...)` (`ReactorContainerEndToEndSpec`)

## 7. Verify

- [x] 7.1 Re-ran the `percolate-integration` project — `mapAddresses` now generates `address.stream().map(address_ -> this.mapAddress(address_)).collect(Collectors.toList())`, no `no plan` errors, `mapHuman`/`mapAddress` unchanged
- [x] 7.2 `./gradlew spotlessApply` then `./gradlew check` → BUILD SUCCESSFUL across all modules (reactor + reactor-blocking green; PMD/errorprone/NullAway/codenarc all pass)
- [ ] 7.3 Commit the completed change with `/commit-commands:commit` — pending user confirmation

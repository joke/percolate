## 1. SPI handle family + container bases (regression-safe rails)

- [x] 1.1 Add the `Codegen` marker interface in `spi`; make `EdgeCodegen extends Codegen`.
- [x] 1.2 Add `ContainerCodegen extends Codegen` (`iterate`/`mapElements`/`flatMapElements`/`collect`) and `WrapperCodegen extends ContainerCodegen` (`mapPresence`/`wrap`/`unwrap(wrapper, Nullability)`) in `spi`.
- [x] 1.3 Add abstract bases `SequenceContainer implements Bridge, ContainerCodegen` and `WrapperContainer implements Bridge, WrapperCodegen` in `spi`: `matches`/`element` abstract; base `bridge(from,to,ctx)` derives collect/iterate/wrap/unwrap `BridgeStep`s from them, attaching `this` as provider; `streamCodegen()` returns `this`, `loopCodegen()` defaults empty.
- [x] 1.4 Widen `BridgeStep.codegen` from `EdgeCodegen` to the `Codegen` family; keep existing scalar callers compiling (they pass an `EdgeCodegen`, still a `Codegen`).
- [x] 1.5 Add `ScopeTransition` (default `PRESERVING`) to `Edge`; add the container `Edge.realised(from,to,weight,Codegen provider,ScopeTransition,fqn)` factory; widen `Edge.codegen` to `Optional<Codegen>`; exclude `scopeTransition` from equality. Keep the scalar `Edge.realised(...,EdgeCodegen,...)` factory (PRESERVING).
- [x] 1.6 Thread `step.getScopeTransition()` onto the realised edge in `FrontierMatcher` (pure data carry — no codegen logic in the driver).
- [x] 1.7 Build `:spi` + `:processor`; run `:processor:test`/`:processor:integrationTest` — scalar/constructor output unchanged, tree green.

## 2. Built-in containers on the bases (delete per-op bridges)

- [x] 2.1 Implement `ListContainer`/`SetContainer`/`ArrayContainer extends SequenceContainer` and `OptionalContainer extends WrapperContainer` in `strategies-builtin`, supplying `matches`/`element` and the snippet methods (List/Set share a parameterised impl differing by collector + `of`-factory; array uses `Arrays.stream`/`toArray`; Optional uses `map`/`ofNullable`/`orElse`/`orElseThrow`).
- [x] 2.2 Delete the 9 per-op bridges (`ListCollect`/`ListWrap`/`SetCollect`/`SetWrap`/`OptionalCollect`/`OptionalWrap`/`OptionalUnwrap`/`ArrayCollect`/`IterableUnwrap`); update `@AutoService` registrations.
- [x] 2.3 Confirm the realised/transforms graph shape is unchanged (same element-scope hops) for the container fixtures; expansion candidacy regression-clean.
- [x] 2.4 Update `builtin-strategy-unit-tests` / container-expansion specs' strategy-class tests to the consolidated containers.

## 3. Composer container weaving

- [x] 3.1 Make `render(node)` in `BuildMethodBodies` return `(CodeBlock, isStream)`; thread the bit up the recursion. Scalar/group cases keep identical output.
- [x] 3.2 Add the container single-edge case: derive the operation from `(scopeTransition, isStream, handle-kind)` and weave per the rules — `ENTERING`-seq+not-stream→`iterate`; `ENTERING`-wrapper+in-stream→`flatMapElements(child,v,iterate(v))` (FilterPresent); `ENTERING`-wrapper+not-stream→`unwrap`/`mapPresence`; `PRESERVING`-map+in-stream→`mapElements`; `EXITING`→`collect`. All snippets from the edge's provider; zero container syntax in the composer.
- [x] 3.3 Wire `unwrap`'s empty-policy to the consumer nullability contract (`orElseThrow` non-null / `orElse(null)` `@Nullable`), reusing the existing nullability resolution.
- [x] 3.4 Unit-test the composer over hand-built `PlanView` shapes: top-level wrapper, sequence, wrapper-in-sequence (FilterPresent), `@Nullable List<@Nullable X>`, `@Nullable Optional<@Nullable X>`.

## 4. Verification

- [x] 4.1 Run full `:processor` + `:spi` + `:strategies-builtin` tests green; `EndToEndCodegenSpec`/`BuildMethodBodiesSpec` scalar/constructor output identical.
- [x] 4.2 Run `./gradlew :mappers:classes` in `percolate-integration`; record `mapHuman` outcome (`Optional.ofNullable(... .flatMap(o -> o.stream()).map(a -> mapAddr(a)).collect(toSet()))`). Capture any remaining container corner as a follow-up rather than expanding scope.

## Implementation notes (deviations + recorded outcomes)

- **Single-element `wrap` is a `PRESERVING` `EdgeCodegen` step, not a container-provider step** (open question settled). `ContainerCodegen` has no `wrap` method, so the base attaches the `List.of`/`Set.of`/`ofNullable` snippet as a scalar `EdgeCodegen`; the composer renders it via the existing scalar case (byte-identical to the old per-op wrap bridges). Container providers ride only on iterate/collect/unwrap edges.
- **`WrapperContainer` unwrap candidacy is keyed on the scalar `to`, not `matches(from)`** (deviates from the spec's "when `matches(from)`… ENTERING" sentence, which is correct only for sequences). A wrapper must *synthesise* `Optional<to>` as the unwrap input so a wrapped source can be reached before any wrapper node exists (matching the old `OptionalUnwrap`). Added `WrapperContainer.wrapped(element, ctx)` for this. Sequences keep `matches(from)` (iterate an existing source). Spec text for the base should be updated to record this sequence/wrapper asymmetry.
- **Driver fan-out fix (`FrontierMatcher`):** a single consolidated container `Bridge` emits several `BridgeStep`s; the matcher now emits one sibling bundle **per matching step** (it previously broke after the first), restoring the multi-fire behaviour the nine separate per-op bridges had. Without this the wrap/collect siblings were lost and container plans went UNSAT.
- **`TypeUniverse` test-fixture hardening:** eagerly force the `List/Set → Iterable` assignability fill in the static block so `isIterable`/`isCollection` are deterministic regardless of which spec touches javac's symbol table first (pre-existing JDK 25 re-entrancy fragility surfaced by the new classes shifting load order).
- **`mapHuman` recorded outcome (FOLLOW-UP, not fixed here):** codegen no longer crashes and now produces a complete body, but plan selection currently prefers a *degenerate, type-incorrect* sibling — `new Human(..., Set.of(this.mapAddress(person.getAddresses().stream().flatMap(v0 -> v0.stream()))).findFirst())` — assigning `collect`/`wrap` to the wrong containers (Set→wrap, Optional→collect) and applying `mapAddress` to the stream instead of per element. The composer weaving itself is correct (proven by `ContainerWeavingSpec`); the gap is the **Wrap-vs-Collect cardinality** disambiguation in plan selection, an already-open expansion concern (see `project_plan_view_codegen`). Out of scope for this change per the change's own non-goals; tracked as a follow-up expansion change.

## 1. SPI handle family + container bases (regression-safe rails)

- [ ] 1.1 Add the `Codegen` marker interface in `spi`; make `EdgeCodegen extends Codegen`.
- [ ] 1.2 Add `ContainerCodegen extends Codegen` (`iterate`/`mapElements`/`flatMapElements`/`collect`) and `WrapperCodegen extends ContainerCodegen` (`mapPresence`/`wrap`/`unwrap(wrapper, Nullability)`) in `spi`.
- [ ] 1.3 Add abstract bases `SequenceContainer implements Bridge, ContainerCodegen` and `WrapperContainer implements Bridge, WrapperCodegen` in `spi`: `matches`/`element` abstract; base `bridge(from,to,ctx)` derives collect/iterate/wrap/unwrap `BridgeStep`s from them, attaching `this` as provider; `streamCodegen()` returns `this`, `loopCodegen()` defaults empty.
- [ ] 1.4 Widen `BridgeStep.codegen` from `EdgeCodegen` to the `Codegen` family; keep existing scalar callers compiling (they pass an `EdgeCodegen`, still a `Codegen`).
- [ ] 1.5 Add `ScopeTransition` (default `PRESERVING`) to `Edge`; add the container `Edge.realised(from,to,weight,Codegen provider,ScopeTransition,fqn)` factory; widen `Edge.codegen` to `Optional<Codegen>`; exclude `scopeTransition` from equality. Keep the scalar `Edge.realised(...,EdgeCodegen,...)` factory (PRESERVING).
- [ ] 1.6 Thread `step.getScopeTransition()` onto the realised edge in `FrontierMatcher` (pure data carry — no codegen logic in the driver).
- [ ] 1.7 Build `:spi` + `:processor`; run `:processor:test`/`:processor:integrationTest` — scalar/constructor output unchanged, tree green.

## 2. Built-in containers on the bases (delete per-op bridges)

- [ ] 2.1 Implement `ListContainer`/`SetContainer`/`ArrayContainer extends SequenceContainer` and `OptionalContainer extends WrapperContainer` in `strategies-builtin`, supplying `matches`/`element` and the snippet methods (List/Set share a parameterised impl differing by collector + `of`-factory; array uses `Arrays.stream`/`toArray`; Optional uses `map`/`ofNullable`/`orElse`/`orElseThrow`).
- [ ] 2.2 Delete the 9 per-op bridges (`ListCollect`/`ListWrap`/`SetCollect`/`SetWrap`/`OptionalCollect`/`OptionalWrap`/`OptionalUnwrap`/`ArrayCollect`/`IterableUnwrap`); update `@AutoService` registrations.
- [ ] 2.3 Confirm the realised/transforms graph shape is unchanged (same element-scope hops) for the container fixtures; expansion candidacy regression-clean.
- [ ] 2.4 Update `builtin-strategy-unit-tests` / container-expansion specs' strategy-class tests to the consolidated containers.

## 3. Composer container weaving

- [ ] 3.1 Make `render(node)` in `BuildMethodBodies` return `(CodeBlock, isStream)`; thread the bit up the recursion. Scalar/group cases keep identical output.
- [ ] 3.2 Add the container single-edge case: derive the operation from `(scopeTransition, isStream, handle-kind)` and weave per the rules — `ENTERING`-seq+not-stream→`iterate`; `ENTERING`-wrapper+in-stream→`flatMapElements(child,v,iterate(v))` (FilterPresent); `ENTERING`-wrapper+not-stream→`unwrap`/`mapPresence`; `PRESERVING`-map+in-stream→`mapElements`; `EXITING`→`collect`. All snippets from the edge's provider; zero container syntax in the composer.
- [ ] 3.3 Wire `unwrap`'s empty-policy to the consumer nullability contract (`orElseThrow` non-null / `orElse(null)` `@Nullable`), reusing the existing nullability resolution.
- [ ] 3.4 Unit-test the composer over hand-built `PlanView` shapes: top-level wrapper, sequence, wrapper-in-sequence (FilterPresent), `@Nullable List<@Nullable X>`, `@Nullable Optional<@Nullable X>`.

## 4. Verification

- [ ] 4.1 Run full `:processor` + `:spi` + `:strategies-builtin` tests green; `EndToEndCodegenSpec`/`BuildMethodBodiesSpec` scalar/constructor output identical.
- [ ] 4.2 Run `./gradlew :mappers:classes` in `percolate-integration`; record `mapHuman` outcome (`Optional.ofNullable(... .flatMap(o -> o.stream()).map(a -> mapAddr(a)).collect(toSet()))`). Capture any remaining container corner as a follow-up rather than expanding scope.

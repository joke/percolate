## 1. Input-declaration model (graph-model)

- [x] 1.1 Add an internal `InputDecl` type to `processor.graph`: an immutable scope-relative `(Location location, TypeMirror type, Nullability nullness)` — an `AddValue` lacking only its scope. Processor-internal (the SPI never sees it).
- [x] 1.2 Add `inputDecls(...)` to the `Scope` interface as a `default` returning `Stream.empty()` — so `MapperScope` and the test `HarnessScope` need no change. Nullness is threaded as a JDK `BiFunction<TypeMirror, Element, Nullability>` to keep the `graph` package free of `processor.nullability` (graph-model: MODIFIED "Scope interface and cases").
- [x] 1.3 Implement `MethodScope.inputDecls()` — one decl per parameter: `(SourceLocation(paramName), paramType, nullness.apply(paramType, param))`, nullness via the threaded `BiFunction` (the caller passes `resolver::resolve`).
- [x] 1.4 Implement `ChildScope.inputDecls()` — its single element input decl `(ElementLocation, elementIn, elementInNullness)`, stored on `ChildScope` so the decl exists without minting a `Value`; ignores the nullness fn (element nullness already known).
- [x] 1.5 `MapperScope.inputDecls()` → empty: inherited from the `Scope` default (no override needed).

## 2. Driver consumes declarations uniformly (graph-expansion, source-path-resolution)

- [x] 2.1 Rewrote `SourceCandidates.sourceTypes(scope)` to union the declared input types (`scope.inputDecls(resolver::resolve).map(InputDecl::getType)`) with already-materialised in-scope sources — deleted the `instanceof MethodScope` `paramTypes` branch.
- [x] 2.2 Rewrote `SourceCandidates.matchingSource(scope, port)` to fall back from an existing in-scope source to the first matching `InputDecl`, materialised via `applier.apply(new AddValue(scope, decl…))` — deleted the `instanceof MethodScope` `matchingParam` branch (idempotent through `valueFor`).
- [x] 2.3 Rewrote `AccessorResolver` root-segment typing (`inputTyping`) to resolve against `scope.inputDecls(...)` by `Location.slotName()` — deleted its `instanceof MethodScope` branch.
- [x] 2.4 Confirmed `SelfCallGuard` keeps its `instanceof MethodScope` test unchanged (the one intentional scope-type branch).

## 3. Split lambda binding from source Value (code-generation)

- [x] 3.1 Changed `HoistPlan.lambdaName(...)` to take the element `TypeMirror` (from the child input decl) instead of the materialised param-root `Value`; same base-name + `element` fallback rules.
- [x] 3.2 `BuildMethodBodies.renderContainerMapping` now names the lambda from `child.getElementInput().getType()` and binds the lambda var into `lambdaVars` only when the element root was actually materialised (`materialisedElementRoot`); `ScopeCodegen.weave` signature unchanged.

## 4. Relax eager child minting (graph-model)

- [x] 4.1 `MapperGraph.initChildScope` (was `mintChildRoots`) now mints only the **return-root** `Value` eagerly and records the element `InputDecl`; the element param-root is no longer force-minted (materialised lazily via §2 only when a port reuses it).

## 5. Audit consumers + regression

- [x] 5.1 Audited: `getParamRoot()` had exactly one consumer (`BuildMethodBodies`, rewired in 3.2); `valuesIn(scope)` readers (`ValidateConstantDefaultLegalityStage`, dump output, plan/walk) iterate what is present and tolerate the lazy absence — confirmed by the green suite.
- [x] 5.2 Unused method parameter: already covered by the existing `SelfSeedExpansionSpec` ("…never materialised", still green). Unused element: covered at unit level by the updated `BipartiteGraphSpec` ("no element param-root Value until a port reuses it"). NOTE: a realisable element transform always sources its element (the element is the child plan's only input), so a higher-level "unused element" mapper is not directive-expressible — the unit assertion is the right level.
- [x] 5.3 Codegen: for realisable mappers the element is always materialised, so the lambda binding still fires — covered by the green container-codegen regression. The defensive "name from decl even with no Value" path is covered by 3.2's `materialisedElementRoot` Optional + the 5.2 unit assertion. A standalone constant-element fixture is not realisable, so none was fabricated.
- [x] 5.4 Regression: full `:processor:test` green (84/84) — recursive `Tree`/`mapCat` and whole-parameter self-call scenarios behave identically (`SelfCallGuard` untouched).
- [x] 5.5 No `.dot` snapshot drift: full `check` (incl. `graph-debug-output`) is green — a realisable mapper still materialises its element, so the param-root still appears in dumps; no snapshots needed updating.

## 6. Verify, sync, archive

- [x] 6.1 `./gradlew check` in `percolate` is green (generated output byte-identical for all realisable mappers — the no-regression guard).
- [x] 6.2 `./gradlew :mappers:classes` green in `percolate-integration` (composite `includeBuild`, so built against the local processor changes).
- [ ] 6.3 `/opsx:sync unify-scope-inputs` — apply the four delta specs to main; `/opsx:archive`; commit via `/commit-commands:commit`.

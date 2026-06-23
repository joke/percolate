## 1. Input-declaration model (graph-model)

- [ ] 1.1 Add an internal `InputDecl` type to `processor.graph` (or `stages.expand`): an immutable scope-relative `(Location location, TypeMirror type, Nullability nullness)` — an `AddValue` lacking only its scope. Keep it processor-internal (the SPI never sees it).
- [ ] 1.2 Add `Stream<InputDecl> inputDecls()` to the `Scope` interface (graph-model: MODIFIED "Scope interface and cases").
- [ ] 1.3 Implement `MethodScope.inputDecls()` — one decl per parameter: `(SourceLocation(paramName), paramType, resolved nullness)`. The nullness source must match today's `matchingParam` resolution (`NullabilityResolver`), so thread or inject what it needs.
- [ ] 1.4 Implement `ChildScope.inputDecls()` — its single element input decl `(ElementLocation(name), elementIn, elementInNullness)`; retain the element-in type/nullness on `ChildScope` (from `ChildScopeSpec`/`ChildScopeDecl`) so the decl exists without minting a `Value`.
- [ ] 1.5 Implement `MapperScope.inputDecls()` → empty.

## 2. Driver consumes declarations uniformly (graph-expansion, source-path-resolution)

- [ ] 2.1 Rewrite `SourceCandidates.sourceTypes(scope)` to union the declared input types (`scope.inputDecls().map(type)`) with already-materialised in-scope sources — deleting the `instanceof MethodScope` `paramTypes` branch (graph-expansion: ADDED "Scopes declare base-case inputs uniformly", grounding scenario).
- [ ] 2.2 Rewrite `SourceCandidates.matchingSource(scope, port)` to fall back from an existing in-scope source to the first matching `InputDecl`, materialised via `applier.apply(new AddValue(scope, decl…))` — deleting the `instanceof MethodScope` `matchingParam` branch. Confirm idempotency through the `valueFor` dedup index.
- [ ] 2.3 Rewrite `AccessorResolver` root-segment typing to resolve against `scope.inputDecls()` — deleting its `instanceof MethodScope` branch (source-path-resolution: MODIFIED "Input-root base case…", accessor-root scenario).
- [ ] 2.4 Confirm `SelfCallGuard` keeps its `instanceof MethodScope` test unchanged (it is the one intentional scope-type branch; graph-expansion "A method never calls itself on its own whole parameter").

## 3. Split lambda binding from source Value (code-generation)

- [ ] 3.1 Change `HoistPlan.lambdaName(...)` to take the child scope's element `InputDecl` (its `Location.slotName()` + type) instead of the materialised param-root `Value`; allocate the unique name from that, preserving the existing base-name + fallback (`element`) rules.
- [ ] 3.2 Update where `BuildMethodBodies` / the walk pass `var` into `ScopeCodegen.weave(operand, var, body)` so the lambda parameter name comes from the declaration; keep `ScopeCodegen`'s signature unchanged (code-generation: MODIFIED "Child scopes render as lambda bodies").

## 4. Relax eager child minting (graph-model)

- [ ] 4.1 In `MapperGraph.mintChildRoots` (or the `ExpandStage` landing of a scope-owning Operation), stop force-minting the element **param-root** `Value`; keep minting the **return-root** eagerly (it is the `FREE` demand enqueued for the child plan). Materialise the element input lazily via §2 only when a port reuses it (graph-model: MODIFIED "Scope tree and child-scope ownership").

## 5. Audit consumers + regression

- [ ] 5.1 Audit every reader of `graph.valuesIn(scope)` / the child param-root (`ValidateConstantDefaultLegalityStage`, debug-dump output, any plan/walk code) for an assumption that the element param-root is always materialised; confirm each tolerates its lazy absence (each iterates what is present).
- [ ] 5.2 Add/adjust expansion-harness specs: an unused method parameter and an unused container element both leave **no** param-root `Value` in the graph (graph-expansion scenarios "An unused parameter…", "An unused element root…").
- [ ] 5.3 Add a codegen spec: a constant-mapped element still emits `stream.map(element -> <constant>)` with the bound parameter, no materialised element `Value` (code-generation "An unused element still binds its lambda parameter").
- [ ] 5.4 Regression: recursive `Tree` / `mapCat` and the whole-parameter self-call scenarios behave identically (SelfCallGuard unaffected by the unification).
- [ ] 5.5 Refresh any golden `.dot` snapshots whose only diff is a removed orphan element param-root LEAF (graph-debug-output).

## 6. Verify, sync, archive

- [ ] 6.1 `./gradlew check` in `percolate` is green (generated output byte-identical for all realisable mappers — the no-regression guard).
- [ ] 6.2 `./gradlew :mappers:classes` green in `percolate-integration`.
- [ ] 6.3 `/opsx:sync unify-scope-inputs` — apply the four delta specs to main; `/opsx:archive`; commit via `/commit-commands:commit`.

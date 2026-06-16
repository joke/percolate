## 1. Preparation

- [ ] 1.1 Load the coding-convention skills (java + java11; groovy/spock for tests) before writing any code
- [ ] 1.2 Confirm the Spock/jqwik suite is green at HEAD (`./gradlew check`, do not pipe to tail) as the behavioural baseline; skim `design.md` decisions D1–D6

## 2. SPI surface (percolate-spi)

- [ ] 2.1 Add a required `label` (String) to `OperationSpec`; thread it through `of`, `ofPartial`, and `mapping` factories (label first arg) and the `@Value` field
- [ ] 2.2 Change `OperationCodegen.render(VarNames, IncomingValues)` to `render(IncomingValues)`
- [ ] 2.3 Delete the `VarNames` type; delete the dead `LoopContainerCodegen` type
- [ ] 2.4 Confirm the `Codegen` marker + `OperationCodegen`/`ScopeCodegen` split is otherwise unchanged

## 3. Built-in strategies (percolate-strategies-builtin)

- [ ] 3.1 Supply a fully-typed `label` at every `OperationSpec.of/ofPartial/mapping` call site: `DirectAssign`→`assign`, `ConstructorCall`→`new <Type>(<paramTypes>)`, `WidenPrimitive`→`<from>→<to>` (glyph `→`), `PrimitiveWrapperConversion`→`<from>→<to>`, `ConstantValue`→the literal, `StreamMap`→`map`/`flatMap`, container ops (`Container` base)→`collect`/`wrap`/`unwrap`/`iterate`, `MethodCallBridge`→`<method>(…)`, `NullnessCrossing`→`requireNonNull`/`coalesce`
- [ ] 3.2 Drop the `VarNames` parameter from every `render` lambda (`(vars, inputs) ->` → `inputs ->`), including the `Container` base wrapping
- [ ] 3.3 `./gradlew :strategies-builtin:compileJava :spi:compileJava` clean (no `VarNames` references remain)

## 4. Processor graph + driver

- [ ] 4.1 Drop `strategyFqn` from `Operation`, `AddOperation`, and `MapperGraph.apply` (the `AddOperation`→`Operation` landing)
- [ ] 4.2 In `ExpandStage`, set the landed Operation's `label` from `spec.getLabel()` (not `spec.getCodegen().getClass()`); stop passing any `strategyFqn`
- [ ] 4.3 In `ExpandStage.expandAccess`, set the accessor Operation's label to its access form (e.g. `getStreet()` / `.street` / `street()`) instead of the literal `"accessor"`, sourced from the resolved accessor
- [ ] 4.4 Update `BuildMethodBodies` render call sites to `render(IncomingValues)` (drop the `VarNamesImpl` argument and any `VarNames` import/impl)

## 5. DotRenderer — readable value labels + unreachable dimming

- [ ] 5.1 Replace `DotRenderer.valueLabel`'s `TypeMirror.toString()` with a recursive `TypeMirror` walk producing simple names + per-level JSpecify nullness suffix `?`/`!` (outer from the Value's `nullness`, nested from each type-argument's annotation mirrors); fall back to the bare simple name for exotic kinds (wildcard/typevar/intersection) without throwing
- [ ] 5.2 Confirm the operation box label already reads `operation.getLabel()` (it does) so it now shows the typed production; no further renderer change for operation labels
- [ ] 5.3 Add unreachable **dimming** to `DotRenderer.render`: accept a reachability predicate (or dimmed-vertex set) and apply grey fill / dashed outline to unreachable vertices instead of filtering them; reachable vertices render normally

## 6. Full dump passes reachability

- [ ] 6.1 In `DumpFullGraphStage`, extract the plan (as `DumpTransforms` does) and pass the `reachable` predicate into the renderer so the full dump dims unreachable vertices; keep `transforms`/`plan` dumps unchanged

## 7. Specs / tests

- [ ] 7.1 `DotRenderer` spec: a value label renders simple names + `?`/`!` (e.g. `Optional<Set<Address?>>!`), with no package qualifiers or inline annotation FQNs; an operation box shows the typed `label` (e.g. `int→long (1)`), never a `$$Lambda` name
- [ ] 7.2 `DotRenderer` spec: unreachable vertices are dimmed (grey/dashed) while reachable ones are not, in a graph with pruned over-emission
- [ ] 7.3 SPI/strategy spec: `OperationSpec` carries a typed `label`; `OperationCodegen.render` takes only `IncomingValues`; the `spi` package declares no `VarNames` and no `LoopContainerCodegen`
- [ ] 7.4 Graph spec: a landed `Operation` exposes the spec's `label` and no `strategyFqn`
- [ ] 7.5 Update any existing spec that asserted on `render(VarNames, …)`, `strategyFqn`, or codegen-class labels

## 8. Verification & wrap-up

- [ ] 8.1 Grep for dangling `VarNames`, `LoopContainerCodegen`, `strategyFqn`, and `getClass().getSimpleName()`/`getClass().getName()` label sourcing across main + tests; remove leftovers
- [ ] 8.2 Regenerate the integration project's `.dot` files and eyeball `mapAddress.full.dot` / `mapHuman.full.dot`: typed operation labels, `?`/`!` value types, dimmed pruned candidates
- [ ] 8.3 Run `openspec validate readable-graph-dumps --strict` and confirm it still validates
- [ ] 8.4 Run `./gradlew check` (do not pipe to tail) and confirm zero violations — NEVER continue if there are violations
- [ ] 8.5 Commit the completed work with `/commit-commands:commit`

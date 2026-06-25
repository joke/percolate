## 1. SPI contract: produce vs descend (design D3)

- [x] 1.1 Add `ProduceDemand` to `percolate-spi`: the existing produce-side fields (`targetType`, `targetNullness`, `directive`, `declaredChildren`, `bindingName`, `nullnessOf`) — the produce shape of `Demand`.
- [x] 1.2 Add `DescendDemand` to `percolate-spi`: a concrete `parentType` + `parentNullness` + single `segment` + `nullnessOf` oracle; no `targetType()` pun.
- [x] 1.3 Change `ExpansionStrategy` to the two-method form: `default Stream<OperationSpec> expand(ProduceDemand, ResolveCtx)` + `default Stream<OperationSpec> descend(DescendDemand, ResolveCtx)`, both default-empty; keep `priority()`.
- [x] 1.4 Update the `Demand decision context` types so neither shape exposes candidates/graph; delete the stale "candidate snapshot" wording carried on the old `expand(Demand)` doc.
- [x] 1.5 Reshape the `Accessor` archetype base to implement `descend(DescendDemand)`: read `parentType()` + `segment()` (not a punned `targetType`), wire the one-port accessor `OperationSpec` with the discovered output type, leave `expand` defaulted.
- [x] 1.6 `./gradlew :percolate-spi:compileJava` green; `percolate-spi` still depends only on JDK + javapoet.

## 2. Migrate strategies to the new surface (design D3, Migration)

- [x] 2.1 Repoint producer built-ins to `expand(ProduceDemand)`: `ConstructorCall`, `DirectAssign`, `ConstantValue`, `NullnessCrossing`, `StreamMap`, `Container`/conversion strategies.
- [x] 2.2 Confirm `GetterPathResolver` / `MethodPathResolver` / `FieldPathResolver` need no change beyond the base (matching/weight/codegen unchanged); they now flow through `descend`.
- [x] 2.3 Repoint reactor + reactor-blocking strategies (`FluxMap`, `CollectList`, `FluxFromStream`, `SingleOptional`, `JustOrEmpty`, `MonoBlock*`, `Flux*Block`, `FluxToStream`, …) to `expand(ProduceDemand)`.
- [x] 2.4 `./gradlew :percolate-strategies-builtin:compileJava :percolate-reactor:compileJava :percolate-reactor-blocking:compileJava` green.

## 3. Driver: forward target-bound descent (design D1, D2)

- [x] 3.1 Materialise a directive's source path by **inline forward descent** at the `pinnedSource` site (no separate work-item kind), rooted at the scope input.
- [x] 3.2 Implement `descendSegment`: build a `DescendDemand` from the landed parent `Value`'s concrete type + the next segment, run the strategy set via `descend`, dedup, land each accessor through the `Applier`, advance to the child `Value`.
- [x] 3.3 Replace `findFirst` accessor selection with over-emit: land every matching accessor into the deduped child `Value`; let plan extraction prune by weight (design D4).
- [x] 3.4 Remove the backward `expandAccess` path and the `ACCESS` Value-demand mode; a multi-segment source `Value` is produced by descent, never demanded.

## 4. Descent seeding and directive-preference (design D8)

- [x] 4.1 Descend a directive's path forward at `pinnedSource`, which already runs in `expandFree` before the target's port binds — so the descended leaf is the in-scope source the target binds to (inline-lazy, not eager per-scope seeding).
- [x] 4.2 Bind a directive-bound target port to the `Value` at the directive's source **location** (location identity), not merely any same-typed in-scope source.
- [x] 4.3 Verify the descent root reads from `Scope.inputDecls` for both method and child scopes with no scope-kind branch, and that a child-scope root is concrete at scope birth (design D5).

## 5. Dissolve AccessorResolver (design D6)

- [x] 5.1 Delete `AccessorResolver` (typing memo + `resolveAccessor`); replace `typing()` callers with land-time lookups off the parent `Value`.
- [x] 5.2 Confirm `SourceCandidates` is untouched (still owns graph binding/materialisation; `sourceTypes()` still feeds grounding).
- [x] 5.3 Confirm the driver is now the **sole** strategy invoker — grep the processor for any `strategy.expand`/`strategy.descend` call outside the driver dispatch.

## 6. Tests

- [x] 6.1 Deep (3+ segment) source path end-to-end: `p.a.b.c` lands accessors parent-first, each typed off its landed parent (currently uncovered).
- [x] 6.2 Getter-vs-field ambiguity: a segment with both `getX()` and public field `x` selects `getX()` by weight, deterministically (independent of `ServiceLoader` order).
- [x] 6.3 Directive-preference: two targets from same-typed sources distinguished only by path (`a.foo` vs `a.bar`) each bind to their own source location.
- [x] 6.4 Child-scope descent root concrete at birth: a container element transform whose element type is grounded then descends a source path from the element root.
- [x] 6.5 `never_forward` regression: assert no speculative source sweep occurs — only named directive segments are descended (reviewed against `feedback_never_forward_expansion`).
- [x] 6.6 All existing `source-path-resolution` and `graph-expansion` scenarios pass (semantically equivalent generated code; ambiguous segments now deterministically prefer the getter).

## 7. Sync, verify, quality

- [x] 7.1 `./gradlew check` green across all modules.
- [x] 7.2 Run `/opsx:sync` to fold the delta specs into `openspec/specs/` (`graph-expansion`, `source-path-resolution`, `expansion-strategy-spi`).
- [ ] 7.3 `/opsx:verify` the implementation against the artifacts; `openspec validate --strict` clean.
- [ ] 7.4 `/code-review` then `/simplify` the diff; confirm no second strategy-invocation site and no scope-kind branch crept back in.

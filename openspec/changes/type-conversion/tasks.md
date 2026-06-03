## 1. Engine — conversion synthesis + reachability SAT (graph-expansion; see design E1–E4)

- [x] 1.1 Add `RegisterConversionFrontier(group, node)` delta: new `Delta` impl + `Delta.Visitor.visitRegisterConversionFrontier`; implement in `Applier` (`group.addVertexToView(node); group.addConversionFrontier(node)`) and `CycleProbe` (no-op). (E2/E4)
- [x] 1.2 `ExpansionGroup`: add a mutable `conversionFrontiers` set with `addConversionFrontier(Node)` and `getConversionFrontiers()`. (E2)
- [x] 1.3 `FrontierMatcher.convertBundle`: reuse-**or-synthesize**. On `findInViewByType` miss, synthesize `new Node(Optional.of(inputType), frontier.getLoc(), frontier.getScope(), frontier.getParent())`, emit `AddNode(node, frontier.directive)` → `RegisterConversionFrontier` → `AddEdge(node→frontier)` → `AddEdgeToView` → `TypeNode(frontier, output)` if untyped. Change `findInViewByType` to exclude only the frontier (no longer exclude `TargetLocation`). Keep the reuse path otherwise unchanged. (E1)
- [x] 1.4 Strategies fire target-driven: keep box/widen as plain `ExpansionStrategy.expand` (not `CombinatorialMatch`) — see tasks 2/3. (E4)
- [x] 1.5 `SlotResolver`: replace `producedInView` with `reachable(node, group, snapshot)` (base-case OR `hasSatChildAt` OR incoming view REALISED edge from a `reachable` source; DFS + visited set). `resolve` = "if `reachable` → satisfied, else expand". Update the other call site `DirectiveBindingExpander` (`reachable(root)`). (E3; graph-expansion: *Conversion-chain satisfaction is base-case reachability*)
- [x] 1.6 Expand conversion frontiers: each of `BridgeExpander`, `DirectiveBindingExpander`, `AssemblyExpander` runs `resolve` over `group.getConversionFrontiers()` (expand, ignore the boolean — not added to `pendingSlots`). Confirm stop-at-SAT/loop unchanged; box∘unbox closes a cycle the `Applier` rejects. (E2; graph-expansion: *Conversion folding makes round-trips structural cycles*, *…stops at SAT*)
- [x] 1.7 Engine tests (expansion-test-harness): (a) synthesis resolves an absent-intermediate chain; (b) a chain SATs only when complete (no premature SAT); (c) a dead-end synthesized node does not block group SAT; (d) box∘unbox rejected as a cycle.

## 2. `PrimitiveWrapperConversion` strategy (boxing + unboxing)

- [x] 2.1 Add `PrimitiveWrapperConversion` under `strategies-builtin/.../spi/builtins/`, `@AutoService(ExpansionStrategy.class)`, single-hop target-to-source `CONVERSION`: target wrapper `W` ⇒ input `ctx.types().unboxedType(W)`, output `W`, codegen `W.valueOf($L)`; target primitive `p` with a wrapper ⇒ input `ctx.types().boxedClass(p).asType()`, output `p`, codegen `$L.<p>Value()`; else empty. Weight `Weights.STEP`. (type-conversion: *PrimitiveWrapperConversion built-in*)
- [x] 2.2 Add `PrimitiveWrapperConversionSpec.groovy` (`extend spock.lang.Specification`, `@spock.lang.Tag('unit')`, `ResolveCtxBuilder` + `TypeUniverse`, metadata-only): boxing (`Integer` ⇒ `CONVERSION`, input `int`, `STEP`), unboxing (`int` ⇒ input `Integer`), empty precondition. (builtin-strategy-unit-tests)

## 3. `WidenPrimitive` strategy (JLS 5.1.2 widening)

- [x] 3.1 Add `WidenPrimitive`, `@AutoService(ExpansionStrategy.class)`, single-hop target-to-source `CONVERSION`: static JLS 5.1.2 consumes-lattice `Map<TypeKind, Set<TypeKind>>` (target ⇒ narrower sources); target primitive `p` ⇒ one `CONVERSION` step per narrower source `q` (input `q`, output `p`, codegen `(p) $L`, `STEP`); `boolean` absent; `char` source-only; include IEEE legs (`int→float`, `long→float`, `long→double`). (type-conversion: *WidenPrimitive built-in*)
- [x] 3.2 Add `WidenPrimitiveSpec.groovy` (same substrate/tagging/metadata-only): widening set for a numeric target, one IEEE leg (`long→double`), `boolean`-target empty, narrowing empty. (builtin-strategy-unit-tests)

## 4. Composition & integration (Google Compile Testing)

- [x] 4.1 Single-hop mapper compile tests resolve: `int→Integer`, `Integer→int`, `int→long`.
- [x] 4.2 Cross-product compile tests resolve via engine-composed chains: `int→Long`, `Integer→long`, `Integer→Long` — assert one group, synthesized intermediates, a complete realised conversion path. (type-conversion: *Lossless cross-products compose*)
- [x] 4.3 Negative tests: narrowing produces no path — `long→int`, `double→int`, `Integer→Byte`. (type-conversion: *Lossless boundary*)
- [x] 4.4 Cheapest-path test: when a shorter conversion path and a longer one both exist, codegen selects the shorter (lower-weight) one; the longer path and any dead-end nodes remain in the graph but are not generated. (graph-expansion: *Conversion expansion … stops at SAT*)

## 5. Verify

- [x] 5.1 Run `./gradlew check` and fix every violation — do not continue while any check fails.
- [ ] 5.2 Commit the completed change with `/commit-commands:commit`.

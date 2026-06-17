## Why

The debug DOT graph dumps are hard to read: operation nodes are labelled with the codegen
**lambda's** synthetic class name (`DirectAssign$$Lambda/0x0000000012753658`), value nodes carry the
raw `TypeMirror.toString()` (full FQNs plus inline JSpecify annotations, e.g.
`java.util.Optional<…io.github.joke.testing.Human.@org.jspecify.annotations.Nullable Address>`), and
the `full` dump shows the heavily over-emitted candidate set with no distinction between what survived
planning and what was pruned. Since there are **no external strategy authors yet**, this is also the
right moment to sharpen the small SPI surface the labels touch (it is cheaper to change now than after
third parties depend on it).

## What Changes

- **Operation labels become a strategy-supplied, fully-typed string.** Add a **required** `label` to
  `OperationSpec`; each built-in composes it from data it already holds (conversions `int→long` with
  the glyph arrow; constructor `new Address(int, String)`; accessors `getStreet()` / `.street` /
  `street()`; constant the literal `"ACTIVE"`; container `map`/`flatMap`/`collect`/`wrap`; method
  bridge `mapAddress(…)`). The driver sets `Operation.label` from `spec.label()` — no lambda-class
  names anywhere. **BREAKING** (internal SPI; no external authors).
- **Drop `Operation.strategyFqn`** — it held the codegen lambda's FQN, stored but read by nobody.
- **Remove the `VarNames` marker from the codegen surface.** It is an empty interface threaded through
  every `OperationCodegen.render(VarNames, IncomingValues)` yet used by zero strategies; the new
  signature is `CodeBlock render(IncomingValues inputs)`. **BREAKING** (internal SPI). The
  shared-Value hoisting seam it reserved is re-added if/when hoisting lands.
- **Delete the dead `LoopContainerCodegen`** type (referenced only by itself and an archived spec).
- **Value labels render via a `TypeMirror` walk:** simple names plus a JSpecify-style nullness suffix
  `?`/`!` per level (outer nullness from the Value's own `nullness` field, nested nullness from each
  type-argument's annotation mirrors), e.g. `Optional<Set<Address?>>!`.
- **The `full` dump dims unreachable vertices** (grey fill / dashed) instead of rendering every
  over-emitted candidate identically — closing the already-unmet `graph-debug-output` requirement
  that the full dump annotate reachability from extraction cost. `DumpFullGraphStage` extracts the
  plan and passes reachability to the renderer; the `transforms`/`plan` dumps are unchanged.

The expansion architecture is **unchanged**: codegen stays bundled with its operation (the locked
codegen-on-operation choice, kept deliberately — see design). Generated mapper code is unchanged; the
Spock/jqwik suite stays green.

## Capabilities

### New Capabilities
- _(none — this change refactors existing capabilities)_

### Modified Capabilities
- `expansion-strategy-spi`: `OperationSpec` gains a required `label`; `OperationCodegen.render` drops
  the `VarNames` parameter; `VarNames` and `LoopContainerCodegen` leave the SPI surface.
- `graph-model`: `Operation` drops `strategyFqn`; its `label` is the strategy-supplied, fully-typed
  production label (no longer a codegen class name).
- `graph-expansion`: the driver sets `Operation.label` from `OperationSpec.label`, never from the
  codegen lambda's class.
- `graph-debug-output`: value-node labels are rendered from a `TypeMirror` walk (simple names +
  `?`/`!` nullness); the `full` dump dims unreachable vertices by extraction cost.

## Impact

- **SPI (`percolate-spi`):** `OperationSpec` (+`label`), `OperationCodegen` (drop `VarNames`),
  removal of `VarNames` and `LoopContainerCodegen`.
- **Strategies (`percolate-strategies-builtin`):** every `OperationSpec.of/ofPartial/mapping` call
  site supplies a typed `label`; every `render` lambda drops its `VarNames` parameter (the `Container`
  base too).
- **Processor:** `ExpandStage` (set `label` from the spec, not the lambda; stop populating
  `strategyFqn`), `Operation`/`AddOperation`/`MapperGraph` (drop `strategyFqn`), `DotRenderer` (value
  `TypeMirror`-walk labels, unreachable dimming), `DumpFullGraphStage` (extract plan, pass
  reachability), `BuildMethodBodies` (drop the `VarNames` argument at the render call sites).
- **Tests:** specs asserting on dump labels, the `DotRenderer`, and the codegen render signature.
- **Non-goals:** byte-stable DOT output (the lambda-hash instability disappears as a side effect, but
  stability is not a goal); any operations-as-data / central-renderer IR; shared-Value hoisting.

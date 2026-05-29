## Why

`BuildMethodBodies` cannot render any mapper whose target chain passes through a container element slot. The group-target render step derives a slot name from every group slot, but `slotName` only handles `TargetLocation` nodes — a container sub-group's slot is an `ElementLocation` node (`elem(element)`), so it throws `cannot derive slot name from node`. Every `List`/`Set`/`Array`/`Optional` conversion that reaches a real codegen pass fails (e.g. `tgt[addresses]:Optional<Set<Address>>` in the integration `PersonMapper`). The expansion engine builds the chain correctly; only the codegen-side naming is missing.

## What Changes

- Extend `BuildMethodBodies.slotName` so a group slot whose `Location` is an `ElementLocation` resolves to the location's `role` (`"element"`; `"key"`/`"value"` for future map-shaped containers). `TargetLocation` slots keep their existing last-segment naming. A node that is neither remains an error.
- The slot name continues to be a stable map key only — container `GroupCodegen`s read positionally (`inputs.single()`), so the role value is unique-per-slot and forward-compatible with name-based access (`inputs.byName("key")`) for multi-axis containers.
- Add the missing `BuildMethodBodiesSpec` coverage: a container group (`ElementLocation` slot) rendered through `renderGroupTarget`. This is the gap that let the defect ship.

No engine, SPI, strategy, or expansion-direction change. The fix lives entirely in the read-only codegen pass, downstream of a converged graph.

## Capabilities

### New Capabilities

<!-- none -->

### Modified Capabilities

- `code-generation`: The method-body composition algorithm's group-target inductive case gains an explicit slot-name derivation rule covering `ElementLocation` slots (container sub-groups), not only `TargetLocation` slots.

## Impact

- **Code**: `processor/src/main/java/io/github/joke/percolate/processor/stages/generate/BuildMethodBodies.java` — one new branch in `slotName` plus an `ElementLocation` import. `BuildMethodBodiesSpec.groovy` — one new container-group scenario.
- **APIs**: None. No SPI, no `Bridge`/`GroupTarget` change, no processor option.
- **Behaviour**: Mappers with container-typed targets begin generating instead of failing with `code generation failed: cannot derive slot name from node`. Observable on the integration `PersonMapper.mapHuman`.
- **Teams**: Anyone whose mapper targets `List`/`Set`/`Array`/`Optional` fields — previously blocked at codegen, now unblocked.

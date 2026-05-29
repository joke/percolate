## 1. Fix slot-name derivation

- [x] 1.1 In `BuildMethodBodies.slotName`, add an `ElementLocation` branch returning `((ElementLocation) loc).getRole()`; keep the `TargetLocation` last-segment branch and the fall-through `IllegalStateException`.
- [x] 1.2 Add the `io.github.joke.percolate.processor.graph.ElementLocation` import.

## 2. Regression coverage

- [x] 2.1 Add a `BuildMethodBodiesSpec` scenario: a container group whose root is a `List`/`Set`/`Optional`-typed `TargetLocation` node and whose single slot is an `ElementLocation` element node renders via its `GroupCodegen` with no `cannot derive slot name from node`.
- [x] 2.2 Add a nested-container scenario mirroring `Optional<Set<Address>>` (an `OptionalCollect` group whose element slot is the root of a `SetCollect` group) — assert the body is produced with no `IllegalStateException`.
- [x] 2.3 Run `:processor` unit tests; confirm the new scenarios pass and existing `BuildMethodBodiesSpec` cases stay green.

## 3. End-to-end verification

- [x] 3.1 Ran `./gradlew :mappers:classes` in `/home/joke/Projects/joke/percolate-integration`. The `cannot derive slot name from node` error is resolved — slot naming verified necessary. `mapHuman` does NOT yet fully generate; it now fails at a different node (see 3.2).
- [x] 3.2 New error: `leaf node is not a SourceLocation` at `tgt[addresses]→elem:Set<HA>→elem:Optional<Set<HA>>`. **Classified as a SEPARATE gap** (out of scope for this change): `BuildMethodBodies.indexGroupRootsByNode` selects a group per root via `putIfAbsent` with no `GroupOutcome` check, so when multi-fire registers sibling groups at one root the render pass can descend into a dead (UNSAT) sibling. Fix belongs in a follow-up change (outcome-driven group selection in codegen), not in slot naming.

## Why

Since the delta-pipeline refactor (`e14e741`), the expansion **graph** is built wrong for directive-bound mappings whose target type differs from the source type: the conversion chain collapses to a single wrong-typed direct-assign edge. The integration mapper `PersonMapper.mapHuman` regressed — `@Map(target = "addresses", source = "person.addresses")` binds `List<Optional<Person.Address>>` to `Optional<Set<Human.Address>>`, but the realised graph (`.transforms.dot`) shows `tgt[addresses]` typed as the **source** type `List<Optional<Person.Address>>`, reached by one `DirectiveBinding (0)` edge from `src[person.addresses]`, with no container bridges and no recursive `Person.Address` → `Human.Address` element mapping. Last known-good commit: `5dcee1c`.

(The downstream compile error in the emitted mapper is a separate, pre-existing issue — codegen is known-incomplete and is NOT in scope here. This change is about the graph being structurally wrong.)

Root cause: `DirectiveBindingExpander.step` types the target root from the **source slot's** type when the root is still untyped (`directAssignTyping`, stamping `slotType` onto `root`). This pre-empts the target's real declared type, which should arrive from the target-chain (`ConstructorCall`) producer. Once the root carries the source type, source and root compare equal, so the expander emits a NOOP direct-assign edge and SATs — the container-conversion branch (`slotResolver.resolve(root, ...)`) never fires. Scalar bindings (`String` → `String`) are unaffected because source and target types genuinely coincide.

## What Changes

- Stop `DirectiveBindingExpander` from propagating the source slot type onto an untyped target root. The target root's type SHALL come from its target-chain producer (e.g. `ConstructorCall`), never from the source slot.
- Direct-assign (NOOP edge + SAT) SHALL fire only when both the slot and the root are independently typed and the types are the same. When they differ, the expander SHALL expand the root as a frontier so container/element bridges convert the source type into the declared target type.
- Restore the container-conversion chain in the realised graph for directive-bound collection/Optional targets, including the recursive element mapping (`mapAddress` via `MethodCallBridge`).
- Add a regression scenario covering a directive binding whose target type differs from the source type (collection-to-collection).

No breaking changes — this restores previously-correct graph structure.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `graph-expansion`: clarify the directive-binding expander's root-typing rule — the target root is typed by its target chain, not the source slot; direct-assign only on independently-typed, same-type endpoints; otherwise expand the root to convert source type into target type. Container conversion for directive-bound targets is thereby preserved.

## Impact

- **Code**: `processor/.../stages/expand/DirectiveBindingExpander.java` (root-typing / direct-assign gating); possibly `SlotResolver` and cross-group ordering in `ExpandGroupsPhase`. Regression landed in `e14e741`.
- **Graph / debug output**: any directive-bound target whose declared type differs from the source (containers, nested mapper types) currently realises a wrong graph (`.transforms.dot` shows direct-assign with the source type on the target); fix restores the conversion chain. Integration project `percolate-integration` `PersonMapper.mapHuman` (`addresses`) is the reproducing case. The pre-existing incomplete-codegen compile error is out of scope.
- **Tests**: expand-stage Spock specs (`DirectiveBindingExpanderSpec`) plus a graph-shape assertion for the differing-type directive binding.
- **APIs / dependencies**: none.
- **Affected teams**: processor/codegen maintainers; integration-test owners.
</content>
</invoke>

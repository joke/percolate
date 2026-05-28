## 1. Structurally pin the declared target type (D2)

- [x] 1.1 Add a package-private mutator to `ExpansionGroup` (e.g. `recordExpectedType(Node, Slot)`) that inserts into `slotMetadata`; scaffolding-only, not exposed on `ExpansionSnapshot`.
- [x] 1.2 In `ResolveTargetChainsPhase`, when a `GroupTarget` slot reuses an existing seed target node, locate the directive-binding group rooted at that node and record the same `Slot` (declared type) on it, so `effectiveTypeFor(root, directiveBindingGroup)` resolves from the group's own metadata.
- [x] 1.3 Verify no global cross-group scan is introduced (`effectiveTypeFor` keeps `node.getType()` → `group.expectedTypeFor(node)` only).

## 2. DirectiveBindingExpander rewrite (D1, D3)

- [x] 2.1 Remove `directAssignTyping` and the branch that stamps the source slot type onto an untyped root.
- [x] 2.2 After the source slot resolves, read the root's declared target type via `snapshot.effectiveTypeFor(root, group)`; if null, return the group pending (wait for the target chain).
- [x] 2.3 When `isSameType(slotType, rootType)`: emit a bundle with `TypeNode(root, rootType, producerScope)` + the NOOP direct-assign `AddEdge(slot → root)` + `AddEdgeToView`, and SAT.
- [x] 2.4 When the types differ: call `slotResolver.resolve(root, group, snapshot, deltas)` to expand the root as a frontier; SAT when it returns true, else leave the root pending.
- [x] 2.5 Confirm no source-type `TypeNode` for the root is emitted on any path.

## 3. Unit specs

- [x] 3.1 `DirectiveBindingExpanderSpec`: scalar same-type binding emits one NOOP direct-assign edge + roots typed with target type + SAT.
- [x] 3.2 `DirectiveBindingExpanderSpec`: untyped root ⇒ no `TypeNode` for root, group stays pending.
- [x] 3.3 `DirectiveBindingExpanderSpec`: differing container types ⇒ no direct-assign edge; root resolved as frontier; SAT only on child sub-group SAT.
- [x] 3.4 `ResolveTargetChainsPhaseSpec`: a directive-binding group whose root is a reused target-chain slot has the declared target type recorded; `effectiveTypeFor(root, directiveBindingGroup)` returns it. A directive root with no corresponding target-chain slot gets none (group later goes `unsatNoPlan`).

## 4. End-to-end graph shape

- [x] 4.1 Add an expansion-harness/graph-shape assertion for the collection-to-collection directive binding: `tgt[addresses]` typed `Optional<Set<Human.Address>>` (not the source type), no direct `src[person.addresses] → tgt[addresses]` NOOP edge, and a bridge chain connecting source to target including a recursive `Person.Address → Human.Address` element mapping.
- [x] 4.2 Rebuild `percolate-integration` and confirm `PersonMapper.transforms.dot` shows the conversion chain for `addresses` (codegen compile error remains out of scope).

## 5. Verify & commit

- [x] 5.1 Run `./gradlew check` and resolve every violation before continuing.
- [x] 5.2 Commit the change with `/commit-commands:commit`.
</content>

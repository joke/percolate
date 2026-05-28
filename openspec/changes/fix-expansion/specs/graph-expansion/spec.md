## ADDED Requirements

### Requirement: DirectiveBindingExpander root typing and direct-assign gating

`DirectiveBindingExpander` resolves a directive-binding group (root at a `TargetLocation`, single slot at a `SourceLocation`; see `seed-graph`). It SHALL NOT write the source slot's type onto the target root. The target root's type is supplied solely by the root's target chain — the `ConstructorCall`/`GroupTarget` producer that consumes the root — via the existing "Slot Nodes are typed at producer commit" lifecycle. The expander SHALL NOT emit a `TypeNode` delta for the root.

While the root is untyped, the group SHALL remain pending across outer passes (it waits for the target chain to type the root); it SHALL NOT be forced to SAT and SHALL NOT direct-assign.

Once both the slot and the root carry independently-resolved types, the expander SHALL gate on type identity:

- If `isSameType(slotType, rootType)`, the expander SHALL emit a single direct-assign REALISED edge (`slot → root`, `weight == Weights.NOOP`, `DirectAssignCodegen`) plus its `AddEdgeToView`, and SHALL SAT the group.
- If the types differ, the expander SHALL NOT emit a direct-assign edge. It SHALL resolve the root as a frontier via `SlotResolver.resolve(root, ...)`, so container/element `Bridge`s (and recursive `GroupTarget`/`MethodCallBridge` element mappings) convert the source type into the declared target type. The group SATs once a spawned child sub-group rooted at the root SATs.

This preserves the container-conversion chain for directive-bound collection/`Optional` targets and prevents the source type from being stamped onto the target.

#### Scenario: Scalar same-type binding emits a direct-assign edge and SATs
- **WHEN** a directive-binding group has slot `src[person.lastName]:String` (typed) and root `tgt[lastName]:String` (typed by its target chain)
- **THEN** `DirectiveBindingExpander.step` emits one bundle with a direct-assign REALISED edge `src[person.lastName] → tgt[lastName]` (`Weights.NOOP`, `DirectAssignCodegen`) and an `AddEdgeToView`
- **AND** the group is returned with `pendingSlots = []` (SAT)

#### Scenario: Source slot type is never stamped onto an untyped target root
- **WHEN** a directive-binding group has typed slot `src[person.addresses]:List<Optional<Person.Address>>` and root `tgt[addresses]:?` (untyped — its target chain has not yet committed)
- **THEN** `DirectiveBindingExpander.step` emits no `TypeNode` delta for the root
- **AND** the group is returned still pending (the root remains untyped after the applier run)

#### Scenario: Differing container types expand a conversion chain instead of direct-assign
- **WHEN** a directive-binding group has typed slot `src[person.addresses]:List<Optional<Person.Address>>` and the root `tgt[addresses]` has been typed `Optional<Set<Human.Address>>` by its `ConstructorCall` target chain
- **THEN** `DirectiveBindingExpander.step` emits no direct-assign edge
- **AND** it resolves the root as a frontier, producing bundles whose container/element bridges convert `List<Optional<Person.Address>>` into `Optional<Set<Human.Address>>` (including a recursive `Person.Address → Human.Address` element mapping)
- **AND** the group SATs only once a spawned child sub-group rooted at `tgt[addresses]` SATs

#### Scenario: Collection-to-collection directive binding realises a conversion chain, not a typed-as-source direct assign
- **WHEN** the mapper declares `@Map(target = "addresses", source = "person.addresses")` with source `List<Optional<Person.Address>>` and target `Optional<Set<Human.Address>>`
- **THEN** the realised graph types `tgt[addresses]` with the declared target type `Optional<Set<Human.Address>>`, not the source type
- **AND** there is no single direct-assign REALISED edge from `src[person.addresses]` straight to `tgt[addresses]`
- **AND** a container-conversion chain of REALISED bridge edges connects `src[person.addresses]` to `tgt[addresses]`
</content>

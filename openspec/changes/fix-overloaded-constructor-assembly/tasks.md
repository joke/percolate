## 1. Characterise the failure (red test first)

- [x] 1.1 Add a processor end-to-end regression to `EndToEndCodegenSpec` (or a new sibling spec) targeting a class with type-divergent overloaded constructors — `Address(int number, String street)` and `Address(long number, String street)` — mapped from an `int`-sourced field; assert it currently fails with `no return-root TargetLocation node in scope` so the fix has a concrete pin (mark `@PendingFeature` / `@Ignore` until §5).
- [x] 1.2 Capture the same shape as a `seed-graph` / `graph-expansion` unit case (umbrella group whose declared children are `{number, street}` and two constructors disagree on `number`'s type) so the per-`(name, required-type)` leaf behaviour is testable below the codegen layer.

## 2. Bind only directive-declared inputs (D3)

- [x] 2.1 In `FrontierMatcher` (assembly path), add a name-set-equality gate for `AssemblyStrategy` boundary steps: before opening a sub-group, reject any constructor step whose parameter-name set ≠ the umbrella group's declared-child name set (`group.getSlots()` names). Apply only when `frontier.equals(group.getRoot())` and the step came from an `AssemblyStrategy`.
- [x] 2.2 In `FrontierMatcher.bindSlot`, remove the `InputAllocator.allocate` fall-through for assembly parameters — an assembly parameter SHALL bind only to a declared child of the same name; an un-declared parameter is never auto-sourced. Keep the `InputAllocator` path for non-assembly (general/bridge) frontiers untouched.
- [x] 2.3 Spec the rejected cases with unit tests: extra parameter (`Address(street, zip, country)` over `{street, zip}`), missing parameter (`Address(street)`), and no-arg `Address()` — each must NOT open a sub-group and must NOT mint a fresh slot; assert no `AddNode`/`AddGroup` delta is emitted for them.

## 3. Per-`(name, required-type)` typed leaves from the shared source (D2)

- [x] 3.1 Replace the single-shared-leaf reuse in `FrontierMatcher.bindSlot`/`existingSlotByName`: for a declared child consumed by an assembly parameter of type `T`, mint a per-`(name, T)` typed leaf (driver `AddNode`) keyed on `(name, required-type)` instead of returning the one untyped umbrella leaf. Two constructors disagreeing on a child's type therefore bind two distinct leaves.
- [x] 3.2 Wire each per-type leaf to its own directive-binding conversion from the **one shared source value** (reuse the seeded `src[...]` node — do NOT duplicate the source). Reuse the existing `DirectiveBindingExpander` path so same-type ⇒ direct-assign and differing ⇒ widen/box chain; verify the shared source node is referenced by both conversions (no second source node added).
- [x] 3.3 Confirm an incompatible overload (no valid conversion from the shared source to the parameter type) leaves its typed leaf unresolved and its sub-group UNSAT — pruned structurally, with no separate type guard.

## 4. Per-constructor pin without collision (graph-expansion MODIFIED)

- [x] 4.1 Update `Applier.pinExpectedTypesOnProducers` so the declared parameter type is recorded onto the directive-binding conversion feeding the **per-constructor typed leaf** (`recordExpectedType(leaf, slot)` per minted leaf), not onto a single shared leaf. Verify the `int` and `long` constructors pin distinct types onto distinct leaves with no last-writer-wins overwrite.
- [x] 4.2 Confirm a directive-binding conversion whose leaf is bound by no assembly producer slot stays unpinned and converges to `unsatNoPlan` (regression-guard the existing behaviour).
- [x] 4.3 Verify the same umbrella/`targetChildren` contract holds inside a container element scope so name-set equality and per-type leaves apply through the element seam without special-casing (exercise via the `Human.addresses` `Optional<Set<Address>>` path).

## 5. Guarantee a planned return-root; diagnose the unsatisfiable case (D4, D5)

- [x] 5.1 Verify `PlanView` selects a single satisfiable constructor at the return-root via the unchanged cheapest-co-rooted-group oracle (DirectAssign `0` beats Widen `1`) — the `int` constructor wins for an `int` source; assert selection is deterministic across repeated runs (D5).
- [x] 5.2 In `BuildMethodBodies.findReturnRoot`, degrade the genuinely-unsatisfiable case from `IllegalStateException` to a diagnostic that names the unresolved target field(s), surfaced through the existing `RealisationDiagnosticsStage` path; the umbrella group records `unsatNoPlan` naming the field(s) and no silent `new Target()` is emitted.
- [x] 5.3 Un-ignore the §1.1 regression and assert: a mapper is generated, `new Address(...)` is emitted for the selected (`int`) constructor, and it compiles. Add a negative test: a target whose declared children match no constructor name set fails with the field-naming diagnostic, not an `IllegalStateException`.

## 6. Integration regression

- [x] 6.1 In `percolate-integration`, confirm `./gradlew :mapper:classes` (the originally-failing task) now succeeds for `PersonMapper.mapAddress` against the `int`/`long` `Human.Address` overloads; inspect the generated mapper to confirm the `int` constructor is chosen.

## 7. Verify and commit

- [x] 7.1 Run `./gradlew check` and resolve every violation — NEVER continue while any check fails.
- [ ] 7.2 Commit the completed change with `/commit-commands:commit`.

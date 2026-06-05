## ADDED Requirements

### Requirement: Assembly binds only directive-declared inputs

The assembly path (`FrontierMatcher` resolving an `AssemblyStrategy` step such as `ConstructorCall`) SHALL bind each constructor parameter **only** to a directive-declared child of the matching name, read from the umbrella assembly group's slots (`group.getSlots()` at the assembly root — the driver already holds them; no cross-group scan). It SHALL NOT fall through to `InputAllocator.allocate` to mint a fresh slot for an un-declared constructor parameter, and SHALL NOT auto-source such a parameter.

Because every constructor parameter is mandatory, this reduces to a **name-set equality** candidacy rule: a constructor is a candidate iff its parameter-name set equals the umbrella group's declared-child name set. The two halves of the equality are coverage (`⊇` — no declared child dropped) and no-silent-sourcing (`⊆` — no un-declared parameter invented). A constructor whose parameter-name set is not equal to the declared-child name set SHALL NOT open a sub-group at the assembly root; the rejection happens in the assembly path before sub-group creation, not via a later cost or SAT tweak.

This strictness is specific to `AssemblyStrategy` producers. Bridge producers (`MethodCallBridge` and friends) legitimately descend their own arguments — the author wrote that method — and SHALL retain their existing argument-sourcing behaviour; the strict-vs-descend split keys on `AssemblyStrategy`, a marker that already exists.

#### Scenario: Constructor whose parameter names equal the declared children is a candidate
- **WHEN** the umbrella declared children are `{number, street}` and the target declares `Address(int number, String street)`
- **THEN** the assembly path opens a sub-group rooted at `tgt[address]` whose slots are the `number` and `street` typed leaves
- **AND** no fresh slot is allocated for any un-declared parameter

#### Scenario: Constructor with an extra parameter is rejected without silent sourcing
- **WHEN** the umbrella declared children are `{street, zip}` and the target declares `Address(String street, String zip, String country)`
- **THEN** the assembly path does NOT open a sub-group for that constructor
- **AND** no fresh `country` slot is allocated and no source is auto-descended for it

#### Scenario: Constructor missing a declared parameter is rejected without dropping a field
- **WHEN** the umbrella declared children are `{street, zip}` and the target declares `Address(String street)`
- **THEN** the assembly path does NOT open a sub-group for that constructor
- **AND** the declared `zip` child is not dropped from any candidate

#### Scenario: No-arg constructor is not a candidate when children are declared
- **WHEN** the umbrella declared children are non-empty and the target declares a no-arg `Address()`
- **THEN** the assembly path does NOT open a zero-slot sub-group for `Address()`
- **AND** `new Address()` is never emitted while declared children remain unmapped

#### Scenario: Name-set equality applies identically inside element scopes
- **WHEN** an umbrella assembly group is constructed for a target inside a container element scope (`container-expansion`) with declared children `{number, street}`
- **THEN** the same name-set equality candidacy rule applies through the element seam without special-casing

### Requirement: Type-divergent overloaded constructors coexist as per-type typed slots

When two or more accessible constructors share the umbrella's declared-child name set but disagree on a child's parameter type, the assembly path SHALL let them coexist as competing OR-siblings rooted at the assembly output, rather than racing to type a single shared leaf. Each constructor's parameter SHALL become its **own** target leaf, keyed on `(name, required-type)` and pinned to that constructor's parameter type, fed from the **one shared source value** seeded for the directive by its **own** directive-binding conversion (reusing the `DirectiveBindingExpander` path: same-type ⇒ direct-assign; differing ⇒ a widen/box conversion chain).

The per-`(name, required-type)` typed leaves and their directive-binding conversions SHALL be minted during expansion by the assembly path (driver `AddNode`/`AddGroup` deltas), **reusing the one shared source node** rather than duplicating the source down to the parameter. The type check is then structural: a parameter leaf resolves iff a valid conversion from the shared source exists, so an incompatible overload is UNSAT and pruned — there is no separate type guard, and SAT comes to mean "compiles".

This respects the live architecture rules that conversions and candidate search exclude `TargetLocation` inputs, and that source→target-type conversion is owned by `DirectiveBindingExpander`: the adaptation is from the shared **source value** to each typed target leaf, never from one target leaf to another.

#### Scenario: int/long divergent overloads both materialise distinct typed leaves
- **WHEN** the declared children are `{number, street}` and the target declares both `Address(int number, String street)` and `Address(long number, String street)` and the shared source value `src[address.number]` is `int`
- **THEN** distinct typed leaves `tgt[number]:int` and `tgt[number]:long` are minted, each rooting its constructor's sub-group
- **AND** `tgt[number]:int` is fed by a direct-assign directive-binding edge from `src[address.number]`
- **AND** `tgt[number]:long` is fed by a widen directive-binding conversion from the same `src[address.number]` node (no duplicate source node)

#### Scenario: An incompatible overload is structurally UNSAT
- **WHEN** a constructor's parameter type admits no valid conversion from the shared source value
- **THEN** that constructor's typed leaf never resolves and its sub-group is UNSAT
- **AND** it is pruned by the plan without any separate type-guard check

### Requirement: Satisfiable assembly yields a planned return-root; unsatisfiable assembly is diagnosed

Whenever **at least one** accessible constructor is a name-set-equality candidate and is satisfiable (all its typed leaves resolve via valid conversions from the declared sources), `planView()` SHALL contain the assembly return-root node, and code generation SHALL emit a `new Target(...)` call for the selected constructor. `BuildMethodBodies.findReturnRoot` SHALL find the return-root in this case and SHALL NOT throw.

When an assembly output has directive-declared children but **no** name-matching, type-correct constructor, the engine SHALL record the umbrella assembly group's outcome as `unsatNoPlan` naming the unsatisfiable field(s), surfaced through the existing realisation-diagnostics path. It SHALL NOT silently drop the return-root and SHALL NOT fail the scope with an internal `IllegalStateException`; `BuildMethodBodies` SHALL degrade to a diagnostic that names the unresolved target rather than throwing. Only a genuinely unsatisfiable assembly fails the scope, and it fails with that diagnostic.

#### Scenario: One satisfiable overload guarantees a planned return-root
- **WHEN** a target declares type-divergent overloaded constructors and at least one is a satisfiable name-set-equality candidate
- **THEN** `planView()` contains the assembly return-root node
- **AND** codegen emits `new Target(...)` for the selected constructor
- **AND** `BuildMethodBodies.findReturnRoot` does not throw

#### Scenario: No name-matching constructor is diagnosed, not dropped
- **WHEN** an assembly output has declared children `{street, zip}` but every accessible constructor has a different parameter-name set
- **THEN** the umbrella assembly group's outcome is recorded `unsatNoPlan` naming the unsatisfiable field(s)
- **AND** codegen surfaces a diagnostic identifying the unresolved target instead of throwing an `IllegalStateException`
- **AND** no silent `new Target()` is emitted

### Requirement: Deterministic cost-driven constructor selection among satisfiable overloads

When more than one name-set-equality constructor is satisfiable, selection SHALL be the existing cheapest-co-rooted-group choice in `PlanView`, unchanged: a DirectAssign typed-leaf binding (cost `0`) beats a Widen binding (cost `1`), so the exact-type constructor is selected — the choice Java overload resolution makes. The selection SHALL be deterministic and stable across runs. No cost-oracle change is required: once per-type typed leaves make SAT mean "compiles" and name-set equality filters candidates, the cheapest-plan oracle already yields a single satisfiable constructor at the return-root.

#### Scenario: Exact-type overload wins over a widening overload
- **WHEN** both `Address(int number, …)` and `Address(long number, …)` are satisfiable and the shared source value is `int`
- **THEN** the `int` constructor is selected because its `number` leaf binds via DirectAssign (cost `0`) versus the `long` leaf's Widen (cost `1`)
- **AND** the selected constructor is stable across repeated runs

## MODIFIED Requirements

### Requirement: Directive-binding declared target type pinned by the assembly producer

When an assembly producer at an umbrella assembly group binds a parameter to a directive-declared child (by name), it binds to a per-`(name, required-type)` typed leaf minted for **that** constructor (see "Type-divergent overloaded constructors coexist as per-type typed slots"). The producer's `AddGroup` delta SHALL carry that slot's declared parameter type in its `slotMetadata`. The `Applier`, on applying the `AddGroup` (`Applier.pinExpectedTypesOnProducers`), SHALL record that declared type onto the **directive-binding conversion feeding that constructor's typed leaf**, via `ExpansionGroup.recordExpectedType(node, slot)`, so the declared target type is readable through that conversion's own `expectedTypeFor`/`effectiveTypeFor` without any cross-group scan.

Because each constructor pins its own typed leaf, type-divergent overloaded constructors pin **distinct** types onto **distinct** leaves and never collide: the pin is no longer a last-writer-wins `slotMetadata.put` over a single shared leaf. A directive-binding conversion whose leaf is bound by no assembly producer slot SHALL NOT be pinned (and converges to `unsatNoPlan` if its leaf is never typed).

#### Scenario: Assembly-bound typed leaf pins its directive-binding conversion
- **WHEN** an umbrella assembly producer (e.g. `ConstructorCall`) binds its parameter slot to a typed leaf with declared type `T`, and the `Applier` applies the resulting `AddGroup`
- **THEN** `Applier.pinExpectedTypesOnProducers` calls `recordExpectedType(leaf, slot)` on the directive-binding conversion feeding that typed leaf
- **AND** that conversion's `expectedTypeFor(leaf)` returns `T`

#### Scenario: Type-divergent overloads pin distinct types without colliding
- **WHEN** one constructor binds a `number` leaf with declared type `int` and another binds a `number` leaf with declared type `long`, both for the same shared source value
- **THEN** each typed leaf's directive-binding conversion is pinned to its own declared type (`int` and `long` respectively)
- **AND** neither pin overwrites the other (no last-writer-wins over a single shared leaf)

#### Scenario: Unbound directive leaf stays unpinned
- **WHEN** a directive-binding conversion's leaf is bound by no assembly producer slot
- **THEN** `expectedTypeFor(leaf)` remains `null`
- **AND** the conversion converges to `unsatNoPlan` if its leaf is never typed by any producer

## ADDED Requirements

### Requirement: Umbrella child leaves are name-keyed demands, not single-type targets

The umbrella assembly group's slots — the parent target node's child target leaves (see "SeedGraph registers one ExpansionGroup per SEED edge") — SHALL be **name-keyed demands**: each child leaf identifies one directive-declared target field by name and carries an empty `type` at seed time. A child leaf SHALL NOT be treated as a single-type target that exactly one producer pins.

When the target's accessible constructors disagree on a child's parameter type (type-divergent overloads such as `Address(int number, …)` and `Address(long number, …)`), the engine SHALL NOT force a single type onto the shared name-keyed leaf. Instead, per-`(name, required-type)` typed leaves and their directive-binding conversions are minted during expansion by the assembly path (see the `graph-expansion` capability), each fed from the **one shared source value** seeded for that directive.

`SeedGraph` SHALL therefore still emit exactly **one** name-keyed child leaf per distinct directive-declared target-field name, and exactly **one** directive-binding group per directive (the shared source→name binding). `SeedGraph` SHALL NOT pre-materialise per-type leaves, because constructor parameter types are not known at seed time. The pre-seeded umbrella shape is unchanged; only the contract of a child leaf — a name demand whose typing is deferred to per-constructor expansion, rather than a single-type pin target — is clarified by this requirement.

#### Scenario: A field name seeds exactly one name-keyed leaf regardless of constructor overloads
- **WHEN** `SeedGraph.apply(...)` seeds `@Map(target = "address.number", source = "person.address.number")` for a target `Human.Address` that declares both `Address(int number, String street)` and `Address(long number, String street)`
- **THEN** the umbrella assembly group for `tgt[address]` contains exactly one child leaf `tgt[address.number]` with empty `type`
- **AND** exactly one directive-binding group is registered with `root = tgt[address.number]:?` and `slot = src[person.address.number]:?`
- **AND** no per-type leaf (`tgt[address.number]:int`, `tgt[address.number]:long`) is created at seed time

#### Scenario: Type-divergent overloads do not collide on the seeded leaf
- **WHEN** `SeedGraph.apply(...)` seeds a directive whose declared target field is consumed by two type-divergent overloaded constructors
- **THEN** the seeded name-keyed leaf carries no type and is bound to no single constructor's parameter type at seed time
- **AND** resolving the divergent per-`(name, required-type)` typed leaves is deferred to the assembly path during expansion

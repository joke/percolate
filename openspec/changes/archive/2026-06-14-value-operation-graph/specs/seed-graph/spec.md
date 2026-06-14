## ADDED Requirements

### Requirement: Seeding produces roots and goal specs, not edges

For each abstract mapper method, `SeedStage` SHALL create: one parameter-root `Value` per method
parameter (typed from the declaration, base-case SAT), one return-root `Value` (typed from the
return type, the initial demand), and the **declared-bindings goal specs**. It SHALL NOT create
edges, groups, or untyped target-leaf nodes: all producer structure is minted during expansion.

#### Scenario: Seed output is roots plus goals
- **WHEN** seeding completes for a method
- **THEN** the method scope contains exactly the parameter-root Values and the return-root Value,
  no edges, and a goal spec attached to the return-root demand

### Requirement: Declared bindings derived per target level

The goal spec SHALL be derived by grouping the method's `@Map` directives by dotted target path
level: each level's spec maps the child name to its directive
(`@Map(target="address.street", …)` contributes `street` at the `address` level, and `address` at
the root level). Each level's spec gates assembly at that level's demand.

#### Scenario: Nested target paths group by level
- **WHEN** directives declare `address.street` and `address.zip`
- **THEN** the root-level goal declares `{address}` and the `address`-level goal declares
  `{street, zip}`

### Requirement: Constant and default directives participate as bindings

A directive with `constant` or `defaultValue` SHALL appear in the declared bindings like any other:
a constant binding's supply is a zero-port Operation minted during expansion; a default participates
in the binding's nullness crossing (see `default-values`). Seeding itself mints no constant nodes.

#### Scenario: Constant declared, not seeded
- **WHEN** a directive declares `constant = "42"` for target `number`
- **THEN** `number` appears in the goal spec and no constant vertex exists until expansion

### Requirement: Seed-time validation preconditions

`SeedStage` SHALL assume `ValidateSourceParameters` has run (every source's first segment names a
parameter) and SHALL drop no directive silently: every directive either contributes to a goal spec
or was rejected earlier with a diagnostic.

#### Scenario: No silent directive loss
- **WHEN** seeding processes a method with N validated directives
- **THEN** all N appear across the method's goal specs

## REMOVED Requirements

### Requirement: SeedGraph registers one ExpansionGroup per SEED edge
**Reason**: There are no SEED edges and no groups.
**Migration**: See ADDED "Seeding produces roots and goal specs, not edges".

### Requirement: SeedGraph emits only SEED-kind edges
**Reason**: Seeding emits no edges at all.
**Migration**: See ADDED "Seeding produces roots and goal specs, not edges".

### Requirement: Parameter root nodes
**Reason**: Restated over `Value`s.
**Migration**: See ADDED "Seeding produces roots and goal specs, not edges".

### Requirement: Return-type root node
**Reason**: Restated over `Value`s.
**Migration**: See ADDED "Seeding produces roots and goal specs, not edges".

### Requirement: Directive-seeded source chains
**Reason**: Source descent is expansion work driven by the demand context's directive, not seed
scaffolding.
**Migration**: See `source-path-resolution` and `graph-expansion` ADDED "Directive context travels
with the demand".

### Requirement: Constant directives seed a constant-value node and bridging edge
**Reason**: Constants are expansion-minted zero-port Operations.
**Migration**: See ADDED "Constant and default directives participate as bindings" and
`constant-values`.

### Requirement: Directive-seeded target chains
**Reason**: Target chains become per-level goal specs; no pre-created leaves.
**Migration**: See ADDED "Declared bindings derived per target level".

### Requirement: Directive-bridging edge
**Reason**: No bridging edges; the binding is the demand-context directive.
**Migration**: See `graph-expansion` ADDED "Directive context travels with the demand".

### Requirement: Directed acyclicity at seed time
**Reason**: Seeding creates no edges; acyclicity at seed time is vacuous.
**Migration**: Cycle posture is owned by `graph-expansion` (Horn well-foundedness).

### Requirement: Idempotent node and edge addition
**Reason**: Value dedup is the identity rule; there are no seed edges to dedup.
**Migration**: See `graph-model` ADDED "Value identity and dedup".

### Requirement: Umbrella child leaves are name-keyed demands, not single-type targets
**Reason**: No umbrella groups or pre-created leaves; the name-keyed demand is the goal spec.
**Migration**: See ADDED "Declared bindings derived per target level".

### Requirement: Seed stage assumes valid source parameters and drops no directive silently
**Reason**: Restated.
**Migration**: See ADDED "Seed-time validation preconditions".

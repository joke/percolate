# Seed Graph Spec

## Purpose

This spec defines the `SeedStage` that initialises the `MapperGraph` from discovered mapper method mappings. Seeding produces only **roots and goal specs**: a parameter-root `Value` per mapper parameter, a return-root `Value` per method, and per-level **declared bindings** (`{target child name → directive}`, derived from dotted `@Map` target paths, with constants and defaults included as bindings). It emits no edges, no groups, and no pre-created target leaves — the expansion engine resolves every demand from the roots against the goal.

## Requirements

### Requirement: Stage classes follow the *Stage naming convention

Every processor pipeline stage that `implements Stage` SHALL have a class name ending in `Stage`. In particular the seed stage SHALL be named `SeedStage` (renamed from `SeedGraph`), reflecting that it is a stage that initially populates the single `MapperGraph` and does **not** own a separate "seeded graph" artifact. Internal `*Phase` orchestration classes (which do not `implement Stage`) are exempt.

#### Scenario: All Stage implementations end in Stage
- **WHEN** every class implementing `Stage` under `processor/src/main/java/.../stages/` is inspected
- **THEN** each class name ends with the suffix `Stage`
- **AND** the seed stage class is named `SeedStage`

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

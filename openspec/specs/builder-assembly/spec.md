# Builder Assembly Spec

## Purpose

This spec defines how percolate assembles a target through a **builder** rather than a constructor: the
operation shape a builder strategy emits, the gate that decides whether it applies, the four conventions that
ship as built-in strategies, the codegen those strategies render, and the priced preference that settles a
target offering both an all-args constructor and a builder.

The governing constraint is that the expansion engine learns nothing. A builder call is a single expression
with N inputs — structurally identical to a constructor call — so builder support is expressed entirely as
one n-ary `OperationSpec` per assembly, carrying one sub-target port per declared child. That single-operation
shape is what makes totality enforceable: an unsatisfied port makes the plan partial, and partials dominate
weight in the cost fold, so a declared mapping can never be silently optimised away. No builder-specific SPI
type exists, which is why a company with an in-house convention ships its own strategy the same way the
built-ins do.

## Requirements

### Requirement: Builder assembly is a single n-ary operation

A builder-assembled target SHALL be produced by exactly **one** `OperationSpec` carrying one `Port.subTarget` per declared child and rendering one chained output expression. A builder strategy SHALL NOT decompose the builder into separate chained operations for the factory call, the individual setter calls, and the terminal `build()`.

The reason is structural, not stylistic. Totality is enforced only by sub-target ports on a single operation: an unsatisfied port makes the plan partial, and `Cost` is the lexicographic vector `(partials, weight)` in which partials dominate absolutely. If each setter were its own chained operation, omitting one would not be a partial — it would merely be a shorter, cheaper chain — so the minimum-cost hyperpath fold would silently drop a declared mapping. A `Builder → Builder` step also carries no target-slot identity, so declared children could not be attached to setter steps at all.

#### Scenario: Every declared child becomes a sub-target port of one operation
- **WHEN** a builder strategy matches a demand for `Person` whose declared children are `{name, age}`
- **THEN** it emits exactly one `OperationSpec` whose output type is `Person`
- **AND** that spec carries exactly two ports, `Port.subTarget("name", …)` and `Port.subTarget("age", …)`

#### Scenario: A declared child cannot be optimised out of the plan
- **WHEN** the plan for a builder-assembled `Person` with declared children `{name, age}` is extracted
- **AND** no producer exists for `age`
- **THEN** the operation is partial rather than a cheaper complete plan that omits the `age` setter
- **AND** the partial dominates weight in the cost comparison

#### Scenario: No per-setter operation is emitted
- **WHEN** the operations emitted for a builder-assembled demand are inspected
- **THEN** no `OperationSpec` has the builder type as its output type
- **AND** no `OperationSpec` represents a single setter call

### Requirement: The engine gains no knowledge of builders

The expansion engine, cost fold, plan extraction, and code-generation stage SHALL remain entirely free of builder vocabulary: a builder operation SHALL be indistinguishable from a constructor operation at the operation boundary. No engine class SHALL reference a builder, a builder convention, or the `percolate.construction.preference` option.

#### Scenario: The engine treats a builder operation like any other assembly
- **WHEN** the driver lands a builder strategy's `OperationSpec`
- **THEN** it binds each sub-target port exactly as it binds `ConstructorCall`'s ports, with no builder-specific branch

#### Scenario: No engine source names a builder
- **WHEN** the `processor` module's sources are inspected
- **THEN** no class in the expansion, extraction, or generation stages references a builder type, a builder convention, or `percolate.construction.preference`

### Requirement: Builder assembly gates on a subset of declared children

A builder strategy SHALL offer an operation only when the demand's declared-children name set is a **subset** of the names its convention resolves to single-value setter methods on the builder type. This differs deliberately from `ConstructorCall`, whose gate is set **equality**, because constructor parameters are mandatory while builder setters are optional.

A builder strategy SHALL preserve the empty-declaration bail: when the demand declares no children it SHALL offer nothing, so that the empty set never vacuously satisfies a leaf demand.

#### Scenario: A builder with unused setters still matches
- **WHEN** `Person` declares children `{name, age}` and its builder exposes setters for `name`, `age`, `email`, and `nickname`
- **THEN** the builder strategy offers an operation with sub-target ports for `name` and `age` only
- **AND** no setter call is rendered for `email` or `nickname`

#### Scenario: A declared child with no matching setter declines
- **WHEN** `Person` declares children `{name, nickname}` and its builder exposes a setter for `name` only
- **THEN** the builder strategy offers nothing for that demand

#### Scenario: An empty declaration never assembles
- **WHEN** a demand for a builder-bearing type declares no children
- **THEN** the builder strategy offers nothing, exactly as `ConstructorCall` bails on an empty declaration

### Requirement: Four builder conventions ship as built-in strategies

The `percolate-strategies-builtin` module SHALL ship four concrete `ExpansionStrategy` implementations in its assembly feature package, each registered with `@AutoService(ExpansionStrategy.class)` and each recognising one builder convention:

- `FluentBuilder` — a static no-argument `builder()` on the target, single-argument setters named exactly after the child (`name(v)`), and a no-argument `build()` returning the target. Covers Lombok `@Builder`, AutoValue, and most hand-written builders.
- `ProtobufBuilder` — a static no-argument `newBuilder()` on the target, setters named `setName(v)`, and a no-argument `build()` returning the target.
- `WithBuilder` — a static no-argument `builder()` on the target, setters named `withName(v)`, and a no-argument `build()` returning the target.
- `SideLocatedBuilder` — a separate builder type named `<Target>Builder` residing alongside the target, instantiated through a public no-argument constructor, setters named exactly after the child, and a no-argument `build()` returning the target.

Each strategy SHALL resolve the builder type, its setters, and its terminal method using only the existing `ResolveCtx` seam queries. The builder type, its entry point, its matched setters, and its `build()` method SHALL all be non-private.

#### Scenario: A Lombok-style builder is assembled
- **WHEN** `Person` exposes `static PersonBuilder builder()`, `PersonBuilder.name(String)`, `PersonBuilder.age(int)`, and `PersonBuilder.build()` returning `Person`
- **THEN** `FluentBuilder` offers an operation rendering `Person.builder().name(…).age(…).build()`

#### Scenario: A protobuf-style builder is assembled
- **WHEN** `Person` exposes `static Builder newBuilder()`, `Builder.setName(String)`, `Builder.setAge(int)`, and `Builder.build()` returning `Person`
- **THEN** `ProtobufBuilder` offers an operation rendering `Person.newBuilder().setName(…).setAge(…).build()`

#### Scenario: A with-style builder is assembled
- **WHEN** `Person` exposes `static PersonBuilder builder()` and `PersonBuilder.withName(String)`
- **THEN** `WithBuilder` offers an operation rendering `Person.builder().withName(…).build()`

#### Scenario: A side-located builder is assembled
- **WHEN** a type `MyClassBuilder` sits beside `MyClass`, has a public no-argument constructor, single-argument setters named after the children, and `build()` returning `MyClass`
- **THEN** `SideLocatedBuilder` offers an operation rendering `new MyClassBuilder().name(…).build()`

#### Scenario: A private builder member declines
- **WHEN** a candidate builder type, its entry point, or its `build()` method is private
- **THEN** no builder strategy offers an operation for that target

#### Scenario: Strategies read only the existing seam
- **WHEN** the four builder strategies' sources are inspected
- **THEN** every type and member question routes through `ResolveCtx`
- **AND** no new method is added to `ResolveCtx` on their behalf

### Requirement: Builder support introduces no builder-specific SPI type

The `percolate-spi` module SHALL NOT gain any builder-shaped type — no `Assembler`, `BuilderShape`, `BuilderMutator`, or equivalent — for this capability. Each builder strategy SHALL implement `ExpansionStrategy` directly.

A third party SHALL be able to add an in-house builder convention by shipping its own `@AutoService(ExpansionStrategy.class)` implementation on the processor path, with no core registration, no SPI change, and no engine change. Where the shipped strategies converge on common plumbing, a shared base MAY be extracted into `percolate-strategies-builtin`; it SHALL NOT be published from `percolate-spi` unless a third party demonstrably needs to subclass it.

#### Scenario: The SPI module gains no builder type
- **WHEN** `percolate-spi`'s public surface is inspected after this change
- **THEN** it declares no type whose name or documentation describes a builder

#### Scenario: A third-party convention needs no core change
- **WHEN** an external module ships an `ExpansionStrategy` recognising an in-house builder convention
- **THEN** it is discovered by `ServiceLoader` and participates in expansion with no change to `percolate-spi`, `percolate-processor`, or `percolate-strategies-builtin`

### Requirement: Builder codegen renders one wrapped chained expression

A builder strategy's `OperationCodegen` SHALL render a single expression chaining the entry point, one setter call per declared child, and the terminal `build()`. Every chain continuation SHALL carry a JavaPoet `$Z` wrap marker so long generated chains wrap at call boundaries.

Setter calls SHALL be emitted in the demand's declared-children iteration order, which is insertion-ordered, so generated output is deterministic across builds.

Builder arguments SHALL be hoisted to named locals by the existing hoist plan, with no builder-specific handling — an operation with two or more ports already qualifies.

#### Scenario: The chain carries wrap markers
- **WHEN** a builder operation renders with three declared children
- **THEN** a `$Z` wrap marker precedes each setter call and the terminal `build()` call

#### Scenario: Setter order is deterministic
- **WHEN** the same mapper is compiled twice with declared children `{name, age}`
- **THEN** both compilations render the setter calls in the same order

#### Scenario: Arguments hoist like constructor arguments
- **WHEN** a builder operation with two or more ports is generated
- **THEN** each port's feeding value materialises as a named local, exactly as for a constructor call

### Requirement: Constructor and builder assembly are arbitrated by a priced preference

When a target admits both constructor assembly and builder assembly, the choice SHALL be made by weight, driven by the `percolate.construction.preference` option, and never by an arbitrary tie-break. Each assembly strategy SHALL read the option and price **only itself**; no assembly strategy SHALL inspect, name, or depend on the existence of another.

Because the plan fold selects the **minimum** cost, the preferred form SHALL carry the **lower** weight: with `constructor` (the default) `ConstructorCall` weighs `Weights.STEP` and every builder strategy weighs `Weights.EXPENSIVE`; with `builder` the weights are exchanged. All assembly weights SHALL remain non-negative.

The preference SHALL be a preference and never an exclusion: when the preferred form does not match a target, the other form SHALL still be used rather than the mapping failing.

#### Scenario: The default prefers the constructor
- **WHEN** `Person` admits both a matching constructor and a matching builder and `percolate.construction.preference` is unset
- **THEN** the extracted plan uses the constructor

#### Scenario: The option flips the preference
- **WHEN** the same target is compiled with `-Apercolate.construction.preference=builder`
- **THEN** the extracted plan uses the builder

#### Scenario: The preference does not exclude the unpreferred form
- **WHEN** `-Apercolate.construction.preference=builder` is set and the target has no builder
- **THEN** the constructor is still used and the mapping succeeds

#### Scenario: Strategies price only themselves
- **WHEN** any assembly strategy's source is inspected
- **THEN** it derives its own weight from the option and references no other assembly strategy

### Requirement: Builder assembly is documented with a worked example

The manual SHALL carry a builder-assembly page owned by `percolate-strategies-builtin`, reachable from the navigation, showing each shipped convention by worked example and documenting the subset gate and the `percolate.construction.preference` switch. The page's example source and its shown generated output SHALL be single-sourced from a real compiling fixture with doc-tagged generated output.

#### Scenario: The page is reachable and module-owned
- **WHEN** the manual's navigation is inspected
- **THEN** it contains an entry for builder assembly
- **AND** the page source resides under `strategies-builtin/src/docs/`

#### Scenario: Shown output comes from real generation
- **WHEN** the generated output shown on the builder page is compared with the fixture's compiled output
- **THEN** they are the same text, included by tag rather than hand-typed

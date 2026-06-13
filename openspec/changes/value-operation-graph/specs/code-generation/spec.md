## ADDED Requirements

### Requirement: Method bodies render by walking the extracted plan

`BuildMethodBodies` SHALL compose each method body by walking the extracted plan view
(`plan-extraction`) from the method's return-root `Value`: render the Value's chosen producer
`Operation` by invoking its codegen with `IncomingValues` keyed by **port name**, where each
incoming value is the recursively rendered port `Value`. Producer identity is structural — the
generator SHALL NOT infer it from shared codegen instances, edge labels, or any
group/`ExpansionGroup` surface, and SHALL NOT read `Nullability` to decide wiring.

#### Scenario: Fan-in renders from the chosen producer
- **WHEN** the return-root's chosen producer is `new Address(int,String)` with ports `number`,
  `street`
- **THEN** the body renders that Operation's codegen once, with incoming values keyed `number` and
  `street`

#### Scenario: No group or label reads
- **WHEN** the source of `BuildMethodBodies` is inspected
- **THEN** it references no `ExpansionGroup`, no group id, and no edge-carried consumer slot

### Requirement: Child scopes render as lambda bodies

A scope-owning `Operation` in the plan SHALL render its child scope's extracted plan as the
per-element lambda body: the child param-root renders as the lambda parameter, the child return-root
expression is the lambda result, and the owning Operation's codegen weaves the container operation
around it.

#### Scenario: List element mapping renders a lambda
- **WHEN** the plan contains a `map` Operation for `List<A> → List<B>` owning a child scope
- **THEN** the generated body contains the container operation applied with a lambda whose body is
  the rendered child plan

### Requirement: Nullness handling renders as ordinary Operations

Code generation SHALL contain no nullability weaving: `[requireNonNull]` and `[coalesce]` are plan
Operations rendered through the same codegen contract as any other Operation. The generated output
for a `NULLABLE → NON_NULL` crossing remains `java.util.Objects.requireNonNull(expr, message)` with
the existing message format, or the coalescing form when the binding declares a default.

#### Scenario: Crossing renders via its Operation
- **WHEN** a nullable source feeds a non-null port without a default
- **THEN** the rendered expression is produced by the `[requireNonNull]` Operation's codegen, not by
  generator-side wrapping

## REMOVED Requirements

### Requirement: Method body composition algorithm
**Reason**: The fan-in/per-element/scalar case analysis reconstructed producer identity from edge
bundles; the plan walk reads it structurally.
**Migration**: See ADDED "Method bodies render by walking the extracted plan".

### Requirement: Slice 1 scope — single-segment paths and ConstructorCall-style group targets
**Reason**: Slice scoping referenced group targets; obsolete.
**Migration**: Superseded by the plan-walk requirements.

### Requirement: Nullability-aware slot wiring at GroupTarget composition
**Reason**: Replaced by explicit nullness Operations in the plan.
**Migration**: See ADDED "Nullness handling renders as ordinary Operations" and `nullability`.

### Requirement: Null-safe propagation form
**Reason**: Owned by the nullness-crossing Operations' codegen.
**Migration**: See ADDED "Nullness handling renders as ordinary Operations".

### Requirement: BuildMethodBodies injects NullabilityResolver
**Reason**: The generator no longer resolves nullness; crossings are decided at expansion time.
**Migration**: Resolver use moves entirely to expansion commit sites (see `nullability`).

### Requirement: Constant and defaulted producers compose through existing method-body composition
**Reason**: Constants are zero-port Operations and defaults are coalesce Operations; they compose
through the plan walk like everything else.
**Migration**: See ADDED "Method bodies render by walking the extracted plan", `constant-values`,
`default-values`.

### Requirement: A defaulted operand suppresses the non-null guard
**Reason**: Structural: a binding emits either `[coalesce]` or `[requireNonNull]`, never both.
**Migration**: See `default-values` and `nullability`.

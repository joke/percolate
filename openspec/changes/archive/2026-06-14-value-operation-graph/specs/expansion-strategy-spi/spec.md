## ADDED Requirements

### Requirement: OperationSpec result type

A strategy match SHALL produce an `OperationSpec`: the operation's codegen, its weight, its ordered
port signature (per port: name, declared `TypeMirror`, declared `Nullability`), the produced output
type and nullness, and optionally a child-scope declaration (container element mapping: element-in
and element-out types). The spec is plain data; the driver turns it into one atomic `AddOperation`
delta. Strategies receive no graph access and stay myopic.

#### Scenario: Spec is plain data
- **WHEN** a strategy returns an `OperationSpec`
- **THEN** it contains codegen, weight, ports, output typing, and optional child-scope declaration,
  and exposes no graph or engine surface

#### Scenario: Codegen render contract survives
- **WHEN** an Operation's codegen renders
- **THEN** it implements `render(VarNames, IncomingValues)` with incoming values keyed by port name

### Requirement: Demand decision context

Strategies SHALL receive a demand context exposing: the demanded Value's type and nullness, the
binding `Directive` in effect (carried by the work-list, see `graph-expansion`), the declared
bindings at the current target level (for assembly strategies), and the candidate snapshot of
in-scope Values. The context replaces the former frontier/`ExpansionGroup` surfaces.

#### Scenario: Assembly reads the goal spec from the context
- **WHEN** `ConstructorCall` matches a demand
- **THEN** it reads the declared-children name set from the demand context, not from a group

## REMOVED Requirements

### Requirement: ExpansionStep result type
**Reason**: Replaced by `OperationSpec` (operation-shaped, not edge-bundle-shaped).
**Migration**: See ADDED "OperationSpec result type".

### Requirement: Intent enum
**Reason**: `CONVERSION`/`BOUNDARY` selected between edge-fold and sub-group; both are uniformly
"add an Operation".
**Migration**: None needed; delete with `ExpansionStep`.

### Requirement: ElementScope enum
**Reason**: `ENTERING`/`EXITING` marked element-scope crossings on edges; crossings are owned by
scope-owning Operations.
**Migration**: See ADDED "OperationSpec result type" (child-scope declaration) and
`container-expansion`.

### Requirement: Slot result type
**Reason**: The consumer contract is the port signature inside `OperationSpec`.
**Migration**: See ADDED "OperationSpec result type".

### Requirement: Frontier decision context
**Reason**: Replaced by the demand context.
**Migration**: See ADDED "Demand decision context".

### Requirement: Candidate snapshot type
**Reason**: Restated over Values inside the demand context.
**Migration**: See ADDED "Demand decision context".

### Requirement: ConstructorCall built-in
**Reason**: Emission reshaped: one Operation per accessible constructor passing the goal-spec gate.
**Migration**: See `graph-expansion` ADDED "Assembly is gated by the declared-bindings goal spec";
matching logic and weights carry over.

### Requirement: DirectAssign built-in
**Reason**: Emission reshaped to a unary identity Operation; `isSameType` gating and weight carry
over.
**Migration**: See ADDED "OperationSpec result type".

### Requirement: MethodCallBridge built-in
**Reason**: Emission reshaped to a unary call Operation; discovery via `callable-method-discovery`
and weights carry over.
**Migration**: See ADDED "OperationSpec result type".

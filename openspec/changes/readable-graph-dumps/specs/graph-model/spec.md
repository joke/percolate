## MODIFIED Requirements

### Requirement: Operation vertex type

`Operation` SHALL represent a single production (constructor call, accessor, conversion, container
operation, constant), carrying a **human-readable `label`** (the strategy-supplied, fully-typed
production description, e.g. `int→long` or `new Address(int, String)`), its codegen, its weight, an
ordered **port signature** (per port: name, declared type, declared nullness — the former consumer
`Slot` contract), a **totality** flag (`total`/`partial`, where partial means the production may throw
on a structurally-valid input, e.g. `unwrap`/`orElseThrow` and `[requireNonNull]`), and optionally an
owned child `Scope` (container element mapping). It SHALL NOT carry a producing-strategy FQN. An
`Operation` is an AND over its ports: it is usable only when every port is fed.

#### Scenario: Operation owns the consumer contract
- **WHEN** code generation needs a port's declared type and nullness
- **THEN** it reads the Operation's port signature, never an edge label or an `ExpansionGroup`

#### Scenario: Operation label is the typed production, not a codegen class
- **WHEN** an Operation's `label` is read
- **THEN** it is the strategy-supplied production description (e.g. `int→long`), never a codegen
  lambda's class name
- **AND** the Operation exposes no `strategyFqn`

#### Scenario: Partial production is flagged
- **WHEN** an `unwrap` (`Optional.orElseThrow`) or `[requireNonNull]` Operation is added
- **THEN** it is flagged `partial`; a total production (e.g. `flatMap`, `[coalesce]`, a constructor)
  is flagged `total` (consumed by `plan-extraction` totality dominance)

#### Scenario: Zero-port Operations are valid
- **WHEN** a constant production is added
- **THEN** it is an `Operation` with an empty port signature and no inbound dependency edges

## REMOVED Requirements

### Requirement: VarNames placeholder type

**Reason**: `VarNames` was an empty marker reserved for future shared-Value hoisting but was threaded
through every `OperationCodegen.render` call and used by no strategy. The render contract drops it
(`render(IncomingValues)`) and the type is deleted.
**Migration**: Re-introduce a local-variable-naming handle (as a `render` parameter or a context type)
if and when shared-Value hoisting is implemented; until then no codegen surface references it.

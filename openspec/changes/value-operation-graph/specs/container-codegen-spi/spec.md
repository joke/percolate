## ADDED Requirements

### Requirement: Container codegen handles attach to Operations

Container codegen handles (per-paradigm snippet providers) SHALL attach to the container
`Operation`: the scope-owning Operation's handle weaves the container operation around the rendered
child-scope lambda; a wrap/unwrap Operation's handle renders inline like any scalar codegen. The
composer obtains every container `CodeBlock` from a handle and owns only the recursive plan walk —
adding a container still requires no engine or composer change.

#### Scenario: Handle weaves around the child lambda
- **WHEN** the composer renders a scope-owning container Operation
- **THEN** it obtains the iterate/collect (or map/orElse) snippets from the Operation's handle and
  inserts the rendered child plan as the lambda body

#### Scenario: One class per container still suffices
- **WHEN** a developer adds support for a new container type
- **THEN** one class provides both candidacy and the codegen handle, with no composer modification

## REMOVED Requirements

### Requirement: Composer container weaving
**Reason**: Weaving consumed `ElementScope`-marked edges and an `isStream` bit threaded through the
edge walk; the structure is now explicit (scope-owning Operation + child plan).
**Migration**: See ADDED "Container codegen handles attach to Operations".

### Requirement: Three orthogonal axes
**Reason**: The axes description was carrier-specific ("edge carries container provider+operation");
the substance (candidacy / paradigm / operation as independent concerns) survives on the Operation
carrier.
**Migration**: See ADDED "Container codegen handles attach to Operations".

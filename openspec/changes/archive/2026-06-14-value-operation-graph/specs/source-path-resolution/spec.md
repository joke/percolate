## ADDED Requirements

### Requirement: Path resolvers emit accessor Operations per segment

Source-path descent SHALL be demand-driven: the demand context's directive supplies the source
path, and each resolver (getter / method / field) emits one unary accessor `Operation` per resolved
segment, producing the segment's `Value` from its parent's. Resolver matching rules, accessibility
checks, and weights carry over unchanged.

#### Scenario: Two-segment path yields two accessor Operations
- **WHEN** the binding's source path is `address.street` from parameter `p`
- **THEN** the supply chain is `p → [getAddress()] → (address) → [getStreet()] → (street)` with each
  accessor an Operation carrying its resolver's weight

### Requirement: Parameter-root base case resolves against the Value's own scope

A single-segment source path SHALL bind directly to the typed parameter-root `Value` of the demand's
own method scope (never a global current-method), preserving multi-method correctness.

#### Scenario: Single-segment path binds the param root
- **WHEN** a binding's source is just `p`
- **THEN** the supply is the parameter-root Value of the method scope that owns the demand

## REMOVED Requirements

### Requirement: Path resolution as a unified ExpansionStrategy
**Reason**: Restated: resolvers emit accessor Operations instead of scaffolding-edge steps.
**Migration**: See ADDED "Path resolvers emit accessor Operations per segment".

### Requirement: Parameter-root base case resolves against the node's own method scope
**Reason**: Restated over Values.
**Migration**: See ADDED "Parameter-root base case resolves against the Value's own scope".

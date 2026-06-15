## MODIFIED Requirements

### Requirement: Path resolvers emit accessor Operations per segment

Source-path descent SHALL be the ordinary demand work-list over `ACCESS`-mode `SourceLocation`
demands: when the work-list processes a multi-segment `SourceLocation` demand, an accessor strategy
(getter / method / field) emits one unary accessor `Operation` for the last segment, producing that
segment's `Value` from its parent's, and the **parent** `SourceLocation` is re-demanded on the same
work-list. There SHALL be no eager whole-path materialisation, no driver-resident descent component,
and no descent-private strategy dispatch or memo; resolver matching rules, accessibility checks, and
weights carry over unchanged. A source value's type is resolved forward (from the parameter) by a
pure, non-mutating helper so the demand is typed at creation; this type resolution performs no graph
mutation and no strategy dispatch, so graph growth remains strictly target-to-source.

#### Scenario: Two-segment path yields two accessor Operations

- **WHEN** the binding's source path is `address.street` from parameter `p`
- **THEN** the supply chain is `p → [getAddress()] → (address) → [getStreet()] → (street)` with each
  accessor an Operation carrying its resolver's weight
- **AND** each accessor Operation is emitted by a path-resolver strategy as the work-list expands the
  corresponding `ACCESS` demand, not by an eager descent pass

### Requirement: Parameter-root base case resolves against the Value's own scope

A single-segment `SourceLocation` SHALL be a `LEAF` base case materialised **lazily** on first
reference — typed from the parameter declaration of the demand's **own** method scope (never a global
current-method), preserving multi-method correctness. It SHALL NOT be pre-seeded before expansion;
when nothing references a parameter, no `Value` for it exists.

#### Scenario: Single-segment path binds the lazily-materialised param leaf

- **WHEN** a binding's source is just `p`
- **THEN** the supply is the parameter-root `LEAF` `Value` of the method scope that owns the demand,
  created on first reference rather than pre-seeded

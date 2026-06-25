# Source Path Resolution Spec (delta)

## MODIFIED Requirements

### Requirement: Path resolvers emit accessor Operations per segment

Source-path descent SHALL be a **forward, target-bound** walk driven by the driver (see `graph-expansion`
"Forward target-bound descent walks a directive path"): the driver walks the named path root→leaf and at each
segment dispatches the accessor (`descend`) strategy set (getter / method / field), which emits one unary
accessor `Operation` producing that segment's `Value` from its parent's. Each parent segment's accessor SHALL be
landed **before** its child segment's, and the child's accessor SHALL be dispatched against the parent type
**read off the parent `Value` just landed** — never a separately predicted type. There SHALL be no backward
parent re-demand, no eager whole-path materialisation, no descent-private strategy dispatch or typing memo, and
**no forward typing helper**: a segment's type falls out of landing its accessor. Resolver matching rules,
accessibility checks, and weights carry over unchanged. When several resolvers match one segment, the driver
SHALL over-emit them all and plan extraction SHALL prune by weight — never `findFirst`/registration order.

#### Scenario: Two-segment path yields two accessor Operations, landed parent-first
- **WHEN** the binding's source path is `address.street` from parameter `p`
- **THEN** the supply chain is `p → [getAddress()] → (address) → [getStreet()] → (street)` with each accessor an
  Operation carrying its resolver's weight
- **AND** `getAddress()` is landed before `getStreet()`, and `getStreet()` is dispatched against the concrete
  type of the landed `address` `Value` — not a predicted type, and not via any `resolveAccessor` helper or typing
  memo

#### Scenario: An ambiguous segment prefers the getter by cost
- **WHEN** a segment `name` matches both `getName()` and a public field `name`
- **THEN** both accessor Operations are over-emitted and plan extraction selects `getName()` by its lower
  `STEP_GETTER` weight, independent of `ServiceLoader` order

### Requirement: Input-root base case resolves against the Value's own scope

A single-segment `SourceLocation` (and, in a child scope, the element root) SHALL be a `LEAF` base case
materialised **lazily** on first reference, typed from the **input declaration of the demand's own scope** —
never a global current-method, and **without branching on the scope kind**. For a method scope the declaration
comes from a parameter (typed from the signature); for a child scope it is the element input (typed by
grounding-at-land when the owning Operation lands). A forward descent SHALL root at this scope input and read the
root type from the scope's input declaration. Because a child scope is created only when its owning Operation
lands — after grounding — the root type is always concrete by the time any descent in that scope runs. There
SHALL be no scope-kind branch in descent-root typing, source-binding, or grounding, and **no surviving
`AccessorResolver` component**. An input SHALL NOT be pre-seeded before expansion; when nothing references it, no
`Value` for it exists.

#### Scenario: Single-segment path binds the lazily-materialised input leaf
- **WHEN** a binding's source is just `p`
- **THEN** the supply is the input-root `LEAF` `Value` of the scope that owns the demand, created on first
  reference rather than pre-seeded, resolved through the scope's input declaration

#### Scenario: Descent-root typing does not branch on scope kind
- **WHEN** a descent's root segment is typed in a method scope versus a child (element) scope
- **THEN** both read the root type from the scope's input declaration, with no `instanceof MethodScope` branch
  and no `AccessorResolver`

#### Scenario: A child-scope descent root is concrete at scope birth
- **WHEN** a container element transform whose element type was bound by grounding-by-match descends a path from
  the element root
- **THEN** the element root's type is already concrete (fixed when the owning Operation landed), so the descent
  reads it directly with no wait for grounding

## RENAMED Requirements

- FROM: `### Requirement: Parameter-root base case resolves against the Value's own scope`
- TO: `### Requirement: Input-root base case resolves against the Value's own scope`

## MODIFIED Requirements

### Requirement: Input-root base case resolves against the Value's own scope

A single-segment `SourceLocation` (and, in a child scope, the element root) SHALL be a `LEAF` base case
materialised **lazily** on first reference, typed from the **input declaration of the demand's own
scope** — never a global current-method, and **without branching on the scope kind**. For a method
scope the declarations come from the method's parameters; for a child scope the declaration is the
element input. Accessor-root typing SHALL resolve the path's first segment against the same per-scope
input declaration, so `AccessorResolver` carries no `instanceof MethodScope` branch. An input SHALL NOT
be pre-seeded before expansion; when nothing references it, no `Value` for it exists.

#### Scenario: Single-segment path binds the lazily-materialised input leaf
- **WHEN** a binding's source is just `p`
- **THEN** the supply is the input-root `LEAF` `Value` of the scope that owns the demand, created on
  first reference rather than pre-seeded, resolved through the scope's input declaration

#### Scenario: Accessor-root typing does not branch on scope kind
- **WHEN** an accessor path's first segment is typed in a method scope versus a child (element) scope
- **THEN** both resolve the root segment against the scope's input declaration, with no `instanceof MethodScope` branch

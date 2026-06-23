## MODIFIED Requirements

### Requirement: Scope interface and cases
The processor SHALL define a `Scope` interface — declaring `String encode()`, a `default Optional<Scope> parent()`, and a declaration of the scope's **base-case inputs** (a lazy sequence of scope-relative `(Location, type, nullness)` input declarations) — with three implementations forming a tree:
- `MapperScope` — the tree root, reserved for mapper-shared elements (e.g. routable methods); it declares no inputs.
- `MethodScope(ExecutableElement method)` — one per abstract mapper method; the scope of that method's Values and Operations; it declares one input per method parameter.
- `ChildScope` — an element scope owned by a scope-owning `Operation` (a container element mapping); its `parent()` is the owning Operation's scope and its `encode()` nests the owning Operation's id; it declares its single element input.

`Scope` SHALL produce a stable text-encoding (`encode()`) suitable for embedding into `GraphVertex.id()` and DOT cluster names. The input declaration carries only types and nullness (and the scope-relative location); materialising it into a `Value` is the driver's job, done lazily.

#### Scenario: Method scope encodes the method signature
- **WHEN** a `MethodScope` is constructed for an `ExecutableElement` representing `Human map(Person person)`
- **THEN** its text-encoding is a stable string derived from the method name and its parameter type strings (each parameter's `TypeMirror.toString()`, comma-joined — e.g., `map(Person)`) and is identical for repeated invocations

#### Scenario: Child scope nests its owning Operation
- **WHEN** a scope-owning Operation lands and owns a `ChildScope`
- **THEN** the child scope's `encode()` includes the owning Operation's id and its `parent()` is the Operation's scope

#### Scenario: Each scope declares its base-case inputs
- **WHEN** a `MethodScope`, a `ChildScope`, and the mapper-root scope are asked for their input declarations
- **THEN** the method scope yields one declaration per parameter, the child scope yields its single element declaration, and the mapper-root scope yields none

### Requirement: Scope tree and child-scope ownership

`Scope`s SHALL form a tree: method scopes under the mapper scope, and element scopes owned by
scope-owning `Operation`s. **No `Dep` edge crosses a scope boundary**; the only coupling between a
child scope and its parent is the owning `Operation` (outer ports in the parent scope, and the child's
roots inside it).

When a scope-owning `Operation` lands, its child scope's **return-root** `Value` (the `FREE` demand the
Operation folds for cost) SHALL be minted eagerly, while its **element input** SHALL be a
lazily-materialised `LEAF` source like any other scope input — it is **declared, not force-minted** (see
graph-expansion "Scopes declare base-case inputs uniformly"). The graph SHALL expose the
no-`Dep`-crosses-scope invariant as a checkable assertion.

#### Scenario: Scope-crossing edge is rejected
- **WHEN** a mutation would connect a parent-scope Value directly to a child-scope vertex
- **THEN** the mutation is rejected by the scope invariant check

#### Scenario: An unreferenced child element input is not minted
- **WHEN** a scope-owning Operation lands but its child plan never sources from the element
- **THEN** the child return-root `Value` exists but no element param-root `Value` is minted

## MODIFIED Requirements

### Requirement: Value vertex type

`Value` represents a typed variable: it SHALL carry a `Location`, a `Scope`, an optional
`TypeRef` type, and an optional `Nullability` nullness. Type and nullness are write-once
(unknown → determined → frozen), set together at the single mutation site. `Value` SHALL NOT carry
group labels, directives, codegen, or weight. A `Value` is an OR over its inbound producer
`Operation`s.

Dedup identity (`MapperGraph.valueFor`'s get-or-create key over `(scope, location, type, nullness)`)
SHALL rest on the **value equality of `TypeRef`** — not on `toString`/string-encoded type keys. The
stable `id()` text-encoding remains for debug output, derived from the `TypeRef` rendering.

#### Scenario: Typing is write-once
- **WHEN** `setTyping` is invoked on an already-typed Value
- **THEN** an `IllegalStateException` is raised

#### Scenario: Value carries no engine bookkeeping
- **WHEN** the public surface of `Value` is inspected
- **THEN** it exposes no group membership, no directive, no codegen, and no weight

#### Scenario: Dedup keys on type value-equality
- **WHEN** two demands arrive for the same `(scope, location)` with independently-constructed but equal `TypeRef`s and equal nullness
- **THEN** `valueFor` returns the same `Value` instance, without comparing type `toString()` renderings

### Requirement: Scope interface and cases

The processor SHALL define a `Scope` interface — declaring `String encode()`, a `default Optional<Scope> parent()`, and a declaration of the scope's **base-case inputs** (a lazy sequence of scope-relative `(Location, type, nullness)` input declarations) — with three implementations forming a tree:
- `MapperScope` — the tree root, reserved for mapper-shared elements (e.g. routable methods); it declares no inputs.
- `MethodScope` — one per abstract mapper method, constructed over the method's model signature (`MethodSig`); the scope of that method's Values and Operations; it declares one input per method parameter.
- `ChildScope` — an element scope owned by a scope-owning `Operation` (a container element mapping); its `parent()` is the owning Operation's scope and its `encode()` nests the owning Operation's id; it declares its single element input.

`Scope` SHALL produce a stable text-encoding (`encode()`) suitable for embedding into `GraphVertex.id()` and DOT cluster names, derived from model values (the `MethodSig` name and its parameter `TypeRef` renderings) — no `javax.lang.model` reference is held or walked. The input declaration carries only types and nullness (and the scope-relative location); materialising it into a `Value` is the driver's job, done lazily.

#### Scenario: Method scope encodes the method signature
- **WHEN** a `MethodScope` is constructed for the model signature of `Human map(Person person)`
- **THEN** its text-encoding is a stable string derived from the method name and its parameter `TypeRef` renderings (comma-joined — e.g., `map(Person)`) and is identical for repeated invocations

#### Scenario: Child scope nests its owning Operation
- **WHEN** a scope-owning Operation lands and owns a `ChildScope`
- **THEN** the child scope's `encode()` includes the owning Operation's id and its `parent()` is the Operation's scope

#### Scenario: Each scope declares its base-case inputs
- **WHEN** a `MethodScope`, a `ChildScope`, and the mapper-root scope are asked for their input declarations
- **THEN** the method scope yields one declaration per parameter, the child scope yields its single element declaration, and the mapper-root scope yields none

## MODIFIED Requirements

### Requirement: Scope interface and cases
The processor SHALL define a `Scope` interface — declaring `String encode()`, a `default Optional<Scope> parent()`, and a declaration of the scope's **base-case inputs** (a lazy sequence of scope-relative input declarations, each carrying a `Location`, a type, a resolved nullness, a **name**, and a **visibility** of `LOCAL` or `INHERITED`) — with three implementations forming a tree:
- `MapperScope` — the tree root, reserved for mapper-shared elements (e.g. routable methods); it declares no inputs.
- `MethodScope(ExecutableElement method)` — one per abstract mapper method; the scope of that method's Values and Operations; it declares one input per method parameter, named after the parameter.
- `ChildScope` — an element scope owned by a scope-owning `Operation` (a container element mapping); its `parent()` is the owning Operation's scope and its `encode()` nests the owning Operation's id; it declares its single element input.

There SHALL be exactly one input-declaration type and one declaration stream per scope. `Scope` SHALL be plain data: it SHALL NOT accept a nullness-resolving callback, and an input declaration SHALL carry its nullness already resolved.

`Scope` SHALL produce a stable text-encoding (`encode()`) suitable for embedding into `GraphVertex.id()` and DOT cluster names. The input declaration carries only the location, name, visibility, type and nullness; materialising it into a `Value` is the driver's job, done lazily.

#### Scenario: Method scope encodes the method signature
- **WHEN** a `MethodScope` is constructed for an `ExecutableElement` representing `Human map(Person person)`
- **THEN** its text-encoding is a stable string derived from the method name and its parameter type strings (each parameter's `TypeMirror.toString()`, comma-joined — e.g., `map(Person)`) and is identical for repeated invocations

#### Scenario: Child scope nests its owning Operation
- **WHEN** a scope-owning Operation lands and owns a `ChildScope`
- **THEN** the child scope's `encode()` includes the owning Operation's id and its `parent()` is the Operation's scope

#### Scenario: Each scope declares its base-case inputs
- **WHEN** a `MethodScope`, a `ChildScope`, and the mapper-root scope are asked for their input declarations
- **THEN** the method scope yields one declaration per parameter, the child scope yields its single element declaration, and the mapper-root scope yields none

#### Scenario: There is one declaration stream
- **WHEN** the `Scope` interface is inspected
- **THEN** it declares exactly one input-declaration method, and no second stream for named or inherited declarations

#### Scenario: Scope takes no resolver callback
- **WHEN** the `Scope` interface's method signatures are inspected
- **THEN** none accepts a function resolving nullness, and no `Scope` implementation reads an annotation

### Requirement: Value vertex type

`Value` represents a typed variable: it SHALL carry a `Location`, a `Scope`, an optional
`TypeMirror` type, an optional `Nullability` nullness, and the ordered list of **refusals** recorded against it
(productions considered and found inadmissible, each carrying an opaque position handle and a message). Type and
nullness are write-once (unknown → determined → frozen), set together at the single mutation site. `Value` SHALL
NOT carry group labels, directives, codegen, or weight. A `Value` is an OR over its inbound producer
`Operation`s; its refusals are not producers and take no part in the cost fold.

The refusal shape SHALL be feature-neutral: it SHALL name no strategy, annotation, or user-facing concept, and
SHALL be the single channel by which an inadmissible production is remembered.

#### Scenario: Typing is write-once
- **WHEN** `setTyping` is invoked on an already-typed Value
- **THEN** an `IllegalStateException` is raised

#### Scenario: Value carries no engine bookkeeping
- **WHEN** the public surface of `Value` is inspected
- **THEN** it exposes no group membership, no directive, no codegen, and no weight

#### Scenario: Refusals are not producers
- **WHEN** a `Value` carries both producers and refusals
- **THEN** the cost fold and plan extraction consider only the producers

#### Scenario: The refusal shape names no feature
- **WHEN** the refusal type is inspected
- **THEN** it carries only a position handle and a message, and no field, method, or parameter naming a strategy, annotation, or feature

## ADDED Requirements

### Requirement: MapperGraph SHALL NOT accumulate per-feature collections

`MapperGraph` SHALL expose no collection, field, method, or parameter introduced to serve one feature's
diagnostics or bookkeeping. Anything a feature needs to remember about a demand SHALL be recorded on the
`Value` through the single feature-neutral refusal channel.

#### Scenario: The graph declares no feature-named collection
- **WHEN** `MapperGraph`'s members are enumerated
- **THEN** none is named after an annotation member, a strategy, or a user-facing feature

#### Scenario: A new feature adds no graph member
- **WHEN** a feature needs to remember why a production was unavailable
- **THEN** it records a refusal on the demanded `Value` and adds nothing to `MapperGraph`

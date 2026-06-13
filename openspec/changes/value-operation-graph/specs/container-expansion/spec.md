## ADDED Requirements

### Requirement: Element mapping is a scope-owning Operation

A container element mapping (`List<A> → List<B>`, `Set`, array, `Optional.map`) SHALL be one
`Operation` that owns a child `Scope`: its outer port is the source container Value in the parent
scope; the child scope contains an element param-root Value (`elem:A`, base-case SAT within the
child) and an element return-root demand (`elem:B`). The child demand expands on the same work-list
with candidate search confined to the child scope. The Operation is SAT iff its outer ports and the
child return-root are SAT.

#### Scenario: Child demand expands like a method body
- **WHEN** a `List<A> → List<B>` element mapping is emitted
- **THEN** the demand `elem:B` joins the work-list and resolves against the child scope's
  param-root, exactly as a method return-root resolves against method parameters

#### Scenario: Nested containers nest scopes
- **WHEN** the target is `List<List<B>>` from `List<List<A>>`
- **THEN** the plan contains a scope-owning Operation whose child plan contains another scope-owning
  Operation

### Requirement: Wrap and unwrap are plain Operations

Wrapping (`Optional.of`, singleton collection) and unwrapping (element get) SHALL be plain unary
Operations with no child scope. The wrap-versus-element-mapping distinction is structural (plain
Operation vs scope-owning Operation), not an SPI mode.

#### Scenario: Wrap emits no child scope
- **WHEN** `T → Optional<T>` is produced by wrapping
- **THEN** the emitted Operation declares no child scope

### Requirement: Scope-ownership invariant for containers

No dependency edge SHALL cross a container child-scope boundary; the owning Operation is the only
coupling (see `graph-model` "Scope tree and child-scope ownership"). This replaces the former
strictly-linear REALISED-chain invariant.

#### Scenario: Element values stay inside the child scope
- **WHEN** the child plan for an element mapping is extracted
- **THEN** every vertex it contains belongs to the child scope, and the parent plan references it
  only through the owning Operation

## REMOVED Requirements

### Requirement: Container strategies bind to ExpansionStrategy via ContainerMatch
**Reason**: Container strategies emit Operation specs (scope-owning or plain) instead of
`BOUNDARY` steps with `ElementScope` markers; the one-class-per-container shape survives.
**Migration**: See ADDED "Element mapping is a scope-owning Operation" and "Wrap and unwrap are
plain Operations".

### Requirement: Linear container chain (no diamond)
**Reason**: The no-diamond invariant policed edge-bundle ambiguity; scope ownership makes the
structure unambiguous.
**Migration**: See ADDED "Scope-ownership invariant for containers".

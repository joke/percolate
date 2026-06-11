## MODIFIED Requirements

### Requirement: Engine stamps Nullability paired with Node typing

Whenever the expansion engine calls `Node.setTyping(TypeMirror, Nullability)` (see the `graph-model` capability for the paired one-shot accessor), the `Nullability` value SHALL be obtained from `NullabilityResolver.resolve(typeMirror, scopeElement)` where:

- `typeMirror` is the type being assigned to the Node.
- `scopeElement` is the `Element` whose lexical context anchors the JSpecify scope walk — typically the underlying `ExecutableElement` (for callable-method matches), `VariableElement` (for parameters, fields, slot consumer Elements), or the enclosing `TypeElement`.

**Exception — intrinsically non-null producers.** A constant-value node (see `constant-values`) and a default-coalesced producer (see `default-values`) have no underlying `AnnotatedConstruct` to resolve: a literal and a source-or-default coalesce are non-null by construction. For these the engine SHALL stamp `Nullability.NON_NULL` **directly**, without invoking `NullabilityResolver`. This is the only typing site where the stamped value is not resolver-obtained.

Strategy code SHALL NOT pre-compute, look up, or otherwise reason about nullability. Strategies surface the `AnnotatedConstruct` they matched on their result types (see the `source-path-resolution` and `expansion-strategy-spi` capabilities); the engine performs the resolver invocation (or, for the intrinsic producers above, stamps `NON_NULL`).

#### Scenario: Engine pairs setTyping with a resolver call at annotated typing sites
- **WHEN** the source of every class in `processor/src/main/java/io/github/joke/percolate/processor/stages/expand/` is inspected
- **THEN** every call to `Node.setTyping(...)` for a node backed by an `AnnotatedConstruct` passes a `Nullability` argument obtained from `NullabilityResolver.resolve(...)`

#### Scenario: Constant-value node is stamped NON_NULL without a resolver call
- **WHEN** the engine types a constant-value node from its demanded target type
- **THEN** it stamps `Nullability.NON_NULL`
- **AND** it does not invoke `NullabilityResolver.resolve(...)` for that node

#### Scenario: Default-coalesced producer is stamped NON_NULL without a resolver call
- **WHEN** the engine types a default-coalesced producer
- **THEN** it stamps `Nullability.NON_NULL`
- **AND** it does not invoke `NullabilityResolver.resolve(...)` for that producer

#### Scenario: No strategy class calls NullabilityResolver
- **WHEN** the source of every class under `spi/src/main/java/` and `strategies-builtin/src/main/java/` is inspected
- **THEN** no source line invokes `NullabilityResolver.resolve(...)`

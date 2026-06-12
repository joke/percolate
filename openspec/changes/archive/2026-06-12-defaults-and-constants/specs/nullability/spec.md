## MODIFIED Requirements

### Requirement: Engine stamps Nullability paired with Node typing

Whenever the expansion engine calls `Node.setTyping(TypeMirror, Nullability)` (see the `graph-model` capability for the paired one-shot accessor), the `Nullability` value SHALL be obtained from `NullabilityResolver.resolve(typeMirror, scopeElement)` where:

- `typeMirror` is the type being assigned to the Node.
- `scopeElement` is the `Element` whose lexical context anchors the JSpecify scope walk — typically the underlying `ExecutableElement` (for callable-method matches), `VariableElement` (for parameters, fields, slot consumer Elements), or the enclosing `TypeElement`.

**Exception — the constant-value node.** A constant-value node (see `constant-values`) has no underlying `AnnotatedConstruct` to resolve: a literal is non-null by construction. For it the engine SHALL stamp `Nullability.NON_NULL` **directly**, without invoking `NullabilityResolver`. This is the only typing site where the stamped value is not resolver-obtained.

A default-coalesced value (see `default-values`) is likewise non-null by construction, but it introduces **no separate producer node**: the coalesce is rendered onto the directive's **target node**, whose nullability is that target's own resolver-obtained typing (`NON_NULL` for a non-null target). Because the coalesce can never yield null, a defaulted operand feeding a `NON_NULL` slot is `NON_NULL → NON_NULL` and needs no guard; the engine performs no extra stamp for it.

Strategy code SHALL NOT pre-compute, look up, or otherwise reason about nullability. Strategies surface the `AnnotatedConstruct` they matched on their result types (see the `source-path-resolution` and `expansion-strategy-spi` capabilities); the engine performs the resolver invocation (or, for the intrinsic producers above, stamps `NON_NULL`).

#### Scenario: Engine pairs setTyping with a resolver call at annotated typing sites
- **WHEN** the source of every class in `processor/src/main/java/io/github/joke/percolate/processor/stages/expand/` is inspected
- **THEN** every call to `Node.setTyping(...)` for a node backed by an `AnnotatedConstruct` passes a `Nullability` argument obtained from `NullabilityResolver.resolve(...)`

#### Scenario: Constant-value node is stamped NON_NULL without a resolver call
- **WHEN** the engine types a constant-value node from its demanded target type
- **THEN** it stamps `Nullability.NON_NULL`
- **AND** it does not invoke `NullabilityResolver.resolve(...)` for that node

#### Scenario: A default-coalesced operand feeding a non-null target needs no guard
- **WHEN** the engine renders a default-coalesced value into a `NON_NULL` target slot
- **THEN** the operand is the coalesce expression with no `Objects.requireNonNull` guard
- **AND** the coalesce introduces no separate producer node, so the engine performs no extra `NON_NULL` stamp beyond the target node's own resolver-obtained typing

#### Scenario: No strategy class calls NullabilityResolver
- **WHEN** the source of every class under `spi/src/main/java/` and `strategies-builtin/src/main/java/` is inspected
- **THEN** no source line invokes `NullabilityResolver.resolve(...)`

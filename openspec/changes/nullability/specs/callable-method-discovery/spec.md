## ADDED Requirements

### Requirement: MethodCandidate is the nullability source for callable methods

`MethodCandidate.method` (the `ExecutableElement` already exposed by today's discovery) SHALL be the authoritative source for callable-method nullability derivation. When the expansion engine commits a callable-method match (typing a slot Node via the callable's return type), it SHALL invoke `NullabilityResolver.resolve(method.getReturnType(), method)` to derive the produced value's `Nullability`. No new field is added to `MethodCandidate`.

Parameter nullability of the callable's input is similarly derived on demand from `method.getParameters().get(i)` (a `VariableElement`) when the engine needs to type the callable's input slot.

The `DiscoverCallableMethods` stage SHALL NOT precompute, cache, or attach any `Nullability` data to the candidate index. Nullability resolution is engine-internal and deferred to producer-commit time.

#### Scenario: MethodCandidate exposes ExecutableElement unchanged
- **WHEN** the source of `MethodCandidate` is inspected
- **THEN** it exposes exactly the two fields documented today (`method`, `receiver`) plus their Lombok-generated accessors
- **AND** no `nullability` or `producedFrom` field is added

#### Scenario: Engine derives return-type nullability from MethodCandidate.method
- **WHEN** the expansion engine commits a callable-method match for `MethodCandidate(method, receiver)` and types the target slot Node
- **THEN** the slot Node's `setTyping(method.getReturnType(), nullability)` is invoked
- **AND** `nullability` equals `NullabilityResolver.resolve(method.getReturnType(), method)`

#### Scenario: DiscoverCallableMethods produces no Nullability state
- **WHEN** `DiscoverCallableMethods.run(ctx)` completes
- **THEN** the produced `CallableMethods` instance carries no precomputed `Nullability` values
- **AND** no class in `processor/src/main/java/io/github/joke/percolate/processor/stages/discover/` imports `Nullability` or `NullabilityResolver`

## MODIFIED Requirements

### Requirement: Slot result type

The percolate-spi module SHALL define an immutable Lombok `@Value` type `io.github.joke.percolate.spi.Slot` with these fields, in this order:
- `String name` — the slot's binding name (matches a directive target tail or a builder-method name).
- `TypeMirror type` — the slot's required type. Used by the engine to drive target-to-source candidate search; SHALL NOT be assumed by callers to equal the realised producer's actual type post-commit.
- `int weight` — the cost of filling the slot.
- `AnnotatedConstruct producedFrom` — the underlying consumer-side `Element` (the constructor parameter, setter parameter, or field that the slot represents). The engine consults this at code-generation time to derive the consumer's nullability contract via `NullabilityResolver`.

`producedFrom` enables consumer-contract derivation without leaking nullability into the strategy SPI: strategy authors simply pass the `VariableElement` they already have in hand when constructing slots; they do not reason about nullability themselves.

#### Scenario: Slot exposes its four fields
- **WHEN** a `Slot` is constructed with `name`, `type`, `weight`, and `producedFrom`
- **THEN** `getName()`, `getType()`, `getWeight()`, and `getProducedFrom()` return those values

#### Scenario: Slot.producedFrom is the consumer-side Element
- **WHEN** `ConstructorCall` constructs a `Slot` for parameter index `i` of constructor `ctor`
- **THEN** the slot's `producedFrom` is `ctor.getParameters().get(i)` (a `VariableElement`)

#### Scenario: Slot is value-equal
- **WHEN** two `Slot` instances are constructed with equal `name`, `type`, `weight`, and `producedFrom`
- **THEN** they are `equal` and have equal `hashCode`s

## ADDED Requirements

### Requirement: ResolvedSegment carries producedFrom

The `ResolvedSegment` type defined by the `source-path-resolution` capability SHALL grow an additional `AnnotatedConstruct producedFrom` field surfacing the underlying `Element` (the getter `ExecutableElement`, the field `VariableElement`, etc.) that the resolver matched. See the `source-path-resolution` capability spec for the full updated requirement.

#### Scenario: ResolvedSegment cross-reference is consistent
- **WHEN** the `source-path-resolution` capability's "ResolvedSegment result type" requirement is inspected
- **THEN** it declares a `producedFrom` field of type `AnnotatedConstruct`

## ADDED Requirements

### Requirement: CallableMethods exposes producing(TypeRef) only

The `CallableMethods` interface SHALL expose a single query method:

```java
public interface CallableMethods {
    Stream<MethodCandidate> producing(TypeRef outputType);
}
```

`producing(outputType)` SHALL return every `MethodCandidate` whose method's return type is assignable to
`outputType` (covariant return, per the `TypeSpace` assignability walk). Strategies SHALL receive an empty
stream if no candidate matches.

`CallableMethods` SHALL NOT expose any other query methods, mutation methods, or accessors. A
`forward-walk` query (e.g. `accepting(inputType)`) is deliberately deferred — no v1 strategy needs it.

#### Scenario: producing returns a candidate whose return type is the queried type
- **WHEN** `callableMethods.producing(<Pet>)` is invoked and the index contains `Pet adopt(Dog d)`
- **THEN** the returned stream contains the corresponding `MethodCandidate`

#### Scenario: producing matches covariantly
- **WHEN** `callableMethods.producing(<Animal>)` is invoked and the index contains `Pet adopt(Dog d)` (where `Pet` is assignable to `Animal` in the `TypeSpace`)
- **THEN** the returned stream contains the `MethodCandidate` for `adopt(Dog)`

#### Scenario: producing returns empty when no candidate matches
- **WHEN** `callableMethods.producing(<UnrelatedType>)` is invoked and no candidate's return type is assignable to `UnrelatedType`
- **THEN** the returned stream is empty

#### Scenario: CallableMethods has no other public methods
- **WHEN** the source of the `CallableMethods` interface is inspected
- **THEN** the interface declares exactly one method (`producing`) and no default methods

## MODIFIED Requirements

### Requirement: Discovery walks the @Mapper interface's full linearisation

`DiscoverCallableMethodsStage` SHALL obtain the complete linearisation of methods on the
`@Mapper`-annotated type, including those inherited from super-interfaces, via the boundary
`Elements.getAllMembers(mapperType)` walk (discovery is the adapter — the one place `javax.lang.model`
is read), and SHALL express each surviving method as a model `MethodSig` value.

The stage SHALL NOT walk methods declared on any other types — including types that appear as method
parameters, return types, or fields of the mapper. Discovery is strictly scoped to the current `@Mapper`
interface and its inherited methods.

#### Scenario: Locally declared methods are discovered
- **WHEN** the `@Mapper` interface declares `Human map(Person p)` directly
- **THEN** the produced index contains a `MethodCandidate` whose `method()` is the `MethodSig` for `map(Person)`

#### Scenario: Methods inherited from super-interfaces are discovered
- **WHEN** `@Mapper interface DogMapper extends BaseMapper` and `BaseMapper` declares `Pet adopt(Dog d)`
- **THEN** the produced index contains a `MethodCandidate` whose `method()` is the inherited `adopt(Dog)`

#### Scenario: Methods on parameter or return types are NOT discovered
- **WHEN** the mapper declares `Human map(Person p)` and `Person` itself declares `String getLastName()`
- **THEN** the produced index does NOT contain a `MethodCandidate` for `Person.getLastName()`

### Requirement: MethodCandidate carries method and receiver

`MethodCandidate` SHALL be an immutable Lombok `@Value` type with two fields:

- `MethodSig method` — the method's model signature (name, parameters, return `TypeRef`, resolved
  nullness, `Origin`).
- `Receiver receiver` — an abstraction over the call expression's receiver (e.g. `this`).

`MethodCandidate` SHALL NOT carry pre-computed weights; consumers compute weight from the candidate's
signature and the bridge query.

#### Scenario: MethodCandidate exposes both fields
- **WHEN** a `MethodCandidate` is constructed with a method signature and receiver
- **THEN** `getMethod()` and `getReceiver()` return those values

#### Scenario: MethodCandidate is value-equal
- **WHEN** two `MethodCandidate` instances are constructed with equal method signature and receiver
- **THEN** they are `equal` and have equal `hashCode`s

### Requirement: MethodCandidate is the nullability source for callable methods

`MethodCandidate.method` (a `MethodSig`) SHALL be the authoritative source for callable-method
nullability: its return nullness and parameter nullness are **resolved once at the discovery boundary**
(where `NullabilityResolver` has the real `TypeMirror`/`Element` in hand) and carried as data on the
signature, per the `type-model` capability. When `MethodCallBridge` produces a callable-method
`OperationSpec`, it SHALL read the produced value's `Nullability` from the signature's return nullness and
the input port's `Nullability` from the signature's sole parameter — no downstream call into
`NullabilityResolver` and no re-derivation.

#### Scenario: Bridge reads return nullness from the signature
- **WHEN** `MethodCallBridge` produces a callable-method `OperationSpec` for `MethodCandidate(method, receiver)`
- **THEN** the spec's output nullness equals the `MethodSig`'s resolved return nullness — the value `NullabilityResolver.resolve` produced at discovery time

#### Scenario: Nullability is resolved exactly once, at the boundary
- **WHEN** the engine and strategy sources are inspected
- **THEN** no class outside the processor boundary invokes `NullabilityResolver`; signature nullness is consumed as plain data

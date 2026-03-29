## ADDED Requirements

### Requirement: DirectAssignableStrategy resolves assignable types
The `DirectAssignableStrategy` SHALL check `Types.isAssignable(sourceType, targetType)`. If assignable, it SHALL return a `TransformProposal` with an identity `CodeTemplate` (returns input unchanged). This replaces the hardcoded `isAssignable` check in the current resolver.

#### Scenario: Same type resolves as direct
- **WHEN** source type is `String` and target type is `String`
- **THEN** the strategy SHALL return a proposal with identity code template

#### Scenario: Subtype resolves as direct
- **WHEN** source type is `String` and target type is `Object`
- **THEN** the strategy SHALL return a proposal with identity code template

#### Scenario: Non-assignable types return empty
- **WHEN** source type is `Person` and target type is `PersonDTO` and they are not assignable
- **THEN** the strategy SHALL return `Optional.empty()`

### Requirement: MethodCallStrategy resolves via mapper methods
The `MethodCallStrategy` SHALL inspect all mapping methods on the mapper interface. If a method exists whose source parameter type is assignable from the given source type and whose return type is assignable to the given target type, the strategy SHALL return a `TransformProposal` with a `CodeTemplate` that emits a method call. The strategy SHALL NOT match the method currently being resolved (to prevent self-referential loops).

#### Scenario: Sibling method resolves type gap
- **WHEN** source type is `Person`, target type is `PersonDTO`, and the mapper has method `PersonDTO map(Person)`
- **THEN** the strategy SHALL return a proposal with code template emitting `map($INPUT)`

#### Scenario: No matching method returns empty
- **WHEN** source type is `Foo`, target type is `Bar`, and no mapper method converts between them
- **THEN** the strategy SHALL return `Optional.empty()`

#### Scenario: Self-referential method excluded
- **WHEN** resolving transforms for method `Set<PersonDTO> map(List<Person>)` and checking if any method handles `List<Person>` → `Set<PersonDTO>`
- **THEN** the strategy SHALL NOT match the method currently being resolved

### Requirement: StreamFromCollectionStrategy streams collection sources
The `StreamFromCollectionStrategy` SHALL check if the source type is assignable to `Collection` or `Iterable`. If so, it SHALL return a `TransformProposal` producing `Stream<T>` (where `T` is the element type). For `Collection` subtypes, the code template SHALL emit `$INPUT.stream()`. For bare `Iterable`, it SHALL emit `StreamSupport.stream($INPUT.spliterator(), false)`.

#### Scenario: List source produces stream
- **WHEN** source type is `List<Person>` and target type requires `Stream<Person>`
- **THEN** the strategy SHALL propose `Stream<Person>` with code template `$INPUT.stream()`

#### Scenario: Set source produces stream
- **WHEN** source type is `Set<String>` and target type requires `Stream<String>`
- **THEN** the strategy SHALL propose `Stream<String>` with code template `$INPUT.stream()`

#### Scenario: Iterable source produces stream via StreamSupport
- **WHEN** source type is `Iterable<Person>`
- **THEN** the strategy SHALL propose `Stream<Person>` with code template `StreamSupport.stream($INPUT.spliterator(), false)`

#### Scenario: Non-collection source returns empty
- **WHEN** source type is `String`
- **THEN** the strategy SHALL return `Optional.empty()`

### Requirement: CollectToListStrategy collects stream to List
The `CollectToListStrategy` SHALL check if the target type is `List<T>`. If so, it SHALL return a `TransformProposal` requiring `Stream<T>` as input with code template `$INPUT.collect(Collectors.toList())`.

#### Scenario: Stream to List
- **WHEN** target type is `List<PersonDTO>` and source is streamable
- **THEN** the strategy SHALL propose requiring `Stream<PersonDTO>` with code template `$INPUT.collect($T.toList())` referencing `Collectors.class`

#### Scenario: Non-List target returns empty
- **WHEN** target type is `Set<PersonDTO>`
- **THEN** `CollectToListStrategy` SHALL return `Optional.empty()`

### Requirement: CollectToSetStrategy collects stream to Set
The `CollectToSetStrategy` SHALL check if the target type is `Set<T>`. If so, it SHALL return a `TransformProposal` requiring `Stream<T>` as input with code template `$INPUT.collect(Collectors.toSet())`.

#### Scenario: Stream to Set
- **WHEN** target type is `Set<PersonDTO>` and source is streamable
- **THEN** the strategy SHALL propose requiring `Stream<PersonDTO>` with code template `$INPUT.collect($T.toSet())` referencing `Collectors.class`

### Requirement: StreamMapStrategy maps stream elements
The `StreamMapStrategy` SHALL check if both source and target types are `Stream<T>` and `Stream<U>` respectively, where T is not assignable to U. It SHALL return a `TransformProposal` and trigger a recursive sub-resolution for `T → U`. The code template SHALL emit `$INPUT.map(e -> <inner>)` where `<inner>` is the resolved element transformation.

#### Scenario: Stream element mapping with sibling method
- **WHEN** source type is `Stream<Person>`, target type is `Stream<PersonDTO>`, and `PersonDTO map(Person)` exists
- **THEN** the strategy SHALL propose with code template `$INPUT.map(e -> map(e))`

#### Scenario: Stream of same element type returns empty
- **WHEN** source type is `Stream<String>` and target type is `Stream<String>`
- **THEN** the strategy SHALL return `Optional.empty()` (handled by `DirectAssignableStrategy`)

### Requirement: OptionalMapStrategy maps optional elements
The `OptionalMapStrategy` SHALL check if both source and target types are `Optional<T>` and `Optional<U>` respectively, where T is not assignable to U. It SHALL return a `TransformProposal` and trigger a recursive sub-resolution for `T → U`. The code template SHALL emit `$INPUT.map(e -> <inner>)`.

#### Scenario: Optional element mapping with sibling method
- **WHEN** source type is `Optional<Person>`, target type is `Optional<PersonDTO>`, and `PersonDTO map(Person)` exists
- **THEN** the strategy SHALL propose with code template `$INPUT.map(e -> map(e))`

### Requirement: OptionalWrapStrategy wraps value in Optional
The `OptionalWrapStrategy` SHALL check if the target type is `Optional<T>` and the source type is not `Optional`. It SHALL return a `TransformProposal` requiring `T` as input with code template `Optional.of($INPUT)`.

#### Scenario: Wrap value in Optional
- **WHEN** source type is `PersonDTO` and target type is `Optional<PersonDTO>`
- **THEN** the strategy SHALL propose with code template `$T.of($INPUT)` referencing `Optional.class`

#### Scenario: Optional source returns empty
- **WHEN** source type is `Optional<Person>` and target type is `Optional<PersonDTO>`
- **THEN** `OptionalWrapStrategy` SHALL return `Optional.empty()` (handled by `OptionalMapStrategy`)

### Requirement: OptionalUnwrapStrategy unwraps Optional value
The `OptionalUnwrapStrategy` SHALL check if the source type is `Optional<T>` and the target type is not `Optional`. It SHALL return a `TransformProposal` producing `T` with code template `$INPUT.get()`.

#### Scenario: Unwrap Optional
- **WHEN** source type is `Optional<Person>` and target type is `Person`
- **THEN** the strategy SHALL propose with code template `$INPUT.get()`

#### Scenario: Non-Optional source returns empty
- **WHEN** source type is `String` and target type is `Person`
- **THEN** `OptionalUnwrapStrategy` SHALL return `Optional.empty()`

## MODIFIED Requirements

### Requirement: Per-strategy unit spec presence, naming and location

Every concrete `@AutoService` implementation of `io.github.joke.percolate.spi.Bridge`, `io.github.joke.percolate.spi.GroupTarget`, or `io.github.joke.percolate.spi.PathSegmentResolver` shipped from the `percolate-strategies-builtin` module SHALL have a corresponding Spock specification named `<StrategyClassSimpleName>Spec.groovy`.

The spec SHALL reside at `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/<StrategyClassSimpleName>Spec.groovy` (mirroring the strategy's main-source package). The spec SHALL `extend spock.lang.Specification` and SHALL carry the annotation `@spock.lang.Tag('unit')` at the class level.

For the ten built-ins shipped after `bind-seed-chain-realisation` (which removes `GetterRead`), the ten required strategy specs are: `DirectAssignSpec`, `ListMapSpec`, `ListWrapSpec`, `SetMapSpec`, `SetWrapSpec`, `OptionalMapSpec`, `OptionalUnwrapSpec`, `OptionalWrapSpec`, `MethodCallBridgeSpec`, `ConstructorCallSpec`.

For the three `PathSegmentResolver` built-ins shipped by `source-path-resolvers`, the three additional required specs are: `GetterPathResolverSpec`, `RecordPathResolverSpec`, `FieldPathResolverSpec`.

#### Scenario: Every builtin has a matching spec
- **WHEN** the contents of `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/` and `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` are inspected
- **THEN** for every public final class in the main tree annotated with `@AutoService(Bridge.class)`, `@AutoService(GroupTarget.class)`, or `@AutoService(PathSegmentResolver.class)`, a sibling `<SimpleName>Spec.groovy` file exists in the test tree

#### Scenario: Specs are tagged as unit
- **WHEN** any one of the required strategy or resolver specs is inspected
- **THEN** the class is annotated with `@spock.lang.Tag('unit')` (the Spock-package `Tag`, not `org.junit.jupiter.api.Tag`)
- **AND** the class extends `spock.lang.Specification`

#### Scenario: PathSegmentResolver builtins have matching specs
- **WHEN** the contents of `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` are inspected after `source-path-resolvers` is applied
- **THEN** `GetterPathResolverSpec.groovy`, `RecordPathResolverSpec.groovy`, and `FieldPathResolverSpec.groovy` are all present
- **AND** each extends `spock.lang.Specification` and carries `@spock.lang.Tag('unit')`

#### Scenario: GetterReadSpec is removed
- **WHEN** the contents of `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` are inspected after `bind-seed-chain-realisation` is applied
- **THEN** neither `GetterReadSpec.groovy` nor `GetterReadMultiHopSpec.groovy` exists

### Requirement: Per-strategy scenario coverage

For each of the ten strategy specs, the spec SHALL include at minimum:

- **Empty-return scenarios.** One Spock feature method per declared precondition demonstrating that the strategy returns an empty `Stream<BridgeStep>` / `Optional.empty()` when that precondition is not met. Examples: `ListMap` returns empty when the target is not a `List`; `OptionalUnwrap` returns empty when the source is not an `Optional`; `ConstructorCall` returns empty when target tails are empty or no matching constructor exists.

- **Happy-path scenarios.** At least one feature method that exercises the strategy on inputs that satisfy all preconditions, asserting on the returned step's metadata (`inputType`, `outputType`, `weight`).

- **Branch scenarios.** For strategies whose behaviour differs across multiple accepted input shapes or member-resolution paths, one feature method per distinguishable branch. Examples: `ListMap` must cover array input, `Iterable` input, and `Optional` input separately; `ConstructorCall` must cover the constructor-by-parameter-name path and the constructor-by-arity-and-fields path.

- **Element-seed scenarios.** For strategies that populate `BridgeStep.getElementSeeds()` (`ListMap`, `ListWrap`, `SetMap`, `SetWrap`, `OptionalMap`, `OptionalWrap`), at least one feature method asserting the seed's name, inner-from type, and inner-to type on the happy path.

#### Scenario: Every strategy spec exercises at least one precondition failure
- **WHEN** any strategy spec is inspected
- **THEN** at least one feature method named to indicate a precondition (e.g. `'returns empty when target is not a List'`) asserts an empty `Stream` / empty `Optional` result

#### Scenario: Container bridges cover all input shapes
- **WHEN** `ListMapSpec` is inspected
- **THEN** there exists a feature method exercising array input
- **AND** a feature method exercising `Iterable` (e.g. `List<E>`) input
- **AND** a feature method exercising `Optional<E>` input (pinning current behaviour — see Findings)

### Requirement: Shape fixtures for ConstructorCall and PathSegmentResolver built-ins

Java fixture types SHALL exist under `strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/`. They SHALL be plain Java sources compiled by the project's standard `compileTestJava` task into the test classpath; specs SHALL resolve them through `TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.<SimpleName>')`.

The fixture set SHALL include at minimum:

- A record with named parameters (e.g. `record PersonRecord(int age, String name) {}`) — exercises `ConstructorCall`'s constructor-by-parameter-name path and `RecordPathResolver`'s record-accessor matching.
- A JavaBean with paired getter/setter accessors (e.g. `getName`/`setName`, `getAge`/`setAge`) — exercises `GetterPathResolver`'s `getX` path.
- A class exposing a boolean-returning accessor named `isFlag()` — exercises `GetterPathResolver`'s `isX` branch.
- A class with a public field, a private field, and a public static field (e.g. `BoxFixture`) — exercises `FieldPathResolver`'s accept/reject branches.
- A class with a parameterized-only accessor `getName(String suffix)` and no zero-arg accessor — exercises `GetterPathResolver`'s parameterized-overload rejection.
- A class whose constructor parameters are positional but whose fields carry meaningful names — exercises `ConstructorCall`'s constructor-by-arity-and-fields path.

#### Scenario: Fixtures are on the test classpath
- **WHEN** the six-or-more fixture classes exist under `strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/`
- **THEN** `TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.PersonRecord')` returns a non-null `TypeElement` whose `asType()` is a `DeclaredType`
- **AND** the same is true for the JavaBean, boolean-accessor, field, parameterized-getter, and positional-ctor fixtures

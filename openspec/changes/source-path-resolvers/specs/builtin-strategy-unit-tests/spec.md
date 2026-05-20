## MODIFIED Requirements

### Requirement: Per-strategy unit spec presence, naming and location

Every concrete `@AutoService` implementation of `io.github.joke.percolate.spi.Bridge`, `io.github.joke.percolate.spi.SourceStep`, `io.github.joke.percolate.spi.GroupTarget`, or `io.github.joke.percolate.spi.PathSegmentResolver` shipped from the `percolate-strategies-builtin` module SHALL have a corresponding Spock specification named `<StrategyClassSimpleName>Spec.groovy`.

The spec SHALL reside at `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/<StrategyClassSimpleName>Spec.groovy` (mirroring the strategy's main-source package). The spec SHALL `extend spock.lang.Specification` and SHALL carry the annotation `@spock.lang.Tag('unit')` at the class level.

For the eleven built-ins shipped after `extract-spi-and-builtins`, the eleven required specs are: `DirectAssignSpec`, `ListMapSpec`, `ListWrapSpec`, `SetMapSpec`, `SetWrapSpec`, `OptionalMapSpec`, `OptionalUnwrapSpec`, `OptionalWrapSpec`, `MethodCallBridgeSpec`, `GetterReadSpec`, `ConstructorCallSpec`.

For the three `PathSegmentResolver` built-ins shipped by `source-path-resolvers`, the three additional required specs are: `GetterPathResolverSpec`, `RecordPathResolverSpec`, `FieldPathResolverSpec`.

#### Scenario: Every builtin has a matching spec
- **WHEN** the contents of `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/` and `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` are inspected
- **THEN** for every public final class in the main tree annotated with `@AutoService(Bridge.class)`, `@AutoService(SourceStep.class)`, `@AutoService(GroupTarget.class)`, or `@AutoService(PathSegmentResolver.class)`, a sibling `<SimpleName>Spec.groovy` file exists in the test tree

#### Scenario: Specs are tagged as unit
- **WHEN** any one of the required strategy or resolver specs is inspected
- **THEN** the class is annotated with `@spock.lang.Tag('unit')` (the Spock-package `Tag`, not `org.junit.jupiter.api.Tag`)
- **AND** the class extends `spock.lang.Specification`

#### Scenario: PathSegmentResolver builtins have matching specs
- **WHEN** the contents of `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` are inspected after `source-path-resolvers` is applied
- **THEN** `GetterPathResolverSpec.groovy`, `RecordPathResolverSpec.groovy`, and `FieldPathResolverSpec.groovy` are all present
- **AND** each extends `spock.lang.Specification` and carries `@spock.lang.Tag('unit')`

## ADDED Requirements

### Requirement: Per-resolver scenario coverage

Each `<ResolverClassSimpleName>Spec.groovy` SHALL cover, at minimum, the scenarios required by the corresponding `Requirement` in the `source-path-resolution` capability — specifically the resolver's positive-match scenarios and rejection scenarios. The spec SHALL exercise the resolver via the `ResolveCtxBuilder` test helper and the `TypeUniverse` substrate, consistent with the existing *Single-substrate javac invariant* and *ResolveCtxBuilder test helper* requirements.

For `GetterPathResolverSpec`, scenarios SHALL include: a positive match on a JavaBean `getX()` accessor, a positive match on an `isX()` accessor with boolean return, rejection of parameterized overloads, rejection of methods inherited from `java.lang.Object`, and rejection of non-declared parent types.

For `RecordPathResolverSpec`, scenarios SHALL include: a positive match on a canonical record component accessor, and rejection when the parent is a plain class (not a record).

For `FieldPathResolverSpec`, scenarios SHALL include: a positive match on a public field, rejection of private fields, and rejection of static fields.

#### Scenario: GetterPathResolverSpec covers JavaBean and boolean accessors
- **WHEN** `GetterPathResolverSpec.groovy` is inspected
- **THEN** the spec contains at least five feature methods covering: getX match, isX match, parameterized-overload rejection, Object-method rejection, non-declared-parent rejection

#### Scenario: RecordPathResolverSpec covers record and non-record parents
- **WHEN** `RecordPathResolverSpec.groovy` is inspected
- **THEN** the spec contains at least two feature methods covering: canonical record accessor match, plain-class rejection

#### Scenario: FieldPathResolverSpec covers visibility and modifiers
- **WHEN** `FieldPathResolverSpec.groovy` is inspected
- **THEN** the spec contains at least three feature methods covering: public-field match, private-field rejection, static-field rejection

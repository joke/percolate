## MODIFIED Requirements

### Requirement: Per-strategy unit spec presence, naming and location

Every concrete `@AutoService(io.github.joke.percolate.spi.ExpansionStrategy.class)` implementation shipped from the `percolate-strategies-builtin` module SHALL have a corresponding Spock specification named `<StrategyClassSimpleName>Spec.groovy`. (`ExpansionStrategy` is the single strategy SPI interface; the former `Bridge` / `GroupTarget` / `PathSegmentResolver` interfaces are removed.)

The spec SHALL reside at `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/<StrategyClassSimpleName>Spec.groovy` (mirroring the strategy's main-source package). The spec SHALL `extend spock.lang.Specification` and SHALL carry the annotation `@spock.lang.Tag('unit')` at the class level.

The shipped built-in strategies and their required specs are the twelve: `DirectAssignSpec`, `MethodCallBridgeSpec`, `ConstructorCallSpec`, `WidenPrimitiveSpec`, `PrimitiveWrapperConversionSpec`, `OptionalContainerSpec`, `ListContainerSpec`, `SetContainerSpec`, `ArrayContainerSpec`, `GetterPathResolverSpec`, `FieldPathResolverSpec`, and `MethodPathResolverSpec`.

The superseded per-operation container specs (`IterableUnwrapSpec`, `OptionalUnwrapSpec`, `OptionalWrapSpec`, `ListWrapSpec`, `SetWrapSpec`, `OptionalCollectSpec`, `SetCollectSpec`, `ListCollectSpec`, `ArrayCollectSpec`, `SetMapSpec`, `ListMapSpec`, `OptionalMapSpec`), and `GetterReadSpec` / `SingletonSpec` / `RecordPathResolverSpec`, SHALL NOT exist — the corresponding strategies were folded into the one-class-per-container-type strategies or otherwise removed.

#### Scenario: Every builtin has a matching spec
- **WHEN** the contents of `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/` and `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` are inspected
- **THEN** for every concrete class in the main tree annotated with `@AutoService(ExpansionStrategy.class)`, a sibling `<SimpleName>Spec.groovy` file exists in the test tree

#### Scenario: The twelve built-in strategy specs are present
- **WHEN** the test tree is inspected
- **THEN** `DirectAssignSpec`, `MethodCallBridgeSpec`, `ConstructorCallSpec`, `WidenPrimitiveSpec`, `PrimitiveWrapperConversionSpec`, `OptionalContainerSpec`, `ListContainerSpec`, `SetContainerSpec`, `ArrayContainerSpec`, `GetterPathResolverSpec`, `FieldPathResolverSpec`, and `MethodPathResolverSpec` are all present
- **AND** each extends `spock.lang.Specification` and carries `@spock.lang.Tag('unit')`

#### Scenario: Removed strategies have no specs
- **WHEN** the test tree is inspected
- **THEN** no file named `IterableUnwrapSpec.groovy`, `OptionalUnwrapSpec.groovy`, `OptionalWrapSpec.groovy`, `ListWrapSpec.groovy`, `SetWrapSpec.groovy`, `OptionalCollectSpec.groovy`, `SetCollectSpec.groovy`, `ListCollectSpec.groovy`, `ArrayCollectSpec.groovy`, `SetMapSpec.groovy`, `ListMapSpec.groovy`, `OptionalMapSpec.groovy`, `GetterReadSpec.groovy`, or `RecordPathResolverSpec.groovy` exists

#### Scenario: Specs are tagged as unit
- **WHEN** any one of the twelve required strategy specs is inspected
- **THEN** the class is annotated with `@spock.lang.Tag('unit')` (the Spock-package `Tag`, not `org.junit.jupiter.api.Tag`)
- **AND** the class extends `spock.lang.Specification`

### Requirement: Assertion scope is metadata-only

Strategy unit specs SHALL assert on the metadata of returned `ExpansionStep` values: the step's `intent` (`CONVERSION` / `BOUNDARY`), input slot types, output type, weight, element scope (presence and `ENTERING`/`EXITING` value), the presence or emptiness of the returned `Stream`, and any other plain-data accessor exposed by `ExpansionStep` and `Slot`.

Strategy unit specs SHALL NOT invoke `io.github.joke.percolate.spi.EdgeCodegen.render(...)` or `io.github.joke.percolate.spi.GroupCodegen.render(...)`. They SHALL NOT assert on `com.palantir.javapoet.CodeBlock.toString()` output or otherwise pin the rendered Java source produced by a strategy's codegen. Codegen pinning is the scope of a separate future change and is explicitly excluded from this contract.

#### Scenario: No codegen invocation in unit specs
- **WHEN** the source of every strategy spec is inspected
- **THEN** no method call to `EdgeCodegen.render`, `GroupCodegen.render`, or `CodeBlock.toString()` appears
- **AND** no `import com.palantir.javapoet.CodeBlock` appears in the strategy specs (the test helpers are free to use CodeBlock internally if needed)

### Requirement: Per-strategy scenario coverage

For each of the twelve strategy specs, the spec SHALL include at minimum:

- **Empty-return scenarios.** One Spock feature method per declared precondition demonstrating that the strategy returns an empty `Stream<ExpansionStep>` when that precondition is not met. Examples: a container strategy returns empty when the target is not its container type; `ConstructorCall` returns empty when the target type is not a `DECLARED` type with accessible constructors; `DirectAssign` returns empty when `isSameType` is false.

- **Happy-path scenarios.** At least one feature method that exercises the strategy on inputs that satisfy all preconditions, asserting on the returned step's metadata (`intent`, input slot type(s), `output`, `weight`, and `elementScope` where applicable).

- **Branch scenarios.** For strategies whose behaviour differs across multiple accepted input shapes or member-resolution paths, one feature method per distinguishable branch. Examples: `ConstructorCall` over-emits one `BOUNDARY` step per accessible constructor (cover a target with multiple constructors); a path resolver covers each member-resolution kind it supports.

- **Element-scope scenarios.** For container strategies that emit a scope-crossing (`BOUNDARY`) step, at least one feature method asserting the step's `elementScope` value (`ENTERING` when entering the element scope, `EXITING` when collecting back out). Scalar steps carry empty `elementScope`.

#### Scenario: Every strategy spec exercises at least one precondition failure
- **WHEN** any strategy spec is inspected
- **THEN** at least one feature method named to indicate a precondition asserts an empty `Stream<ExpansionStep>` result

#### Scenario: ConstructorCall covers multi-constructor over-emit
- **WHEN** `ConstructorCallSpec` is inspected
- **THEN** there exists a feature method exercising a target type with more than one accessible constructor
- **AND** it asserts one `BOUNDARY` `ExpansionStep` is emitted per accessible constructor

#### Scenario: Scope-bearing container strategies assert their element scope
- **WHEN** a container strategy spec that emits a scope-crossing step is inspected
- **THEN** at least one feature method asserts the returned `ExpansionStep`'s `elementScope` equals the expected `ENTERING` / `EXITING` value

### Requirement: Shape fixtures for ConstructorCall and path resolvers

Java fixture types SHALL exist under `strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/`. They SHALL be plain Java sources compiled by the project's standard `compileTestJava` task into the test classpath; specs SHALL resolve them through `TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.<SimpleName>')`.

The fixture set SHALL include at minimum:

- A record with named parameters (e.g. `record PersonRecord(int age, String name) {}`) — exercises `ConstructorCall`'s per-constructor over-emit and `MethodPathResolver`'s canonical-record-accessor branch.
- A JavaBean with paired getter/setter accessors (e.g. `getName`/`setName`, `getAge`/`setAge`) — exercises `GetterPathResolver`'s `getX` path.
- A class exposing a boolean-returning accessor named `isFlag()` — exercises `GetterPathResolver`'s `isX` branch.
- A class with at least one public, non-static field whose name carries meaning (e.g. `public String value`) — exercises `FieldPathResolver`'s public-field-match path.
- A non-record fluent-style class with at least one zero-arg method whose name carries meaning (e.g. `class Address { private String street; public String street() { return street; } }`) — exercises `MethodPathResolver`'s non-record branch and the precedence rule that `MethodPathResolver` outranks `FieldPathResolver`.
- A class declaring more than one constructor (e.g. distinct parameter lists) — exercises `ConstructorCall`'s per-constructor over-emit.

#### Scenario: Fixtures are on the test classpath
- **WHEN** the six-or-more fixture classes exist under `strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/`
- **THEN** `TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.PersonRecord')` returns a non-null `TypeElement` whose `asType()` is a `DeclaredType`
- **AND** the same is true for the JavaBean, boolean-accessor, public-field, fluent-method, and multi-constructor fixtures

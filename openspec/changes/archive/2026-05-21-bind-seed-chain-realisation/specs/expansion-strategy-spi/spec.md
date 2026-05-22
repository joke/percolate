## MODIFIED Requirements

### Requirement: SPI package isolation

The percolate-spi Gradle module SHALL ship a package `io.github.joke.percolate.spi` containing exactly the strategy-author surface: three interfaces (`SourceStep`, `GroupTarget`, `Bridge`), four immutable result types (`Step`, `BridgeStep`, `Slot`, `GroupBuild`), the `ResolveCtx` interface, the codegen interfaces (`EdgeCodegen`, `GroupCodegen`, `IncomingValues`, `VarNames`), the `Receiver` / `ThisReceiver` / `CallableMethods` / `MethodCandidate` / `ElementSeed` types, and the `Containers` and `Weights` utilities. The module SHALL depend only on JDK types plus `com.palantir.javapoet` (because `CodeBlock` is part of the codegen interface surface). It SHALL NOT depend on `percolate-annotations` or `percolate-processor`.

The package SHALL declare `@NullMarked` via `package-info.java`.

Built-in strategies (`ConstructorCall`, `DirectAssign`, the seven container bridges, `MethodCallBridge`) SHALL ship from a separate Gradle module `percolate-strategies-builtin` whose only compile dependency is `percolate-spi`. They SHALL NOT import any class from `io.github.joke.percolate.processor.graph` or `io.github.joke.percolate.processor.stages.expand`. This invariant SHALL be enforced structurally by the module compile graph: `percolate-strategies-builtin`'s `build.gradle` declares no compile dependency on `percolate-processor`, so engine internals are unreachable from built-in sources.

#### Scenario: spi package has @NullMarked
- **WHEN** the source of `spi/src/main/java/io/github/joke/percolate/spi/package-info.java` is inspected
- **THEN** the package declaration carries `@org.jspecify.annotations.NullMarked`

#### Scenario: Built-in strategies have no forbidden imports
- **WHEN** the import statements of `ConstructorCall`, `DirectAssign`, and the seven container bridges are inspected
- **THEN** none reference any class in `io.github.joke.percolate.processor.graph.*` or `io.github.joke.percolate.processor.stages.expand.*`
- **AND** the enforcement is structural: `strategies-builtin`'s `build.gradle` declares no compile dependency on `processor`, so attempting such an import would fail compilation

#### Scenario: SPI module has no dependency on annotations or processor
- **WHEN** `spi/build.gradle` is inspected
- **THEN** it declares neither `project(':annotations')` nor `project(':processor')` on any `compile` / `implementation` / `api` configuration
- **AND** its only non-JDK `api` dependency is `com.palantir.javapoet:javapoet`

### Requirement: Strategy registration via ServiceLoader and AutoService

Strategy implementations SHALL register through `java.util.ServiceLoader`. Each concrete `@AutoService(<Interface>.class)` implementation SHALL generate (via Google AutoService at compile time) an entry in `META-INF/services/io.github.joke.percolate.spi.<Interface>`.

The processor's Dagger module SHALL provide each strategy list as `@Singleton List<Interface>` by:
1. Calling `ServiceLoader.load(<Interface>.class, classLoader)` exactly once.
2. Materialising the iterator into a list.
3. Sorting that list lexicographically by `getClass().getName()` (FQN ascending).
4. Wrapping in `Collections.unmodifiableList(...)` before publishing.

The processor module SHALL declare `percolate-strategies-builtin` as a `runtimeOnly` Gradle dependency so that, by default, end users receive the built-in strategies on their annotation-processor classpath without an explicit declaration. End users wanting a custom-only setup MAY `exclude` the `strategies-builtin` artifact.

#### Scenario: Built-in ConstructorCall is annotated AutoService
- **WHEN** the source of `ConstructorCall` (in `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/`) is inspected
- **THEN** the class carries `@AutoService(GroupTarget.class)`

#### Scenario: Built-in DirectAssign is annotated AutoService
- **WHEN** the source of `DirectAssign` (in `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/`) is inspected
- **THEN** the class carries `@AutoService(Bridge.class)`

#### Scenario: Provided strategy list is sorted by FQN
- **WHEN** Dagger provides the `List<Bridge>` for a round
- **THEN** the list is sorted ascending by `getClass().getName()` for each element

#### Scenario: User strategy registered via META-INF/services is discovered
- **WHEN** a JAR on the annotation-processor classpath contains `META-INF/services/io.github.joke.percolate.spi.Bridge` referencing a user class
- **THEN** the user class is included in the `List<Bridge>` provided by Dagger
- **AND** the list remains sorted by FQN including the user class

#### Scenario: ServiceLoader is invoked once per round
- **WHEN** `ExpandStage.apply(graph)` is invoked twice in a round
- **THEN** `ServiceLoader.load(...)` is invoked exactly once for each strategy interface across the round

#### Scenario: Processor declares strategies-builtin as runtimeOnly
- **WHEN** `processor/build.gradle` is inspected
- **THEN** it declares `runtimeOnly project(':strategies-builtin')`
- **AND** no `compile` / `implementation` / `api` configuration mentions `:strategies-builtin`

### Requirement: Built-in service registration smoke spec

The `percolate-strategies-builtin` module SHALL contain a Spock specification at `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/BuiltinServiceRegistrationSpec.groovy` that asserts the contract between modules: when `percolate-strategies-builtin` is on the classpath, `ServiceLoader.load(...)` discovers exactly the expected built-in classes for each SPI interface.

The spec SHALL load each of `Bridge` and `GroupTarget` via `ServiceLoader.load(<Interface>.class)` and assert that the discovered set contains, at minimum, the ten shipped built-ins:

- `Bridge`: `DirectAssign`, `ListMap`, `ListWrap`, `SetMap`, `SetWrap`, `OptionalMap`, `OptionalUnwrap`, `OptionalWrap`, `MethodCallBridge`.
- `GroupTarget`: `ConstructorCall`.

The spec SHALL be tagged `@spock.lang.Tag('unit')` and SHALL NOT invoke `ExpansionHarness` or any expansion-pipeline code. Its sole concern is verifying that the `META-INF/services/...` files generated by `auto-service` correctly register the strategy classes.

#### Scenario: ServiceLoader discovers all expected Bridge builtins
- **WHEN** `ServiceLoader.load(Bridge.class)` is invoked from `BuiltinServiceRegistrationSpec`
- **THEN** the returned stream's classes contain, as a subset, `DirectAssign`, `ListMap`, `ListWrap`, `SetMap`, `SetWrap`, `OptionalMap`, `OptionalUnwrap`, `OptionalWrap`, and `MethodCallBridge`
- **AND** the returned stream's classes do NOT contain `GetterRead` (the strategy has been removed)

#### Scenario: ServiceLoader discovers all expected GroupTarget builtins
- **WHEN** `ServiceLoader.load(GroupTarget.class)` is invoked from `BuiltinServiceRegistrationSpec`
- **THEN** the returned stream's classes contain `ConstructorCall`

#### Scenario: Spec does not depend on the expansion pipeline
- **WHEN** `BuiltinServiceRegistrationSpec.groovy` is inspected
- **THEN** the spec does not import `ExpansionHarness` or any class under `io.github.joke.percolate.processor`

## REMOVED Requirements

### Requirement: GetterRead built-in

**Reason:** Superseded by `GetterPathResolver` (introduced in `source-path-resolvers`). With `source-path-resolvers` typing every directive's source chain at seed time via `PathSegmentResolver`s, and with `bind-seed-chain-realisation` consuming the typed source structurally via directive-pinned target slot scaffolding, the engine no longer needs a `Bridge` (or `SourceStep`) that walks getters at expansion time. Getter-based source-path resolution is owned by `GetterPathResolver` at seed time. The stale `SourceStep`-flavoured requirement (left over from before `target-to-source-expansion` flipped expansion direction) is also dropped — the entire `GetterRead`-as-`SourceStep` / `GetterRead`-as-`Bridge` lineage is removed.

**Migration:** Delete `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/GetterRead.java`. Delete the corresponding unit specs `GetterReadSpec.groovy` and `GetterReadMultiHopSpec.groovy`. Update `BuiltinServiceRegistrationSpec` to drop the `GetterRead` assertion (see modified *Built-in service registration smoke spec* requirement). Mappers that previously relied on implicit getter-bridging without an explicit `@Map` directive SHALL add the directive (e.g., `@Map(target = "name", source = "person.name")`). Until automatic same-name mapping (a future change) lands, no implicit getter-chain expansion exists.

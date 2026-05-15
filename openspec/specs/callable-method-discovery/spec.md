# Callable Method Discovery Spec

## Purpose

This spec defines the callable method discovery stage that walks the `@Mapper` interface's full method linearisation and produces an index of single-parameter methods available for method-call bridge expansion.

## Requirements

### Requirement: DiscoverCallableMethods stage exists and runs after MapperShape discovery

The processor SHALL define a stage `DiscoverCallableMethods` in package `io.github.joke.percolate.processor.stages.discover` that consumes the discovered `@Mapper` `TypeElement` (already available via `MapperContext.getMapperType()`) and produces a `CallableMethods` instance, attached to the per-mapper context.

`DiscoverCallableMethods` SHALL run before `SeedGraph` so the `CallableMethods` index is available to expansion phases via `ResolveCtx`. The stage SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

`DiscoverCallableMethods` SHALL NOT modify the `MapperGraph` (the graph does not exist yet at this stage's runtime).

#### Scenario: Stage is wired into the pipeline before SeedGraph
- **WHEN** the pipeline's stage list is inspected
- **THEN** `DiscoverCallableMethods` appears before `SeedGraph` and after the mapper-shape discovery stages
- **AND** the stage is `@Inject`-constructed via Lombok

#### Scenario: Stage produces a CallableMethods instance attached to MapperContext
- **WHEN** `DiscoverCallableMethods.run(ctx)` completes
- **THEN** the produced `CallableMethods` instance is reachable from `ctx` (and therefore from any `ResolveCtx` derived from it)

### Requirement: Discovery walks the @Mapper interface's full linearisation

`DiscoverCallableMethods` SHALL invoke `ctx.getElements().getAllMembers(mapperType)` to obtain the complete linearisation of methods on the `@Mapper`-annotated `TypeElement`, including those inherited from super-interfaces.

The stage SHALL NOT walk methods declared on any other types — including types that appear as method parameters, return types, or fields of the mapper. Discovery is strictly scoped to the current `@Mapper` interface and its inherited methods.

#### Scenario: Locally declared methods are discovered
- **WHEN** the `@Mapper` interface declares `Human map(Person p)` directly
- **THEN** the produced index contains a `MethodCandidate` whose `method()` is the `ExecutableElement` for `map(Person)`

#### Scenario: Methods inherited from super-interfaces are discovered
- **WHEN** `@Mapper interface DogMapper extends BaseMapper` and `BaseMapper` declares `Pet adopt(Dog d)`
- **THEN** the produced index contains a `MethodCandidate` whose `method()` is the inherited `adopt(Dog)`

#### Scenario: Methods on parameter or return types are NOT discovered
- **WHEN** the mapper declares `Human map(Person p)` and `Person` itself declares `String getLastName()`
- **THEN** the produced index does NOT contain a `MethodCandidate` for `Person.getLastName()`

### Requirement: Object-inherited members are filtered

`DiscoverCallableMethods` SHALL exclude any method whose enclosing element is `java.lang.Object` (i.e. `toString`, `hashCode`, `equals`, `getClass`, `clone`, `wait`, `wait(long)`, `wait(long, int)`, `notify`, `notifyAll`, `finalize`).

#### Scenario: toString from Object is excluded
- **WHEN** discovery walks an `@Mapper` interface that does not override `toString`
- **THEN** no `MethodCandidate` is produced for `toString()`

#### Scenario: User-declared toString on the @Mapper IS discovered
- **WHEN** an `@Mapper` interface explicitly declares its own `String toString()` method
- **THEN** a `MethodCandidate` is produced for that method (the filter applies by enclosing type, not by name)

### Requirement: Multi-parameter methods are filtered

`DiscoverCallableMethods` SHALL exclude any method with more than one declared parameter. Methods with exactly one parameter are eligible. Methods with zero parameters are eligible only if they have a non-`void` return type, but for v1 they remain eligible (single-input bridges still work for them when the single input is the bare receiver — though `MethodCallBridge` itself only emits for one-parameter cases; see expansion-strategy-spi).

In v1, only single-parameter, non-static, non-void methods are practically usable. Multi-parameter methods are deferred to a future `MethodCallGroupTarget` strategy.

#### Scenario: Two-parameter method is excluded
- **WHEN** the mapper declares `Pet adopt(Dog d, Owner o)`
- **THEN** the produced index does NOT contain a `MethodCandidate` for `adopt(Dog, Owner)`

#### Scenario: Single-parameter method is included
- **WHEN** the mapper declares `Pet adopt(Dog d)`
- **THEN** the produced index contains a `MethodCandidate` for `adopt(Dog)`

### Requirement: CallableMethods exposes producing(TypeMirror) only

The `CallableMethods` interface SHALL expose a single query method:

```java
public interface CallableMethods {
    Stream<MethodCandidate> producing(TypeMirror outputType);
}
```

`producing(outputType)` SHALL return every `MethodCandidate` whose method's return type is assignable to `outputType` (covariant return). Strategies SHALL receive an empty stream if no candidate matches.

`CallableMethods` SHALL NOT expose any other query methods, mutation methods, or accessors. A `forward-walk` query (e.g. `accepting(inputType)`) is deliberately deferred — no v1 strategy needs it.

#### Scenario: producing returns a candidate whose return type is the queried type
- **WHEN** `callableMethods.producing(<Pet>)` is invoked and the index contains `Pet adopt(Dog d)`
- **THEN** the returned stream contains the corresponding `MethodCandidate`

#### Scenario: producing matches covariantly
- **WHEN** `callableMethods.producing(<Animal>)` is invoked and the index contains `Pet adopt(Dog d)` (where `Pet` is assignable to `Animal`)
- **THEN** the returned stream contains the `MethodCandidate` for `adopt(Dog)`

#### Scenario: producing returns empty when no candidate matches
- **WHEN** `callableMethods.producing(<UnrelatedType>)` is invoked and no candidate's return type is assignable to `UnrelatedType`
- **THEN** the returned stream is empty

#### Scenario: CallableMethods has no other public methods
- **WHEN** the source of the `CallableMethods` interface is inspected
- **THEN** the interface declares exactly one method (`producing`) and no default methods

### Requirement: MethodCandidate carries method and receiver

`MethodCandidate` SHALL be an immutable Lombok `@Value` type with two fields:

- `ExecutableElement method` — the method itself.
- `Receiver receiver` — an abstraction over the call expression's receiver (e.g. `this`).

`MethodCandidate` SHALL NOT carry pre-computed types or weights; consumers compute weight from the candidate's method and the bridge query.

#### Scenario: MethodCandidate exposes both fields
- **WHEN** a `MethodCandidate` is constructed with a method and receiver
- **THEN** `getMethod()` and `getReceiver()` return those values

#### Scenario: MethodCandidate is value-equal
- **WHEN** two `MethodCandidate` instances are constructed with equal method and receiver
- **THEN** they are `equal` and have equal `hashCode`s

### Requirement: Receiver abstraction with ThisReceiver as the v1 implementor

The processor SHALL define an interface `Receiver`:

```java
public interface Receiver {
    CodeBlock asExpression();
}
```

`asExpression()` SHALL return a `com.palantir.javapoet.CodeBlock` that renders the receiver's call-expression form (e.g. `this`, or for a future cross-mapper `FieldReceiver`, `this.fieldName`).

The processor SHALL ship exactly one v1 implementation: `ThisReceiver`. `ThisReceiver.asExpression()` SHALL return `CodeBlock.of("this")`. `ThisReceiver` is a singleton or stateless instance; the discovery stage attaches it to every `MethodCandidate` produced in v1.

#### Scenario: ThisReceiver renders the literal token "this"
- **WHEN** `ThisReceiver.INSTANCE.asExpression()` is invoked
- **THEN** the returned `CodeBlock` renders to the literal string `this`

#### Scenario: All v1 candidates carry ThisReceiver
- **WHEN** `DiscoverCallableMethods` produces an index and any `MethodCandidate` is inspected
- **THEN** the candidate's `receiver()` is the `ThisReceiver` instance

### Requirement: SPI types live in processor.spi and respect the package boundary

The new types `CallableMethods`, `MethodCandidate`, `Receiver`, `ThisReceiver` SHALL reside in `io.github.joke.percolate.spi`, which ships as the dedicated `percolate-spi` Gradle module (not as a sub-package of `percolate-processor`). They SHALL NOT import any class from `io.github.joke.percolate.processor.graph` or `io.github.joke.percolate.processor.stages.*`. This package boundary is enforced by the compile graph of the multi-module split: the `percolate-spi` module has no compile dependency on `percolate-processor`, so engine internals are unreachable from SPI sources.

The package SHALL declare `@NullMarked` via `package-info.java` (already declared; the relocated types simply respect the existing declaration).

#### Scenario: New SPI types have no forbidden imports
- **WHEN** the import statements of `CallableMethods`, `MethodCandidate`, `Receiver`, and `ThisReceiver` are inspected
- **THEN** none reference any class in `io.github.joke.percolate.processor.graph.*` or `io.github.joke.percolate.processor.stages.*`
- **AND** the enforcement is structural: `percolate-spi`'s `build.gradle` declares no compile dependency on `percolate-processor`, so attempting such an import fails compilation

#### Scenario: SPI types live in the percolate-spi module
- **WHEN** the location of `CallableMethods.java`, `MethodCandidate.java`, `Receiver.java`, and `ThisReceiver.java` is inspected
- **THEN** all four reside under `spi/src/main/java/io/github/joke/percolate/spi/`
- **AND** none reside under `processor/src/main/java/`

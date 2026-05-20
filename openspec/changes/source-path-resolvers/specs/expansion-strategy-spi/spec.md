## ADDED Requirements

### Requirement: PathSegmentResolver interface

The percolate-spi module SHALL define a Java interface `io.github.joke.percolate.spi.PathSegmentResolver` with the following shape:

```java
public interface PathSegmentResolver {
    Optional<ResolvedSegment> resolve(
        TypeMirror parentType,
        String segment,
        ResolveCtx ctx);
}
```

Implementations SHALL return `Optional.of(ResolvedSegment)` describing a typed access for `segment` against a value of `parentType`, or `Optional.empty()` if the resolver does not apply. Implementations MUST NOT throw on a non-applicable input — they SHALL return `Optional.empty()` instead.

`PathSegmentResolver` is part of the SPI package surface defined in *SPI package isolation* and SHALL therefore live in `io.github.joke.percolate.spi` under the same `@NullMarked` declaration as the other SPI types.

The full per-resolver semantics (probe order, codegen shape, weight) are defined in the `source-path-resolution` capability.

#### Scenario: PathSegmentResolver with no match returns empty
- **WHEN** an implementor decides nothing matches the inputs
- **THEN** `resolve(...)` returns `Optional.empty()`
- **AND** does not throw

#### Scenario: PathSegmentResolver is part of the SPI package
- **WHEN** `spi/src/main/java/io/github/joke/percolate/spi/PathSegmentResolver.java` is inspected
- **THEN** the type lives in package `io.github.joke.percolate.spi`
- **AND** the package's `package-info.java` carries `@org.jspecify.annotations.NullMarked`

### Requirement: ResolvedSegment result type

The percolate-spi module SHALL define an immutable value type `io.github.joke.percolate.spi.ResolvedSegment` with three fields:

- `TypeMirror getReturnType()` — the type produced by the access
- `EdgeCodegen getCodegen()` — renders the access expression
- `int getWeight()` — strategy weight

The type SHALL be Lombok `@Value`-style: final fields, all-args constructor, value semantics, equality field-by-field. The codegen SHALL receive one slot through `IncomingValues` (representing the parent value) and a `VarNames` placeholder.

#### Scenario: ResolvedSegment is immutable
- **WHEN** a `ResolvedSegment` is constructed via its all-args constructor
- **THEN** none of the three fields can be reassigned (no setters)

#### Scenario: ResolvedSegment carries access codegen
- **WHEN** a `ResolvedSegment` is constructed with a non-null `EdgeCodegen`
- **THEN** invoking `getCodegen().render(vars, inputs)` produces a `CodeBlock`

### Requirement: PathSegmentResolver registration via ServiceLoader and AutoService

Concrete `PathSegmentResolver` implementations SHALL register through `java.util.ServiceLoader` by adding the resource `META-INF/services/io.github.joke.percolate.spi.PathSegmentResolver`. The recommended mechanism for built-ins is `@com.google.auto.service.AutoService(PathSegmentResolver.class)` on the implementing class, which Google AutoService translates into the service file at compile time.

`ProcessorModule.pathSegmentResolvers()` SHALL collect the resolvers via `ServiceLoader.load(PathSegmentResolver.class, ProcessorModule.class.getClassLoader())`, sort by `Class.getName()` ascending, and return as `@Singleton` `List<PathSegmentResolver>`.

#### Scenario: AutoService generates the service file at compile time
- **WHEN** a class is annotated with `@AutoService(PathSegmentResolver.class)` and the module is compiled
- **THEN** the generated `META-INF/services/io.github.joke.percolate.spi.PathSegmentResolver` resource is included in the resulting JAR
- **AND** the resource lists the fully-qualified class name on its own line

#### Scenario: ProcessorModule provides a sorted, singleton resolver list
- **WHEN** the `pathSegmentResolvers()` `@Provides` method is invoked twice
- **THEN** both invocations return lists in `Class.getName()` ascending order
- **AND** the iteration order is identical across invocations

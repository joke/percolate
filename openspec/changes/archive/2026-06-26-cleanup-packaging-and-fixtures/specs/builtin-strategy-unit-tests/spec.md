## MODIFIED Requirements

### Requirement: Single-substrate javac invariant

All `javax.lang.model.type.TypeMirror` instances flowing through any strategy **unit** spec SHALL originate from the single `com.sun.source.util.JavacTask` held inside `io.github.joke.percolate.spi.test.TypeUniverse` (the fixture published by `percolate-spi`'s `testFixtures` configuration). This invariant governs the per-strategy unit specs that live directly under `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/`; it does NOT govern the end-to-end compile specs under the `…/spi/builtins/e2e/` subpackage, which legitimately drive `PercolateProcessor` through the shared compile harness.

No strategy unit spec SHALL construct a second `JavacTask`, instantiate a separate `com.google.testing.compile.Compiler`, or otherwise introduce a parallel `Elements`/`Types` pair. Both JDK types and `strategies-builtin/src/test/java/.../fixtures/` user types SHALL resolve through that single substrate.

If a future build configuration prevents `TypeUniverse.of(...)`/`element(...)` from resolving user-defined classpath types, the remediation SHALL be a local change to `TypeUniverse` that wires an explicit `javax.tools.StandardJavaFileManager` with the test classpath. Introducing a second javac task to work around the visibility issue SHALL NOT be considered an acceptable remediation.

#### Scenario: No second JavacTask in any unit spec
- **WHEN** the imports of every per-strategy unit spec directly under `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` (excluding the `e2e/` subpackage) are inspected
- **THEN** no such file imports `com.sun.source.util.JavacTask`, `com.google.testing.compile.Compiler`, or `com.google.testing.compile.JavaFileObjects`
- **AND** every `TypeMirror`-typed expression is sourced from `io.github.joke.percolate.spi.test.TypeUniverse` (directly or through `HarnessResolveCtx` / `ResolveCtxBuilder`)

#### Scenario: Fixture types resolve through TypeUniverse
- **WHEN** a strategy spec needs a user-defined fixture type
- **THEN** the spec obtains it via `TypeUniverse.of(<Fixture>.class).asType()` (a Class literal, not a fully-qualified string)
- **AND** `Types.isSameType(...)` between this type and any JDK type drawn from `TypeUniverse` behaves consistently (both come from the same javac element table)

### Requirement: Shape fixtures for ConstructorCall and path resolvers

Java fixture types SHALL exist under `strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/`. They SHALL be plain Java sources compiled by the project's standard `compileTestJava` task into the test classpath; specs SHALL resolve them through `TypeUniverse.of(<Fixture>.class)` (a Class literal), not a fully-qualified string.

The fixture set SHALL include at minimum:

- A record with named parameters (e.g. `record PersonRecord(int age, String name) {}`) — exercises `ConstructorCall`'s per-constructor over-emit and `MethodPathResolver`'s canonical-record-accessor branch.
- A JavaBean with paired getter/setter accessors (e.g. `getName`/`setName`, `getAge`/`setAge`) — exercises `GetterPathResolver`'s `getX` path.
- A class exposing a boolean-returning accessor named `isFlag()` — exercises `GetterPathResolver`'s `isX` branch.
- A class with at least one public, non-static field whose name carries meaning (e.g. `public String value`) — exercises `FieldPathResolver`'s public-field-match path.
- A non-record fluent-style class with at least one zero-arg method whose name carries meaning (e.g. `class Address { private String street; public String street() { return street; } }`) — exercises `MethodPathResolver`'s non-record branch and the precedence rule that `MethodPathResolver` outranks `FieldPathResolver`.
- A class declaring more than one constructor (e.g. distinct parameter lists) — exercises `ConstructorCall`'s per-constructor over-emit.

#### Scenario: Fixtures are on the test classpath
- **WHEN** the six-or-more fixture classes exist under `strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/`
- **THEN** `TypeUniverse.of(PersonRecord.class)` returns a non-null `TypeElement` whose `asType()` is a `DeclaredType`
- **AND** the same is true for the JavaBean, boolean-accessor, public-field, fluent-method, and multi-constructor fixtures

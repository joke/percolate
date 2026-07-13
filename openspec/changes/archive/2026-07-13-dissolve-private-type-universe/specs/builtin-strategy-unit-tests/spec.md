## MODIFIED Requirements

### Requirement: Builtin unit specs are mock-based over the ResolveCtx seam

Every per-strategy **unit** spec directly under `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` (excluding the `e2e/` subpackage) SHALL exercise its strategy against a **Spock-mocked** `io.github.joke.percolate.spi.ResolveCtx`, stubbing the seam questions the strategy asks (`isList`/`isSet`/`isOptional`/`isAssignable`/`isSameType`/`membersOf`/`superclassOf`/`callableMethods`/…) per scenario. No unit spec SHALL construct a `com.sun.source.util.JavacTask`, a `com.google.testing.compile.Compiler`, or any `Types`/`Elements` pair — there SHALL be **no javac** on the unit path. The former javac fixtures `TypeUniverse` and `PrivateTypeUniverse` are both deleted (see the `expansion-test-harness` capability); no unit spec SHALL reintroduce them or any equivalent shared type substrate.

Every `javax.lang.model.type.TypeMirror` and `javax.lang.model.element.Element` a unit spec passes SHALL be an **opaque, never-stubbed token** (a bare `Mock()` or a distinct object identity): the spec SHALL NOT stub a `TypeMirror`/`Element` method to return type-internal behaviour, because the seam — not the mirror — answers every type question.

#### Scenario: No javac substrate in any builtin unit spec

- **WHEN** the imports of every per-strategy unit spec under `…/spi/builtins/` (excluding `e2e/`) are inspected
- **THEN** none imports `com.sun.source.util.JavacTask`, `com.google.testing.compile.Compiler`, `com.google.testing.compile.JavaFileObjects`, or any `io.github.joke.percolate.spi.test` javac fixture (the deleted `TypeUniverse`/`PrivateTypeUniverse`)

#### Scenario: The seam is mocked and its questions are stubbed

- **WHEN** a builtin unit spec exercises its strategy
- **THEN** it holds a `ResolveCtx ctx = Mock()` and stubs the seam questions the scenario needs (e.g. `ctx.isList(type) >> true`)
- **AND** it asserts the returned `OperationSpec` metadata, never invoking `render(...)`

#### Scenario: TypeMirror is an opaque never-stubbed token

- **WHEN** a builtin unit spec passes a `TypeMirror` or `Element` to its strategy
- **THEN** that value is a bare `Mock()` (or a distinct identity) with no stubbed `TypeMirror`/`Element` method
- **AND** the type answers the strategy needs come from the mocked `ResolveCtx`, not from the mirror

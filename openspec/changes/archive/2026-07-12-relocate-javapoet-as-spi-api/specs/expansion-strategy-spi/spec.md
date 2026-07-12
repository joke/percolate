## MODIFIED Requirements

### Requirement: SPI package isolation

The percolate-spi Gradle module SHALL ship a package `io.github.joke.percolate.spi` containing exactly the strategy-author surface: two author interfaces (`ExpansionStrategy` and the source-facing `SourceProjection`) plus the `Container` base and the `Conversion` / `Accessor` archetype bases; the immutable result/context types (`OperationSpec`, `Port`, `PortType`, `Demand`, `Directive`, `ChildScopeSpec`, `Nullability`); the `ResolveCtx` interface; the codegen interfaces (`Codegen`, `OperationCodegen`, `ScopeCodegen`, `IncomingValues`); the `Receiver` / `ThisReceiver` / `CallableMethods` / `MethodCandidate` types; the `LiteralCoercion` helper; and the `TypeProbe`, `Containers`, and `Weights` utilities. The module SHALL depend only on JDK types plus the relocated `percolate-javapoet` module — the codegen interface surface's `CodeBlock` is `io.github.joke.percolate.javapoet.CodeBlock`. It SHALL NOT depend on `com.palantir.javapoet`, `percolate-annotations`, or `percolate-processor`.

The package SHALL NOT contain `ExpansionStep`, `Slot`, `Frontier`, `EdgeCodegen`, `GroupCodegen`, `VarNames`, `Intent`, `ElementScope`, `Bridge`, `GroupTarget`, `PathSegmentResolver`, `BridgeStep`, `GroupBuild`, `ResolvedSegment`, `ScopeTransition`, `SourceStep`, `Step`, or `ElementSeed` — these are removed or replaced by the unified `OperationSpec` / `Port` / `Demand` / `OperationCodegen` surface.

The package SHALL declare `@NullMarked` via `package-info.java`.

Built-in strategies SHALL ship from a separate Gradle module `percolate-strategies-builtin` whose only compile dependency is `percolate-spi`. They SHALL NOT import any class from `io.github.joke.percolate.processor.graph` or `io.github.joke.percolate.processor.stages.expand`. This invariant SHALL be enforced structurally by the module compile graph: `percolate-strategies-builtin`'s `build.gradle` declares no compile dependency on `percolate-processor`.

#### Scenario: spi package has @NullMarked
- **WHEN** the source of `spi/src/main/java/io/github/joke/percolate/spi/package-info.java` is inspected
- **THEN** the package declaration carries `@org.jspecify.annotations.NullMarked`

#### Scenario: retired SPI types do not exist
- **WHEN** the `io.github.joke.percolate.spi` package source tree is inspected
- **THEN** no class or enum named `ExpansionStep`, `Slot`, `Frontier`, `EdgeCodegen`, `GroupCodegen`, `VarNames`, `Intent`, `ElementScope`, `Bridge`, `GroupTarget`, `PathSegmentResolver`, `BridgeStep`, `GroupBuild`, `ResolvedSegment`, or `ScopeTransition` exists

#### Scenario: result and context types are present
- **WHEN** the `io.github.joke.percolate.spi` package is inspected
- **THEN** the result type `OperationSpec`, the port type `Port`, the demand context `Demand`, and the codegen interfaces `Codegen` / `OperationCodegen` / `ScopeCodegen` are present

#### Scenario: Built-in strategies have no forbidden imports
- **WHEN** the import statements of the built-in strategies are inspected
- **THEN** none reference any class in `io.github.joke.percolate.processor.graph.*` or `io.github.joke.percolate.processor.stages.expand.*`

#### Scenario: SPI module has no dependency on annotations or processor
- **WHEN** `spi/build.gradle` is inspected
- **THEN** it declares neither `project(':annotations')` nor `project(':processor')` on any `compile` / `implementation` / `api` configuration
- **AND** its only non-JDK `api` dependency is `project(':percolate-javapoet')`, and it declares no dependency on `com.palantir.javapoet:javapoet`

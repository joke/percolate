## MODIFIED Requirements

### Requirement: SPI package isolation

The processor SHALL ship a new package `io.github.joke.percolate.processor.spi` containing exactly the strategy-author surface: three interfaces (`SourceStep`, `GroupTarget`, `Bridge`), four immutable result types (`Step`, `BridgeStep`, `Slot`, `GroupBuild`), the `ResolveCtx` interface, and re-exports of the codegen interfaces (`EdgeCodegen`, `GroupCodegen`, `IncomingValues`, `VarNames`) shipped by the alignment change.

The package SHALL declare `@NullMarked` via `package-info.java`.

Built-in strategies (`GetterRead`, `ConstructorCall`, `DirectAssign`) SHALL NOT import any class from `io.github.joke.percolate.processor.graph` or `io.github.joke.percolate.processor.stages.expand`. This invariant SHALL be enforced by an architectural test.

#### Scenario: spi package has @NullMarked
- **WHEN** the source of `processor/spi/package-info.java` is inspected
- **THEN** the package declaration carries `@org.jspecify.annotations.NullMarked`

#### Scenario: Built-in strategies have no forbidden imports
- **WHEN** the import statements of `GetterRead`, `ConstructorCall`, and `DirectAssign` are inspected
- **THEN** none reference any class in `io.github.joke.percolate.processor.graph.*` or `io.github.joke.percolate.processor.stages.expand.*`

## MODIFIED Requirements

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

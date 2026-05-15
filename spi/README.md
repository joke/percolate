# percolate-spi

Defines the strategy-author surface for percolate: `Bridge`, `SourceStep`, `GroupTarget`, plus value types (`Step`, `BridgeStep`, `Slot`, `GroupBuild`, `ElementSeed`), codegen abstractions (`EdgeCodegen`, `GroupCodegen`, `Receiver`, `ThisReceiver`, `VarNames`, `IncomingValues`), and utilities (`Containers`, `Weights`).

The module depends only on the JDK and `com.palantir.javapoet:javapoet` (because `CodeBlock` is part of the strategy contract). It does NOT depend on `percolate-annotations` or `percolate-processor`.

Third-party strategy authors need only this module at compile time:

```groovy
implementation 'io.github.joke.percolate:spi'
```

For testing strategies against the same type substrate the built-ins use:

```groovy
testImplementation testFixtures(project(':spi'))
```

This exposes `TypeUniverse` and `HarnessResolveCtx` in `io.github.joke.percolate.spi.test`.

From this change forward, `percolate-spi` is a published contract. Changes to it are either non-breaking additions or breaking version bumps.

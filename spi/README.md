# spi

Defines the strategy-author surface for percolate: `Bridge`, `SourceStep`, `GroupTarget`, plus value types (`Step`, `BridgeStep`, `Slot`, `GroupBuild`, `ElementSeed`), codegen abstractions (`EdgeCodegen`, `GroupCodegen`, `Receiver`, `ThisReceiver`, `VarNames`, `IncomingValues`), and utilities (`Containers`, `Weights`).

The module depends only on the JDK and the relocated `percolate-javapoet` (`io.github.joke.percolate.javapoet.CodeBlock` is part of the strategy contract). It does NOT depend on `annotations` or `processor`.

Third-party strategy authors need only this module at compile time:

```groovy
implementation 'io.github.joke.percolate:spi'
```

Strategy specs need no shared type substrate: `spi`'s `testFixtures` export **no** javac-backed
(`JavacTask`/`Types`/`Elements`) type at all. The former real-javac `ResolveCtx` substrates — `HarnessResolveCtx`,
`TypeUniverse`, and the per-spec `PrivateTypeUniverse` — have all been deleted (changes `type-query-seam`,
`cutover-strategies-to-mock-seam`, and `dissolve-private-type-universe`). A unit spec drives its subject against a
mocked seam (`ResolveCtx ctx = Mock()`), stubbing the type questions it asks; a genuinely compiler-backed
`TypeMirror` (e.g. JavaPoet's `TypeName.get(mirror)` rendering) is exercised by the compile-based feature-e2e layer
instead.

From this change forward, `spi` is a published contract. Changes to it are either non-breaking additions or breaking version bumps.

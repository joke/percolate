## RENAMED Requirements

- FROM: `### Requirement: ResolveCtx exposes Types, Elements, mapperType, currentMethod, callableMethods`
- TO: `### Requirement: ResolveCtx exposes Types, Elements, callableMethods`

## MODIFIED Requirements

### Requirement: ResolveCtx exposes Types, Elements, callableMethods

The percolate-spi module SHALL define an interface `io.github.joke.percolate.spi.ResolveCtx` with
exactly these methods:

```java
public interface ResolveCtx {
    Types types();
    Elements elements();
    CallableMethods callableMethods();
}
```

The interface SHALL NOT expose `mapperType()` or `currentMethod()` — they were dead in production
strategy code (only `callableMethods()` is read, by `MethodCallBridge`) and `currentMethod()` was a
footgun under whole-graph expansion (there is no single current method). The interface SHALL NOT
expose any reference to `MapperGraph`, `Edge`, `Node`, `EdgeKind`, `MapperStep`, or any other type
from `processor.graph` or `processor.stages.*`. A strategy author SHALL be able to write a complete
strategy by importing only `io.github.joke.percolate.spi.*`, `javax.lang.model.*`,
`com.palantir.javapoet.*`, and JDK types.

`callableMethods()` SHALL return the per-mapper index produced by `DiscoverCallableMethods`. The
`ResolveCtx` SHALL be constructed **per mapper**, binding its `callableMethods` at construction time;
the processor SHALL NOT use a `ThreadLocal` to back any `ResolveCtx` accessor.

#### Scenario: ResolveCtx provides Types

- **WHEN** `resolveCtx.types()` is invoked
- **THEN** it returns the `javax.lang.model.util.Types` instance from the active `ProcessingEnvironment`

#### Scenario: ResolveCtx provides Elements

- **WHEN** `resolveCtx.elements()` is invoked
- **THEN** it returns the `javax.lang.model.util.Elements` instance from the active `ProcessingEnvironment`

#### Scenario: ResolveCtx provides the callable-method index

- **WHEN** `resolveCtx.callableMethods()` is invoked
- **THEN** it returns the `CallableMethods` instance produced by `DiscoverCallableMethods` for the
  current mapper

#### Scenario: Retired ResolveCtx accessors do not exist

- **WHEN** the `ResolveCtx` interface is inspected
- **THEN** it declares no `mapperType()` and no `currentMethod()` method

#### Scenario: No ThreadLocal backs ResolveCtx

- **WHEN** the `ProcessorModule` source is inspected
- **THEN** no `ThreadLocal` is used to supply any `ResolveCtx` value; `callableMethods` is bound when
  the per-mapper `ResolveCtx` is constructed

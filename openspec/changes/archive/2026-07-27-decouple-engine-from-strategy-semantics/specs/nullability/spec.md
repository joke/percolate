## MODIFIED Requirements

### Requirement: NullabilityResolver is processor-internal

The processor SHALL define an interface `io.github.joke.percolate.processor.nullability.NullabilityResolver` with the following shape:

```java
public interface NullabilityResolver {
    Nullability resolve(TypeMirror type, Element scope);
}
```

`NullabilityResolver` SHALL NOT reside in `percolate-spi`. Strategy authors SHALL NOT call the resolver — instead, strategies surface the `AnnotatedConstruct` they matched through their `Demand`, and the engine invokes the resolver opaquely. This boundary is structural: `percolate-spi` declares no compile dependency on the processor module.

The resolver SHALL NOT be threaded through the graph model. `Scope` SHALL NOT accept a nullness-resolving
callback: a scope's own input declarations carry their nullness already resolved, resolved once where they are
declared. The resolver SHALL remain reachable only through the two `Demand` shapes, for the elements a strategy
discovers mid-expansion (a candidate's parameters and return, a constructor's parameters, an accessor's member),
which are not scope inputs and cannot be pre-resolved.

The processor module SHALL provide exactly one `NullabilityResolver` binding via Dagger as `@Singleton NullabilityResolver`. The binding SHALL be `JspecifyNullabilityResolver`.

#### Scenario: NullabilityResolver resides in processor.nullability package
- **WHEN** the location of `NullabilityResolver.java` is inspected
- **THEN** the file resides under `processor/src/main/java/io/github/joke/percolate/processor/nullability/`
- **AND** does not reside under `spi/src/main/java/`

#### Scenario: Strategy SPI does not import NullabilityResolver
- **WHEN** the source of any class under `spi/src/main/java/io/github/joke/percolate/spi/` or `strategies-builtin/src/main/java/` is inspected
- **THEN** no source line imports `io.github.joke.percolate.processor.nullability.NullabilityResolver`

#### Scenario: Dagger binds exactly one NullabilityResolver
- **WHEN** the processor module's bindings are inspected
- **THEN** exactly one `@Provides`-or-`@Binds` method returns `NullabilityResolver`
- **AND** the bound implementation is `JspecifyNullabilityResolver`

#### Scenario: The graph model holds no resolver
- **WHEN** `Scope`, its implementations, and the source-binding collaborators are inspected
- **THEN** none declares a `NullabilityResolver` field or accepts a nullness-resolving function

#### Scenario: A strategy still resolves a discovered element's nullness
- **WHEN** a strategy asks its `Demand` for the nullness of a candidate method's parameter
- **THEN** the resolver answers, because that element is not a scope input and cannot be pre-resolved

## ADDED Requirements

### Requirement: Nullness SHALL be resolved by exactly one rule

Exactly one component SHALL decide a construct's nullness. No other component SHALL infer nullness by matching an
annotation's name, simple or qualified. In particular, debug and rendering code SHALL consult the resolver's
answer through the graph rather than re-deriving it.

#### Scenario: The debug renderer does not re-derive nullness
- **WHEN** the DOT renderer displays a type's nullness mark
- **THEN** it reads the nullness recorded on the graph, and matches no annotation name

#### Scenario: No second nullness rule exists
- **WHEN** the processor's sources are searched for annotation-name matching against a nullness annotation
- **THEN** only the single resolver implementation matches, and it does so through its configured annotation set

#### Scenario: Scoping rules are honoured everywhere
- **WHEN** a type in a `@NullMarked` scope carries no explicit annotation
- **THEN** every component reporting its nullness agrees with the resolver, including debug output

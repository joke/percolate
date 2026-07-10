## MODIFIED Requirements

### Requirement: Container-codegen handle family

The percolate-spi module SHALL define a `Codegen` marker and a **single** container codegen handle
exposing the kind-local operations a container can supply, each optional: `iterate`
(`Cont<E> → Stream<E>`), `collect` (`Stream<E> → Cont<E>`), `wrap` (`E → Cont<E>`), `unwrap`
(`Cont<E> → E`, partial), and the same-kind scope-owning `mapPresence` (`Cont<A> → Cont<B>`, for a
presence wrapper). There SHALL be no separate sequence/presence handle split (`ContainerCodegen` and
`WrapperCodegen` are unified into one). The paradigm-generic element `map`/`flatMap` is **not** a
container handle method — it is the generic stream strategy's `ScopeCodegen`. A scope-owning
Operation's handle weaves around the rendered child-plan lambda; `iterate`/`collect`/`wrap`/`unwrap`
render inline. The composer obtains every container `CodeBlock` from a handle and contains no literal
container syntax.

Any handle implementation whose rendered `CodeBlock` **chains a call onto its operand** (i.e. its
format string appends `.methodName(...)` after the spliced-in operand, such as `iterate`'s
`stream()`/`collect`'s `collect(...)`/`mapPresence`'s `map(...)`) SHALL prefix that call's leading `.`
with JavaPoet's `$Z` (zero-width space) wrap marker, so that a long fluent pipeline composed from
multiple handles' snippets wraps gracefully at call boundaries instead of rendering as one unbroken
line. This applies uniformly to every first-party handle (built-in containers, `reactor`,
`reactor-blocking`) and is the expected convention for any third-party handle implementation. This is a
textual convention on the snippet's own format string — it does not change any handle method's
signature, and a handle that supplies no chain-shaped snippet (e.g. `wrap`'s `$T.of($L)`, which
prepends rather than appends) is unaffected.

#### Scenario: One handle family, optional operations
- **WHEN** the container codegen handle is inspected
- **THEN** it exposes `iterate`/`collect`/`wrap`/`unwrap`/`mapPresence` as optional providers on one
  type, with no `ContainerCodegen`-vs-`WrapperCodegen` split and no element `map`/`flatMap` method

#### Scenario: A sequence handle supplies stream snippets
- **WHEN** the composer renders a sequence container hop
- **THEN** the `iterate`/`collect` `CodeBlock`s originate from that container's handle, not the composer

#### Scenario: A wrapper handle supplies presence snippets
- **WHEN** the composer renders an `Optional` hop
- **THEN** the `wrap`/`unwrap`/`mapPresence` `CodeBlock`s originate from that container's handle, and
  it provides no `collect`

#### Scenario: A chain-shaped handle snippet carries a wrap point
- **WHEN** a first-party handle's `iterate`, `collect`, or `mapPresence` snippet is inspected
- **THEN** its format string carries a `$Z` immediately before the chained call's leading `.`, so the
  rendered text is unaffected below JavaPoet's column limit and wraps at that `.` above it

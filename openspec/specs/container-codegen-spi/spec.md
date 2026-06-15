# Container Codegen SPI Spec

## Purpose

The container-codegen SPI is the strategy-supplied seam through which a developer adds container support (`List`/`Set`/array/`Optional`, and developer `Flux`/`Mono`/custom) by writing **one class per container** — both a `ContainerMatch` candidacy and a codegen-handle provider (per-paradigm snippets). The composer owns only structure (the recursive extracted-plan walk) and obtains every container `CodeBlock` from a handle attached to the container `Operation` — a scope-owning Operation's handle weaves around the rendered child-plan lambda, a wrap/unwrap handle renders inline — so adding a container needs no engine or composer change. Code generation is a pure function of the solved graph.

## Requirements

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

### Requirement: A single Container base; kind is emergent

The percolate-spi module SHALL define one abstract `Container` base (implementing `ContainerMatch`)
that lets a developer declare a container in **one class** by supplying its type predicate
(`matches`), its element extractor (`element`), and the optional kind-local operation snippets it
supports. Container **kind is emergent from which operations are present** — a container that supplies
`collect` is a sequence; one that omits `collect` (supplying `wrap`/`unwrap`/`mapPresence`) is a
presence wrapper. There SHALL be no `SequenceContainer`/`WrapperContainer` type distinction.

#### Scenario: A developer adds a container with one class
- **WHEN** a developer writes a class extending `Container` implementing `matches`, `element`, and the
  operation snippets it supports, annotated `@AutoService`
- **THEN** the processor discovers it via `ServiceLoader` and uses it for matching and codegen, with no
  change to the composer, the driver, or any built-in

#### Scenario: Absence of collect makes a wrapper
- **WHEN** a `Container` supplies no `collect`
- **THEN** the engine treats it as a presence wrapper (no stream-collapse into it), and supplies its
  `wrap`/`unwrap`/`mapPresence` where demanded

### Requirement: Built-in containers are the first customers of the SPI

The built-in List, Set, array, and Optional containers SHALL be implemented as `ListContainer`,
`SetContainer`, `ArrayContainer`, and `OptionalContainer` on the **single** `Container` base a
developer uses, with no privileged internal path: sequences supply `iterate`/`collect` (and a
singleton `wrap`); `OptionalContainer` supplies `iterate`/`wrap`/`unwrap`/`mapPresence` and no
`collect`.

#### Scenario: List and Optional are expressed on the same base
- **WHEN** `ListContainer` and `OptionalContainer` are inspected
- **THEN** both extend the one `Container` base; `ListContainer` supplies `collect`, `OptionalContainer`
  does not, and neither is distinguishable in shape from a developer-authored container

### Requirement: Container codegen handles attach to Operations

Container codegen handles (per-paradigm snippet providers) SHALL attach to the container
`Operation`: the scope-owning Operation's handle weaves the container operation around the rendered
child-scope lambda; a wrap/unwrap Operation's handle renders inline like any scalar codegen. The
composer obtains every container `CodeBlock` from a handle and owns only the recursive plan walk —
adding a container still requires no engine or composer change.

#### Scenario: Handle weaves around the child lambda
- **WHEN** the composer renders a scope-owning container Operation
- **THEN** it obtains the iterate/collect (or map/orElse) snippets from the Operation's handle and
  inserts the rendered child plan as the lambda body

#### Scenario: One class per container still suffices
- **WHEN** a developer adds support for a new container type
- **THEN** one class provides both candidacy and the codegen handle, with no composer modification

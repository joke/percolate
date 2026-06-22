# Reactor Containers Spec

## Purpose

This spec defines the Project Reactor (`Flux`/`Mono`) container family — a third-party `percolate-spi` plugin (the `reactor` Gradle module) that adds reactive mapper support with **zero engine change**, as the first real customer of the target-driven dev SPI. Both containers compose over a **single shared reactive intermediate (`Flux`)**: `Flux` is the sequence kind and `Mono` is a presence wrapper that projects to `Flux` (mirroring how `Optional` projects to `Stream`), so cross-kind reactive (`Mono → Flux`) composes exactly as JDK cross-kind does over `Stream`. The module supplies `FluxContainer`/`MonoContainer` + a `FluxMap` functor-lift, the non-blocking **downward** interop bridges and same-paradigm reductions (`justOrEmpty`/`fromStream`/`collectList`/`single()`/`singleOptional`), and enforces the **boundary-direction rule** — the engine auto-crosses the JDK↔reactive paradigm boundary only downward (sync→async, free); **upward** (async→sync, blocking) crossings are never auto-invented and require an opt-in module (deferred). All behaviour rides the published `spi` surface (`polymorphic-conversion`, `container-expansion`, `expansion-strategy-spi`) and changes no engine requirement.

## Requirements

### Requirement: Container mappings use the bean-field convention

Reactive container conversions SHALL generate in both shapes: as a **bean field** of reactive type
(sourced from another bean field via `@Map`, with the per-element transform delegated to a declared
`@Map`-annotated method), and as a **direct container-return** top-level method. The direct
container-return case (`Flux<DAO> map(Flux<DTO>)`, and the builtin `List<DAO> map(Set<DTO>)`) SHALL no
longer be a limitation — it is fixed by the engine changes in this change (the narrow self-bridge
exclusion and the seeded return-root identity, per `graph-expansion` / `plan-extraction` /
`code-generation`) and MUST behave identically across the JDK and reactive paradigms.

#### Scenario: Reactive field conversion composes

- **WHEN** a mapper maps a target bean field of type `Flux<DAO>` from a source bean field of type `Flux<DTO>` via `@Map(target="people", source="src.people")`, with a declared element method `DAO mapOne(DTO)`
- **THEN** the generated code produces the field via `src.getPeople().map(e -> mapOne(e))` and compiles

#### Scenario: Direct container-return generates the real plan

- **WHEN** a mapper declares a direct container-return method `Flux<DAO> map(Flux<DTO>)` with a declared element method `DAO mapOne(DTO)` (and identically for the builtin `List<DAO> mapAddresses(Set<DTO>)` with `DAO mapAddress(DTO)`)
- **THEN** the generated code delegates per element to the sibling method (`flux.map(e -> mapOne(e))`; `src.stream().map(e -> mapAddress(e)).collect(...)`) — never a `return this.map(src)` self-call and never a "no plan" diagnostic

### Requirement: Reactor container family over a single shared Flux intermediate

The `reactor` module SHALL provide `Flux` and `Mono` containers as third-party SPI strategies registered with `@AutoService({ExpansionStrategy.class, SourceProjection.class})`, extending the public `Container` base. Both SHALL declare `reactor.core.publisher.Flux` as the **single shared** element-sequence intermediate. `Mono` SHALL be a presence wrapper (supplies `wrap`/`mapPresence`, omits `collect`) that projects to `Flux` (`Mono<X> → Flux<X>`), mirroring how `Optional` projects to `Stream`. The modules SHALL use only the published `spi` surface and require **no change** to `spi`, `processor`, `strategies-builtin`, or `annotations`.

#### Scenario: Flux-to-Flux element map

- **WHEN** a `Flux<DTO>` field is mapped to a `Flux<DAO>` field and a `DTO → DAO` producer is resolvable
- **THEN** the generated code transforms via `flux.map(...)`, contains no `java.util.stream.Stream`, and is produced with no modification to any engine module

#### Scenario: Mono-to-Mono element map

- **WHEN** a `Mono<DTO>` field is mapped to a `Mono<DAO>` field and a `DTO → DAO` producer is resolvable
- **THEN** the generated code transforms via `mono.map(...)`

#### Scenario: Cross-kind reactive via the shared intermediate

- **WHEN** a `Mono<DTO>` field is mapped to a `Flux<DAO>` field and a `DTO → DAO` producer is resolvable
- **THEN** the `Mono` source is opened to the shared `Flux` intermediate (`mono.flux()`) and mapped, the result staying in the reactive world without blocking

### Requirement: kind-equals-intermediate composition for Flux

Because `Flux`'s own type IS the shared intermediate, the `Flux` container SHALL compose element maps and cross-kind reactive conversions without minting a degenerate identity self-loop operation (`Flux<X> ← Flux<X>`), and expansion SHALL terminate.

#### Scenario: Flux source grounds a map port directly

- **WHEN** the engine grounds a type-variable `Flux<A>` map port against an in-scope `Flux<X>` source
- **THEN** it binds `A := X` directly without an intermediate projection/iterate step, emits no `Flux<X> ← Flux<X>` identity self-loop, and the expansion terminates

### Requirement: Non-blocking downward interop bridges (sync to async)

The `reactor` module SHALL provide downward bridges that enter the reactive world without blocking a thread: `Optional<T> → Mono<T>` (`Mono.justOrEmpty`), `Stream<T> → Flux<T>` (`Flux.fromStream`, which the JDK collection containers feed via the shared `Stream` intermediate), and `T → Mono<T>` (`Mono.just`, the `Mono` container's `wrap`). These are target-driven conversions keyed on the concrete demanded reactive target.

#### Scenario: Optional field to Mono field

- **WHEN** an `Optional<DTO>` field is mapped to a `Mono<DAO>` field and a `DTO → DAO` producer is resolvable
- **THEN** the generated code bridges via `Mono.justOrEmpty(...)` over the mapped optional

#### Scenario: List field to Flux field

- **WHEN** a `List<DTO>` field is mapped to a `Flux<DAO>` field and a `DTO → DAO` producer is resolvable
- **THEN** the generated code bridges via `Flux.fromStream(...)` over the mapped element stream

### Requirement: Same-paradigm reductions stay reactive

The `reactor` module SHALL provide reductions that remain in the reactive world (output is a reactive type, no blocking): `Flux<T> → Mono<List<T>>` (`collectList`), `Flux<T> → Mono<T>` via `single()` as the **canonical single-element reduction**, and `Mono<T> → Mono<Optional<T>>` (`singleOptional`). For `Flux<T> → Mono<T>` the module SHALL emit only `single()`; `next`/`last`/positional selections SHALL NOT be auto-generated — a developer reducing a stream to a single value means exactly one element, and any other selection requires a manually written converter.

#### Scenario: Flux field to Mono-of-List field

- **WHEN** a `Flux<DTO>` field is mapped to a `Mono<List<DAO>>` field and a `DTO → DAO` producer is resolvable
- **THEN** the generated code maps and reduces via `collectList()`, remaining non-blocking

#### Scenario: Flux field to single Mono field uses single()

- **WHEN** a `Flux<DTO>` field is mapped to a `Mono<DAO>` field and a `DTO → DAO` producer is resolvable
- **THEN** the generated reduction uses `single()` and never `next()` or `last()`

#### Scenario: Mono field to Mono-of-Optional field

- **WHEN** a `Mono<DTO>` field is mapped to a `Mono<Optional<DAO>>` field and a `DTO → DAO` producer is resolvable
- **THEN** the generated code uses `singleOptional()`

### Requirement: Boundary-direction rule — upward crossings are never auto-invented

With only the `reactor` module on the annotation-processor classpath, the engine SHALL NOT auto-generate any async-to-sync (blocking) crossing of the JDK/reactive paradigm boundary. A demand that requires extracting a synchronous value from a reactive source SHALL be reported as a "no producer" diagnostic, never silently satisfied with `.block()`, `collectList().block()`, or `toStream()`. This is the `target-driven-engine` D4 invariant ("the engine invents no bridges") realised for the reactive paradigm.

#### Scenario: Mono field to scalar field reports no producer (negative)

- **WHEN** a `Mono<DTO>` field is mapped to a plain `DAO` field and only the `reactor` module is present
- **THEN** compilation reports a "no producer" diagnostic and the generated output contains no `.block()`

#### Scenario: Flux field to synchronous collection field reports no producer (negative)

- **WHEN** a `Flux<DTO>` field is mapped to a `List<DAO>` field and only the `reactor` module is present
- **THEN** compilation reports a "no producer" diagnostic and the generated output contains no `collectList().block()` or `toStream()`

### Requirement: Opt-in blocking module ships (upward async-to-sync crossings)

An opt-in `reactor-blocking` Gradle module SHALL provide the upward async→sync crossings — `block`
(`Mono<T>→T`), `blockOptional` (`Mono<T>→Optional<T>`), `single().block` (`Flux<T>→T`),
`collectList().block` (`Flux<T>→List<T>`), and `toStream` (`Flux<T>→Stream<T>`) — as a pure SPI plugin
(`@AutoService`, `implementation project(':spi')`, a `reactor-core` pin, no engine dependency). Each
upward edge SHALL be weighted strictly above any non-blocking alternative and SHALL use reuse-only
ports (the `unwrap` pattern) so it never mints an ever-deeper source. The boundary-direction rule SHALL
remain enforced by packaging: downward auto (`reactor`), upward only when `reactor-blocking` is on the
annotation-processor classpath.

The module SHALL additionally register the matching **blocking `SourceProjection`s** so a JDK element
transform can ground its element type against a reactive in-scope source: `Flux<X> → Stream<X>` (so a
type-variable `Stream<A>` element-map port grounds `A := X`) and `Mono<X> → Optional<X>`. Each
grounding view SHALL be projected **only** from a **total** upward bridge (`toStream` /
`collectList().block` for `Flux`, `blockOptional` for `Mono`), never from a partial one (`block`,
`single().block`). A grounding view only widens the grounding-match set; the concrete intermediate
SHALL still be produced by the existing weighted reuse-only bridge, so no eager block is invented and
no non-blocking alternative is out-priced. These views SHALL ship **only** in `reactor-blocking`: they
cannot live in non-blocking `reactor` (which would make upward element crossings auto-discoverable) nor
in a JDK container (which names no reactive kind), so packaging continues to enforce the
boundary-direction rule.

#### Scenario: Upward crossing is satisfied only with the blocking module present

- **WHEN** a `Mono<DTO>` field maps to a plain `DAO` field and both `reactor` and `reactor-blocking` are on the annotation-processor classpath
- **THEN** the engine satisfies the upward crossing via the weighted blocking strategy (e.g. `.block()` feeding the element transform), and the same mapper with only `reactor` present still reports "no producer"

#### Scenario: Reactive element transform into a JDK container generates

- **WHEN** `List<DAO> map(Flux<DTO> src)` (param-direct) is expanded with both `reactor` and `reactor-blocking` present and the mapper declares `DAO mapOne(DTO)`
- **THEN** the `Flux<X> → Stream<X>` grounding view grounds the element type, so the engine generates `src.toStream().map(this::mapOne).collect(...)` — the element transform is no longer ungroundable

#### Scenario: A grounding view is projected only from a total bridge

- **WHEN** the `reactor-blocking` projections are inspected
- **THEN** `Flux<X> → Stream<X>` and `Mono<X> → Optional<X>` are contributed (from `toStream`/`collectList().block` and `blockOptional`), and no grounding view is contributed by the partial `block` / `single().block` bridges

#### Scenario: Blocking never out-prices a non-blocking alternative

- **WHEN** a demand can be satisfied either by staying reactive (a non-blocking path) or by an upward blocking crossing — e.g. `Mono<DAO> map(Mono<DTO>)` where lazy `mono.map(f)` competes with eager `Mono.just(f(mono.block()))`
- **THEN** the non-blocking (lazy) path is always selected, because every blocking edge is weighted strictly higher — a correctness property (deferred vs blocks-at-assembly), not a style one

#### Scenario: Blocking is not auto-invented and does not self-bridge

- **WHEN** `reactor-blocking` is present and an upward crossing is demanded at a method return root
- **THEN** the blocking strategy (not a `this.m(...)` self-call) is selected, confirming the self-call rule lets the high-weight blocking path surface rather than being masked

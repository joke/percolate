# Reactor Containers Spec

## Purpose

This spec defines the Project Reactor (`Flux`/`Mono`) container family — a third-party `percolate-spi` plugin (the `reactor` Gradle module) that adds reactive mapper support with **zero engine change**, as the first real customer of the target-driven dev SPI. Both containers compose over a **single shared reactive intermediate (`Flux`)**: `Flux` is the sequence kind and `Mono` is a presence wrapper that projects to `Flux` (mirroring how `Optional` projects to `Stream`), so cross-kind reactive (`Mono → Flux`) composes exactly as JDK cross-kind does over `Stream`. The module supplies `FluxContainer`/`MonoContainer` + a `FluxMap` functor-lift, the non-blocking **downward** interop bridges and same-paradigm reductions (`justOrEmpty`/`fromStream`/`collectList`/`single()`/`singleOptional`), and enforces the **boundary-direction rule** — the engine auto-crosses the JDK↔reactive paradigm boundary only downward (sync→async, free); **upward** (async→sync, blocking) crossings are never auto-invented and require an opt-in module (deferred). All behaviour rides the published `spi` surface (`polymorphic-conversion`, `container-expansion`, `expansion-strategy-spi`) and changes no engine requirement.

## Requirements

### Requirement: Container mappings use the bean-field convention

Reactive container conversions SHALL be expressed the same way every percolate container conversion is: a **bean field** of reactive type, sourced from another bean field via `@Map`, with the per-element transform delegated to a declared `@Map`-annotated method. A **direct container-return** top-level method (`Flux<DAO> map(Flux<DTO>)`) is a **known pre-existing percolate limitation** — the engine produces no plan for a root demand that is itself a container, and the builtin `StreamMap` fails identically for `List<DAO> map(List<DTO>)`. It is documented here and tracked as a separate follow-up; it is NOT introduced by this change and affects the JDK and reactive paradigms equally.

#### Scenario: Reactive field conversion composes

- **WHEN** a mapper maps a target bean field of type `Flux<DAO>` from a source bean field of type `Flux<DTO>` via `@Map(target="people", source="src.people")`, with a declared element method `DAO mapOne(DTO)`
- **THEN** the generated code produces the field via `src.getPeople().map(e -> mapOne(e))` and compiles

#### Scenario: Direct container-return is not silently mis-generated

- **WHEN** a mapper declares a direct container-return method `Flux<DAO> map(Flux<DTO>)`
- **THEN** compilation reports a "no plan" diagnostic (the pre-existing limitation), never silently wrong output — identical to the builtin `List<DAO> map(List<DTO>)` behaviour

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

### Requirement: Opt-in blocking module is deferred (blocked by a pre-existing self-bridge quirk)

The opt-in `reactor-blocking` module (upward async-to-sync crossings: `block`/`blockOptional`/`single().block`/`collectList().block`/`toStream`, each weighted above any non-blocking alternative) is **deferred to a follow-up change**. The strategies were prototyped and shown to terminate (reuse-only ports, the `unwrap` pattern), but their behaviour cannot be demonstrated correctly because of a **pre-existing, paradigm-agnostic percolate quirk**: a mapper method bridges its own signature (`Tgt map(Src)` is generated as `return this.map(src)`), and that self-bridge (weight `METHOD`) out-prices the deliberately high-weighted blocking path, masking it. The quirk also masks a clean "no plan" for an unsatisfiable bean root. The blocking module SHALL ship only alongside a fix that excludes a method from bridging its own signature; until then, a developer needing an upward crossing writes a manual converter (the D4 path), and the engine never auto-invents blocking (next requirement).

#### Scenario: No blocking strategy is registered until the self-bridge fix ships

- **WHEN** the `reactor` module is assembled and only its strategies are on the annotation-processor classpath
- **THEN** no `reactor-blocking` module is present, no async-to-sync blocking strategy (`block`/`blockOptional`/`single().block`/`collectList().block`/`toStream`) is registered, and an upward crossing is satisfiable only by a hand-written converter

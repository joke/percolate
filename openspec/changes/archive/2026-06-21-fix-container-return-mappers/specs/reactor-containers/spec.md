## MODIFIED Requirements

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

## REMOVED Requirements

### Requirement: Opt-in blocking module is deferred (blocked by a pre-existing self-bridge quirk)

**Reason**: The self-bridge quirk it was blocked on is fixed in this change (the narrow "a method never satisfies its own return root" exclusion, `graph-expansion`), so the blocking module now ships.
**Migration**: The upward async-to-sync crossings are now provided by the `reactor-blocking` module (see the new "Opt-in blocking module ships" requirement). Add `reactor-blocking` to the annotation-processor classpath to enable blocking; omit it to keep upward crossings unavailable (the boundary-direction negative still holds).

## ADDED Requirements

### Requirement: Opt-in blocking module ships (upward async-to-sync crossings)

An opt-in `reactor-blocking` Gradle module SHALL provide the upward async→sync crossings — `block`
(`Mono<T>→T`), `blockOptional` (`Mono<T>→Optional<T>`), `single().block` (`Flux<T>→T`),
`collectList().block` (`Flux<T>→List<T>`), and `toStream` (`Flux<T>→Stream<T>`) — as a pure SPI plugin
(`@AutoService`, `implementation project(':spi')`, a `reactor-core` pin, no engine dependency). Each
upward edge SHALL be weighted strictly above any non-blocking alternative and SHALL use reuse-only
ports (the `unwrap` pattern) so it never mints an ever-deeper source. The boundary-direction rule SHALL
remain enforced by packaging: downward auto (`reactor`), upward only when `reactor-blocking` is on the
annotation-processor classpath.

#### Scenario: Upward crossing is satisfied only with the blocking module present

- **WHEN** a `Mono<DTO>` field maps to a plain `DAO` field and both `reactor` and `reactor-blocking` are on the annotation-processor classpath
- **THEN** the engine satisfies the upward crossing via the weighted blocking strategy (e.g. `.block()` feeding the element transform), and the same mapper with only `reactor` present still reports "no producer"

#### Scenario: Blocking never out-prices a non-blocking alternative

- **WHEN** a demand can be satisfied either by staying reactive (a non-blocking path) or by an upward blocking crossing — e.g. `Mono<DAO> map(Mono<DTO>)` where lazy `mono.map(f)` competes with eager `Mono.just(f(mono.block()))`
- **THEN** the non-blocking (lazy) path is always selected, because every blocking edge is weighted strictly higher — a correctness property (deferred vs blocks-at-assembly), not a style one

#### Scenario: Blocking is not auto-invented and does not self-bridge

- **WHEN** `reactor-blocking` is present and an upward crossing is demanded at a method return root
- **THEN** the blocking strategy (not a `this.m(...)` self-call) is selected, confirming the self-bridge exclusion lets the high-weight blocking path surface rather than being masked

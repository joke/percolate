## MODIFIED Requirements

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

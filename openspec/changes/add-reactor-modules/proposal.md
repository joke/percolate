## Why

The `target-driven-engine` change proved — **on paper only** (design D7, the `Flux<Dto>→Flux<Entity>` / `Mono<Dto>→Mono<Entity>` traces) — that a third party can add Project Reactor support on the *same* SPI the built-ins use, with **zero engine change**. No reactive code shipped, so the north-star's central promise is currently an **unfalsified claim**: nothing in the build exercises a non-`Stream` intermediate, a `kind == intermediate` container, or the "engine invents no bridges" (D4) invariant against a real reactive paradigm. This change builds it — turning the paper proof into executed tests and giving reactive consumers genuine `Flux`/`Mono` mapper support.

## What Changes

- **New `reactor` Gradle module (peer of `strategies-builtin`).** A pure third-party SPI plugin — `@AutoService({ExpansionStrategy.class, SourceProjection.class})`, `implementation project(':spi')`, **no engine dependency, no engine change**. It contains:
  - `FluxContainer` + `MonoContainer` over a **single shared reactive intermediate = `Flux`** (`Mono` is a presence-wrapper that projects to `Flux`, exactly as `Optional` projects to `Stream`). This is the first `kind == intermediate` container (`Flux`'s own type is the intermediate).
  - `FluxMap` — the `Flux<B> ← Flux<A>` functor-lift (a near-copy of the shipped `StreamMap`, keyed to `Flux` instead of `java.util.stream.Stream`).
  - **Downward (sync→async) interop bridges — non-blocking, always safe:** `Optional<T>→Mono<T>` (`justOrEmpty`), `List`/`Iterable`/`Stream<T>→Flux<T>` (`fromIterable`/`fromStream`), `T→Mono<T>` (`fromCallable`).
  - **Same-paradigm reductions — non-blocking, stay in the reactive world:** `Flux<T>→Mono<List<T>>` (`collectList`), `Flux<T>→Mono<T>` (`single()` — the canonical single-element reduction), `Mono<T>→Mono<Optional<T>>` (`singleOptional`).
- **`reactor-blocking` opt-in module — DEFERRED to a follow-up.** The **upward (async→sync) crossings** (`block`/`blockOptional`/`single().block`/`collectList().block`/`toStream`, weighted high, reuse-only ports) were prototyped and terminate, but their behaviour is masked by a **pre-existing percolate self-bridge quirk** (`Tgt map(Src)` is generated as `return this.map(src)`, out-pricing the high-weight blocking path). The module ships in a follow-up alongside a fix that excludes a method from bridging its own signature. The boundary rule's "no auto-blocking" half is still delivered and proven here (the `reactor`-only negatives).
- **Boundary-direction rule (the load-bearing invariant).** The engine auto-crosses the JDK↔reactive paradigm boundary **only downward** (sync→async, free); the **upward** direction is never auto-invented (this *is* D4). With only the `reactor` module on the classpath, an async→sync demand (`PersonDAO map(Mono<PersonDTO>)`, `List<X> map(Flux<X>)`) reports **"no producer"** rather than silently emitting `.block()` — a **negative test** is the guard.
- **`reactor-core` is an `implementation` dependency of `percolate-reactor`** (pinned in `dependencies`). Reactor support is a hard requirement of the plugin, so it may reference `Flux`/`Mono` directly; erasure matching still resolves against the consumer's compile classpath via `TypeProbe`. (Building the module surfaced that the `Container` base cannot tolerate a genuinely-absent intermediate without an `spi` change — see design D6; making `reactor-core` a hard dependency keeps **zero `spi` change**.)
- **Spike — `kind == intermediate` for `Flux`** (gate, first task): confirm the `Container` base composes `Flux→Flux` map and `Mono→Flux` without minting a degenerate identity self-loop, before building the family.

## Capabilities

### New Capabilities
- `reactor-containers`: the Project Reactor plugin behaviour — `Flux`/`Mono` containers over a single shared `Flux` intermediate (`Mono` as a `Flux`-projecting presence wrapper), `FluxMap`, the non-blocking downward bridges and same-paradigm reductions (`single()`/`collectList`/`singleOptional`), the two-module packaging (`reactor` default + `reactor-blocking` opt-in), the boundary-direction rule (downward auto, upward opt-in only), and the `Mono→scalar`/`Flux→List` "no producer" negative requirement.

### Modified Capabilities
<!-- None. The whole premise is ZERO engine change: this rides the existing target-driven SPI
     (polymorphic-conversion, container-expansion, expansion-strategy-spi, graph-expansion) without
     altering any of their requirements. It is the concrete realisation those specs already anticipate
     (they cite Flux as the worked example); if drafting the specs/design surfaces a genuine requirement
     change to an existing capability, it will be added here rather than assumed. -->

## Impact

- **Build:** one new module (`reactor`) added to `settings.gradle` (`reactor-blocking` deferred); `io.projectreactor:reactor-core` version pinned in `dependencies/build.gradle`. No change to `annotations`, `processor`, `spi`, or `strategies-builtin` source.
- **Consumer wiring:** the plugin goes on the **annotationProcessor** classpath (it runs inside the processor), not `implementation`:
  ```
  annotationProcessor 'io.github.joke:percolate-processor'
  annotationProcessor 'io.github.joke:percolate-reactor'            // non-blocking (reactor-blocking is a follow-up)
  implementation       'io.projectreactor:reactor-core'             // a reactive project already has this
  ```
- **No consumer-facing annotation change.** Existing mappers and the `@Mapper` surface are untouched.
- **Engine invariant made load-bearing in tests:** "the engine invents no bridges" (D4) is what keeps the upward crossings out of the default module — verified by the `Mono→scalar`/`Flux→List` "no producer" negative test.
- **Teams:** SPI/plugin authors + build. Validates `polymorphic-conversion` and `container-expansion` against a real non-`Stream` paradigm (referenced, not modified).
- **Risk:** `kind == intermediate` (`Flux`) is the one new wrinkle — gated by the spike (first task). The type-variable grounding mechanic it relies on already shipped and is proven.

## 1. Spike — `kind == intermediate` (Flux) (gate)

- [x] 1.1 Load the Java / Lombok / null-safety / Spock convention skills before writing any code
- [x] 1.2 On the shipped `Grounding`/`Container` surface, confirm a `Flux` whose `kindErasure == intermediateErasure == Flux` grounds a type-variable `Flux<A>` map port **directly** against an in-scope `Flux<X>` source (binds `A := X`, no projection/iterate step) and emits **no** degenerate `Flux<X> ← Flux<X>` identity self-loop — CONFIRMED: base would emit `collect`/`iterate` identities (`matches` && `isIntermediate` both hold), avoided by `FluxContainer` omitting both
- [x] 1.3 Confirm `Mono → Flux` (project to the shared intermediate) composes a cross-kind reactive conversion and that expansion terminates (Value + spec dedup, strictly shrinking elements) — CONFIRMED: `MonoContainer.iterate = mono.flux()` projects `Mono<X> → Flux<X>`, `iterateInto(Flux<X>)` produces `Flux<X> ← Mono<X>`; element shrinks one step
- [x] 1.4 **Gate:** if the `Container` base cannot express `kind == intermediate` without an engine/`spi` change, STOP and surface the architecture finding before any module code; otherwise record which of `FluxContainer`'s `iterate`/`collect` are needed vs identity-omitted (design D2 fallback) — **PASS, no engine change**: `FluxContainer` omits `iterate` + `collect`; `MonoContainer` omits `collect` + `unwrap` (unwrap = `block()` → `reactor-blocking`); recorded in design D2

## 2. `reactor` module scaffold + dependency

- [x] 2.1 Add `reactor` to `settings.gradle`; `build.gradle` mirroring `strategies-builtin` (java + groovy plugins, `@AutoService` annotation processor, `implementation project(':spi')`, Spock test deps, single-threaded test config, `compileTestJava` release 17)
- [x] 2.2 Pin `io.projectreactor:reactor-core` in `dependencies/build.gradle` constraints; `implementation` of `reactor` (decision D6 — `reactor-core` is a hard plugin dependency) + `testImplementation` for the end-to-end mappers

## 3. Reactor containers over the shared `Flux` intermediate

- [x] 3.1 `FluxContainer` (sequence): `kindErasure == intermediateErasure == Flux`, omits `iterate`/`collect` (spike fallback), `wrap` = `Flux.just`, `@AutoService({ExpansionStrategy.class, SourceProjection.class})`
- [x] 3.2 `MonoContainer` (presence wrapper): `wrap` via `Mono.just`, `mapPresence` via `mono.map`, `iterate`/projects to `Flux` (`mono.flux()`), omits `collect` + `unwrap`; `@AutoService` both interfaces
- [x] 3.3 `FluxMap`: `Flux<B> ← Flux<A>` functor lift (`map`/`flatMap`) — a near-copy of `StreamMap` keyed to `Flux`
- [x] 3.4 Spock end-to-end (bean-field convention — direct container-return is a pre-existing limitation, see spec/design): `Flux<DTO> → Flux<DAO>`, `Mono<DTO> → Mono<DAO>`, cross-kind `Mono<DTO> → Flux<DAO>` — all green

## 4. Non-blocking downward bridges + same-paradigm reductions (`reactor`)

- [x] 4.1 `justOrEmpty`: `Optional<T> → Mono<T>` — concrete target-driven conversion (no type variable; design D5)
- [x] 4.2 `fromStream`: `Stream<T> → Flux<T>` (`FluxFromStream`; JDK collections feed it through the shared `Stream` intermediate, covering `List`/`Set`/array)
- [x] 4.3 `T → Mono<T>` covered by `MonoContainer.wrap` (`Mono.just`) — no separate `fromCallable` needed
- [x] 4.4 `collectList`: `Flux<T> → Mono<List<T>>` (stays reactive)
- [x] 4.5 `single`: `Flux<T> → Mono<T>` — emit **only** `single()`, never `next()`/`last()` (design D4)
- [x] 4.6 `singleOptional`: `Mono<T> → Mono<Optional<T>>` (weight `STEP` so the single operator wins the tie vs `map`+`ofNullable`)
- [x] 4.7 Spock end-to-end per bridge/reduction; the `Flux → Mono<T>` reduction renders `single()` and not `next()`/`last()` — all green

## 5. Boundary-direction negatives (the `reactor`-only guards)

- [x] 5.1 Negative: `PersonDAO map(Mono<PersonDTO> src)` with only `reactor` present → never invents a `.block()` (direct-return form; green)
- [x] 5.2 Negative: `List<DAO> map(Flux<DTO> src)` with only `reactor` present → "no plan" diagnostic, no `collectList().block()`/`toStream()` (green)

## 6. `reactor-blocking` opt-in module (upward crossings) — DEFERRED to a follow-up

- [~] 6.1 Prototyped (`reactor-blocking` module: 5 strategies `block`/`blockOptional`/`single().block`/`collectList().block`/`toStream`, reuse-only ports so they terminate). **Removed from this change** — see 6.3
- [~] 6.2 Weighted above any non-blocking alternative (design D7) — implemented, terminates
- [~] 6.3 **Blocked by a pre-existing self-bridge quirk**: a mapper method bridges its own signature (`Tgt map(Src)` → `return this.map(src)`), out-pricing the high-weight blocking path and masking it (and masking a clean "no plan"). Blocking ships in a follow-up change alongside a fix excluding a method from bridging its own signature. Module deleted; boundary "no auto-block" guarantee is still proven by 5.x

## 7. Verify

- [x] 7.1 `./gradlew spotlessApply` + `./gradlew check` → BUILD SUCCESSFUL (all modules; reactor's own `:reactor:check` green: spotless/PMD/errorprone/NullAway/codenarc/tests). NOTE: a ~10%/run nondeterministic failure under *parallel* `check` was root-caused to a **single-threaded javac `ClassFinder` re-entrancy** (`Filling X during Y`) in the shared `TypeUniverse` test fixture — **not** a quality-task/cross-module issue (the original suspicion), and **not** suppressible by `-ea`/`-da` (`com.sun.tools.javac.util.Assert` throws unconditionally — no `$assertionsDisabled` field). A record fixture's `getAllMembers` lazily completes `java.lang.Record` mid-traversal of another in-flight fill; parallelism only raised the odds via class-load timing. **Fixed in `TypeUniverse`**: eagerly complete each resolved type's inheritance/nesting closure on `lookup` (generalises the hardcoded JDK preload — records auto-pull `java.lang.Record`, enums `java.lang.Enum`, etc., with no per-fixture additions). Confirmed by a 40× parallel `--rerun-tasks check` hammer (0 re-entrancy failures, was ~1/10); `--max-workers=1` no longer required.
- [ ] 7.2 Commit the completed change with `/commit-commands:commit` — pending user confirmation (significant scope change: `reactor-blocking` deferred)

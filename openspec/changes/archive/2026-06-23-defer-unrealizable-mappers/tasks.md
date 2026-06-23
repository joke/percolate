## 1. Spike — round/fixpoint mechanics (DONE)

- [x] 1.1 Confirm a deferred mapper re-resolves by name in a later round where the Lombok-injected constructor is visible (standalone `BasicAnnotationProcessor` + real Lombok 1.18.46, one compilation unit). Result: **GO** — round 1 sees `[0]`-arg only; round 2 sees the all-args ctor; compile succeeds.
- [x] 1.2 Confirm the diagnostic-reclaim mechanism. Result: the Step is never called at `processingOver`, and `postRound` emission does not suppress `BasicAnnotationProcessor`'s `MiscError`; a **monotone fixpoint** (emit + consume when the outcome stops changing in a no-progress round) yields a single clean error. Design decisions D3/D4 reflect this.

## 2. Record the realisation outcome on the context

- [x] 2.1 Add an ordered, empty-by-default recorded-outcome field to `MapperContext` (e.g. `List<String> unsatisfiedRealisation`) with Lombok `@Getter`/`@Setter`, holding **strings only** (no `Element`/`TypeMirror`), per design D5.
- [x] 2.2 Change `RealisationDiagnosticsStage` to **record** its closest-miss messages onto `MapperContext` instead of calling `Diagnostics.error(...)`. Keep the early-return on `diagnostics.hasErrorsFor(mapperType)` and the seeded-root / dead-sibling rules unchanged (realisation-validation spec: MODIFIED "Diagnostics walk unsatisfied demands").

## 3. Round-aware MapperStep (defer + fixpoint emit)

- [x] 3.1 Add `MapperStep` cross-round state: a `Map<String mapperFqn, List<String> outcome>` of the previous round's recorded outcome (keyed by FQN, no `Element` refs).
- [x] 3.2 After `Pipeline.process(...)` per mapper, classify (processor spec: MODIFIED "MapperStep"):
  contract-error (`hasErrorsFor`) → consume; empty outcome → consume (generated); non-empty outcome → defer or fixpoint-emit.
- [x] 3.3 Implement the monotone fixpoint: defer (return the mapper `TypeElement`) while the outcome differs from last round **or** the prior round made global progress; when the outcome is unchanged across a no-progress round, emit each recorded message via `Diagnostics.error(ctx.getMapperType(), msg)` once, then consume (processor spec: ADDED "Mapper realisation is deferred to a monotone fixpoint").
- [x] 3.4 Change `process(...)` to return the set of deferred mapper `TypeElement`s (no longer always empty).

## 4. Per-round global-progress signal

- [x] 4.1 Override `PercolateProcessor.postRound(RoundEnvironment)` to compute "did this round make global progress" (a mapper consumed or new top-level types appeared) and expose it to `MapperStep`'s fixpoint check. Keep this the only round-state the processor holds; stages stay round-agnostic (design D4; resolve the exact signal per design Open Questions).

## 5. Tests

- [x] 5.1 Update existing processor/`MapperStep` unit + Spock specs to the new return contract (deferred-set instead of always-empty) and the record-not-emit behaviour of `RealisationDiagnosticsStage`.
- [x] 5.2 Compile-testing spec: a genuinely unrealisable mapper (target with no producer) emits the `no plan` message **exactly once**, anchored on the mapper type, and the output contains **no** `BasicAnnotationProcessor` "could not be processed" / `MiscError`.
- [x] 5.3 Compile-testing spec: a contract error (duplicate target / unknown source) is still reported eagerly and the mapper is never deferred (regression — message unchanged).
- [x] 5.4 Regression: existing realisable mappers (containers, reactor, constants) still generate identical output and consume on their first realising round.
- [x] 5.5 Co-module Lombok positive: validate against the `percolate-integration` working tree (`Human`/`Person` `@Value` co-located with `PersonMapper`) — `./gradlew :mappers:classes` is green after the fix. Add an in-tree compile-testing variant with Lombok on the processor path only if it proves reliable; otherwise the integration build is the acceptance bar.

## 6. Verify & commit

- [x] 6.1 Run `./gradlew check` in `percolate` and confirm zero violations (do not continue if red).
- [x] 6.2 Confirm `./gradlew :mappers:classes` is green in `percolate-integration` with the same-module layout.
- [x] 6.3 Commit the completed change with `/commit-commands:commit`.

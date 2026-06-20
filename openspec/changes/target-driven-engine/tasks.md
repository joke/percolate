## 1. Spike — prove the type-variable matching mechanic (gate)

- [x] 1.1 Load the Java / Lombok / null-safety / spock convention skills before writing any code
- [x] 1.2 On a throwaway spike branch, decide the type-variable representation (synthetic `TypeVariable` vs `TypeMirror` wrapper vs index placeholder) and prototype unifying a parameterised port type (`F<A>`) against a concrete source type (`F<Person>`), grounding `A := Person`
- [x] 1.3 Prototype grounding-by-match end to end on `Set<Person> → Set<PersonView>`: ground `A`, substitute across output + child scope, land a concrete Operation, confirm no abstract type enters the work-list
- [x] 1.4 Establish the termination argument (finite in-scope sources, grounded-type dedup, Value dedup, bounded nesting) and the wildcard/bounded-generic policy (`Flux<? extends T>`: support or restrict)
- [x] 1.5 Write the `Flux<Dto> → Flux<Entity>` and `Mono<Dto> → Mono<Entity>` paper trace into `design.md` proving a third party adds reactive with zero engine change
- [x] 1.6 **Gate:** if the mechanic does not hold (termination/representation/agnosticism), STOP and revise the design before any further task — re-opened (the decomposed cross-kind Stream pipeline exposed a hole), revised, and APPROVED: a source-facing `SourceProjection` SPI parallel to `ExpansionStrategy` feeds grounding's match set; engine stays type-agnostic; reactive accepted as the justification (see design.md § Spike Revision / D8)

## 2. Engine — type-variable ports and grounding-by-match

- [x] 2.1 Add type-variable support to `Port` / the `OperationSpec` types (a port type may carry a variable; output + child scope may reference it)
- [x] 2.2 Implement grounding-by-match in the driver's port-sourcing: unify a type-variable port against in-scope sources, ground the variable, substitute, instantiate one concrete Operation per match through the `Applier`
- [x] 2.3 Keep the work-list strictly concrete (no abstract Value), preserve target→source order (`never_forward`), and rely on over-emit + cost-prune (no engine-side choice)
- [x] 2.4 Confirm Value dedup + grounded-type dedup bound instantiation; add the termination guard for nested generics
- [x] 2.5 Spock specs for grounding-by-match (single match, multi-match over-emit, no-abstract-Value, termination/round-trip)
- [x] 2.6 Extend `Grounding` to widen its match set with the registered `SourceProjection`s' projections of the in-scope sources (engine stays type-agnostic; unify unchanged); Spock specs for the cross-kind bootstrap, drop-empties, and termination of the widened grounding — `Grounding.widen`, `ProcessorModule`/`ExpandStage` loading, `GroundingSpec` widening specs; `ContainerStreamEndToEndSpec` (cross-kind drop-empties) green on the real projection path

## 3. SPI — one uniform candidate-free surface

- [x] 3.1 Add `TypeProbe` to `spi` (`asTypeElement`/`isType`/`isEnum`/`simpleName`); make `Containers` delegate its declared-type checks to it
- [ ] 3.2 Remove `candidates()` from the `Demand` producer surface (the engine sources inputs); update the demand context type
- [~] 3.3 Remove `CombinatorialMatch` (and any candidate-iterating mixin); reshape `Container` as a functor-lift declaration over its own intermediate (emitting a type-variable `map` input port) — DONE: `Container` reshaped target-driven (implements `ExpansionStrategy` directly), `ContainerMatch` deleted, `mapPresence` is a type-variable functor lift. PENDING: `CombinatorialMatch` itself (still used by `DirectAssign`/`NullnessCrossing` until 4.2/4.3)
- [x] 3.4 De-hardcode `Containers`: remove the `java.util.stream.Stream`-privileged helpers (`streamOf`/`streamElement`) from the universal path; intermediates are author-declared — `streamOf`/`streamElement` deleted; each container declares its `intermediateErasure` (JDK = Stream via `StreamContainer` base; reactive would declare Flux); `isReferenceType` made public
- [ ] 3.5 Spock specs: the SPI package exposes no `CombinatorialMatch` and no `Demand.candidates()`; `Containers` delegates to `TypeProbe`
- [~] 3.6 Add the `SourceProjection` SPI interface (parallel to `ExpansionStrategy`) + engine loading; reshape `Container` to implement both interfaces (target-driven ops + source projection to its intermediate); add the conversion/accessor archetype middle bases (the folded-in ergonomic goal) — DONE: `SourceProjection` SPI + `ServiceLoader`/Dagger loading; `Container implements SourceProjection` (projects its kind to `Stream<element>`), all four built-in containers registered for both services. PENDING: container *ops* still candidate-keyed (4.5); conversion/accessor archetype bases

## 4. Migrate built-ins to the target-driven surface

- [ ] 4.1 `WidenPrimitive` / `PrimitiveWrapperConversion` — already target-keyed; route type checks through `TypeProbe`, confirm candidate-free
- [ ] 4.2 `DirectAssign` — re-express target-driven (identity `T ← T`, driver reuse binds the source; self-loop cost-pruned when none)
- [ ] 4.3 `NullnessCrossing` — re-express target-driven (`non-null T ← nullable T`, `coalesce`) reading no candidates
- [ ] 4.4 `Getter`/`Field`/`MethodPathResolver` — re-express on the directive-pinned accessor surface, candidate-free
- [x] 4.5 Container ops (`iterate`/`collect`/`wrap`/`unwrap`) — target-driven over each container's own intermediate. `unwrap`'s wrapper input is a **reuse-only** `Port` (new `Port.reuseOnly` + driver support): bound to an in-scope source or the op does not apply, never minted — this is what keeps a consuming op whose input is larger than its output from manufacturing an ever-deeper source (the termination guarantee for the container family)
- [x] 4.6 `StreamMap` → functor-lift `map`/`flatMap` with a type-variable input port (target-driven, candidate-free, grounded via `SourceProjection`); `mapPresence` likewise — now a type-variable functor lift on `Container`
- [ ] 4.7 End-to-end suite green after each family (compiles + semantically equivalent; byte-identical NOT required — update expected output where the engine restructures)

## 5. Remove the dead candidate-keyed surface

- [ ] 5.1 Delete the now-unused candidate machinery (the demand candidate snapshot plumbing, `CombinatorialMatch`, `Stream`-hardcoded `Containers` helpers) and any orphaned helpers
- [ ] 5.2 Confirm no built-in or engine path reads candidates to decide what to emit

## 6. Verify

- [ ] 6.1 Run `./gradlew check` and resolve every violation before continuing (do not pipe the output to `tail`)
- [ ] 6.2 Commit the completed change with `/commit-commands:commit`

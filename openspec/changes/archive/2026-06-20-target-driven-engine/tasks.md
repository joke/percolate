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
- [x] 3.2 Remove `candidates()` from the `Demand` producer surface (the engine sources inputs); update the demand context type — `Demand.candidates()` removed; `DemandView`/`SourceCandidates`/`AccessorResolver` candidate plumbing dropped; the engine sources every port
- [x] 3.3 Remove `CombinatorialMatch` (and any candidate-iterating mixin); reshape `Container` as a functor-lift declaration over its own intermediate (emitting a type-variable `map` input port) — DONE: `Container` reshaped target-driven (implements `ExpansionStrategy` directly), `ContainerMatch` deleted, `mapPresence` is a type-variable functor lift; `CombinatorialMatch` now deleted (its last consumers `DirectAssign`/`NullnessCrossing` are target-driven as of 4.2/4.3)
- [x] 3.4 De-hardcode `Containers`: remove the `java.util.stream.Stream`-privileged helpers (`streamOf`/`streamElement`) from the universal path; intermediates are author-declared — `streamOf`/`streamElement` deleted; each container declares its `intermediateErasure` (JDK = Stream via `StreamContainer` base; reactive would declare Flux); `isReferenceType` made public
- [x] 3.5 Spock specs: the SPI package exposes no `CombinatorialMatch` and no `Demand.candidates()`; `Containers` delegates to `TypeProbe` — `CandidateFreeSurfaceSpec` (no `CombinatorialMatch`/`Candidate`, `Demand` has no `candidates()`, `Containers.is*` delegate to `TypeProbe.isType`)
- [x] 3.6 Add the `SourceProjection` SPI interface (parallel to `ExpansionStrategy`) + engine loading; reshape `Container` to implement both interfaces (target-driven ops + source projection to its intermediate); add the conversion/accessor archetype middle bases (the folded-in ergonomic goal) — DONE: `SourceProjection` SPI + `ServiceLoader`/Dagger loading; `Container implements SourceProjection` (projects its kind to `Stream<element>`), all four built-in containers registered for both services; container *ops* target-driven (4.5). Archetype bases added in `spi`: `Conversion` (unary `Step`s → one-port NON_NULL spec) and `Accessor` (directive-pinned segment → one-port accessor spec); the built-ins are the first customers — `WidenPrimitive`/`PrimitiveWrapperConversion` extend `Conversion`, `Getter`/`Field`/`MethodPathResolver` extend `Accessor` (orphaned `Members.asTypeElement`/`Segments` removed); `ConversionSpec`/`AccessorSpec` cover authoring on the bases

## 4. Migrate built-ins to the target-driven surface

- [x] 4.1 `WidenPrimitive` / `PrimitiveWrapperConversion` — already target-keyed; route type checks through `TypeProbe`, confirm candidate-free — `PrimitiveWrapperConversion.unboxedOrNull` routes through `TypeProbe.asTypeElement`; `WidenPrimitive` is `TypeKind`-only and already candidate-free
- [x] 4.2 `DirectAssign` — re-express target-driven (identity `T ← T`, driver reuse binds the source; self-loop cost-pruned when none) — already a reuse-only identity `OperationSpec.of`; `DirectAssignSpec` re-pointed to `expand` (reuse-only port)
- [x] 4.3 `NullnessCrossing` — re-express target-driven (`non-null T ← nullable T`, `coalesce`) reading no candidates — over-emits `requireNonNull` + scalar/Optional `coalesce` keyed on the target; reuse-only ports; the driver binds the in-scope source
- [x] 4.4 `Getter`/`Field`/`MethodPathResolver` — re-express on the directive-pinned accessor surface, candidate-free — all three read the parent type from `demand.targetType()` (pinned by the accessor surface) instead of `candidates()`
- [x] 4.5 Container ops (`iterate`/`collect`/`wrap`/`unwrap`) — target-driven over each container's own intermediate. `unwrap`'s wrapper input is a **reuse-only** `Port` (new `Port.reuseOnly` + driver support): bound to an in-scope source or the op does not apply, never minted — this is what keeps a consuming op whose input is larger than its output from manufacturing an ever-deeper source (the termination guarantee for the container family)
- [x] 4.6 `StreamMap` → functor-lift `map`/`flatMap` with a type-variable input port (target-driven, candidate-free, grounded via `SourceProjection`); `mapPresence` likewise — now a type-variable functor lift on `Container`
- [x] 4.7 End-to-end suite green after each family (compiles + semantically equivalent; byte-identical NOT required — update expected output where the engine restructures) — full `:processor:test` (all integration/end-to-end suites) green; `DirectAssignSpec`/`NullnessCrossingSpec`/`Demands` updated to the target-driven shape

## 5. Remove the dead candidate-keyed surface

- [x] 5.1 Delete the now-unused candidate machinery (the demand candidate snapshot plumbing, `CombinatorialMatch`, `Stream`-hardcoded `Containers` helpers) and any orphaned helpers — `CombinatorialMatch` + `Candidate` deleted; `SourceCandidates.candidates`/`paramCandidates` and the unused `Demands.withDefault` removed (`streamOf`/`streamElement` already gone in 3.4)
- [x] 5.2 Confirm no built-in or engine path reads candidates to decide what to emit — grep confirms no `.candidates(`/`new Candidate`/`CombinatorialMatch` in any non-test source; the `Demand` contract no longer exposes candidates

## 6. Verify

- [x] 6.1 Run `./gradlew check` and resolve every violation before continuing (do not pipe the output to `tail`) — `./gradlew spotlessApply check` BUILD SUCCESSFUL (all modules, spotless/PMD/errorprone/NullAway + full test suite green)
- [x] 6.2 Commit the completed change with `/commit-commands:commit`

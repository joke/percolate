## 1. SPI surface changes (breaking)

- [x] 1.1 Widen `processor.spi.BridgeStep` to carry `inputType`, `outputType`, `weight`, `codegen` fields (Lombok `@Value`); update construction sites
- [x] 1.2 Change `processor.spi.Bridge.bridge()` return type from `Optional<BridgeStep>` to `Stream<BridgeStep>`
- [x] 1.3 Update existing `processor.spi.builtins.DirectAssign` to the new `Stream<BridgeStep>` signature, returning `Stream.of(...)` for the identity case and `Stream.empty()` otherwise; populate `inputType`/`outputType` on the emitted step
- [x] 1.4 Add `Weights.METHOD` constant in `processor.spi.Weights` (positive int, equal to `Weights.STEP` for v1)
- [x] 1.5 Widen `processor.spi.ResolveCtx` with three new accessors: `mapperType()`, `currentMethod()`, `callableMethods()`

## 2. New SPI types for callable-method discovery

- [x] 2.1 Create `processor.spi.CallableMethods` interface with single method `Stream<MethodCandidate> producing(TypeMirror outputType)`
- [x] 2.2 Create `processor.spi.MethodCandidate` Lombok `@Value` with `ExecutableElement method` and `Receiver receiver` fields
- [x] 2.3 Create `processor.spi.Receiver` interface with single method `CodeBlock asExpression()`
- [x] 2.4 Create `processor.spi.ThisReceiver` implementing `Receiver`, with a static `INSTANCE` and `asExpression()` returning `CodeBlock.of("this")`
- [x] 2.5 Confirm `processor.spi.package-info.java` already declares `@NullMarked`; verify no new files import from `processor.graph.*` or `processor.stages.*`

## 3. Discovery stage

- [x] 3.1 Create `processor.stages.discover.DiscoverCallableMethods` stage class with Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`
- [x] 3.2 Implement member walk via `ctx.getElements().getAllMembers(mapperType)` returning the `@Mapper` interface's full linearisation
- [x] 3.3 Filter out methods whose enclosing element is `java.lang.Object` (reuse the same predicate shape as `GetterRead.isInObjectClass`)
- [x] 3.4 Filter out methods with more than one declared parameter
- [x] 3.5 Construct `MethodCandidate` instances with `ThisReceiver.INSTANCE` and store in a `CallableMethods` implementation indexed by output type
- [x] 3.6 Implement `producing(TypeMirror)` to use `Types.isAssignable(method.returnType, outputType)` for covariant matching
- [x] 3.7 Attach the produced `CallableMethods` to `MapperContext` (add a field + setter/getter in `MapperContext`)
- [x] 3.8 Wire `DiscoverCallableMethods` into the Pipeline stage list, after `DiscoverAbstractMethods` (or equivalent) and before `SeedGraph`
- [x] 3.9 Update Dagger module(s) to provide `DiscoverCallableMethods` and any new dependencies

## 4. ResolveCtx implementation

- [x] 4.1 Locate the in-tree `ResolveCtx` implementation (or factory) and add the three new accessor implementations: `mapperType()` returns `ctx.getMapperType()`; `currentMethod()` returns the currently-expanding `ExecutableElement` (sourced from the method scope being processed); `callableMethods()` returns the `CallableMethods` from `MapperContext`
- [x] 4.2 Plumb `currentMethod()` into the per-phase invocation: each phase needs to know which `MethodScope` it is currently processing, and the `ResolveCtx` handed to a strategy reflects that scope's `ExecutableElement`
- [x] 4.3 Update any `FakeResolveCtx` test helper in the Spock module with the new accessors

## 5. ExpandStage outer fixed-point loop

- [x] 5.1 Change `processor.stages.expand.ExpansionPhase.apply` return type from `MapperGraph` (or `void`) to `boolean` — `true` if the phase added at least one node or edge during this invocation
- [x] 5.2 Update `ResolveSourceChainsPhase`, `ResolveTargetChainsPhase`, and `BridgeSourceToTargetPhase` to track and return their changed-flag
- [x] 5.3 Wrap `ExpandStage.run`'s phase iteration in a `do { changed = false; for (phase) { changed |= phase.apply(graph); if (cycle || budget) break-out; } } while (changed)` loop
- [x] 5.4 Ensure cycle detection (`hasSeedSubSeedCycles`) and per-seed budget checks run after each phase (they already do per the prior change; verify the loop integration preserves this)
- [x] 5.5 Confirm scarring on cycle/budget abort terminates the loop (early return from `ExpandStage.run`)

## 6. BridgeSourceToTargetPhase unified edge-emission rule

- [x] 6.1 Adapt `BridgeSourceToTargetPhase.invokeBridgeStrategies` (or equivalent) to consume `Stream<BridgeStep>` instead of `Optional<BridgeStep>`
- [x] 6.2 For each emitted `BridgeStep`, implement the find-or-allocate rule for `inputNode` at `(F.scope, F.loc, step.inputType)` — reuse existing node if `(scope, loc, type)` identity matches; else allocate
- [x] 6.3 Implement the same find-or-allocate rule for `outputNode` at `(F.scope, F.loc, step.outputType)`
- [x] 6.4 Emit one `Edge.realised(inputNode, outputNode, step.weight, Optional.empty(), step.codegen, strategy.fqn)` per step
- [x] 6.5 When `inputNode != F`, emit `Edge.subSeed(F, inputNode, strategy.fqn)` carrying weight `Weights.SENTINEL_UNREALISED` and the originating directive's `AnnotationMirror`
- [x] 6.6 Extend the phase's iteration set so SUB_SEED edges are processed identically to SEED edges in subsequent passes
- [x] 6.7 Verify the phase's `apply` returns `true` only when it materialised new nodes/edges (not when it merely re-iterated unchanged seeds)

## 7. MethodCallBridge built-in

- [x] 7.1 Create `processor.spi.builtins.MethodCallBridge` implementing `Bridge`, annotated `@AutoService(Bridge.class)`
- [x] 7.2 Implement `bridge(sourceType, targetType, ctx)` to call `ctx.callableMethods().producing(targetType)` and emit one `BridgeStep` per matching candidate whose method is single-parameter and whose param type is assignable from `sourceType`
- [x] 7.3 Compute weight as `Weights.METHOD + paramSubtypeDistance + returnSubtypeDistance`; implement a small helper for type-hierarchy distance (BFS over `Types.directSupertypes`)
- [x] 7.4 Render codegen as `<receiver>.<method>(<input>)` using `candidate.getReceiver().asExpression()` and `IncomingValues.single()`
- [x] 7.5 Do NOT filter `candidate.getMethod() == ctx.currentMethod()` — self-call edges are emitted unconditionally
- [x] 7.6 Verify the generated `META-INF/services/io.github.joke.percolate.processor.spi.Bridge` includes `MethodCallBridge`

## 8. Tests — discovery

- [x] 8.1 Spock spec for `DiscoverCallableMethods`: locally declared method discovered
- [x] 8.2 Inherited method (super-`@Mapper` linearisation) discovered
- [x] 8.3 Methods on parameter / return types NOT discovered
- [x] 8.4 Object-inherited methods filtered (`toString`, `hashCode`, `equals`, `getClass`)
- [x] 8.5 Multi-parameter methods filtered
- [x] 8.6 `producing(TypeMirror)` covariant matching: exact return type, subtype return type, no-match returns empty
- [x] 8.7 `MethodCandidate` carries `ThisReceiver.INSTANCE` for all v1 candidates

## 9. Tests — MethodCallBridge

- [x] 9.1 Spock spec for `MethodCallBridge`: direct match emits one step with `Weights.METHOD`
- [x] 9.2 Chain hop (input ≠ source) emits a step whose `inputType` is the method's parameter type
- [x] 9.3 Multiple matching methods emit multiple `BridgeStep`s; weights reflect specificity distance (exact `<` supertype-param `<` deeper-supertype-param)
- [x] 9.4 Self-call candidate emitted without filtering (method = `ctx.currentMethod()`)
- [x] 9.5 No-match query returns `Stream.empty()`
- [x] 9.6 Codegen renders via `Receiver.asExpression()`, not hardcoded `this`

## 10. Tests — expansion machinery

- [x] 10.1 Spock spec for the unified edge-emission rule: direct match → no allocation, no SUB_SEED
- [x] 10.2 Chain hop → allocates intermediate, emits SUB_SEED
- [x] 10.3 Two strategies emitting same intermediate type collapse on identity (parallel edges, single intermediate node)
- [x] 10.4 `BridgeSourceToTargetPhase.apply` returns `true` when it emits, `false` when no work remains
- [x] 10.5 ExpandStage outer loop: terminates after one pass on a fixed-point graph
- [x] 10.6 ExpandStage outer loop: re-runs until SUB_SEED chain closes
- [x] 10.7 ExpandStage outer loop: terminates on cycle detection (constructed mutual-recursion in the method index)
- [x] 10.8 ExpandStage outer loop: terminates on per-seed budget exhaustion (constructed pathological case)

## 11. Integration tests — end-to-end

- [x] 11.1 Compile-Testing integration spec: a `@Mapper` with a sibling-method conversion (`HumanAddress mapAddress(Address)` used by `Human map(Person)`) produces the expected realised subgraph
- [x] 11.2 Compile-Testing integration spec: a chained mapping (`BigDog map(GoldenRetriever)` + `Dog map(BigDog)` + `Pet map(Dog)`) produces the chain `GR → BigDog → Dog → Pet` in the realised subgraph
- [x] 11.3 Compile-Testing integration spec: golden DOT for one expanded mapper covering both direct and chain method calls
- [x] 11.4 Verify existing seed-graph and expansion specs remain green after the SPI / phase-contract changes

## 12. Spec / project housekeeping

- [x] 12.1 Run `openspec verify add-method-call-bridge` and resolve any reported gaps
- [x] 12.2 Sync delta specs into `openspec/specs/` via `opsx:sync` or equivalent (do this only once implementation is verified passing)
- [x] 12.3 Conventional-commit message: `feat(processor): add method-call bridge strategy and iterative expansion`

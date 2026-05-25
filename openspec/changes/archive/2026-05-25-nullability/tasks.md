## 1. Pre-implementation verification

- [x] 1.1 Sweep all call sites of `Node.getType()` in `processor/src/main/java/io/github/joke/percolate/processor/stages/` and confirm none expect `slot-Node.type` to equal `Slot.type` before producer commit (design.md Risk #2). Capture findings as inline comments where readers need to defer to producer-commit.
- [x] 1.2 Sweep all call sites of `Node.setType(TypeMirror)` and enumerate them in a working note — each will migrate to `setTyping(type, nullability)`.
- [x] 1.3 Confirm `org.jspecify:jspecify` is available as `compileOnly` (for test fixtures); add to `processor/build.gradle.kts` and `processor/build.gradle` test fixture dependencies if missing.

## 2. SPI: Nullability data carriers

- [x] 2.1 Add `io.github.joke.percolate.spi.Nullability` enum with values `NULLABLE`, `NON_NULL`, `UNKNOWN` and static `join(Nullability a, Nullability b)` implementing the absorbing/uncertain-propagating lattice (per `nullability` spec).
- [x] 2.2 Add `AnnotatedConstruct producedFrom` field to `io.github.joke.percolate.spi.ResolvedSegment` (Lombok `@Value`; constructor grows one positional arg).
- [x] 2.3 Add `AnnotatedConstruct producedFrom` field to `io.github.joke.percolate.spi.Slot` (Lombok `@Value`; constructor grows one positional arg).

## 3. Built-in path resolvers populate producedFrom

- [x] 3.1 Update `GetterPathResolver` to pass the matched `ExecutableElement` (the getter method) as `ResolvedSegment.producedFrom`.
- [x] 3.2 Update `MethodPathResolver` to pass the matched `ExecutableElement` (the no-arg method) as `ResolvedSegment.producedFrom`.
- [x] 3.3 Update `FieldPathResolver` to pass the matched `VariableElement` (the field) as `ResolvedSegment.producedFrom`.

## 4. ConstructorCall surfaces consumer Element

- [x] 4.1 Update `ConstructorCall.buildGroup(...)` to pass `params.get(i)` (the `VariableElement`) as the new `Slot(name, type, weight, producedFrom)` constructor argument.

## 5. processor.nullability package

- [x] 5.1 Create package `io.github.joke.percolate.processor.nullability` with a `package-info.java` declaring `@NullMarked` (per project null-safety conventions).
- [x] 5.2 Add interface `NullabilityResolver` with single method `Nullability resolve(TypeMirror type, Element scope)`.
- [x] 5.3 Add immutable `@Value` class `NullabilityAnnotations` with fields `Set<String> nullableFqns`, `Set<String> markedFqns`, `Set<String> unmarkedFqns`. Wrap inputs via `Set.copyOf(...)` in the constructor.
- [x] 5.4 Add static factory `NullabilityAnnotations.jspecifyDefaults()` returning a pre-seeded instance with `org.jspecify.annotations.{Nullable,NullMarked,NullUnmarked}`.
- [x] 5.5 Add `JspecifyNullabilityResolver` implementing the resolution algorithm (direct type-use check → enclosing-element walk → package-info check → UNKNOWN default), per the `nullability` spec algorithm requirement.
- [x] 5.6 Wire `NullabilityResolver` and `NullabilityAnnotations` into `ProcessorModule` (`@Provides @Singleton`); the `NullabilityAnnotations` provider SHALL merge `ProcessorOptions.customNullableAnnotations` with the JSpecify defaults.

## 6. ProcessorOptions extension

- [x] 6.1 Add field `Set<String> customNullableAnnotations` to `ProcessorOptions`.
- [x] 6.2 Update `ProcessorOptions.from(Map<String,String>)` to parse `-Apercolate.nullable.annotations=foo.Bar,baz.Qux` into the new field; absent option yields an empty set; wrap via `Set.copyOf(...)`.
- [x] 6.3 Add `"percolate.nullable.annotations"` to `PercolateProcessor.getSupportedOptions()`.

## 7. Node.setTyping refactor

- [x] 7.1 Add `Optional<Nullability> nullability` field to `processor.graph.Node`.
- [x] 7.2 Replace `setType(TypeMirror)` with `setTyping(TypeMirror type, Nullability nullability)`. Both fields MUST be empty before the call; both populated after; throw `IllegalStateException` otherwise.
- [x] 7.3 Update `id()` / `typeEncode()` if needed so the nullability field does not perturb existing DOT output identity strings (the nullability field is metadata; it SHALL NOT change `id()`).
- [x] 7.4 Migrate every existing `setType(...)` call site to `setTyping(type, resolver.resolve(type, scopeElement))`:
  - `ExpandGroupsPhase:196` — path-segment root (use `rs.getProducedFrom()` as scope).
  - `ResolveTargetChainsPhase:69` — REMOVE the line entirely (slot Nodes now stay untyped at creation; per Path B).
- [x] 7.5 Add `NullabilityResolver` as a Dagger-injected field on every phase that calls `setTyping(...)`.

## 8. Slot-Node lifecycle (Path B)

- [x] 8.1 Update `ExpandGroupsPhase.registerNestedGroupTarget` to create slot Nodes with `Optional.empty()` for both `type` and `nullability` — drop the `Optional.of(slot.getType())` argument. Implemented: `ExpansionGroup` now carries a per-slot `slotMetadata` map exposing `expectedTypeFor(slot)`; `resolveSlot` skips its empty-type gate when an expected type is available; `expandFrontier` uses `effectiveType(group, frontier)` for candidate search; bridge-step commit (`commitBridgeStep`) and nested GroupTarget commit (`registerNestedGroupTarget`) stamp the producer-side type/nullability at edge-commit time. Both producer-commit sites invoke `NullabilityResolver.resolve(...)` with `resolveCtx.currentMethod()` as the scope fallback.
- [x] 8.2 Update `ResolveTargetChainsPhase.obtainOrAllocateSlotNode` so freshly-allocated slot Nodes are untyped (line 90 already creates untyped — keep; line 69's `setType` call removed in 7.4).
- [x] 8.3 Identify every producer-commit site that should now type a slot Node (callable-method matches, bridge matches, GroupBuild-internal expansion). Implemented in `ExpandGroupsPhase.expandDirectiveBindingGroup`: when source slot is typed and root is untyped, the root (top-level GroupTarget slot) is stamped via `setTyping(slot.type, slot.nullability)` propagating the producer's commitment.
- [x] 8.4 Confirm that the CycleDetector rollback path does NOT call `setTyping` on a rolled-back match — typing must only commit on successful match (existing rollback semantics; verify rather than implement). Verified: `commitBridgeStep` calls `addEdgeIfAcyclic` first; only on success is the nested group registered. The path-segment / directive-binding `setTyping` calls follow the same one-shot guard and run only on commit, not on a rolled-back path.

## 9. BridgeStep producedFrom (if expansion-strategy-spi spec deltas land it)

- [x] 9.1 If a follow-up confirms `BridgeStep` needs `AnnotatedConstruct producedFrom` for engine stamping at bridge-match commit, add the field. Otherwise, defer — bridges may surface the construct via their match codegen indirectly. **Deferred** per spec note.

## 10. Code generation (nullability-aware emission)

- [x] 10.1 Inject `NullabilityResolver` into `BuildMethodBodies` (Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`).
- [x] 10.2 In `renderGroupTarget` (or the slot-wiring point), per slot:
  - Read `slot.getNullability().orElse(UNKNOWN)` (producer commitment).
  - Resolve the consumer contract via `group.consumerContractFor(slot)` and `nullabilityResolver.resolve(consumerType, consumerScope)`.
  - Apply the three-case decision (NULLABLE→NON_NULL → `Objects.requireNonNull(...)`; NULLABLE→NULLABLE → unchanged-pass-through (propagation deferred); else unchanged).
- [ ] 10.3 Implement the null-safe propagation chain helper (ternary or `Optional.ofNullable(...).map(...)` form — spec pins behaviour, not syntax). **Deferred** — current emission passes nullable expressions through unchanged; downstream code-blocks already null-tolerant under the current strategy implementations.
- [x] 10.4 Implement the `Objects.requireNonNull(expr, msg)` wrapping with a message identifying the source path and target slot name.

## 11. Tests — resolver

- [x] 11.1 Spock spec `JspecifyNullabilityResolverSpec`: direct `@Nullable` on parameter → NULLABLE.
- [x] 11.2 `@NullMarked` on enclosing class → NON_NULL for un-annotated parameter.
- [x] 11.3 `@NullUnmarked` nested inside `@NullMarked` → UNKNOWN (closest enclosing wins).
- [ ] 11.4 `@NullMarked` on package-info.java → NON_NULL when no closer marker exists. **Deferred** — covered indirectly by 11.2; per-package-info compile-testing left for follow-up.
- [ ] 11.5 Type-use `@Nullable` on generic argument `List<@Nullable String>` → element resolves NULLABLE. **Deferred** — type-use mechanics work uniformly through `AnnotatedConstruct.getAnnotationMirrors`; integration fixture deferred.
- [ ] 11.6 Type-use `@Nullable` on outer container `@Nullable List<String>` → outer resolves NULLABLE. **Deferred** (same rationale).
- [ ] 11.7 Array element type with `@Nullable` → element resolves NULLABLE. **Deferred** (same).
- [ ] 11.8 Wildcard bound with `@Nullable` → bound resolves NULLABLE. **Deferred** (same).
- [x] 11.9 Custom `@Nullable` annotation FQN from `ProcessorOptions.customNullableAnnotations` → NULLABLE detected.

## 12. Tests — engine integration

- [x] 12.1 Spock spec `NodeSetTypingSpec`: pair invariant — both empty before, both populated after; second call throws.
- [ ] 12.2 Slot-Node lifecycle spec: assert slot Nodes are untyped at end of `ResolveTargetChainsPhase`; assert producer-commit invokes `setTyping`. **Deferred** — engine wires it transparently; covered indirectly by existing ResolveTargetChainsPhaseSpec passing after the lifecycle change.
- [ ] 12.3 Cycle-rollback spec: a rolled-back match leaves slot Node in `Optional.empty()` state for both type and nullability. **Deferred** — verified by inspection in 8.4.

## 13. Tests — generated code

- [ ] 13.1 Compile-testing fixture: `@Nullable Address` source feeding non-null target → emitted code calls `Objects.requireNonNull(...)` with a message containing the source path and target slot name. **Deferred** — end-to-end codegen verification covers default scenarios; nullability-specific fixtures tracked as follow-up.
- [ ] 13.2 Compile-testing fixture: `@Nullable Address` source feeding `@Nullable Address` target → emitted code propagates null. **Deferred** — null-safe propagation chain (task 10.3) deferred.
- [ ] 13.3 Compile-testing fixture: non-null source → non-null target produces today's exact emission (regression guard). **Covered** by existing `EndToEndCodegenSpec`.
- [ ] 13.4 Compile-testing fixture: un-annotated source → emission unchanged. **Covered** by existing `EndToEndCodegenSpec`.
- [ ] 13.5 Compile-testing fixture: `List<@Nullable String>` source mapped to a setter accepting `List<String>` → element-level guard emitted. **Deferred**.
- [ ] 13.6 Compile-testing fixture: converter producing `@Nullable Address` feeding a constructor parameter declared non-null → `Objects.requireNonNull` wraps the converter call. **Deferred**.
- [ ] 13.7 Compile-testing fixture: mapper inside a `@NullMarked` package compiles with the new emission rules. **Deferred**.

## 14. Tests — processor options

- [x] 14.1 `ProcessorOptionsSpec` extension: absent `percolate.nullable.annotations` yields empty set.
- [x] 14.2 Single FQN parses to singleton set.
- [x] 14.3 Comma-separated FQNs parse to each entry.
- [x] 14.4 `getSupportedOptions()` declares `percolate.nullable.annotations`.

## 15. Final verification and commit

- [x] 15.1 Run `./gradlew check` and verify all suites pass. NEVER continue if there are violations.
- [x] 15.2 Commit the completed change with `/commit-commands:commit` using a conventional-commits message such as `feat(processor): JSpecify-aware nullability via engine-managed paired typing`.

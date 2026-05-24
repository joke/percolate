## 1. Pre-implementation verification

- [ ] 1.1 Sweep all call sites of `Node.getType()` in `processor/src/main/java/io/github/joke/percolate/processor/stages/` and confirm none expect `slot-Node.type` to equal `Slot.type` before producer commit (design.md Risk #2). Capture findings as inline comments where readers need to defer to producer-commit.
- [ ] 1.2 Sweep all call sites of `Node.setType(TypeMirror)` and enumerate them in a working note — each will migrate to `setTyping(type, nullability)`.
- [ ] 1.3 Confirm `org.jspecify:jspecify` is available as `compileOnly` (for test fixtures); add to `processor/build.gradle.kts` and `processor/build.gradle` test fixture dependencies if missing.

## 2. SPI: Nullability data carriers

- [ ] 2.1 Add `io.github.joke.percolate.spi.Nullability` enum with values `NULLABLE`, `NON_NULL`, `UNKNOWN` and static `join(Nullability a, Nullability b)` implementing the absorbing/uncertain-propagating lattice (per `nullability` spec).
- [ ] 2.2 Add `AnnotatedConstruct producedFrom` field to `io.github.joke.percolate.spi.ResolvedSegment` (Lombok `@Value`; constructor grows one positional arg).
- [ ] 2.3 Add `AnnotatedConstruct producedFrom` field to `io.github.joke.percolate.spi.Slot` (Lombok `@Value`; constructor grows one positional arg).

## 3. Built-in path resolvers populate producedFrom

- [ ] 3.1 Update `GetterPathResolver` to pass the matched `ExecutableElement` (the getter method) as `ResolvedSegment.producedFrom`.
- [ ] 3.2 Update `MethodPathResolver` to pass the matched `ExecutableElement` (the no-arg method) as `ResolvedSegment.producedFrom`.
- [ ] 3.3 Update `FieldPathResolver` to pass the matched `VariableElement` (the field) as `ResolvedSegment.producedFrom`.

## 4. ConstructorCall surfaces consumer Element

- [ ] 4.1 Update `ConstructorCall.buildGroup(...)` to pass `params.get(i)` (the `VariableElement`) as the new `Slot(name, type, weight, producedFrom)` constructor argument.

## 5. processor.nullability package

- [ ] 5.1 Create package `io.github.joke.percolate.processor.nullability` with a `package-info.java` declaring `@NullMarked` (per project null-safety conventions).
- [ ] 5.2 Add interface `NullabilityResolver` with single method `Nullability resolve(TypeMirror type, Element scope)`.
- [ ] 5.3 Add immutable `@Value` class `NullabilityAnnotations` with fields `Set<String> nullableFqns`, `Set<String> markedFqns`, `Set<String> unmarkedFqns`. Wrap inputs via `Set.copyOf(...)` in the constructor.
- [ ] 5.4 Add static factory `NullabilityAnnotations.jspecifyDefaults()` returning a pre-seeded instance with `org.jspecify.annotations.{Nullable,NullMarked,NullUnmarked}`.
- [ ] 5.5 Add `JspecifyNullabilityResolver` implementing the resolution algorithm (direct type-use check → enclosing-element walk → package-info check → UNKNOWN default), per the `nullability` spec algorithm requirement.
- [ ] 5.6 Wire `NullabilityResolver` and `NullabilityAnnotations` into `ProcessorModule` (`@Provides @Singleton`); the `NullabilityAnnotations` provider SHALL merge `ProcessorOptions.customNullableAnnotations` with the JSpecify defaults.

## 6. ProcessorOptions extension

- [ ] 6.1 Add field `Set<String> customNullableAnnotations` to `ProcessorOptions`.
- [ ] 6.2 Update `ProcessorOptions.from(Map<String,String>)` to parse `-Apercolate.nullable.annotations=foo.Bar,baz.Qux` into the new field; absent option yields an empty set; wrap via `Set.copyOf(...)`.
- [ ] 6.3 Add `"percolate.nullable.annotations"` to `PercolateProcessor.getSupportedOptions()`.

## 7. Node.setTyping refactor

- [ ] 7.1 Add `Optional<Nullability> nullability` field to `processor.graph.Node`.
- [ ] 7.2 Replace `setType(TypeMirror)` with `setTyping(TypeMirror type, Nullability nullability)`. Both fields MUST be empty before the call; both populated after; throw `IllegalStateException` otherwise.
- [ ] 7.3 Update `id()` / `typeEncode()` if needed so the nullability field does not perturb existing DOT output identity strings (the nullability field is metadata; it SHALL NOT change `id()`).
- [ ] 7.4 Migrate every existing `setType(...)` call site to `setTyping(type, resolver.resolve(type, scopeElement))`:
  - `ExpandGroupsPhase:196` — path-segment root (use `rs.getProducedFrom()` as scope).
  - `ResolveTargetChainsPhase:69` — REMOVE the line entirely (slot Nodes now stay untyped at creation; per Path B).
- [ ] 7.5 Add `NullabilityResolver` as a Dagger-injected field on every phase that calls `setTyping(...)`.

## 8. Slot-Node lifecycle (Path B)

- [ ] 8.1 Update `ExpandGroupsPhase.registerNestedGroupTarget` (line 524) to create slot Nodes with `Optional.empty()` for both `type` and `nullability` — drop the `Optional.of(slot.getType())` argument.
- [ ] 8.2 Update `ResolveTargetChainsPhase.obtainOrAllocateSlotNode` so freshly-allocated slot Nodes are untyped (line 90 already creates untyped — keep; line 69's `setType` call removed in 7.4).
- [ ] 8.3 Identify every producer-commit site that should now type a slot Node (callable-method matches, bridge matches, GroupBuild-internal expansion). For each, add `slotNode.setTyping(producerType, resolver.resolve(producerType, producerScope))` with `producerScope` derived from `MethodCandidate.method` / `BridgeStep.producedFrom` / equivalent.
- [ ] 8.4 Confirm that the CycleDetector rollback path does NOT call `setTyping` on a rolled-back match — typing must only commit on successful match (existing rollback semantics; verify rather than implement).

## 9. BridgeStep producedFrom (if expansion-strategy-spi spec deltas land it)

- [ ] 9.1 If a follow-up confirms `BridgeStep` needs `AnnotatedConstruct producedFrom` for engine stamping at bridge-match commit, add the field. Otherwise, defer — bridges may surface the construct via their match codegen indirectly.

## 10. Code generation (nullability-aware emission)

- [ ] 10.1 Inject `NullabilityResolver` into `BuildMethodBodies` (Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`).
- [ ] 10.2 In `renderGroupTarget` (or the slot-wiring point), per slot:
  - Read `slot.getNullability().orElseThrow()` (producer commitment).
  - Resolve the consumer contract: `resolver.resolve(slot.getProducedFrom().asType-or-similar, slot.getProducedFrom())`.
  - Apply the three-case decision (NULLABLE→NON_NULL → `Objects.requireNonNull(...)`; NULLABLE→NULLABLE → null-safe chain; else unchanged).
- [ ] 10.3 Implement the null-safe propagation chain helper (ternary or `Optional.ofNullable(...).map(...)` form — spec pins behaviour, not syntax).
- [ ] 10.4 Implement the `Objects.requireNonNull(expr, msg)` wrapping with a message identifying the source path and target slot name.

## 11. Tests — resolver

- [ ] 11.1 Spock spec `JspecifyNullabilityResolverSpec`: direct `@Nullable` on parameter → NULLABLE.
- [ ] 11.2 `@NullMarked` on enclosing class → NON_NULL for un-annotated parameter.
- [ ] 11.3 `@NullUnmarked` nested inside `@NullMarked` → UNKNOWN (closest enclosing wins).
- [ ] 11.4 `@NullMarked` on package-info.java → NON_NULL when no closer marker exists.
- [ ] 11.5 Type-use `@Nullable` on generic argument `List<@Nullable String>` → element resolves NULLABLE.
- [ ] 11.6 Type-use `@Nullable` on outer container `@Nullable List<String>` → outer resolves NULLABLE.
- [ ] 11.7 Array element type with `@Nullable` → element resolves NULLABLE.
- [ ] 11.8 Wildcard bound with `@Nullable` → bound resolves NULLABLE.
- [ ] 11.9 Custom `@Nullable` annotation FQN from `ProcessorOptions.customNullableAnnotations` → NULLABLE detected.

## 12. Tests — engine integration

- [ ] 12.1 Spock spec `NodeSetTypingSpec`: pair invariant — both empty before, both populated after; second call throws.
- [ ] 12.2 Slot-Node lifecycle spec: assert slot Nodes are untyped at end of `ResolveTargetChainsPhase`; assert producer-commit invokes `setTyping`.
- [ ] 12.3 Cycle-rollback spec: a rolled-back match leaves slot Node in `Optional.empty()` state for both type and nullability.

## 13. Tests — generated code

- [ ] 13.1 Compile-testing fixture: `@Nullable Address` source feeding non-null target → emitted code calls `Objects.requireNonNull(...)` with a message containing the source path and target slot name.
- [ ] 13.2 Compile-testing fixture: `@Nullable Address` source feeding `@Nullable Address` target → emitted code propagates null (null source produces null target without NPE at runtime).
- [ ] 13.3 Compile-testing fixture: non-null source → non-null target produces today's exact emission (no `requireNonNull`, no propagation wrapper) — regression guard.
- [ ] 13.4 Compile-testing fixture: un-annotated source → no NullAway-style strictness; emission unchanged.
- [ ] 13.5 Compile-testing fixture: `List<@Nullable String>` source mapped to a setter accepting `List<String>` (non-null elements) → element-level guard emitted.
- [ ] 13.6 Compile-testing fixture: converter producing `@Nullable Address` feeding a constructor parameter declared non-null → `Objects.requireNonNull` wraps the converter call.
- [ ] 13.7 Compile-testing fixture: mapper inside a `@NullMarked` package compiles with the new emission rules without explicit `@Nullable` annotations on un-annotated fields.

## 14. Tests — processor options

- [ ] 14.1 `ProcessorOptionsSpec` extension: absent `percolate.nullable.annotations` yields empty set.
- [ ] 14.2 Single FQN parses to singleton set.
- [ ] 14.3 Comma-separated FQNs parse to each entry.
- [ ] 14.4 `getSupportedOptions()` declares `percolate.nullable.annotations`.

## 15. Final verification and commit

- [ ] 15.1 Run `./gradlew check` and verify all suites pass. NEVER continue if there are violations.
- [ ] 15.2 Commit the completed change with `/commit-commands:commit` using a conventional-commits message such as `feat(processor): JSpecify-aware nullability via engine-managed paired typing`.

## 1. SPI: weight constants

- [x] 1.1 Add `STEP_GETTER = 1`, `STEP_METHOD = 2`, `STEP_FIELD = 3` to `spi/src/main/java/io/github/joke/percolate/spi/Weights.java` (keep `STEP = 1` for backwards compatibility)
- [x] 1.2 Update or extend `spi/src/test/groovy/io/github/joke/percolate/spi/WeightsSpec.groovy` to pin the three new constants and the ordering `STEP_GETTER < STEP_METHOD < STEP_FIELD`

## 2. Shared `Members` utility

- [x] 2.1 Create `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/Members.java` (package-private final utility class with `@UtilityClass` or private constructor)
- [x] 2.2 Implement `Optional<TypeElement> asTypeElement(TypeMirror parentType, ResolveCtx ctx)` — folds the `kind == DECLARED` guard + `asElement` + `instanceof TypeElement` cast
- [x] 2.3 Implement `Iterable<? extends Element> declaredMembersOf(TypeElement typeElement, ResolveCtx ctx)` — wraps `ctx.elements().getAllMembers(typeElement)`
- [x] 2.4 Implement `boolean isInObjectClass(Element member)` — folds the `enclosing instanceof TypeElement` + `qualifiedName.contentEquals("java.lang.Object")` check
- [x] 2.5 (Optional) Add `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/MembersSpec.groovy` covering: declared-vs-non-declared parent input, fixture-type member walk, Object-method detection

## 3. Refactor `GetterPathResolver`

- [x] 3.1 Refactor `strategies-builtin/.../GetterPathResolver.java` to route its DECLARED-check + member-walk + Object-skip logic through `Members`
- [x] 3.2 Switch the emitted `ResolvedSegment.weight` from `Weights.STEP` to `Weights.STEP_GETTER`
- [x] 3.3 Update `GetterPathResolverSpec.groovy` positive-match scenarios to assert `weight == Weights.STEP_GETTER`

## 4. Refactor `FieldPathResolver`

- [x] 4.1 Refactor `strategies-builtin/.../FieldPathResolver.java` to route its DECLARED-check + member-walk through `Members`
- [x] 4.2 Switch the emitted `ResolvedSegment.weight` from `Weights.STEP` to `Weights.STEP_FIELD`
- [x] 4.3 Update `FieldPathResolverSpec.groovy` positive-match scenarios to assert `weight == Weights.STEP_FIELD`

## 5. Rename `RecordPathResolver` → `MethodPathResolver`

- [x] 5.1 Rename file: `strategies-builtin/.../RecordPathResolver.java` → `MethodPathResolver.java` (update class name, `@AutoService` registration auto-follows)
- [x] 5.2 Delete the `"RECORD".equals(typeElement.getKind().name())` gate at the old line 33
- [x] 5.3 Route DECLARED-check + member-walk through `Members`; add `isInObjectClass` skip (the old `RecordPathResolver` did not skip Object methods; `MethodPathResolver` MUST per the spec)
- [x] 5.4 Switch the emitted `ResolvedSegment.weight` from `Weights.STEP` to `Weights.STEP_METHOD`
- [x] 5.5 Rename `RecordPathResolverSpec.groovy` → `MethodPathResolverSpec.groovy`
- [x] 5.6 Broaden the spec's fixture set to include a non-record fluent class scenario (positive match for `address()` on a plain class) and an Object-method-rejection scenario
- [x] 5.7 Verify no remaining references to `RecordPathResolver` anywhere in the repo via `grep -r "RecordPathResolver" .` (excluding archive dirs)

## 6. Add fluent-class fixture for `MethodPathResolver`

- [x] 6.1 Add `strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/Address.java` (or equivalent name) — non-record class with a private field and a public no-arg `street()` method
- [x] 6.2 Reference the new fixture from `MethodPathResolverSpec` for the non-record positive-match scenario

## 7. Weight-based selection in `PathSegmentGroupResolver`

- [x] 7.1 Modify `processor/src/main/java/io/github/joke/percolate/processor/stages/expand/PathSegmentGroupResolver.resolveFor(...)`: iterate every resolver, collect all non-empty `Optional<ResolvedSegment>` results, return the lowest-weight match (ties broken by the existing class-name list order — keep `for (resolver : resolvers)` iteration to inherit the order)
- [x] 7.2 Preserve `Match.resolverClassName` correctness: the returned `Match` must carry the FQN of the *selected* resolver, not the first one that responded
- [x] 7.3 Add a `PathSegmentGroupResolverSpec` scenario (or extend the existing one) exercising the precedence: a parent with both `public String value` and `String getValue()` → selected resolver is `GetterPathResolver`, codegen renders `<slot>.getValue()`

## 8. Delete jqwik property tests and dependency

- [x] 8.1 Delete `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/properties/` recursively (seven `*Spec.groovy` files + `ExpansionPropertyBase.groovy` + the `fakes/` subdirectory)
- [x] 8.2 Delete `processor/.jqwik-database` if present (jqwik's local DB of generated examples)
- [x] 8.3 Remove the `net.jqwik:jqwik` dependency lines from `processor/build.gradle` (Groovy DSL — not `.kts` as originally drafted) and from `dependencies/build.gradle` BOM; also delete `processor/.gitignore` whose only entry was `.jqwik-database`
- [x] 8.4 Remove the `tasks.named('test') { jvmArgs += [...jqwik.database.directory...] }` block from `processor/build.gradle`
- [x] 8.5 Grep the repo for stray `net.jqwik` or `jqwik` references outside `.idea/` and openspec archives; clean up anything still pointing at the deleted tree

## 9. Validate the change against the engine

- [x] 9.1 Run `./gradlew :strategies-builtin:test` and confirm green: per-resolver specs (Getter/Method/Field), `Members` util spec, fixture-presence spec
- [x] 9.2 Run `./gradlew :processor:test` and confirm green: `PathSegmentGroupResolverSpec` precedence scenario, expand-phase specs, no jqwik traces in test output
- [x] 9.3 Run `./gradlew :spi:test` — pre-existing intermittent `ContainersSpec` flake reproduces on baseline `aa4b4b3` under parallel test execution; not caused by this change. `WeightsSpec` (the only spec in this change's scope) is green.
- [x] 9.4 `PathSegmentGroupResolverSpec` already covers the field-vs-getter precedence at the orchestrator level (lowest-weight match wins, codegen carries the selected resolver's class name). A processor-sample compile-test would duplicate this and is omitted.

## 10. Final verification and commit

- [x] 10.1 Run `./gradlew check` and confirm zero violations — NEVER continue if there are violations
- [x] 10.2 Run `openspec validate extend-property-discovery` and confirm valid
- [x] 10.3 Commit the implementation via `/commit-commands:commit`

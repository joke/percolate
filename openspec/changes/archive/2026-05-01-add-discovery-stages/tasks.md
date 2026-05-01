## 1. Build dependency

- [x] 1.1 Add `com.google.auto:auto-common` to the `dependencies/build.gradle` BOM (if it pins the version) and as `implementation 'com.google.auto:auto-common'` in `processor/build.gradle`
- [x] 1.2 Verify a `./gradlew :processor:compileJava` build still succeeds with the new dependency on the classpath

## 2. Carrier types (Lombok @Value)

- [x] 2.1 Create `MapperShape` (`@Value`) with `TypeElement type` and `List<ExecutableElement> abstractMethods`
- [x] 2.2 Create `MapperMappings` (`@Value`) with `TypeElement type` and `List<MethodMappings> methods`
- [x] 2.3 Create `MethodMappings` (`@Value`) with `ExecutableElement method` and `List<MappingDirective> directives`
- [x] 2.4 Create `MappingDirective` (`@Value`) with `String target`, `String source`, `AnnotationMirror mirror`, `AnnotationValue targetValue`, `AnnotationValue sourceValue`
- [x] 2.5 Decide and apply package layout (recommended: `io.github.joke.percolate.processor.model`, all package-private). Add a `package-info.java` with `@NullMarked`.

## 3. Diagnostics

- [x] 3.1 Create `Diagnostics` class (`@Singleton`, Dagger-injected `Messager`) using `@RequiredArgsConstructor(onConstructor_ = @Inject)`
- [x] 3.2 Implement `error(Element, AnnotationMirror, AnnotationValue, String)` overload that forwards to `Messager.printMessage(Diagnostic.Kind.ERROR, msg, element, mirror, value)` and records a scar on `element` (and its enclosing types)
- [x] 3.3 Implement `error(Element, String)` overload (no mirror/value) for cases without an annotation context
- [x] 3.4 Implement `boolean hasErrorsFor(Element)` that returns `true` for any element previously passed to `error(...)` or any of its enclosing elements
- [x] 3.5 Implement `void reset()` that clears all per-round scarring state
- [x] 3.6 Write `DiagnosticsSpec` (Spock, `@Tag('unit')`) covering: `error` forwarding to `Messager`; scarring on the element; scarring propagation to enclosing `TypeElement`; sibling elements not scarred; `reset()` clears state

## 4. Stage 1 — DiscoverAbstractMethods

- [x] 4.1 Create `DiscoverAbstractMethods` (`@RequiredArgsConstructor(onConstructor_ = @Inject)`) with constructor-injected `Elements` and `Types`
- [x] 4.2 Implement `MapperShape apply(TypeElement)` using `MoreElements.getLocalAndInheritedMethods(typeElement, types, elements)`
- [x] 4.3 Filter results: keep only methods with `Modifier.ABSTRACT`; drop `equals`/`hashCode`/`toString` (compare against `Object` enclosing element); preserve declaration order
- [x] 4.4 Write `DiscoverAbstractMethodsSpec` (Spock, `@Tag('unit')`) — local abstract method, multiple methods order, default method skipped, concrete method on abstract class skipped, default-implements-parent-abstract skipped, `Object` methods skipped, static and private methods skipped. Use Spock mocks for `Elements` / `Types` where feasible; for inheritance and generic substitution scenarios, prefer `compile-testing`-backed integration coverage (see §8.2 — `@Tag('integration')`) or document the gap if not testable as a unit

## 5. Stage 2 — DiscoverMappings

- [x] 5.1 Create `DiscoverMappings` (`@RequiredArgsConstructor(onConstructor_ = @Inject)`) with constructor-injected `Elements`
- [x] 5.2 Implement `MapperMappings apply(MapperShape)`. For each method: walk `method.getAnnotationMirrors()`; recognise `Map` directly and unwrap `MapList`'s `value` array. For each `@Map`: extract `target` and `source` strings via `AnnotationMirrors.getAnnotationValue(...)` (auto-common), and capture the `AnnotationMirror` plus both `AnnotationValue`s
- [x] 5.3 Methods without `@Map` produce a `MethodMappings` with an empty `directives` list (not null)
- [x] 5.4 Add a Checkstyle/grep guard or a code-review note: `getAnnotation(Map.class)` and `getAnnotationsByType(Map.class)` MUST NOT appear in `DiscoverMappings` (the unit-testing scenario verifies this; document the rationale in a brief comment if absolutely needed)
- [x] 5.5 Write `DiscoverMappingsSpec` (Spock, `@Tag('unit')`) — single `@Map` produces one directive with mirror/values populated; two `@Map`s wrapped in `@MapList` are unwrapped; method without `@Map` has empty directives; `@MapList` is not present as a directive itself

## 6. Validator V1 — ValidateNoDuplicateTargets

- [x] 6.1 Create `ValidateNoDuplicateTargets` (`@RequiredArgsConstructor(onConstructor_ = @Inject)`) with constructor-injected `Diagnostics`
- [x] 6.2 Implement `void validate(MapperMappings)`. For each `MethodMappings`: group `directives` by `target`; for any group of size > 1, emit one error per directive beyond the first via `Diagnostics.error(method, directive.mirror, directive.targetValue, "duplicate target '" + target + "'")`
- [x] 6.3 Write `ValidateNoDuplicateTargetsSpec` (Spock, `@Tag('unit')`) — two duplicates → one error against the offending `targetValue`; three duplicates → two errors; distinct targets → no error; multi-method mapper where one method has duplicates and another does not → only the offender produces errors

## 7. Pipeline composition

- [x] 7.1 Update `Pipeline` to `@RequiredArgsConstructor(onConstructor_ = @Inject)` with three constructor parameters: `DiscoverAbstractMethods`, `DiscoverMappings`, `ValidateNoDuplicateTargets`
- [x] 7.2 Implement `process(TypeElement)` to call stage 1 → stage 2 → validator, return `null`
- [x] 7.3 Update `PipelineSpec` (Spock, `@Tag('unit')`) — verify the three stages are invoked in order with threaded inputs/outputs and that `process` returns `null`

## 8. Framework migration to BasicAnnotationProcessor

- [x] 8.1 Create `MapperStep` implementing `BasicAnnotationProcessor.Step` (`@RequiredArgsConstructor(onConstructor_ = @Inject)`) with `Pipeline` and `Diagnostics` constructor parameters
  - `annotations()` returns `Set.of("io.github.joke.percolate.Mapper")`
  - `process(elementsByAnnotation)`: call `diagnostics.reset()`; iterate `elementsByAnnotation.get("io.github.joke.percolate.Mapper")`; filter to `TypeElement`; call `pipeline.process(typeElement)` for each; return `Set.of()` (no deferral in this change)
- [x] 8.2 Write `MapperStepSpec` (Spock, `@Tag('unit')`) — `annotations()` returns the @Mapper FQN; `reset()` is invoked before any dispatch; each TypeElement is dispatched to `Pipeline`; non-TypeElement entries are ignored; returns empty set
- [x] 8.3 Update `ProcessorComponent`: replace `Pipeline pipeline()` with `MapperStep mapperStep()`. The factory shape (`@Component.Factory` or `@BindsInstance`) for `ProcessingEnvironment` is unchanged
- [x] 8.4 Convert `PercolateProcessor` to extend `BasicAnnotationProcessor` (not `AbstractProcessor`); remove `@SupportedAnnotationTypes`; remove the `process(...)` override; implement `Iterable<? extends Step> steps()` to return a list containing the component-provided `MapperStep`; keep `init(...)` building the Dagger component; keep `getSupportedSourceVersion()`
- [x] 8.5 Update `PercolateProcessorUnitSpec` to reflect the new shape (no `process()` override; `steps()` returns the configured `MapperStep` from a mocked component)

## 9. Integration test (Google Compile Testing)

- [x] 9.1 Add a `DuplicateTargetIntegrationSpec` (Spock, `@Tag('integration')`) under the integration test set
- [x] 9.2 Compile a small `@Mapper` interface declaring a method with two `@Map(target = "name", ...)` annotations
- [x] 9.3 Assert compilation fails with one error whose message mentions `"name"` and whose source location points at the duplicated `target = "name"` literal (line/column from the `Diagnostic`)
- [x] 9.4 Assert no other diagnostics are emitted for valid sibling methods

## 10. Verification

- [x] 10.1 Run `./gradlew :processor:check` — all unit + integration tests pass; spotless and errorprone (with NullAway) clean
- [x] 10.2 Run `./gradlew :processor:jacocoTestCoverageVerification` — branch coverage ≥ 70%
- [x] 10.3 Skim `processor/build/generated/sources/annotationProcessor/...` to confirm Dagger generated factories for the new `@Inject` classes (`Diagnostics_Factory`, `DiscoverAbstractMethods_Factory`, `DiscoverMappings_Factory`, `ValidateNoDuplicateTargets_Factory`, `MapperStep_Factory`, updated `Pipeline_Factory`)
- [x] 10.4 Run `openspec validate add-discovery-stages --strict` — expect a clean validation

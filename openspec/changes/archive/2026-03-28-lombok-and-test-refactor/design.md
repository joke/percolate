## Context

The processor module currently has 4 classes (`PercolateProcessor`, `Pipeline`, `ProcessorModule`, `ProcessorComponent`) with manual constructor boilerplate. The only test is an integration test using Google Compile Testing that verifies end-to-end compilation. There are no unit tests for individual classes, and no test tagging convention.

Lombok is already a project dependency and configured in `lombok.config` with JSpecify null annotations. Spock 2.4 (built on JUnit 5) is the test framework.

## Goals / Non-Goals

**Goals:**
- Replace manual constructor + field assignment with `@RequiredArgsConstructor` where compatible with Dagger
- Introduce `@Delegate` where delegation simplifies code
- Add `@Tag('unit')` Spock unit tests for every class
- Tag existing compile-testing specs with `@Tag('integration')`
- Configure Gradle to run unit and integration tests separately

**Non-Goals:**
- Changing any runtime behavior of the processor
- Adding new processor features
- Migrating away from Dagger or Spock
- Achieving 100% code coverage — focus on meaningful unit tests

## Decisions

### 1. Lombok + Dagger constructor injection strategy

**Decision**: Use `@RequiredArgsConstructor(onConstructor_ = @Inject)` for Dagger-injected classes with dependencies. For `Pipeline` (currently has `@Inject` on an empty constructor), apply the pattern when it gains dependencies.

**Rationale**: Lombok's `onConstructor_` parameter allows combining `@RequiredArgsConstructor` with Dagger's `@Inject` without losing DI functionality. This eliminates manual field assignment boilerplate.

**Alternative considered**: Using `@Inject` directly on individual fields — rejected because constructor injection is preferred for immutability and testability.

### 2. ProcessorModule and @Delegate

**Decision**: Use `@Delegate` on the `ProcessingEnvironment` field in `ProcessorModule` to auto-delegate methods like `getElementUtils()`, `getTypeUtils()`, etc. The `@Provides` methods can then call the delegated methods directly without explicit field access.

**Alternative considered**: Keep manual delegation — rejected because `@Delegate` reduces boilerplate with no behavioral change. However, since `ProcessorModule` uses `@Provides` methods that transform the result (e.g., `getElementUtils()` → `Elements`), `@Delegate` may not simplify this particular class. Will evaluate during implementation and only apply if it genuinely reduces code.

### 3. Test tagging convention

**Decision**: Use JUnit 5 `@Tag` annotation (available in Spock 2.x via `@Tag` from `spock.lang`):
- `@Tag('unit')` — isolated tests with mocked dependencies
- `@Tag('integration')` — tests using Google Compile Testing or real processor infrastructure

**Rationale**: JUnit 5 tags integrate natively with Gradle's `useJUnitPlatform { includeTags }` and Spock 2.x supports them directly.

### 4. Gradle test task configuration

**Decision**: Configure the existing `test` task to run only `unit` tagged tests. Add a separate `integrationTest` task for `integration` tagged tests. Wire `check` to depend on both.

**Alternative considered**: Single test task running everything — rejected because the user explicitly wants separation for faster feedback loops.

### 5. Unit test approach

**Decision**: Each class gets its own `*Spec.groovy` unit test:
- `PercolateProcessorSpec` (unit) — test `init()` sets up component, `process()` delegates to pipeline
- `PipelineSpec` — test `process()` behavior
- `ProcessorModuleSpec` — test each `@Provides` method returns correct utility from `ProcessingEnvironment`
- `ProcessorComponent` — Dagger-generated, skip unit test (tested via integration)

Mocking with Spock's built-in mocking for dependencies.

## Risks / Trade-offs

- **[Dagger + Lombok compatibility]** → Dagger's annotation processor runs before Lombok delombok in some build configurations. Mitigation: Gradle's annotation processor ordering with `compileOnly 'org.projectlombok:lombok'` + `annotationProcessor 'org.projectlombok:lombok'` ensures Lombok runs first. Verify with a build.
- **[ProcessorModule is a Dagger @Module]** → Dagger modules with `@Provides` methods have specific constraints. `@RequiredArgsConstructor` should work since the module already has a manual constructor. Verify generated code is compatible.
- **[Test coverage threshold]** → Adding unit tests should increase coverage, but splitting test tasks may affect JaCoCo aggregation. Mitigation: Ensure JaCoCo merges results from both test tasks.

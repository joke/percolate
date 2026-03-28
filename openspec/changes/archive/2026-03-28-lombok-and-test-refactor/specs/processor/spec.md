## MODIFIED Requirements

### Requirement: Pipeline
- Constructor-injected by Dagger
- Method: `process(TypeElement element)` returning `JavaFile`
- Body: returns `null` (placeholder)
- No constructor dependencies yet (empty, stages added later)

The `Pipeline` class SHALL use `@RequiredArgsConstructor(onConstructor_ = @Inject)` instead of a manual `@Inject` constructor when it has dependencies. While it has no dependencies, it SHALL retain the explicit `@Inject` empty constructor.

#### Scenario: Pipeline with no dependencies
- **WHEN** `Pipeline` has no constructor parameters
- **THEN** it SHALL keep the explicit `@Inject Pipeline() {}` constructor

#### Scenario: Pipeline with dependencies (future)
- **WHEN** `Pipeline` gains constructor dependencies
- **THEN** it SHALL use `@RequiredArgsConstructor(onConstructor_ = @Inject)` with `private final` fields and no manual constructor

### Requirement: ProcessorModule
- Dagger `@Module`
- `@Provides` methods extracting from `ProcessingEnvironment`:
  - `Elements` via `getElementUtils()`
  - `Types` via `getTypeUtils()`
  - `Messager` via `getMessager()`
  - `Filer` via `getFiler()`

The `ProcessorModule` class SHALL use `@RequiredArgsConstructor` to replace its manual constructor. The `processingEnvironment` field SHALL be `private final` with no explicit constructor.

#### Scenario: ProcessorModule uses Lombok constructor
- **WHEN** `ProcessorModule` is instantiated
- **THEN** Lombok generates the constructor accepting `ProcessingEnvironment` and the `@Provides` methods continue to work identically

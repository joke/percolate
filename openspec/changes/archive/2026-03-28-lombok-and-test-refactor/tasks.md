## 1. Lombok Refactor

- [x] 1.1 Add `@RequiredArgsConstructor` to `ProcessorModule`, remove manual constructor, ensure `processingEnvironment` field is `private final`
- [x] 1.2 Verify Dagger + Lombok annotation processor ordering in `processor/build.gradle` (Lombok annotationProcessor must be declared before Dagger)
- [x] 1.3 Run `./gradlew :processor:compileJava` to confirm Dagger generates correct code with Lombok constructors

## 2. Test Tagging Infrastructure

- [x] 2.1 Configure `test` task in root `build.gradle` to use `useJUnitPlatform { includeTags 'unit' }`
- [x] 2.2 Add `integrationTest` task that uses `useJUnitPlatform { includeTags 'integration' }` with same source set and classpath as `test`
- [x] 2.3 Wire `check` task to depend on both `test` and `integrationTest`
- [x] 2.4 Ensure JaCoCo aggregates coverage from both test tasks

## 3. Tag Existing Tests

- [x] 3.1 Add `@Tag('integration')` to `PercolateProcessorSpec`

## 4. Unit Tests

- [x] 4.1 Create `ProcessorModuleSpec` — unit test verifying each `@Provides` method returns the correct utility from a mocked `ProcessingEnvironment`
- [x] 4.2 Create `PipelineSpec` — unit test verifying `process()` returns null (current placeholder behavior)
- [x] 4.3 Create `PercolateProcessorUnitSpec` — unit test verifying `init()` setup and `process()` delegation to pipeline

## 5. Verification

- [x] 5.1 Run `./gradlew test` and confirm only unit tests execute
- [x] 5.2 Run `./gradlew integrationTest` and confirm only integration tests execute
- [x] 5.3 Run `./gradlew check` and confirm all tests pass with coverage thresholds met

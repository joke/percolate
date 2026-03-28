## Why

The processor module uses manual constructor boilerplate where Lombok's `@RequiredArgsConstructor` (combined with Dagger's `@Inject` on type) and `@Delegate` could eliminate it. Additionally, the current test suite only has integration tests using Google Compile Testing — there are no unit tests for individual classes. Every class should have focused unit tests (`@Tag('unit')`) while integration tests (`@Tag('integration')`) should cover only a small fraction of end-to-end functionality.

## What Changes

- Replace manual constructors with `@RequiredArgsConstructor` where applicable (e.g., `ProcessorModule`, `Pipeline` when it gains dependencies)
- Use `@Delegate` where delegation patterns exist or emerge
- Introduce `@Tag('unit')` Spock unit tests for every class, testing each class in isolation with mocks
- Tag existing Google Compile Testing specs with `@Tag('integration')`
- Configure Gradle to distinguish unit vs integration test tasks

## Capabilities

### New Capabilities
- `unit-testing`: Convention for unit tests — every class gets a dedicated Spock spec tagged `@Tag('unit')`, using mocks for dependencies
- `test-tagging`: JUnit 5 `@Tag` based test categorization with `unit` and `integration` tags, and Gradle task filtering

### Modified Capabilities
- `processor`: Implementation changes to use Lombok `@RequiredArgsConstructor` / `@Delegate` instead of manual constructors. No behavioral changes — purely internal refactor.

## Impact

- **Code**: All classes in `io.github.joke.percolate.processor` — constructor patterns change
- **Tests**: New unit test specs added for `PercolateProcessor`, `Pipeline`, `ProcessorModule`, `ProcessorComponent`; existing `PercolateProcessorSpec` tagged as integration
- **Build**: Gradle test configuration updated to support tag-based filtering
- **Dependencies**: No new dependencies (Lombok and Spock already present)
- **Affected teams**: None — internal refactor with no API changes

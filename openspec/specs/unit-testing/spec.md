# Unit Testing Spec

## Every processor class SHALL have a unit test

Each class in the processor module SHALL have a corresponding Spock specification file tagged with `@Tag('unit')` that tests the class in isolation using mocked dependencies.

### PercolateProcessor unit test exists
- **WHEN** the unit test suite runs
- **THEN** `PercolateProcessorUnitSpec` verifies that `init()` creates a Dagger component from the processing environment and that `process()` iterates annotated elements and delegates to the pipeline

### Pipeline unit test exists
- **WHEN** the unit test suite runs
- **THEN** `PipelineSpec` verifies the `process()` method behavior with mocked dependencies

### ProcessorModule unit test exists
- **WHEN** the unit test suite runs
- **THEN** `ProcessorModuleSpec` verifies each `@Provides` method returns the correct utility extracted from a mocked `ProcessingEnvironment`

## Unit tests SHALL use Spock mocking

Unit tests SHALL use Spock's built-in `Mock()` and `Stub()` for dependency isolation. No real annotation processing infrastructure SHALL be used in unit tests.

### Mocked ProcessingEnvironment
- **WHEN** `ProcessorModuleSpec` tests the `elements()` provider
- **THEN** a `Mock(ProcessingEnvironment)` is used and `getElementUtils()` interaction is verified

### Mocked Pipeline in processor test
- **WHEN** `PercolateProcessorUnitSpec` tests the `process()` method
- **THEN** the pipeline dependency is mocked and `pipeline.process(element)` invocations are verified

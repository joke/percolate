## Why

The existing test suite consists of 79 Spock/Groovy test files (specs, golden files, test helpers) that are tightly coupled to Spock 2.4, Groovy 5.0, and Google Compile Testing. The team wants to remove all existing tests and establish a new testing setup from scratch, keeping the Gradle build configuration intact.

## What Changes

- Remove all test files under `processor/src/test/` — 79 files including `.groovy` specs, `.dot` golden files, `META-INF/services` registration, and test helper classes
- Remove the obsolete `updateGoldens` task from `processor/build.gradle`
- No changes to Gradle build configuration (`settings.gradle`, `gradle.properties`, etc.)
- The `openspec/specs/unit-testing/` and `openspec/specs/test-tagging/` spec requirements will become obsolete and need replacement in a follow-up change

## Capabilities

### Modified Capabilities
- `unit-testing`: Requirements are obsolete — all existing test existence requirements will be removed
- `test-tagging`: Requirements are obsolete — all test tagging requirements will be removed

## Impact

- **Code**: `processor/src/test/` directory removed entirely
- **Specs**: `openspec/specs/unit-testing/spec.md` and `openspec/specs/test-tagging/spec.md` will no longer be satisfied
- **Build**: No changes to `build.gradle` or Gradle configuration
- **CI**: Existing test-related CI steps will fail until a new test setup is implemented in a follow-up change

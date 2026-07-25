## MODIFIED Requirements

### Requirement: The builtin unit suite is mutation-tested under threaded pitest, sharing the uniform threshold

The `strategies-builtin` module SHALL run pitest mutation testing over its `@Tag('unit')` suite as part of `check`, threaded (`threads = availableProcessors()`), using the `pitest-history-plugin` for incremental analysis. `strategies-builtin` SHALL meet the same shared `mutationThreshold`/`coverageThreshold`/`testStrengthThreshold` as every other enrolled module (see `test-coverage-tooling`) — no per-module override or tolerant floor SHALL be configured. pitest SHALL be scoped to the unit suite only and SHALL NOT run against the `e2e/` compile-test suite.

#### Scenario: pitest runs on the strategies-builtin unit suite under check
- **WHEN** `check` runs for `strategies-builtin`
- **THEN** pitest executes against the `@Tag('unit')` specs and enforces the shared mutation/coverage/test-strength thresholds, with no per-module override

#### Scenario: pitest is scoped away from the e2e suite
- **WHEN** the `strategies-builtin` pitest configuration is inspected
- **THEN** it targets the unit suite and excludes the `…/spi/builtins/e2e/` compile tests

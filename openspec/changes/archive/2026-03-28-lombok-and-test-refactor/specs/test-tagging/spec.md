## ADDED Requirements

### Requirement: Tests SHALL be tagged as unit or integration
Every Spock specification SHALL be annotated with either `@Tag('unit')` or `@Tag('integration')`. No untagged test classes SHALL exist.

#### Scenario: Unit tests are tagged
- **WHEN** a Spock specification tests a class in isolation with mocks
- **THEN** it SHALL be annotated with `@Tag('unit')`

#### Scenario: Integration tests are tagged
- **WHEN** a Spock specification uses Google Compile Testing or real annotation processing
- **THEN** it SHALL be annotated with `@Tag('integration')`

### Requirement: Gradle SHALL support tag-based test filtering
The Gradle build SHALL configure separate tasks for unit and integration tests using JUnit 5 tag filtering.

#### Scenario: Running only unit tests
- **WHEN** `./gradlew test` is executed
- **THEN** only specs tagged `@Tag('unit')` SHALL run

#### Scenario: Running only integration tests
- **WHEN** `./gradlew integrationTest` is executed
- **THEN** only specs tagged `@Tag('integration')` SHALL run

#### Scenario: Check runs all tests
- **WHEN** `./gradlew check` is executed
- **THEN** both unit and integration tests SHALL run

### Requirement: Integration tests SHALL cover minimal functionality
Integration tests using Google Compile Testing SHALL cover only end-to-end compilation scenarios. The majority of testing SHALL be done through unit tests.

#### Scenario: Integration test scope
- **WHEN** reviewing the integration test suite
- **THEN** there SHALL be at most a few integration specs covering successful compilation and basic error cases

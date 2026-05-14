## Context

The percolate processor module has an existing test suite at `processor/src/test/` with 79 files:
- 74 Spock `.groovy` specs (unit tests, integration tests, golden specs, architectural specs)
- 6 golden `.dot` graph files under `resources/golden-graphs/`
- 1 Groovy extension module registration (`META-INF/services/`)
- 1 test helper (`TestCompilers.groovy`)

The Gradle build configures Spock 2.4 + Groovy 5.0 + Google Compile Testing via the `dependencies` subproject. The `check` task runs both `test` (unit) and `integrationTest` tasks.

## Goals / Non-Goals

**Goals:**
- Remove all existing test files cleanly
- Leave Gradle build configuration untouched
- Produce a minimal, implementable task list

**Non-Goals:**
- Designing the new testing framework (done separately)
- Modifying `build.gradle` or any Gradle configuration
- Migrating or rewriting existing tests
- Updating CI/CD pipelines

## Decisions

1. **Remove `processor/src/test/` as a single directory** — All test files are under this path, and the test source set is defined as `src/test` in the Groovy plugin configuration. Removing the entire directory is the simplest approach.

2. **Do not modify `build.gradle`** — The user explicitly requested this. The Groovy plugin and Spock/Compile Testing dependencies will remain configured but unused until the new testing setup is implemented.

3. **Leave OpenSpec spec directories in place** — The obsolete `unit-testing/` and `test-tagging/` specs remain as documentation of what was removed. They will be cleaned up in a follow-up change.

## Risks / Trade-offs

- [Build will have no tests] — The `check` task will pass with zero tests, which may hide regressions. → Mitigation: Re-enable tests in a follow-up change.
- [Obsolete specs remain] — `unit-testing/spec.md` and `test-tagging/spec.md` reference 50+ scenarios that no longer exist. → Mitigation: Archive or remove them in a follow-up change.
- [Dependencies unused] — Spock, Groovy, and Compile Testing dependencies remain in `dependencies/build.gradle` but are unused. → Mitigation: Remove in a follow-up change.

## MODIFIED Requirements

### Requirement: Spock is configured by a checked-in per-module SpockConfig.groovy

Every module with a Spock suite SHALL carry a checked-in `SpockConfig.groovy` on that suite's own resources
path, and those files SHALL be identical in content. For an ordinary `test` suite the path is
`src/test/resources/SpockConfig.groovy`; for a suite bound to another source set the file lives on that
source set's resources path — in particular `architecture-tests` carries it at
`src/archRulesTest/resources/SpockConfig.groovy`, since its specs moved out of `src/test` when the module
became a rules library whose only tests are the rule-library's own negative fixtures.

The configuration SHALL cover at minimum:

- `mockMaker { preferredMockMaker spock.mock.MockMakers.mockito }`, so final classes, final
  methods, and `SpyStatic` are available everywhere
- `runner { parallel { enabled !Boolean.getBoolean('spock.parallel.disabled') } }` — parallel by
  default, switchable off by a system property (see the requirement below)
- `timeout { globalTimeout java.time.Duration.ofMinutes(1); applyGlobalTimeoutToFixtures false }`,
  so a deadlock or a runaway spec fails as a timed-out feature instead of hanging the build

Spock's `optimizeRunOrder` SHALL NOT be enabled in any module. Its run-history file lives under
the user's Spock home (`~/.spock/RunHistory/<SpecName>`), shared across every concurrent JVM on
the machine rather than scoped to a build, project, or module; concurrent JVMs race on it and
corrupt a spec's entry, producing `IllegalArgumentException: Comparison method violates its
general contract!` during test discovery, which under pitest surfaces as widespread survived
mutants unrelated to the code under test. The setting is off by default, so the requirement is
that nothing turns it on — an explicit `optimizeRunOrder false` declaration is not required.

A generated or plugin-synthesized `SpockConfig.groovy` SHALL NOT be used. The file is test input,
it is read by anyone debugging a spec, and its per-module copies are kept in step by review of a
tracked file rather than by a build step nobody sees.

#### Scenario: Every module with a Spock suite carries the file

- **WHEN** `SpockConfig.groovy` is inspected on the suite resources path for `processor`, `spi`,
  `strategies-builtin`, `reactor`, `reactor-blocking`, `annotations`, `architecture-tests`, and
  `percolate-smoke`
- **THEN** each exists, is checked into version control, and carries the same mock-maker, parallel,
  and timeout settings

#### Scenario: The rules library carries the file on its own suite's path

- **WHEN** `architecture-tests` is inspected
- **THEN** it carries `src/archRulesTest/resources/SpockConfig.groovy` and no `src/test` source set, its
  specs having moved to the suite that tests the rule library

#### Scenario: No module enables run-order optimization

- **WHEN** every `SpockConfig.groovy` and every `build.gradle` is inspected
- **THEN** none sets `optimizeRunOrder true`, and the default-off behaviour stands

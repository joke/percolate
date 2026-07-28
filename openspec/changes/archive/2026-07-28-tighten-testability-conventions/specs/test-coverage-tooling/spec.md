## MODIFIED Requirements

### Requirement: Every pitest-enrolled module disables Spock's cross-JVM run-order optimization

Every module enrolled in pitest (see the auto-enrollment requirement above) SHALL run with Spock's `optimizeRunOrder` runner setting disabled. Spock's `OptimizeRunOrderExtension` persists per-spec run-history to a file under the user's Spock home (`~/.spock/RunHistory/<SpecName>`) shared across **every concurrent JVM on the machine**, not scoped to a build, project, or module. Under pitest's own minion-level parallelism, concurrent JVMs race to read/write that file, corrupting a spec's history entry and causing `IllegalArgumentException: Comparison method violates its general contract!` during Spock's test-discovery sort — intermittently crashing the *entire* spec class (not any specific mutant) during a pitest coverage or mutation pass, which pitest then misreports as widespread survived mutants unrelated to the actual code under test. This SHALL NOT be diagnosed as a pitest test-to-mutant attribution limitation or accommodated with a lowered per-module threshold; it SHALL be fixed by disabling `optimizeRunOrder`.

The Spock configuration SHALL be supplied uniformly by the `percolate.conventions` plugin rather than duplicated as a per-module `SpockConfig.groovy`. Configuration that must hold for every module SHALL NOT depend on a developer noticing that five near-identical copies exist and editing all of them; the mock-maker setting demonstrated this failure mode by reaching only one module of six. Every module with a Spock suite — including modules not enrolled in pitest — SHALL receive the same configuration, covering at minimum:

- `runner { optimizeRunOrder false }`, for the reason above
- `runner { parallel { enabled false } }`, both for deterministic single-threaded execution under a mutation-testing oracle and because Mockito's static mocking is confined to the registering thread
- `mockMaker { preferredMockMaker spock.mock.MockMakers.mockito }`, so final classes, final methods, and `SpyStatic` are available everywhere

The rationale for each setting SHALL remain recorded alongside it; consolidating the files SHALL NOT drop the explanations.

#### Scenario: Every module with a Spock suite carries the configuration
- **WHEN** the resolved Spock configuration is inspected for `processor`, `spi`, `strategies-builtin`, `reactor`, `reactor-blocking`, `annotations`, and `architecture-tests`
- **THEN** each has `optimizeRunOrder` disabled, parallel execution disabled, and the mockito preferred mock maker

#### Scenario: The configuration is defined once
- **WHEN** the repository is searched for `SpockConfig.groovy` under a module's `src/test/resources`
- **THEN** no per-module copy remains, and the settings originate from the `percolate.conventions` plugin

#### Scenario: A module newly enrolled in pitest gets the same treatment
- **WHEN** a module with real production code and a `unit`-tagged test suite is newly enrolled in pitest
- **THEN** it inherits the shared configuration automatically, rather than requiring a new file and rather than relying on run-to-run variance in mutation/coverage scores to be tolerated or attributed to pitest itself

#### Scenario: A settings drift cannot recur silently
- **WHEN** a new Spock setting must hold repo-wide
- **THEN** it is added in one place in the conventions plugin and applies to every module at once

## 1. Architecture-tests module and baseline rules

- [x] 1.1 Add an unpublished `architecture-tests` module (no `maven-publish`); register it in `settings.gradle` and wire it into `./gradlew check`
- [x] 1.2 Add the ArchUnit dependency (pinned against the Java 11 / Gradle 9.3 toolchain) on the module's test classpath, plus `testImplementation project(...)` edges to every publishable module so its classes are importable
- [x] 1.3 Author the layering rule (`layeredArchitecture`/`onionArchitecture`): `annotations` ← `spi` ← `processor`; strategy modules depend on `spi` in production and on `processor`/`test-foundation` only in test; `test-foundation` depends on `processor` and on no strategy module
- [x] 1.4 Author the convention rules that already pass: `ExpansionStrategy`/`Strategy` implementations must not depend on the engine graph package ("strategies stay myopic"); `Stage` implementations are named `*Stage`; no package cycles
- [x] 1.5 Run the architecture suite; confirm 1.3–1.4 pass against the current tree (no production-scope violations expected)

## 2. Processor api/internal split

- [x] 2.1 Introduce an `internal` package segment in `processor`; move the graph, expansion/discovery/generate stages, and plan-extraction implementation types under it, keeping the entry point and externally-consumed types in the public surface
- [x] 2.2 Rewrite imports across the engine for the new package names
- [x] 2.3 Run the `processor` (and dependent) suites; they MUST pass unchanged, proving the move is placement-only with no behavioural change

## 3. Encapsulation rule and forced test relocation

- [x] 3.1 Add the rule "no class outside `processor` depends on a `processor` `internal` package" (production and test); confirm it now goes red on `strategies-builtin`
- [x] 3.2 Relocate `SelfSeedExpansionSpec` from `strategies-builtin` into `processor`, rewiring it to drive expansion through `FakeStrategy` (sentinel producer where a producing operation is needed) instead of ServiceLoading the real builtins
- [x] 3.3 Relocate any sibling spec the encapsulation rule also reddened, by the same FakeStrategy approach; do NOT triage or move specs that genuinely assert a builtin's own atom/output/targeted diagnostic (that is the later change)
- [x] 3.4 Re-run the architecture suite; the encapsulation rule now passes (no module other than `processor` imports `processor` internals)

## 4. Manual and verification

- [x] 4.1 Update the Extending (SPI) page of the user manual to state the now-enforced strategy-author boundary: a strategy implementation makes a local decision only and must not depend on the engine graph (enforced by the architecture suite)
- [x] 4.2 Confirm this change adds NO build-config rule that fails on the existing `docs/` `srcDir` reach (that is healed by the later documentation-overhaul change)
- [x] 4.3 Run `./gradlew check` and verify everything passes. NEVER continue if there are violations
- [ ] 4.4 Commit the completed change with `/commit-commands:commit`

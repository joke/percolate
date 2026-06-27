## 1. pitest spike

- [ ] 1.1 Apply `id("info.solidsoft.pitest") version "1.19.0"` to `processor`, bound to the **unit** `test` task only (exclude `integrationTest`), with `pitest-junit5-plugin` `1.2.3`, `mutators = ["ALL"]`, and `enableDefaultIncrementalAnalysis = true`; pin the core `pitestVersion`. Confirm it runs the Spock unit suite, emits a mutation report, and the runtime on the unit suite is acceptable
- [ ] 1.2 Record the baseline mutation score per engine package; **decide per area whether the existing unit tests are fixed in place or deleted and rewritten at the seam** based on the surviving mutants

## 2. Relocate docTags to spi

- [ ] 2.1 Extract the tag-wrapping (`// tag::<name>[]` / `// end::<name>[]` around a `CodeBlock`) out of `BuildMethodBodies` into a pure `spi` codegen helper; have the engine call it (no behavioural change to generated output)
- [ ] 2.2 Unit-test the helper in `spi` (no processor, no compile); remove the processor `DocTagsEmissionSpec`

## 3. Grow the engine unit tests to 95% + mutation-proof

- [ ] 3.1 Following the 1.2 fix/rewrite decision, grow or rewrite the engine unit tests **at the seams** toward 95% branch and surviving pitest — discover/expand/generate/validate stages, graph, plan extraction, cost, assembly hoisting, realisation diagnostics, nullness crossing — constructing inputs and asserting structure, with no compilation
- [ ] 3.2 Apply narrow, individually-justified jacoco exclusions to any pure debug/rendering package (`dump`, `DotRenderer`) only where 95% is not honestly reachable; note each

## 4. Remove the engine integration layer and raise the gates

- [ ] 4.1 Once the unit suite covers their contracts, remove the engine integration specs (`EngineWeavingFakeStrategySpec`, `SelfSeedExpansionSpec`, `GenerateStageFailureModesSpec`, any sibling fake-driven compile-test) and empty `processor`'s `integrationTest`
- [ ] 4.2 Remove `FakeStrategy` from `test-foundation` and its engine-side `META-INF/services` registration (keep `PercolateCompiler` for the feature layer)
- [ ] 4.3 Raise `processor`'s jacoco branch gate to `0.95` and wire the pitest mutation-score threshold into `check` (ratcheted from the spike baseline) — sequenced after 3.x so `main` is never red

## 5. Verification

- [ ] 5.1 Run `./gradlew check` and verify it passes: `processor` unit coverage ≥ 95%, the pitest threshold holds, and the ArchUnit module-boundary rules stay green. NEVER continue if there are violations
- [ ] 5.2 Commit the completed change with `/commit-commands:commit`

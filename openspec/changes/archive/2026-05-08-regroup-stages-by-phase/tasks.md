## 1. Create new package directories

- [x] 1.1 Create `processor/src/main/java/io/github/joke/percolate/processor/stages/`
- [x] 1.2 Create `stages/discover/`, `stages/seed/`, `stages/dump/` sub-directories
- [x] 1.3 Mirror the directory shape under `processor/src/test/groovy/io/github/joke/percolate/processor/stages/...`

## 2. Move the `Stage` interface

- [x] 2.1 Move `Stage.java` from `processor/` to `stages/`, update its `package` declaration
- [x] 2.2 Update every implementation's import to reference `processor.stages.Stage`
- [x] 2.3 Update `Pipeline.java`'s import

## 3. Move discovery stages into `stages/discover/`

- [x] 3.1 Move `DiscoverAbstractMethods.java` to `stages/discover/`, change `package`, promote to `public`
- [x] 3.2 Move `DiscoverMappings.java` to `stages/discover/`, change `package`, promote to `public`
- [x] 3.3 Move the matching Spock specs under `test/groovy/.../stages/discover/`
- [x] 3.4 Update `ProcessorModule.java` imports for the two classes

## 4. Move validators into `stages/validate/`

- [x] 4.1 Move `ValidateNoDuplicateTargets.java` from root to `stages/validate/`, change `package`, promote to `public`
- [x] 4.2 Move `ValidateSourceParameters.java` from root to `stages/validate/`, change `package`, promote to `public`
- [x] 4.3 Move existing `processor/validate/` package contents (`ValidateRealisationStage`, `ValidateMarkersPhase`, `ValidatePathsPhase`, `ValidationPhase`, `package-info.java` if any) into `stages/validate/`, updating their `package` declarations
- [x] 4.4 Move the matching Spock specs under `test/groovy/.../stages/validate/`
- [x] 4.5 Update `ProcessorModule.java` imports for all five validator classes/phases

## 5. Move seed stage into `stages/seed/`

- [x] 5.1 Move `SeedGraph.java` to `stages/seed/`, change `package`, promote to `public`
- [x] 5.2 Move the matching Spock specs under `test/groovy/.../stages/seed/`
- [x] 5.3 Update `ProcessorModule.java` imports

## 6. Move expand stage into `stages/expand/`

- [x] 6.1 Move existing `processor/expand/` package contents (`ExpandStage`, `ExpansionPhase`, `ResolveSourceChainsPhase`, `ResolveTargetChainsPhase`, `BridgeSourceToTargetPhase`, `package-info.java` if any) into `stages/expand/`, updating their `package` declarations
- [x] 6.2 Move the matching Spock specs under `test/groovy/.../stages/expand/`
- [x] 6.3 Update `ProcessorModule.java` imports for all expand classes/phases

## 7. Move dump stages into `stages/dump/`

- [x] 7.1 Move `DumpGraph.java` to `stages/dump/`, change `package`, promote to `public`
- [x] 7.2 Move `DumpExpandedGraph.java` to `stages/dump/`, change `package`, promote to `public`
- [x] 7.3 Move the matching Spock specs under `test/groovy/.../stages/dump/`
- [x] 7.4 Update `ProcessorModule.java` imports for both dump classes

## 8. Sweep for stale imports outside `ProcessorModule`

- [x] 8.1 `grep -rn "processor.expand\|processor.validate\|processor\.\(Stage\|DiscoverAbstractMethods\|DiscoverMappings\|ValidateNoDuplicateTargets\|ValidateSourceParameters\|SeedGraph\|DumpGraph\|DumpExpandedGraph\)" processor/src/` and update any remaining references
- [x] 8.2 Verify the architectural test in `expansion-strategy-spi` still asserts the SPI's freedom from `processor.graph.*` and the new `processor.stages.expand.*`
- [x] 8.3 Confirm `Stage` is reachable only from `processor.stages` and that no consumer imports it from `processor` (the old location)

## 9. Build and test

- [x] 9.1 `./gradlew :processor:compileJava` succeeds with no warnings about unresolved imports
- [x] 9.2 `./gradlew :processor:test` passes the full Spock + Compile Testing suite
- [x] 9.3 Verify golden DOT files under `processor/src/test/resources/golden-graphs/` re-pass byte-for-byte with no regeneration

## 10. Sync specs

- [x] 10.1 Apply the five capability deltas (`processor`, `graph-debug-output`, `graph-expansion`, `realisation-validation`, `expansion-strategy-spi`) to main specs
- [x] 10.2 Verify with `openspec verify regroup-stages-by-phase` that the change is coherent
- [x] 10.3 Confirm no other capability spec under `openspec/specs/` still references the obsolete package paths (`processor.expand`, `processor.validate` for moved classes, `processor` for the moved stages)

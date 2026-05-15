## 1. Module scaffolding

- [x] 1.1 Add `include 'spi'` and `include 'strategies-builtin'` to `settings.gradle`
- [x] 1.2 Create empty `spi/` and `strategies-builtin/` directories at the repo root
- [x] 1.3 Create `spi/build.gradle` mirroring `processor/build.gradle`'s plugin set (`java`, `groovy`, `palantir-baseline`, `errorprone`, `nullaway`, `spotless`); declare `compileOnly` Lombok, JSpecify, AutoService annotations; `annotationProcessor` Lombok; `api 'com.palantir.javapoet:javapoet'` (because `CodeBlock` is in the API surface); `implementation platform(project(':dependencies'))`. Java 11 source/target.
- [x] 1.4 Create `strategies-builtin/build.gradle` mirroring `spi`'s plugin set plus `compileOnly 'com.google.auto.service:auto-service-annotations'` and `annotationProcessor 'com.google.auto.service:auto-service'`; declare `implementation project(':spi')`; `testImplementation` Spock + Mockito + byte-buddy (testRuntimeOnly) + junit-platform-launcher (testRuntimeOnly) + `testFixtures(project(':spi'))`. Java 11 source/target.
- [x] 1.5 Confirm both new modules build with `./gradlew :spi:build :strategies-builtin:build` (currently empty — expect clean success or "no source" report)

## 2. Move SPI types into the new module

- [x] 2.1 In IDE, run move-package on `io.github.joke.percolate.processor.spi.*` (excluding the `builtins/` sub-package) → target `io.github.joke.percolate.spi.*` under `spi/src/main/java/io/github/joke/percolate/spi/`. Affected files (per design): `Bridge`, `SourceStep`, `GroupTarget`, `BridgeStep`, `Step`, `Slot`, `GroupBuild`, `ResolveCtx`, `CallableMethods`, `MethodCandidate`, `EdgeCodegen`, `GroupCodegen`, `Receiver`, `ThisReceiver`, `IncomingValues`, `VarNames`, `Containers`, `Weights`, `ElementSeed`, `package-info.java`
- [x] 2.2 Verify all moved files retain `@NullMarked` via the relocated `package-info.java`
- [x] 2.3 Run Spotless across all modules to normalise imports and formatting
- [x] 2.4 Build `:spi` in isolation: `./gradlew :spi:build`

## 3. Move built-in strategies into the new module

- [x] 3.1 In IDE, run move-package on `io.github.joke.percolate.processor.spi.builtins.*` → target `io.github.joke.percolate.spi.builtins.*` under `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/`. Affected files: `DirectAssign`, `ListMap`, `ListWrap`, `SetMap`, `SetWrap`, `OptionalMap`, `OptionalUnwrap`, `OptionalWrap`, `MethodCallBridge`, `GetterRead`, `ConstructorCall`, `package-info.java`
- [x] 3.2 Verify the package retains `@NullMarked`
- [x] 3.3 Run Spotless
- [x] 3.4 Build `:strategies-builtin` in isolation: `./gradlew :strategies-builtin:build`
- [x] 3.5 Inspect the produced JAR (`strategies-builtin/build/libs/`) and confirm `META-INF/services/io.github.joke.percolate.spi.Bridge`, `META-INF/services/io.github.joke.percolate.spi.SourceStep`, and `META-INF/services/io.github.joke.percolate.spi.GroupTarget` are present with the expected class FQNs

## 4. Wire the processor module

- [x] 4.1 In `processor/build.gradle`: replace any in-module SPI declaration with `implementation project(':spi')`; add `runtimeOnly project(':strategies-builtin')`; add `testImplementation testFixtures(project(':spi'))`
- [x] 4.2 Update every import under `processor/src/main/` and `processor/src/test/` from `io.github.joke.percolate.processor.spi.*` → `io.github.joke.percolate.spi.*` (53 files per recon)
- [x] 4.3 Update `ProcessorModule`'s ServiceLoader call sites to use the new FQNs (`ServiceLoader.load(io.github.joke.percolate.spi.Bridge.class)` etc.)
- [x] 4.4 Run Spotless
- [x] 4.5 Build `:processor` in isolation: `./gradlew :processor:build`. Expect compile failures only if step 4.2 missed an import — fix and re-run

## 5. Move test fixtures to spi `testFixtures`

- [x] 5.1 Create `spi/src/testFixtures/groovy/io/github/joke/percolate/spi/test/` directory; add Spock + Groovy `testFixtures*` configurations to `spi/build.gradle` if not already present (Gradle's `java-test-fixtures` plugin)
- [x] 5.2 Move `TypeUniverse.groovy` from `processor/src/test/groovy/io/github/joke/percolate/processor/test/` to `spi/src/testFixtures/groovy/io/github/joke/percolate/spi/test/TypeUniverse.groovy`; update its package declaration; update all imports referencing it across `processor/src/test/`
- [x] 5.3 Move `HarnessResolveCtx.groovy` the same way; update package, imports, and any `ResolveCtx` reference now pointing at the new SPI package
- [x] 5.4 Verify `TypeUniverseSpec.groovy` (the existing fixture self-test) still lives in `processor/src/test/groovy/` (or moves alongside its SUT into `spi/src/testFixtures/groovy/...` — pick one; design implies the latter)
- [x] 5.5 Build `:spi` and `:processor` in succession; confirm `processor`'s tests can resolve `io.github.joke.percolate.spi.test.TypeUniverse` from the testFixtures configuration

## 6. Delete dead code and add the replacement spec

- [x] 6.1 Delete the single-arg overload `ExpansionHarness.expand(MapperGraph seed)` from `processor/src/test/groovy/io/github/joke/percolate/processor/test/ExpansionHarness.groovy`. Also delete the now-unused private `loadService` helper if it has no remaining callers
- [x] 6.2 Delete `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/ExpansionCapabilitiesSpec.groovy`
- [x] 6.3 Create `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/BuiltinServiceRegistrationSpec.groovy` implementing the contract specified by the new `Built-in service registration smoke spec` requirement in `expansion-strategy-spi`. Tag `@spock.lang.Tag('unit')`. Cover the three ServiceLoader assertions plus the "no processor imports" structural scenario
- [x] 6.4 Run `./gradlew :strategies-builtin:test --tests '*BuiltinServiceRegistrationSpec'` and confirm green

## 7. Module READMEs

- [x] 7.1 Add `spi/README.md` (one paragraph): the SPI defines the strategy-author surface (`Bridge`, `SourceStep`, `GroupTarget` plus value types and utilities), depends only on JDK + JavaPoet, and is the only Gradle dependency a third-party strategy author needs at compile time. Mention `testFixtures(project(':spi'))` for `TypeUniverse` / `HarnessResolveCtx`. Note: from this change forward, SPI changes are versioned and breaking changes need bumps
- [x] 7.2 Add `strategies-builtin/README.md` (one paragraph): ships the eleven `@AutoService`-registered built-in strategies (`DirectAssign`, `ListMap`, …, `ConstructorCall`), depends only on `percolate-spi`, and is included automatically by `percolate-processor`'s `runtimeOnly` declaration. Users wanting custom-only setups may `exclude` this artifact

## 8. Architectural-test cleanup

- [x] 8.1 If `processor/src/test/` contains an architectural test that enforces "built-ins do not import `processor.graph` / `processor.stages.expand.*`" (the invariant cited in the existing `expansion-strategy-spi` spec), either delete it (the compile graph now enforces this structurally) or update its FQNs and relocate to `strategies-builtin/src/test/groovy/` so the check runs against the right sources
- [x] 8.2 Verify the relocated/deleted architectural test does not regress any other invariant

## 9. Verification

- [x] 9.1 Verify no file under `processor/src/` references `io.github.joke.percolate.processor.spi.*` (grep should return zero hits)
- [x] 9.2 Verify no file outside `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/` references `io.github.joke.percolate.spi.builtins.*` (except as a discovered string in tests like `graph-debug-output` scenarios that pin strategy FQNs)
- [x] 9.3 Verify `processor/src/test/groovy/` no longer contains `TypeUniverse.groovy` or `HarnessResolveCtx.groovy` (both moved out)
- [x] 9.4 Verify the four algebraic property specs and `ExpansionFailureModesSpec` still resolve `TypeUniverse` and `HarnessResolveCtx` correctly via the testFixtures configuration
- [x] 9.5 Run `./gradlew check` from the repo root and confirm all checks green: every module builds, every test passes, no Spotless / NullAway / Errorprone violations introduced, the new `BuiltinServiceRegistrationSpec` passes, the existing algebraic property specs and `ExpansionFailureModesSpec` still pass. NEVER continue if there are violations.

## 1. Module scaffolding

- [x] 1.1 Realise the `// include 'bom'` stub in `settings.gradle`; add `include 'percolate'` (starter) and `include 'test-foundation'`
- [x] 1.2 Create `bom/build.gradle` (`java-platform`, group `io.github.joke.percolate`)
- [x] 1.3 Create `percolate/build.gradle` (starter aggregator)
- [x] 1.4 Create `test-foundation/build.gradle` (depends on `processor` + compile-testing + spock/groovy)

## 2. Consumer BOM

- [x] 2.1 Declare `constraints` in `bom/build.gradle` for percolate's own artifacts only (annotations, percolate, processor, spi, strategies-builtin, reactor, reactor-blocking)
- [x] 2.2 Confirm the BOM does not pin third-party versions (that stays in `:dependencies`)

## 3. Convenience starter

- [x] 3.1 `percolate/build.gradle`: `api project(':processor')` + `api project(':strategies-builtin')`
- [x] 3.2 Verify the starter's resolved graph contains both processor and strategies-builtin

## 4. Maven publication

- [x] 4.1 Apply `maven-publish` (via a root convention or per-module) to annotations, spi, processor, strategies-builtin, reactor, reactor-blocking, the starter, and the BOM under group `io.github.joke.percolate`
- [x] 4.2 Set a project version (e.g. `gradle.properties`) used by the publications
- [x] 4.3 Run `./gradlew publishToMavenLocal`; confirm artifacts and POMs land under `~/.m2/repository/io/github/joke/percolate/`
- [x] 4.4 Confirm the starter's published POM declares the processor and strategies-builtin dependencies

## 5. Sever the engine↔builtins edge

- [x] 5.1 Remove `runtimeOnly project(':strategies-builtin')` from `processor/build.gradle`
- [x] 5.2 Verify resolved `processor` compile/runtime/test configurations no longer contain any strategy module

## 6. test-foundation harness

- [x] 6.1 Implement the strategy-agnostic compile harness (`compileMapper(source, target, directives) -> Compilation` plus helpers) running `PercolateProcessor`
- [x] 6.2 Implement a reusable `FakeStrategy` (synthetic SPI implementation emitting a sentinel) and a way for tests to register it for a compilation
- [x] 6.3 Verify `test-foundation` depends on `processor` + compile-testing but no strategy module

## 7. Re-slice end-to-end specs

- [x] 7.1 Add `testImplementation project(':processor')`, `project(':test-foundation')`, and compile-testing to `strategies-builtin/build.gradle`
- [x] 7.2 Classify each `processor` `stages/**/*EndToEndSpec` by the litmus test (asserts strategy-specific output → builtin; asserts engine behaviour → engine; pure stage/validation → unaffected)
- [x] 7.3 Move the builtin-output specs into `strategies-builtin/src/test`, rewritten on the shared harness (carry their fixtures along)
- [x] 7.4 Rewrite the remaining engine specs in `processor` against `FakeStrategy`, asserting on the sentinel + structure; register the fake via a test-local `META-INF/services` entry
- [x] 7.5 Migrate `reactor` and `reactor-blocking` e2e specs onto the `test-foundation` harness
- [x] 7.6 Verify no `processor` spec asserts strategy-specific output (e.g. `Integer.valueOf`, container expressions)

## 8. Black-box smoke build

- [x] 8.1 Create `percolate-smoke/` as a standalone Gradle build (own `settings.gradle` + `build.gradle`, `repositories { mavenLocal() }`)
- [x] 8.2 Wire it to resolve `io.github.joke.percolate:percolate` (annotationProcessor) + annotations (compileOnly) + BOM (platform) by GAV — no `project(':...')`, no test-foundation
- [x] 8.3 Add one fixed `@Mapper` plus source/target types and a check asserting a mapper implementation is generated and compiles
- [x] 8.4 Assert the generated implementation references only the consumer's types and `javax.annotation.processing.Generated`
- [x] 8.5 Validate two-pass: `./gradlew publishToMavenLocal` then build `percolate-smoke`

## 9. Documentation and final validation

- [x] 9.1 Document the canonical consumer setup (starter on `annotationProcessor` + annotations `compileOnly` + BOM platform, optional `percolate-reactor`) in the README/quickstart
- [x] 9.2 Run `./gradlew check` and resolve every violation before completing — NEVER continue with violations
- [x] 9.3 Validate the smoke build (Task 8.5) passes against freshly published artifacts
- [x] 9.4 Commit the completed change with `/commit-commands:commit`

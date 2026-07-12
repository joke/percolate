## 1. Relocation module `percolate-javapoet`

- [x] 1.1 Make the GradleUp/shadow plugin (`com.gradleup.shadow`) available to the build (plugin version via `settings.gradle` pluginManagement or the version catalog).
- [x] 1.2 Create the `percolate-javapoet` module directory and `build.gradle`: take `com.palantir.javapoet:javapoet:0.16.0` as a `compileOnly`/shadow-scoped input, and configure `shadowJar` to `relocate 'com.palantir.javapoet', 'io.github.joke.percolate.javapoet'` — relocating that package **only** (leave `javax.lang.model.*` untouched).
- [x] 1.3 Configure the module so the relocated shadow jar is its consumable artifact: `apiElements`/`runtimeElements` expose the relocated jar, so a `project(':percolate-javapoet')` dependent compiles against `io.github.joke.percolate.javapoet.*`.
- [x] 1.4 Enforce the swallow invariant: the published POM/Gradle metadata declares **zero** dependency on `com.palantir.javapoet` (upstream is `compileOnly`/shadow-only, never `api`/`implementation`).
- [x] 1.5 Register `include 'percolate-javapoet'` in `settings.gradle`.
- [x] 1.6 Verify the artifact: it contains classes under `io.github.joke.percolate.javapoet` and **none** under `com.palantir.javapoet`.

## 2. SPI cutover

- [x] 2.1 In `spi/build.gradle`, replace `api 'com.palantir.javapoet:javapoet'` with `api project(':percolate-javapoet')`.
- [x] 2.2 Flip every `com.palantir.javapoet` import in `spi/src/main` (14 files) to `io.github.joke.percolate.javapoet` (IDE move-package + Spotless); confirm the public surface — `CodeBlock` and `TypeName` — now resolves to the relocated package.

## 3. Atomic cutover of processor + strategy modules

- [x] 3.1 `processor`: drop the direct `com.palantir.javapoet:javapoet` dependency (resolve via `percolate-javapoet`, transitively where possible) and flip its imports (17 files).
- [x] 3.2 `strategies-builtin`: drop the direct dependency and flip its imports (33 files).
- [x] 3.3 `reactor`: drop the direct dependency and flip its imports (8 files).
- [x] 3.4 `reactor-blocking`: drop the direct dependency and flip its imports (5 files).
- [x] 3.5 Flip any test-source `com.palantir.javapoet` imports across the five modules so tests compile against the relocated package.

## 4. Guards and invariants

- [x] 4.1 Add an ArchUnit guard (architecture-tests module) asserting no production source in spi/processor/strategies-builtin/reactor/reactor-blocking imports `com.palantir.javapoet`.
- [x] 4.2 Add a test asserting `com.palantir.javapoet` appears on **no** downstream compile or runtime classpath (the swallow invariant).
- [x] 4.3 Confirm a downstream module compiles against `io.github.joke.percolate.javapoet.CodeBlock` (the SPI itself proves consumability of the relocated jar).

## 5. Packaging

- [x] 5.1 Apply Maven publication to `percolate-javapoet`; verify its POM is self-contained and declares no `com.palantir.javapoet` dependency.
- [x] 5.2 Add `percolate-javapoet` to the `percolate-bom` managed artifacts.
- [x] 5.3 Keep the `com.palantir.javapoet:javapoet:0.16.0` pin in the `:dependencies` platform as the relocation module's input only; confirm it does not leak into any published percolate POM.

## 6. Verify and commit

- [x] 6.1 Run `./gradlew check` (including `percolate-smoke`) and confirm it is green. NEVER continue if there are violations.
- [x] 6.2 Mark tasks complete, run `opsx:sync` to sync the delta specs, and commit with `/commit-commands:commit`.

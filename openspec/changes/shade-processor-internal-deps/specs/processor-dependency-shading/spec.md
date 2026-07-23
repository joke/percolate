## ADDED Requirements

### Requirement: Processor shades its internal-only third-party dependencies

The `processor` module SHALL shade and relocate `org.jgrapht:jgrapht-core`, `com.google.auto:auto-common`, and `com.google.dagger:dagger` (the runtime artifact, not `dagger-compiler`) — together with the internal-only runtime dependencies each of them structurally requires to function (`org.jheaps`, `org.apfloat` for jgrapht-core; Guava's `com.google.common` for auto-common) — into its own published jar, relocating each under `io.github.joke.percolate.lib.<lib>`: `org.jgrapht` to `io.github.joke.percolate.lib.jgrapht`, `org.jheaps` to `io.github.joke.percolate.lib.jheaps`, `org.apfloat` to `io.github.joke.percolate.lib.apfloat`, `com.google.auto.common` to `io.github.joke.percolate.lib.autocommon`, `com.google.common` to `io.github.joke.percolate.lib.guava`, and `dagger` (the library's actual top-level Java package — distinct from its `com.google.dagger` Maven groupId) to `io.github.joke.percolate.lib.dagger`. This SHALL happen directly within `processor`'s own build (no separate relocation module), since these libraries are consumed exclusively inside `processor` and never cross the spi↔processor boundary. `dagger-compiler` (an `annotationProcessor`-scope, build-time-only code generator) and `com.google.auto.service` (a separate, unrelocated dependency) are unaffected. `org.jgrapht:jgrapht-io` is dropped entirely rather than shaded: its sole use (DOT export for debug graph dumps) is replaced by a small hand-written DOT serializer, avoiding jgrapht-io's own `antlr4-runtime`/`commons-text`/`commons-lang3` dependencies altogether. `jakarta.inject-api`/`javax.inject` are kept as ordinary declared dependencies (not shaded): the Dagger runtime's `dagger.internal.Provider` directly implements both JSR-330 `Provider` interfaces, so they are needed, unrelocated, at runtime, but as a tiny and effectively-frozen spec API there is no meaningful version-collision risk to shade against.

#### Scenario: Relocated classes carry percolate's package

- **WHEN** the published `processor` artifact is inspected
- **THEN** it contains classes under `io.github.joke.percolate.lib.jgrapht`, `io.github.joke.percolate.lib.jheaps`, `io.github.joke.percolate.lib.apfloat`, `io.github.joke.percolate.lib.autocommon`, `io.github.joke.percolate.lib.guava`, and `io.github.joke.percolate.lib.dagger`
- **AND** it contains no class under `org.jgrapht`, `org.jheaps`, `org.apfloat`, `com.google.auto.common`, `com.google.common`, or `dagger`

#### Scenario: Build-time-only Dagger code generation is not relocated

- **WHEN** `processor`'s build configuration is inspected
- **THEN** `com.google.dagger:dagger-compiler` remains an `annotationProcessor`-scope dependency, untouched by the relocation

#### Scenario: jgrapht-io is not a dependency at all

- **WHEN** `processor`'s build configuration and published POM are inspected
- **THEN** neither declares any dependency on `org.jgrapht:jgrapht-io`

### Requirement: Upstream shaded dependencies are fully swallowed

`processor`'s published POM and Gradle metadata SHALL declare **zero** dependency on `org.jgrapht:jgrapht-core`, `com.google.auto:auto-common`, or `com.google.dagger:dagger` (nor on their shaded transitive dependencies — `org.jheaps`, `org.apfloat`, Guava). This eliminates both the "missing dependency version" publish failure (their versions were managed only by the internal, unpublished `:dependencies` platform) and the risk of a foreign version of any of them colliding with percolate's on a consumer's shared annotationProcessor classpath. `processor`'s published POM SHALL continue to declare `jakarta.inject:jakarta.inject-api` and `javax.inject:javax.inject` as ordinary, explicitly-versioned runtime dependencies (see the "kept unshaded" clause above) — these are not part of the swallow invariant.

#### Scenario: Published metadata declares no upstream dependency

- **WHEN** `processor`'s generated POM and Gradle module metadata are inspected
- **THEN** they declare no dependency on `org.jgrapht:jgrapht-core`, `org.jgrapht:jgrapht-io`, `com.google.auto:auto-common`, `com.google.dagger:dagger`, `org.jheaps:jheaps`, `org.apfloat:apfloat`, or `com.google.guava:guava`
- **AND** every dependency it does declare (`jakarta.inject:jakarta.inject-api`, `javax.inject:javax.inject`, and the `annotations`/`spi` project coordinates) carries an explicit version

#### Scenario: Publish no longer fails on missing dependency version

- **WHEN** `processor`'s artifacts are validated for Maven Central publication
- **THEN** validation does not fail with a missing-dependency-version error for any shaded or unshaded coordinate

### Requirement: Dagger-generated component code is self-consistently relocated

`dagger-compiler`-generated sources (e.g. `DaggerProcessorComponent`), which are compiled as part of `processor`'s own `main` sourceSet and — like all of `processor`'s own production code — legitimately reference the unrelocated upstream packages directly in source (relocation is a post-compile bytecode rewrite applied by shading, not a source-level restriction), SHALL end up entirely self-contained within the relocated `io.github.joke.percolate.lib.*` package trees after shading — both `processor`'s own compiled classes and the upstream runtime classes they depend on SHALL be relocated together in the same shading pass, so no reference to an unrelocated shaded package remains anywhere in the published jar.

#### Scenario: Shaded jar contains no unrelocated upstream reference

- **WHEN** the published `processor` jar's classes are inspected after shading
- **THEN** no class file contains a reference to any class under `org.jgrapht`, `org.jheaps`, `org.apfloat`, `com.google.auto.common`, `com.google.common`, or `dagger` outside the relocated `io.github.joke.percolate.lib.*` trees

#### Scenario: Annotation processing works end-to-end through the shaded jar

- **WHEN** the shaded `processor` artifact is used as an `annotationProcessor` in a real compilation (verified via `percolate-smoke`)
- **THEN** `PercolateProcessor` loads and runs successfully, generating the expected mapper implementation

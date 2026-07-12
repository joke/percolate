# JavaPoet Relocation Spec

## Purpose

Annotation processors share one classloader on a consumer's processorpath. If another processor
contributes a different `com.palantir.javapoet` version, the version percolate compiled against can be
silently substituted, producing a `LinkageError`/`NoSuchMethodError` at annotation-processing time. This
spec defines the `percolate-javapoet` relocation module that shades upstream JavaPoet into percolate's own
namespace so percolate is immune to a foreign JavaPoet version on a shared processorpath.

## Requirements

### Requirement: Relocated JavaPoet module

The project SHALL provide a source-free Gradle module `percolate-javapoet` that repackages `com.palantir.javapoet:javapoet:0.16.0` by relocating the `com.palantir.javapoet` package to `io.github.joke.percolate.javapoet`. The relocated jar SHALL be the module's consumable `api` artifact. The relocation SHALL rewrite the `com.palantir.javapoet` package **only**; the JDK packages the JavaPoet API references (in particular `javax.lang.model.*`) SHALL remain unrelocated so the codegen API still interoperates with the compiler's mirror types.

#### Scenario: Relocated classes carry percolate's package

- **WHEN** the `percolate-javapoet` artifact is inspected
- **THEN** it contains classes under `io.github.joke.percolate.javapoet`
- **AND** it contains no class under `com.palantir.javapoet`

#### Scenario: Compiler mirror types are not relocated

- **WHEN** a relocated type that references a compiler mirror is inspected (for example `TypeName.get(javax.lang.model.type.TypeMirror)`)
- **THEN** its `javax.lang.model.*` parameter and return types are unchanged

### Requirement: Upstream JavaPoet is fully swallowed

`percolate-javapoet` SHALL fully absorb upstream JavaPoet: its published POM and Gradle metadata SHALL declare **zero** dependency on `com.palantir.javapoet`, and no module that depends on `percolate-javapoet` SHALL receive `com.palantir.javapoet` on any compile or runtime classpath. This guarantees percolate is immune to another annotation processor contributing a different `com.palantir.javapoet` version to a shared processorpath.

#### Scenario: Published metadata declares no upstream dependency

- **WHEN** the `percolate-javapoet` POM and Gradle metadata are inspected
- **THEN** they declare no dependency on `com.palantir.javapoet:javapoet`

#### Scenario: Original package is absent from downstream classpaths

- **WHEN** the resolved compile and runtime classpaths of any percolate module that depends on `percolate-javapoet` are inspected
- **THEN** no artifact or class in the `com.palantir.javapoet` package is present

### Requirement: Percolate codegen speaks the relocated package

All percolate production code that renders generated output — the `percolate-spi` public codegen surface, the `processor`, and the strategy modules (`strategies-builtin`, `reactor`, `reactor-blocking`) — SHALL reference JavaPoet through `io.github.joke.percolate.javapoet` and SHALL NOT import `com.palantir.javapoet`. Each of these modules SHALL obtain JavaPoet via `percolate-javapoet` (directly or transitively) and SHALL NOT declare a direct dependency on `com.palantir.javapoet:javapoet`.

#### Scenario: No production source imports the upstream package

- **WHEN** the production sources of spi, processor, strategies-builtin, reactor, and reactor-blocking are inspected
- **THEN** no import statement references `com.palantir.javapoet`

#### Scenario: No module declares the upstream dependency

- **WHEN** the build files of spi, processor, strategies-builtin, reactor, and reactor-blocking are inspected
- **THEN** none declares `com.palantir.javapoet:javapoet`
- **AND** each resolves JavaPoet through `percolate-javapoet`

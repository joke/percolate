## MODIFIED Requirements

### Requirement: Relocated JavaPoet module

The project SHALL provide a Gradle module `percolate-javapoet` that repackages `com.palantir.javapoet:javapoet:0.16.0` by relocating the `com.palantir.javapoet` package to `io.github.joke.percolate.lib.javapoet`. The relocated jar SHALL be the module's consumable `api` artifact. The relocation SHALL rewrite the `com.palantir.javapoet` package **only**; the JDK packages the JavaPoet API references (in particular `javax.lang.model.*`) SHALL remain unrelocated so the codegen API still interoperates with the compiler's mirror types. The module MAY additionally carry a minimal relocated **source overlay** (see "Relocated JavaPoet class overlay"); apart from any overlaid class, its classes derive entirely from the upstream artifact. This package target, `io.github.joke.percolate.lib.javapoet`, follows the project-wide `io.github.joke.percolate.lib.<lib>` convention used for every shaded third-party dependency (see the `processor-dependency-shading` capability).

#### Scenario: Relocated classes carry percolate's package

- **WHEN** the `percolate-javapoet` artifact is inspected
- **THEN** it contains classes under `io.github.joke.percolate.lib.javapoet`
- **AND** it contains no class under `com.palantir.javapoet`

#### Scenario: Compiler mirror types are not relocated

- **WHEN** a relocated type that references a compiler mirror is inspected (for example `TypeName.get(javax.lang.model.type.TypeMirror)`)
- **THEN** its `javax.lang.model.*` parameter and return types are unchanged

### Requirement: Relocated JavaPoet class overlay

`percolate-javapoet` MAY replace an individual upstream JavaPoet class with a percolate-owned **overlay** in order to add codegen behaviour the upstream class does not expose (specifically, `MethodSpec` gains the ability to bracket a whole method with leading/trailing AsciiDoc include-tag comments — see the `code-generation` capability). An overlaid class SHALL be authored under `src/main/java/com/palantir/javapoet/` so it compiles against the upstream API with package-private access and is relocated to `io.github.joke.percolate.lib.javapoet` alongside every other class. The overlay SHALL win over its upstream twin during shading via **project-files-first ordering** plus `DuplicatesStrategy.EXCLUDE` scoped to the overlaid class's path; the shaded jar SHALL therefore contain exactly one copy of the overlaid class, the percolate one. The overlay SHALL preserve the relocation and full-swallow invariants (it introduces no `com.palantir.javapoet` dependency and no unrelocated class) and SHALL change only the behaviour it documents, leaving the rest of the class's observable behaviour equivalent to upstream.

#### Scenario: The overlay wins and is the only copy

- **WHEN** the shaded `percolate-javapoet` jar is inspected for the overlaid class (`io.github.joke.percolate.lib.javapoet.MethodSpec`)
- **THEN** exactly one class entry for it is present, and it is the percolate overlay (it carries the added include-tag behaviour), not the unmodified upstream class

#### Scenario: Overlay preserves the swallow and relocation invariants

- **WHEN** the `percolate-javapoet` POM/metadata and the shaded jar are inspected with the overlay present
- **THEN** no dependency on `com.palantir.javapoet:javapoet` is declared and no class under `com.palantir.javapoet` remains

### Requirement: Percolate codegen speaks the relocated package

All percolate production code that renders generated output — the `percolate-spi` public codegen surface, the `processor`, and the strategy modules (`strategies-builtin`, `reactor`, `reactor-blocking`) — SHALL reference JavaPoet through `io.github.joke.percolate.lib.javapoet` and SHALL NOT import `com.palantir.javapoet`. Each of these modules SHALL obtain JavaPoet via `percolate-javapoet` (directly or transitively) and SHALL NOT declare a direct dependency on `com.palantir.javapoet:javapoet`.

#### Scenario: No production source imports the upstream package

- **WHEN** the production sources of spi, processor, strategies-builtin, reactor, and reactor-blocking are inspected
- **THEN** no import statement references `com.palantir.javapoet`

#### Scenario: No module declares the upstream dependency

- **WHEN** the build files of spi, processor, strategies-builtin, reactor, and reactor-blocking are inspected
- **THEN** none declares `com.palantir.javapoet:javapoet`
- **AND** each resolves JavaPoet through `percolate-javapoet`

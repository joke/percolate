## Why

Percolate's annotation processor renders generated code with JavaPoet (`com.palantir.javapoet:javapoet:0.16.0`), and `CodeBlock`/`TypeName` are part of the **public SPI surface** every strategy author compiles against. Annotation processors share one classloader on the consumer's processorpath: if any *other* processor drags a different `com.palantir.javapoet` version onto that path, the version percolate compiled against can be silently substituted, producing a `LinkageError`/`NoSuchMethodError` **at annotation-processing time** — a failure in the *consumer's* build that names neither percolate nor the real culprit. Square archived JavaPoet and Palantir's fork is its successor, so the population of processors sharing this exact package only grows. Relocating JavaPoet into percolate's own namespace makes percolate permanently immune. **Now** is the moment: ~20 more SPI strategies are about to be authored — done first, they are born on the relocated package instead of being written against the old one and flipped later.

## What Changes

- **NEW** `percolate-javapoet` module: a source-free relocation module that shades `com.palantir.javapoet:javapoet:0.16.0` and relocates `com.palantir.javapoet` → `io.github.joke.percolate.javapoet`, publishing the relocated jar as its consumable `api` artifact. It relocates **that package only** — `javax.lang.model.*` stays intact so the SPI still interops with the compiler's mirror types.
- **Swallow invariant**: the module fully absorbs upstream JavaPoet — its POM/Gradle metadata declares **zero** dependency on `com.palantir.javapoet`, so no downstream compile or runtime classpath ever contains the original package.
- **BREAKING (SPI api)**: the SPI's public codegen types move package — `CodeBlock` (used by `Receiver`, `OperationCodegen`, `IncomingValues`, `Container`, `ScopeCodegen`, `LiteralCoercion`, `DocTags`) and `TypeName` (`MemberRequest`) become `io.github.joke.percolate.javapoet.*`. Strategy authors import the relocated package. The SPI's sole non-JDK `api` dependency changes from `com.palantir.javapoet:javapoet` to `project(':percolate-javapoet')`.
- **Atomic cutover** across the five modules that name JavaPoet — spi, processor, strategies-builtin, reactor, reactor-blocking (79 files) — flipping every `com.palantir.javapoet` import to the relocated package and replacing the direct upstream dependency with `percolate-javapoet`. Cannot be incremental: the relocated `CodeBlock` is a distinct class from upstream, and a `CodeBlock` crossing spi↔processor↔strategies must be one type. Mechanical and behavior-preserving; no change to generated code.
- `percolate-javapoet` becomes a published, BOM-managed artifact; published POMs stay self-contained and never expose `com.palantir.javapoet`.

## Capabilities

### New Capabilities
- `javapoet-relocation`: the relocation module and its invariants — relocate `com.palantir.javapoet` only (leaving `javax.lang.model.*` intact); fully swallow upstream so no downstream classpath or production source references the original package; the relocated jar is the module's consumable `api` artifact; all percolate codegen (SPI + processor + strategies) speaks `io.github.joke.percolate.javapoet`.

### Modified Capabilities
- `expansion-strategy-spi`: the SPI's only non-JDK `api` dependency changes from `com.palantir.javapoet:javapoet` to the relocated `percolate-javapoet`; the codegen interface surface (`CodeBlock`) is now `io.github.joke.percolate.javapoet.CodeBlock`.
- `callable-method-discovery`: `Receiver.asExpression()` returns `io.github.joke.percolate.javapoet.CodeBlock` (package move only; behavior unchanged).
- `consumer-packaging`: `percolate-javapoet` is a new published artifact managed by the BOM; published POMs remain self-contained and MUST NOT expose `com.palantir.javapoet` to consumers.

## Impact

- **Affected modules**: `percolate-javapoet` (new); spi, processor, strategies-builtin, reactor, reactor-blocking (import + dependency flip); bom (adds the new artifact); the `:dependencies` platform (keeps the 0.16.0 pin as the relocation module's input only); architecture-tests (new guard: no production source imports `com.palantir.javapoet`).
- **Affected consumers**: external SPI strategy authors compile against `io.github.joke.percolate.javapoet.CodeBlock` instead of `com.palantir.javapoet.CodeBlock` — a source-visible package change (reversible later behind a percolate-owned `Code` type if ever wanted). Plain `@Mapper` users are unaffected — they never name JavaPoet.
- **Build tooling**: adds GradleUp/shadow (`com.gradleup.shadow`) to produce the relocated artifact.
- **Out of scope**: shading the Dagger runtime + jgrapht-core/jheaps into the processor jar (internal-only deps — next change); the doc-tag "tags around the method" feature (dropped for now). No change to generated output or engine behavior.

## Why

Today every strategy interface, every built-in strategy implementation, every value type they exchange, and the engine that drives them all live in the single `percolate-processor` Gradle module. A third party who wants to write a custom `Bridge` cannot do so without depending on the entire processor — including JGraphT, Dagger, Google AutoCommon and the rest of the engine's internals. The module boundary is invisible: nothing prevents the engine from reaching into strategy internals or vice versa. As we prepare to add many more built-in strategies, and as we want to invite third-party strategy authors, the cost of leaving everything in one module compounds.

Extracting the SPI into its own module makes the strategy-author contract explicit, lets third parties depend only on what they need, and lets us evolve the engine and the strategies independently behind a stable interface.

## What Changes

**Module split** (BREAKING — coordinates and packages move)
- New `percolate-spi` module containing the strategy-author surface: `Bridge`, `SourceStep`, `GroupTarget` interfaces; value types `BridgeStep`, `Step`, `Slot`, `GroupBuild`, `ElementSeed`, `IncomingValues`, `MethodCandidate`; `ResolveCtx` and `CallableMethods` protocols; codegen abstractions `EdgeCodegen`, `GroupCodegen`, `Receiver`, `ThisReceiver`, `VarNames`; utilities `Containers`, `Weights`.
- New `percolate-strategies-builtin` module containing the eleven `@AutoService` implementations: `DirectAssign`, `ListMap`, `ListWrap`, `SetMap`, `SetWrap`, `OptionalMap`, `OptionalUnwrap`, `OptionalWrap`, `MethodCallBridge`, `GetterRead`, `ConstructorCall`.
- `percolate-processor` keeps the engine: graph data model, expansion pipeline / stages, codegen runner, annotation entry point, Dagger wiring, `MapperContext`, `Diagnostics`, `ProcessorModule`, `ExpansionHarness`, property-test fakes, `HarnessScope`.

**Package rename** (BREAKING)
- `io.github.joke.percolate.processor.spi.*` → `io.github.joke.percolate.spi.*`
- `io.github.joke.percolate.processor.spi.builtins.*` → `io.github.joke.percolate.spi.builtins.*`
- Affects 53 files in `processor/src/` plus 5 OpenSpec specs that quote these FQNs.

**Dependency wiring**
- `percolate-spi` depends only on JDK + JavaPoet (the codegen surface — `CodeBlock` — is part of the strategy contract). Does NOT depend on `percolate-annotations`.
- `percolate-strategies-builtin` compile-depends on `percolate-spi` only.
- `percolate-processor` compile-depends on `percolate-spi` and `percolate-annotations`; `runtimeOnly`-depends on `percolate-strategies-builtin` so end users transparently get the builtins, with `exclude` available for custom-only setups.
- `percolate-spi` publishes a `testFixtures` configuration exposing `TypeUniverse` and `HarnessResolveCtx` for any SPI consumer to use when testing strategies.

**Test-harness simplification**
- `ExpansionHarness.expand(MapperGraph seed)` — the single-arg `ServiceLoader` overload — is removed. The only caller (`ExpansionCapabilitiesSpec`) is deleted with it.
- `ExpansionCapabilitiesSpec` is replaced by `BuiltinServiceRegistrationSpec` in `percolate-strategies-builtin`, which directly verifies that `ServiceLoader.load(Bridge | SourceStep | GroupTarget)` discovers exactly the expected builtin classes. This is the real contract between modules.

**Naming convention** (intentional inconsistency)
- `percolate-annotations` keeps its bare-root packages (`io.github.joke.percolate.Mapper` etc.). `percolate-spi` uses the `.spi` infix (`io.github.joke.percolate.spi.Bridge`) to avoid collision. Documented so the inconsistency is clearly deliberate, not drift.

**Documentation**
- One short `README.md` per new module explaining purpose and consumption.

**Out of scope**
- Per-strategy Spock unit tests (`DirectAssignSpec`, `ListMapSpec`, etc.) and `src/test/java` fixtures for `ConstructorCall` / `GetterRead`. These land in a follow-up change `unit-test-builtin-strategies`, scoped against the new module layout.
- Maven publishing config. All three modules remain unpublished.

## Capabilities

### New Capabilities

None. This change introduces no new runtime behavior — every capability that exists today continues to exist, just relocated and repackaged.

### Modified Capabilities

- `expansion-strategy-spi`: the requirement "SPI package isolation" is rewritten — the SPI ships in its own Gradle module (`percolate-spi`) at package `io.github.joke.percolate.spi`, not as a sub-package of the processor module. The architectural invariant that built-ins do not import from `processor.graph` / `processor.stages.expand` is preserved and strengthened (now enforced by module boundaries at compile time, not only by an import-check test).
- `expansion-test-harness`: the requirement "ExpansionHarness two-mode entry points" is reduced to a single mode — only the explicit-list overload remains; the `expand(seed)` `ServiceLoader` overload and its "SPI mode" scenarios are removed. The `TypeUniverse` and `HarnessResolveCtx` fixtures move from `processor/src/test/groovy/` into the `percolate-spi` `testFixtures` configuration (the requirement text fixes the location reference).
- `container-expansion`: every container built-in FQN in the spec (`OptionalWrap`, `OptionalUnwrap`, `OptionalMap`, `ListWrap`, `ListMap`, `SetWrap`, `SetMap`) moves from `io.github.joke.percolate.processor.spi.builtins.*` to `io.github.joke.percolate.spi.builtins.*`. The `META-INF/services/io.github.joke.percolate.spi.Bridge` file path follows. `Containers` FQN moves correspondingly. The strategies now live in `percolate-strategies-builtin`, not in `percolate-processor`.
- `graph-debug-output`: `strategyClassFqn` literals in scenarios (`GetterRead`, `OptionalWrap`) update to the new package; the "package prefix omitted from label" scenario updates its expected prefix string accordingly.
- `callable-method-discovery`: the requirement that places `CallableMethods`, `MethodCandidate`, `Receiver`, `ThisReceiver` in `io.github.joke.percolate.processor.spi` updates to `io.github.joke.percolate.spi`.

## Impact

**Affected teams** — solo project at present; no cross-team coordination needed. The discipline shift is that, after this change, `percolate-spi` is a published contract: changes to it are either non-breaking additions or breaking version bumps. Engine internals (`percolate-processor`) and built-in strategies (`percolate-strategies-builtin`) may evolve freely behind the SPI.

**Code** — 53 files in `processor/src/` change their imports (mechanical, IDE-driven move-package + Spotless). Eleven built-in strategy classes physically relocate to a new module. One harness method and one Spock spec are deleted; one new Spock spec is added.

**Build** — `settings.gradle` adds two `include` lines. Two new `build.gradle` files come into being, each mirroring the processor's plugin set (`palantir-baseline`, `errorprone`, `nullaway`, `spotless`, Lombok, JSpecify, `dependencies` BOM); `strategies-builtin` additionally pulls in `auto-service`. The processor's `build.gradle` swaps its `spi/`-related ownership for `runtimeOnly project(':strategies-builtin')` and `testFixtures(project(':spi'))`. Strategies-builtin uses Spock + Mockito + byte-buddy + junit-platform-launcher for its tests; no jqwik, no AssertJ.

**APIs** — every external consumer of `Bridge` / `SourceStep` / `GroupTarget` (none today, but the design is forward-looking) depends on `percolate-spi` from this change onward. Maven coordinates for the SPI are introduced even though publication is deferred — consumers within this repo reference `project(':spi')` until that day comes.

**Dependencies** — `percolate-spi` introduces `com.palantir.javapoet:javapoet` as an `api` dependency (because `CodeBlock` is in its public types). This is honest: anyone writing a strategy needs JavaPoet to render their codegen. `percolate-strategies-builtin` keeps `com.google.auto.service:auto-service-annotations` `compileOnly` and `com.google.auto.service:auto-service` as `annotationProcessor` to generate `META-INF/services`. `percolate-processor`'s own runtime dependency set is unchanged on the engine side; it adds `runtimeOnly project(':strategies-builtin')`.

**Systems** — CI: `./gradlew test` automatically picks up new modules through `settings.gradle` `include`s; no CI config edit is required unless a `subprojects.filter` exists.

## 1. Generalise the processor-option seam

- [x] 1.1 Add `Optional<String> option(String key)` to `ResolveCtx` with javadoc stating it is the only processor-option accessor and that strategies parse their own raw values
- [x] 1.2 Give `CompileResolveCtx` the raw `processingEnv.getOptions()` map and implement `option(String)` over it, replacing the `elemConfiguredTimeZone` field
- [x] 1.3 Wire the option map into the per-mapper `ResolveCtx` construction in `ExpandStage`/`ProcessorModule`, keeping construction per-mapper and free of any `ThreadLocal`
- [x] 1.4 Migrate the temporal zone bridge to read `option("percolate.time.zone")`, declaring the key as its own constant in the temporal feature package
- [x] 1.5 Delete `ResolveCtx.configuredTimeZone()` and every remaining caller
- [x] 1.6 Migrate the enum conversion codegen to read `option("percolate.switch.style")` via `resolveCtx()` and parse `AUTO`/`CLASSIC`/`ARROW` itself, treating absent and unrecognised values as `AUTO`
- [x] 1.7 Delete `BodyRenderContext.switchStyle()`, drop the `switchStyle` field from `BodyRenderContextImpl`, and simplify `BodyRenderContextFactory` and `BuildMethodBodies` accordingly
- [x] 1.8 Update the affected unit specs (`CompileResolveCtx`, temporal, enum conversion, `BodyRenderContextFactory`, `BuildMethodBodies`) to stub `option(String)` instead of the deleted accessors
- [x] 1.9 Run `./gradlew check --no-configuration-cache` and confirm the seam refactor is green before any builder work starts

## 2. Declare the construction.preference option

- [x] 2.1 Add a `ConstructionPreference` enum (`CONSTRUCTOR`, `BUILDER`) with a case-insensitive parse degrading to `CONSTRUCTOR`, in the `strategies-builtin` assembly package that owns the option's meaning — not in `percolate-spi`, which gains no builder-named type
- [x] 2.2 Add the `CONSTRUCTION_PREFERENCE` key constant to `ProcessorOptions` and add `"percolate.construction.preference"` to `PercolateProcessor.getSupportedOptions()`
- [x] 2.3 Delete the now-unread `timeZone` and `switchStyle` fields from `ProcessorOptions` and `parseSwitchStyle` from `ProcessorOptionsReader` — strategy-consumed options carry no typed field and are parsed once, in the strategy that owns them
- [x] 2.4 Update the `ProcessorOptions.builder()` call sites in `ProcessorOptionsReaderSpec`, `ProcessorModuleSpec`, `ExpandStageSpec`, and `BuildMethodBodiesSpec` for the dropped fields
- [x] 2.5 Extend `ConstructionPreferenceSpec` with absent, `builder`, `BUILDER`, and unrecognised-value cases, and drop `ProcessorOptionsReaderSpec`'s `parseSwitchStyle` features

## 3. Price constructor assembly from the option

- [x] 3.1 Make `ConstructorCall` read `option("percolate.construction.preference")` and emit `Weights.STEP` when it resolves to `constructor` (including unset) and `Weights.EXPENSIVE` otherwise
- [x] 3.2 Verify `ConstructorCall` references no builder strategy and keeps its `declared.isEmpty()` bail unchanged
- [x] 3.3 Extend `ConstructorCallSpec` with the three pricing cases, stubbing the mocked seam's `option(String)`

## 4. FluentBuilder — the baseline convention

- [x] 4.1 Add `FluentBuilder` to the assembly feature package: discover a static no-arg `builder()` on the target, a builder type with a no-arg `build()` returning the target, and single-argument setters named exactly after each declared child
- [x] 4.2 Implement the containment gate (`declaredChildren ⊆ setterNames`) plus the empty-declaration bail, declining when the builder type, entry point, matched setters, or `build()` is private
- [x] 4.3 Emit one `OperationSpec` with a `Port.subTarget` per declared child, typed from the setter parameter and nulled through `demand.nullnessOf(...)`, in declared-children iteration order
- [x] 4.4 Render the chained expression with a `$Z` wrap marker before each setter call and before `build()`
- [x] 4.5 Price the strategy from `option("percolate.construction.preference")`, inverse to `ConstructorCall`
- [x] 4.6 Write `FluentBuilderSpec` over the mocked seam covering positive discovery, private/absent members, an unmatched declared child, a strict-subset match, the empty declaration, and both pricing cases

## 5. The three remaining conventions

- [x] 5.1 Add `ProtobufBuilder` (`newBuilder()` entry, `setName(v)` setters) and `ProtobufBuilderSpec`
- [x] 5.2 Add `WithBuilder` (`builder()` entry, `withName(v)` setters) and `WithBuilderSpec`
- [x] 5.3 Add `SideLocatedBuilder` (`<Target>Builder` type beside the target, public no-arg constructor, `name(v)` setters) and `SideLocatedBuilderSpec`, narrowing the name match by structure so a same-named type that lacks `build()` does not match
- [x] 5.4 Review the four implementations for shared plumbing and extract a common base **inside `percolate-strategies-builtin`** only if the duplication is real; add nothing to `percolate-spi`
- [x] 5.5 Confirm `percolate-spi` gained no builder-named type and that the four strategies ask every question through `ResolveCtx`

## 6. Registration and engine-neutrality checks

- [x] 6.1 Add the four strategies to `BuiltinServiceRegistrationSpec`'s expected `ServiceLoader` set
- [x] 6.2 Confirm the assembly feature package holds its strategies together and its specs mirror the package structure
- [x] 6.3 Assert no `processor`-module class references a builder type, a builder convention, or `percolate.construction.preference`

## 7. End-to-end coverage

- [ ] 7.1 Add compile-based e2e fixtures and assertions for each of the four conventions, checking the generated chained expression
- [ ] 7.2 Add an e2e proving the containment gate: a builder with surplus setters assembles, and a declared child with no setter fails to realise
- [ ] 7.3 Add an e2e proving the empty declaration assembles nothing through either form
- [ ] 7.4 Add an e2e over a Lombok-style target exposing both an all-args constructor and a builder, asserting the constructor by default and the builder under `-Apercolate.construction.preference=builder`
- [ ] 7.5 Add an e2e proving the preference never excludes: with the preference set to `builder`, a builder-less target still assembles through its constructor

## 8. Documentation

- [ ] 8.1 Write `strategies-builtin/src/docs/builder-assembly.adoc` with a worked example per convention, the subset rule, and the preference switch, including source and generated output by tag
- [ ] 8.2 Add the compiling documentation fixtures and the doc-e2e that materialises their generated output
- [ ] 8.3 Add the page to `docs/modules/ROOT/nav.adoc`
- [ ] 8.4 Document `percolate.construction.preference` in `processor/src/docs/compile-time-switches.adoc` — table row, value section, and a cross-reference to the builder page
- [ ] 8.5 Run `./gradlew antora` and confirm the site builds with no warnings and the new page renders

## 9. Verification

- [ ] 9.1 Run `openspec validate add-builder-assembly` and confirm it passes
- [ ] 9.2 Run `./gradlew check --no-configuration-cache` and fix every violation — NEVER continue while any check fails
- [ ] 9.3 Commit the completed change with `/commit-commands:commit`

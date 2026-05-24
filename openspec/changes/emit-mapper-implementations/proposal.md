## Why

The processor pipeline today stops at *validated graph*: `discover → seed → expand → validate → dump`. Every strategy already knows how to render itself as a `CodeBlock` (via `EdgeCodegen` / `GroupCodegen`), but nothing walks the realised subgraph and assembles those snippets into a `JavaFile`. The whole reason the graph machinery exists — emitting working mapper implementations — is missing.

A first slice covering trivial mappers (single-segment paths, `DirectAssign`, `ConstructorCall`-style group targets) is enough to validate the recursive-composition contract end-to-end without taking on the harder territory of multi-segment property discovery, container scope transitions, or nested mapper composition. Those each become their own future slice.

## What Changes

- **New `generate` pipeline stage** at the end of the pipeline, split into two phases: `BuildMethodBodies` (graph walk → `List<MethodImpl>`) and `AssembleMapperType` (typed value list → `TypeSpec` → `JavaFile.writeTo(Filer)`).
- **Pipeline ordering invariant**: `dump` runs after the last graph-modifying stage; `generate` is a read-only consumer that follows `dump`. Final order: `discover → seed → expand → validate → dump → generate`. Codegen MUST NOT mutate `MapperGraph`. Any future graph-modifying stage (e.g., optimization) slots in before `dump`.
- **Per-mapper failure policy**: if `Diagnostics.hasErrorsFor(mapperType)` is true, `generate` skips the mapper completely — no class file is emitted. If `generate` throws on a mapper, the stage catches the exception, records a `Diagnostics.error`, and skips that mapper only. Other mappers in the same round continue normally. **Never ship broken code; always fail at compile time.**
- **Generated class shape** (stable, additive across future slices):
  ```
  @javax.annotation.processing.Generated("io.github.joke.percolate")
  public final class <Name>Impl implements <Name> {
      public <Name>Impl([deps…]) { … }
      @Override <method-body>
  }
  ```
  Slice 1 emits a no-arg constructor with no fields. Future slices add `private final <Dep>` fields + constructor parameters for nested-mapper composition without changing the surface shape. The package is the same as the `@Mapper` interface; the class name is `<InterfaceName>Impl`.
- **New SPI implementations** (in the processor module, package-private): one `IncomingValues` impl backed by an indexed list (for `byGroupPosition`) and a name map (for `byName` / `single`); one `VarNames` impl that is a no-op for slice 1 with a `fresh(String hint)` seam for slice 3's lambda-parameter naming.
- **Expression-style codegen**: post-order recursion over the realised subgraph from each method's target root. Leaves bottom out at `SourceLocation` parameter nodes — codegen synthesises the parameter reference from `MapperShape`'s parameter list. No statement-style intermediate locals in slice 1; shared-intermediate extraction is deferred.
- **JavaPoet import discipline**: all type references in generated code use `ClassName.get(...)` / `TypeName.get(...)` so JavaPoet manages imports automatically — generated files are human-readable, not FQN-laden.

## Capabilities

### New Capabilities

- `code-generation`: the generate stage, class shape, body composition, leaf parameter handling, `@Generated` annotation, JavaPoet integration, failure policy (validation skip + exception tolerance), the read-only graph invariant.

### Modified Capabilities

- `processor`: the `Pipeline` stage list grows from nine stages to ten; the new stage (`GenerateStage`) sits after `DumpExpandedGraph`. The "and return `null` (no code generation in this change)" disclaimer drops. Pipeline ordering pins `dump` before `generate`.

## Impact

- **Code**: new `processor/src/main/java/io/github/joke/percolate/processor/stages/generate/` package containing `GenerateStage`, `BuildMethodBodies`, `AssembleMapperType`, `MethodImpl` (value type), `IncomingValuesImpl`, `VarNamesImpl`. `Pipeline.java` / `ProcessorModule.java` extended to register the new stage. `MapperContext.java` may gain a field for `List<MethodImpl>` if the two phases need to hand off through context (alternatively the split is internal to `GenerateStage` and stays out of `MapperContext`).
- **Tests**: Spock specs for body-builder (synthetic `RealisedSubgraph` → expected `CodeBlock`), for assembler (`List<MethodImpl>` → expected `JavaFile`), plus a Google `compile-testing` end-to-end spec that runs the processor against a `Person → Human` record-to-record fixture and asserts the generated class compiles and produces the expected output at runtime.
- **Build**: no new dependencies — `com.palantir.javapoet` is already on the classpath via the SPI module.
- **APIs**: no SPI changes. `EdgeCodegen` / `GroupCodegen` / `IncomingValues` / `VarNames` / `Receiver` are consumed as already specified.
- **Teams**: processor maintainers only.

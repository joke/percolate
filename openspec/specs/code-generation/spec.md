# Code Generation Spec

## Purpose

`GenerateStage` is the final pipeline stage. It walks the **extracted plan** (the read-only cheapest-plan view produced by `plan-extraction`) and emits a `<Mapper>Impl` Java source file via JavaPoet, one per fully-realised `@Mapper`-annotated type. The stage is read-only with respect to `MapperGraph` and skips entire mappers on validation errors or exceptions so the processor never ships broken code. This spec pins the class shape, body composition algorithm, failure policy, and SPI implementations that downstream slices will additively extend.

## Requirements

### Requirement: GenerateStage pipeline stage

The processor SHALL ship a `GenerateStage` class in package `io.github.joke.percolate.processor.stages.generate` implementing `Stage`. It SHALL be `@Inject`-constructed by Dagger via `@RequiredArgsConstructor(onConstructor_ = @Inject)` and provided through `ProcessorModule`'s ordered `List<Stage>` provision such that it runs as the *last* stage in `Pipeline.process(typeElement)`.

`GenerateStage.run(MapperContext)` SHALL be a read-only consumer of the `MapperGraph` and `RealisedSubgraph` produced by earlier stages. It SHALL NOT mutate the graph or any field on `MapperContext` other than (optionally) generate-stage-local state.

Internally `GenerateStage` SHALL orchestrate two phases:
1. `BuildMethodBodies` — walks the realised subgraph per abstract method and produces a `List<MethodImpl>`.
2. `AssembleMapperType` — assembles the `List<MethodImpl>` into a `JavaFile` and writes it via the injected `Filer`.

The split between the two phases SHALL be internal to `GenerateStage`. The intermediate `List<MethodImpl>` SHALL NOT be added to `MapperContext`'s public surface; it lives in stage-local scope.

#### Scenario: GenerateStage runs last in the pipeline

- **WHEN** the `ProcessorModule`-provided ordered `List<Stage>` is inspected
- **THEN** the last element is an instance of `GenerateStage`

#### Scenario: GenerateStage does not mutate the graph

- **WHEN** `GenerateStage.run(ctx)` returns
- **THEN** the `MapperGraph` referenced by `ctx.getGraph()` has the same node set, edge set, and group set as it had on entry
- **AND** no `addNode`, `addEdge`, `addGroup`, or analogous mutator method has been invoked on the graph during the stage

#### Scenario: GenerateStage composes the two phases internally

- **WHEN** the source of `GenerateStage` is inspected
- **THEN** it invokes `BuildMethodBodies` and `AssembleMapperType` in that order against the same `MapperContext`
- **AND** no other path constructs `JavaFile` instances outside `AssembleMapperType`

### Requirement: Generated class shape

For every `@Mapper`-annotated interface (or abstract class) `<Name>` for which `GenerateStage` emits code, the generated `JavaFile` SHALL contain a single top-level type with the following shape:

- **Package**: the same package as the `@Mapper`-annotated type.
- **Class name**: `<Name>Impl` where `<Name>` is the simple name of the `@Mapper`-annotated type.
- **Visibility**: `public`.
- **Modifier**: `final`.
- **Annotations**: exactly one — `@javax.annotation.processing.Generated("io.github.joke.percolate")`.
- **Supertype clause**: `implements <Name>` (or `extends <Name>` if the `@Mapper` is an abstract class — slice-1 fixtures use interfaces).
- **Constructor**: exactly one `public` constructor. In slice 1 the constructor has an empty parameter list and an empty body. Future slices may add constructor parameters (one per nested-mapper dependency) without altering visibility, name, or count.
- **Methods**: one `@Override`-annotated `public` method per abstract method discovered in the source `<Name>`, with the same signature (return type, parameter types and names) as the abstract method.

The class SHALL declare no nested types, no static fields, and no instance fields (in slice 1). Imports SHALL be managed by JavaPoet — no fully-qualified class references SHALL appear in the rendered source for any type that can be imported.

#### Scenario: Generated class for a trivial mapper

- **WHEN** the processor runs against an interface `package com.example; @Mapper public interface PersonMapper { Human map(Person person); }` whose graph is fully realised
- **THEN** the `Filer` receives a `JavaFile` whose package is `com.example` and whose top-level type is `PersonMapperImpl`
- **AND** the type is declared `public final class PersonMapperImpl implements PersonMapper`
- **AND** the type carries exactly the annotation `@Generated("io.github.joke.percolate")` (resolved to `javax.annotation.processing.Generated`)
- **AND** the type declares one public no-arg constructor with an empty body
- **AND** the type declares one method `@Override public Human map(Person person)`

#### Scenario: Imports are managed by JavaPoet

- **WHEN** the rendered source of a generated `<Name>Impl` is inspected
- **THEN** no `import` line declares a class from the same package as the generated type
- **AND** every reference to a class outside `java.lang` resolves through an `import` line or through a `ClassName`/`TypeName` JavaPoet construct
- **AND** no rendered source line contains the substring `java.lang.` (the JavaPoet-managed default import covers it)

### Requirement: @Generated annotation

The generated class SHALL be annotated with `@javax.annotation.processing.Generated` (the JDK 9+ standard annotation). The annotation's single `value` SHALL be the string literal `"io.github.joke.percolate"` (the processor's package). No other annotation members SHALL be set.

The processor module SHALL NOT depend on `javax.annotation:javax.annotation-api` for this purpose — the JDK-shipped annotation is sufficient at Java 11+.

#### Scenario: Annotation FQN and value

- **WHEN** the rendered source of any generated `<Name>Impl` is inspected
- **THEN** the type carries an annotation whose FQN is `javax.annotation.processing.Generated`
- **AND** the annotation's `value` member equals `"io.github.joke.percolate"`

### Requirement: Per-mapper failure policy

`GenerateStage.run(ctx)` SHALL skip an entire mapper — emitting no `JavaFile` for that mapper — when either of these conditions holds:

1. `Diagnostics.hasErrorsFor(ctx.getMapperType())` returns `true` at the time `GenerateStage` runs.
2. Any exception is thrown during `BuildMethodBodies` or `AssembleMapperType` for that mapper.

For condition 2, `GenerateStage` SHALL catch the exception, record a `Diagnostics.error(mapperType, "code generation failed: " + exception.getMessage())` diagnostic carrying the offending mapper element, and continue with the next mapper.

In neither case SHALL the stage emit a partial implementation, a stub class, or any other artifact bearing the `<Name>Impl` name. The "never ship broken code" principle takes precedence over "produce something the user can iterate on."

Per-mapper isolation SHALL hold: a skipped mapper SHALL NOT cause other mappers in the same processor round to be skipped. `MapperStep` dispatches each `@Mapper`-annotated `TypeElement` to `Pipeline.process(...)` independently, and `GenerateStage` SHALL preserve that isolation.

#### Scenario: Validation error skips the entire mapper

- **WHEN** `Diagnostics.hasErrorsFor(mapperType)` returns `true` on entry to `GenerateStage.run(ctx)`
- **THEN** `GenerateStage` returns without invoking `Filer.createSourceFile` for `mapperType`
- **AND** no new diagnostic is added (the existing validation diagnostic stands)

#### Scenario: Exception during generation is caught and diagnosed

- **WHEN** `BuildMethodBodies` throws a `RuntimeException` while processing `mapperType`
- **THEN** `GenerateStage` catches the exception
- **AND** records a `Diagnostics.error` whose element is `mapperType` and whose message contains `"code generation failed"` plus the exception's message
- **AND** does not invoke `Filer.createSourceFile` for `mapperType`
- **AND** does not rethrow

#### Scenario: One failing mapper does not block others

- **WHEN** a processor round contains two `@Mapper` types `A` and `B`, where `A` has validation errors and `B` does not
- **THEN** `Filer.createSourceFile` is invoked for `BImpl`
- **AND** is not invoked for `AImpl`

### Requirement: Read-only graph invariant

`GenerateStage` (and any phase it invokes) SHALL NOT call any mutating method on `MapperGraph`, including but not limited to `addNode`, `addEdge`, `addGroup`, `recordGroupOutcome`, or any method that adds, removes, or mutates nodes, edges, groups, or outcomes. The stage's view of the graph SHALL be obtained through read-only accessors (`graph.nodes()`, `graph.edges()`, `RealisedSubgraph`).

This invariant exists so that the `.dot` debug outputs produced by `DumpGraph` / `DumpFullGraph` / `DumpTransforms` faithfully reflect the graph that `GenerateStage` consumed. Future graph-modifying stages (e.g., an optimisation pass) SHALL be ordered before `dump` in the pipeline so the same invariant holds.

#### Scenario: No mutating call sites in generate

- **WHEN** the source of every class under `processor/src/main/java/io/github/joke/percolate/processor/stages/generate/` is inspected
- **THEN** no source line contains a call to `MapperGraph#addNode`, `MapperGraph#addEdge`, `MapperGraph#addGroup`, `MapperGraph#recordGroupOutcome`, or any other graph mutator

#### Scenario: Dump precedes generate in the pipeline

- **WHEN** the ordered `List<Stage>` provided by `ProcessorModule` is inspected
- **THEN** every graph-dumping stage (`DumpGraph`, `DumpFullGraph`, `DumpTransforms`, plus any future `Dump*` stage) appears strictly before `GenerateStage`

### Requirement: IncomingValues and VarNames runtime implementations

The processor module SHALL ship one package-private `IncomingValues` implementation and one package-private `VarNames` implementation, both under `io.github.joke.percolate.processor.stages.generate`.

The `IncomingValues` implementation SHALL be constructed with an indexed `List<CodeBlock>` (for `byGroupPosition(int)`) and a `Map<String, CodeBlock>` (for `byName(String)` and `single()`). `single()` SHALL return the sole positional entry when the list has size 1; it MAY throw `IllegalStateException` if invoked when there is more than one positional entry.

The `VarNames` implementation in slice 1 SHALL be a placeholder with no public methods beyond what the `VarNames` SPI interface declares (the interface is currently empty). It exists so that codegen call sites can pass a non-null `VarNames` reference without slice-1 strategies needing a stateful fresh-name supplier.

#### Scenario: IncomingValues delegates by name and position

- **WHEN** an `IncomingValues` instance is constructed with `byName = {"firstName" → CB1, "lastName" → CB2}` and positional list `[CB1, CB2]`
- **THEN** `byName("firstName")` returns `CB1`
- **AND** `byName("lastName")` returns `CB2`
- **AND** `byGroupPosition(0)` returns `CB1`
- **AND** `byGroupPosition(1)` returns `CB2`

#### Scenario: IncomingValues single() returns the only positional entry

- **WHEN** an `IncomingValues` instance is constructed with a single-entry positional list `[CB]`
- **THEN** `single()` returns `CB`

#### Scenario: VarNames placeholder is non-null

- **WHEN** `BuildMethodBodies` invokes any `EdgeCodegen.render(varNames, …)` or producer `Codegen.render(varNames, …)`
- **THEN** the `varNames` argument is a non-null `VarNames` instance

### Requirement: Method bodies render by walking the extracted plan

`BuildMethodBodies` SHALL compose each method body by walking the extracted plan view
(`plan-extraction`) from the method's return-root `Value`: render the Value's chosen producer
`Operation` by invoking its codegen with `IncomingValues` keyed by **port name**, where each
incoming value is the recursively rendered port `Value`. Producer identity is structural — the
generator SHALL NOT infer it from shared codegen instances, edge labels, or any
group/`ExpansionGroup` surface, and SHALL NOT read `Nullability` to decide wiring.

#### Scenario: Fan-in renders from the chosen producer
- **WHEN** the return-root's chosen producer is `new Address(int,String)` with ports `number`,
  `street`
- **THEN** the body renders that Operation's codegen once, with incoming values keyed `number` and
  `street`

#### Scenario: No group or label reads
- **WHEN** the source of `BuildMethodBodies` is inspected
- **THEN** it references no `ExpansionGroup`, no group id, and no edge-carried consumer slot

### Requirement: Child scopes render as lambda bodies

A scope-owning `Operation` in the plan SHALL render its child scope's extracted plan as the
per-element lambda body: the child param-root renders as the lambda parameter, the child return-root
expression is the lambda result, and the owning Operation's codegen weaves the container operation
(`map`/`flatMap`/`mapPresence`) around it.

#### Scenario: Stream element mapping renders a lambda
- **WHEN** the plan contains a `map` Operation for `Stream<A> → Stream<B>` owning a child scope
- **THEN** the generated body contains the stream operation applied with a lambda whose body is
  the rendered child plan

### Requirement: Stream stages render as a threaded pipeline

Each plain container Operation in the plan (`iterate`, `collect`, `wrap`, `unwrap`) SHALL render by
threading its single `StreamOps`/container snippet onto the rendered expression of its operand, so a
chain of such Operations composes into one fluent pipeline expression. The generator SHALL NOT fuse
`iterate`/`map`/`collect` into a single Operation's codegen.

#### Scenario: Cross-kind pipeline threads stage by stage
- **WHEN** the plan for `List<Optional<A>> → Optional<Set<B>>` is
  `wrap ⟵ collect ⟵ map ⟵ flatMap ⟵ iterate`
- **THEN** the rendered expression is a single chain
  `Optional.ofNullable(src.stream().flatMap(…).map(…).collect(…))`, each stage's snippet threaded onto
  its operand

### Requirement: Nullness handling renders as ordinary Operations

Code generation SHALL contain no nullability weaving: `[requireNonNull]` and `[coalesce]` are plan
Operations rendered through the same codegen contract as any other Operation. The generated output
for a `NULLABLE → NON_NULL` crossing remains `java.util.Objects.requireNonNull(expr, message)` with
the existing message format, or the coalescing form when the binding declares a default.

#### Scenario: Crossing renders via its Operation
- **WHEN** a nullable source feeds a non-null port without a default
- **THEN** the rendered expression is produced by the `[requireNonNull]` Operation's codegen, not by
  generator-side wrapping

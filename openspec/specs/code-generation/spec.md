# Code Generation Spec

## Purpose

`GenerateStage` is the final pipeline stage. It walks the **extracted plan** (the read-only cheapest-plan view produced by `plan-extraction`) and emits a `<Mapper>Impl` Java source file via JavaPoet, one per fully-realised `@Mapper`-annotated type. The stage is read-only with respect to `MapperGraph` and skips entire mappers on validation errors or exceptions so the processor never ships broken code. This spec pins the class shape, body composition algorithm, failure policy, and SPI implementations that downstream slices will additively extend.

## Requirements

### Requirement: GenerateStage pipeline stage

The processor SHALL ship a `GenerateStage` class in package `io.github.joke.percolate.processor.stages.generate` implementing `Stage`. It SHALL be `@Inject`-constructed by Dagger via `@RequiredArgsConstructor(onConstructor_ = @Inject)` and provided through `ProcessorModule`'s ordered `List<Stage>` provision such that it runs as the *last* stage in `Pipeline.process(typeElement)`.

`GenerateStage.run(MapperContext)` SHALL be a read-only consumer of the `MapperGraph` and the extracted plan (`plan-extraction`) produced by earlier stages. It SHALL NOT mutate the graph or any field on `MapperContext` other than (optionally) generate-stage-local state.

Internally `GenerateStage` SHALL orchestrate two phases:
1. `BuildMethodBodies` — walks the extracted plan per abstract method and produces a `List<MethodImpl>`.
2. `AssembleMapperType` — assembles the `List<MethodImpl>` into a `JavaFile` and writes it via the injected `Filer`.

The split between the two phases SHALL be internal to `GenerateStage`. The intermediate `List<MethodImpl>` SHALL NOT be added to `MapperContext`'s public surface; it lives in stage-local scope.

#### Scenario: GenerateStage runs last in the pipeline

- **WHEN** the `ProcessorModule`-provided ordered `List<Stage>` is inspected
- **THEN** the last element is an instance of `GenerateStage`

#### Scenario: GenerateStage does not mutate the graph

- **WHEN** `GenerateStage.run(ctx)` returns
- **THEN** the `MapperGraph` referenced by `ctx.getGraph()` has the same vertex set and edge set as it had on entry
- **AND** no graph-append method (`apply(AddValue)`, `apply(AddOperation)`, `valueFor`) has been invoked during the stage

#### Scenario: GenerateStage composes the two phases internally

- **WHEN** the source of `GenerateStage` is inspected
- **THEN** it invokes `BuildMethodBodies` and `AssembleMapperType` in that order against the same `MapperContext`
- **AND** no other path constructs `JavaFile` instances outside `AssembleMapperType`

### Requirement: Generated class shape

For every `@Mapper`-annotated interface (or abstract class) `<Name>` for which `GenerateStage` emits code, the generated `JavaFile` SHALL contain a single top-level type with the following shape:

- **Package**: the same package as the `@Mapper`-annotated type.
- **Class name**: `<Name>Impl` where `<Name>` is the simple name of the `@Mapper`-annotated type.
- **Visibility**: `public`.
- **Modifier**: `final` **iff** the `-Apercolate.classes.final` compiler option is `true`; absent by default. (Default changed: previously the class was unconditionally `final`.)
- **Annotations**: exactly one — `@javax.annotation.processing.Generated("io.github.joke.percolate")`.
- **Supertype clause**: `implements <Name>` (or `extends <Name>` if the `@Mapper` is an abstract class — slice-1 fixtures use interfaces).
- **Constructor**: exactly one `public` constructor. In slice 1 the constructor has an empty parameter list and an empty body. Future slices may add constructor parameters (one per nested-mapper dependency) without altering visibility, name, or count.
- **Methods**: one `@Override`-annotated `public` method per abstract method discovered in the source `<Name>`, with the same signature (return type, parameter types and names) as the abstract method, additionally `final` iff `-Apercolate.methods.final` is `true`, and with each parameter additionally `final` iff `-Apercolate.parameters.final` is `true`.

The class SHALL declare no nested types and no instance fields. It MAY declare `private static final` fields **only** as strategy-requested hoisted members (see the member-hoisting requirement); a mapper whose strategies request no members SHALL declare no fields. Imports SHALL be managed by JavaPoet — no fully-qualified class references SHALL appear in the rendered source for any type that can be imported.

#### Scenario: Generated class for a trivial mapper with no finality options set

- **WHEN** the processor runs against an interface `package com.example; @Mapper public interface PersonMapper { Human map(Person person); }` whose graph is fully realised, with none of `percolate.classes.final`, `percolate.methods.final`, `percolate.parameters.final` set
- **THEN** the `Filer` receives a `JavaFile` whose package is `com.example` and whose top-level type is `PersonMapperImpl`
- **AND** the type is declared `public class PersonMapperImpl implements PersonMapper` (no `final` on the class)
- **AND** the type carries exactly the annotation `@Generated("io.github.joke.percolate")` (resolved to `javax.annotation.processing.Generated`)
- **AND** the type declares one public no-arg constructor with an empty body
- **AND** the type declares one method `@Override public Human map(Person person)` (no `final` on the method, no `final` on the parameter)
- **AND** the type declares no fields (no strategy requested a member)

#### Scenario: Imports are managed by JavaPoet

- **WHEN** the rendered source of a generated `<Name>Impl` is inspected
- **THEN** no `import` line declares a class from the same package as the generated type
- **AND** every reference to a class outside `java.lang` resolves through an `import` line or through a `ClassName`/`TypeName` JavaPoet construct
- **AND** no rendered source line contains the substring `java.lang.` (the JavaPoet-managed default import covers it)

#### Scenario: percolate.classes.final renders a final class

- **WHEN** the processor runs against `PersonMapper` with `-Apercolate.classes.final=true`
- **THEN** the generated type is declared `public final class PersonMapperImpl implements PersonMapper`

#### Scenario: percolate.methods.final renders final methods

- **WHEN** the processor runs against `PersonMapper` with `-Apercolate.methods.final=true`
- **THEN** the generated method is declared `@Override public final Human map(Person person)`

#### Scenario: percolate.parameters.final renders final parameters

- **WHEN** the processor runs against `PersonMapper` with `-Apercolate.parameters.final=true`
- **THEN** the generated method's parameter is declared `final Person person`

#### Scenario: The three finality switches compose independently

- **WHEN** the processor runs against `PersonMapper` with `-Apercolate.classes.final=true` and `-Apercolate.methods.final=true` but `-Apercolate.parameters.final` unset
- **THEN** the generated class is `final`, the generated method is `final`, and its parameter carries no `final` modifier

### Requirement: Strategy-requested class members are hoisted and deduplicated

When operations in the extracted plan declare member requests (see the `expansion-strategy-spi` capability), `GenerateStage` SHALL collect them during the same recursive plan walk that gathers hoisted locals, **deduplicate** them by their content dedup key across all method bodies of the generated type, allocate a unique class-scope name per distinct member (via a class-scoped `NameAllocator`, the sibling of the method-scoped local allocator), and emit each distinct member once on the generated type as a `private static final` field with the requested type and initializer. Each requesting operation's codegen SHALL reference the member by its allocated name through the same indirection used for a hoisted local, so the composer holds zero field syntax. Member collection SHALL mutate neither the `MapperGraph` nor the `ExtractedPlan`.

#### Scenario: Two methods sharing a formatter emit one field

- **WHEN** two mapper methods both parse with `@Map(format = "yyyy-MM-dd")` into `java.time` targets
- **THEN** the generated type declares exactly one `private static final DateTimeFormatter` field initialized with `DateTimeFormatter.ofPattern("yyyy-MM-dd")`
- **AND** both method bodies reference that single field

#### Scenario: Distinct patterns emit distinct fields

- **WHEN** one method uses `@Map(format = "yyyy-MM-dd")` and another uses `@Map(format = "dd.MM.yyyy")`, both into `java.time` targets
- **THEN** the generated type declares two distinct `private static final DateTimeFormatter` fields with distinct names

#### Scenario: An inline production requests no field

- **WHEN** a mapper method formats a `java.util.Date` with a per-call `SimpleDateFormat`
- **THEN** the generated type declares no `SimpleDateFormat` field and the method body constructs it inline

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

`GenerateStage` (and any phase it invokes) SHALL NOT call any append method on `MapperGraph` (`apply(AddValue)`, `apply(AddOperation)`, `valueFor`) or any other method that adds vertices or edges. The stage's view of the graph SHALL be obtained through read-only accessors (`graph.values()`, `graph.operations()`, `graph.deps()`, `graph.bipartiteView()`) and the extracted plan.

This invariant exists so that the `.dot` debug outputs produced by `DumpFullGraphStage` / `DumpTransformsStage` / `DumpPlanStage` faithfully reflect the graph that `GenerateStage` consumed. Future graph-modifying stages (e.g., an optimisation pass) SHALL be ordered before the dump stages in the pipeline so the same invariant holds.

#### Scenario: No mutating call sites in generate

- **WHEN** the source of every class under `processor/src/main/java/io/github/joke/percolate/processor/stages/generate/` is inspected
- **THEN** no source line invokes `MapperGraph#apply`, `MapperGraph#valueFor`, or any other graph-append method

#### Scenario: Dump precedes generate in the pipeline

- **WHEN** the ordered `List<Stage>` provided by `ProcessorModule` is inspected
- **THEN** every graph-dumping stage (`DumpFullGraphStage`, `DumpTransformsStage`, `DumpPlanStage`, plus any future `Dump*Stage`) appears strictly before `GenerateStage`

### Requirement: IncomingValues runtime implementation

The processor module SHALL ship one package-private `IncomingValuesImpl` under `io.github.joke.percolate.processor.stages.generate`, implementing the SPI `IncomingValues` interface. There SHALL be no `VarNames` implementation (the `VarNames` type has been removed from the codegen surface).

`IncomingValuesImpl` SHALL be constructed with an indexed `List<CodeBlock>` (for `byGroupPosition(int)`) and a `Map<String, CodeBlock>` (for `byName(String)` and `single()`). `single()` SHALL return the sole positional entry when the list has size 1; it MAY throw `IllegalStateException` if invoked when there is more than one positional entry.

#### Scenario: IncomingValues delegates by name and position

- **WHEN** an `IncomingValuesImpl` instance is constructed with `byName = {"firstName" → CB1, "lastName" → CB2}` and positional list `[CB1, CB2]`
- **THEN** `byName("firstName")` returns `CB1`
- **AND** `byName("lastName")` returns `CB2`
- **AND** `byGroupPosition(0)` returns `CB1`
- **AND** `byGroupPosition(1)` returns `CB2`

#### Scenario: IncomingValues single() returns the only positional entry

- **WHEN** an `IncomingValuesImpl` instance is constructed with a single-entry positional list `[CB]`
- **THEN** `single()` returns `CB`

#### Scenario: Codegen renders with only IncomingValues

- **WHEN** `BuildMethodBodies` invokes a producer's `OperationCodegen.render(...)`
- **THEN** the sole argument is an `IncomingValues` instance and no `VarNames` argument is passed

### Requirement: Method bodies render by walking the extracted plan

`BuildMethodBodies` SHALL compose each method body by walking the extracted plan view
(`plan-extraction`) from the method's **seeded return-root `Value`** (the graph-recorded return root
per `graph-expansion` — the one Value matching the declared return type, never an over-emitted typed
sibling at the return location): render the Value's chosen producer `Operation` by invoking its codegen
with `IncomingValues` keyed by **port name**, where each incoming value is the recursively materialised
port `Value` — a **variable reference** when that `Value` is hoisted to a local (see "Assembly
arguments hoist to local variables"), otherwise its inline expression. A **direct container-return**
method SHALL render from this root exactly like any other: the root's producer is the container
`collect`/`wrap` Operation and the per-element transform is its child scope. Producer identity is
structural — the generator SHALL NOT infer it from shared codegen instances, edge labels, or any
grouping label, and SHALL NOT read `Nullability` to decide wiring.

#### Scenario: Fan-in renders from the chosen producer
- **WHEN** the return-root's chosen producer is `new Address(int,String)` with ports `number`,
  `street`
- **THEN** the body renders that Operation's codegen once, with incoming values keyed `number` and
  `street`

#### Scenario: No group or label reads
- **WHEN** the source of `BuildMethodBodies` is inspected
- **THEN** it references no grouping label, no group id, and no edge-carried consumer slot

#### Scenario: A direct container-return body renders from the seeded root
- **WHEN** `List<DAO> mapAddresses(Set<DTO>)` has a plan rooted at the seeded `List<DAO>` Value whose
  producer is `collect` over a `map` delegating to `mapAddress`
- **THEN** the body renders `return src.stream().map(e -> mapAddress(e)).collect(...)`, selecting the
  seeded root and ignoring dead typed siblings at the return location

### Requirement: Assembly arguments hoist to local variables

The generate stage SHALL materialise a plan `Value` as a local variable declaration
(`<Type> <name> = <expr>;`), emitted before the statement that uses it and referenced by `<name>`
at each use site, **iff** that `Value` feeds a port of an **n-ary** `Operation`
(`Operation.getPorts().size() >= 2`) — that is, the arguments of multi-argument constructor / assembly
calls become named locals. A `Value` whose only consumers are single-port Operations (container
`iterate` / `collect` / `flatMap` / `wrap` / `unwrap`, conversions, accessors, nullness crossings)
SHALL remain inline, so a fluent container pipeline still renders as one threaded chain (consistent
with "Stream stages render as a threaded pipeline").

A `Value` with **no chosen producer** — a bare parameter root or an element-lambda variable, which
already renders as a simple name — SHALL NOT be hoisted, even when it feeds an n-ary port; aliasing it
into `Person v = person;` would add only noise.

The method return-root `Value` SHALL render inline as the `return` expression — never as a trailing
temporary (`return new Human(street, first);`, not `Human v = ...; return v;`).

Local declarations within a scope SHALL be emitted in dependency (topological) order, so every
variable is declared before its first reference; the plan is acyclic, so a post-order walk of the
chosen-producer DAG yields such an order.

The hoist decision and variable naming SHALL be a **pure function of the extracted plan**, implemented
as a separable helper within the generate stage. It SHALL NOT mutate the `MapperGraph` or the
`ExtractedPlan`, SHALL NOT add a codegen intermediate representation, and SHALL be invisible to
strategies (a strategy still receives its operand `CodeBlock`s through `IncomingValues` and cannot
observe whether an operand is an inline expression or a variable reference).

#### Scenario: Constructor arguments become locals
- **WHEN** the chosen producer of `mapAddress`'s return-root is `new Human.Address(int number, String street)` (two ports)
- **THEN** the body declares a slot-named local for each argument (`int number = address.getNumber();` and `String street = address.getStreet();`) before `return new Human.Address(number, street);`

#### Scenario: Single-port chains stay inline
- **WHEN** the plan for a target is a container pipeline `wrap ⟵ collect ⟵ map ⟵ flatMap ⟵ iterate`, every stage single-port
- **THEN** the pipeline renders as one fluent chain with no intermediate locals between the stages
- **AND** only the chain's overall result is hoisted, and only when it feeds an n-ary assembly port

#### Scenario: Return-root renders inline
- **WHEN** the return-root's chosen producer is an n-ary constructor
- **THEN** the body's final statement is `return new <Type>(<arg references>);`, with no `<Type> v = ...; return v;` temporary

#### Scenario: Hoisting is invisible to strategies
- **WHEN** an n-ary Operation's `OperationCodegen.render(IncomingValues)` is invoked for a hoisted argument
- **THEN** the `IncomingValues` operand it reads is a variable-reference `CodeBlock`, and the strategy makes no distinction from an inline-expression operand

#### Scenario: A bare parameter argument is not aliased
- **WHEN** a two-arg producer `new Pair(Person, Person)` is fed directly by the parameter Values `person` and `person2` (no chosen producer)
- **THEN** the body renders `return new Pair(person, person2);` with no `Person v = person;` aliasing statements

### Requirement: A shared Value is materialised once

The generate stage SHALL render a `Value` consumed by more than one in-plan port exactly once — as a
single local variable referenced at each use site — rather than re-rendering (and so re-evaluating)
its producing expression per use. A shared `Value` SHALL be hoisted by virtue of being shared, even
when it would otherwise render inline. The generator SHALL NOT rely on accessor idempotency to excuse
duplicate evaluation.

#### Scenario: A value feeding two ports is emitted once
- **WHEN** one `Value` `name:String` feeds ports of two in-plan Operations
- **THEN** the generated code declares one local for it and references that local at both use sites, evaluating the producing expression exactly once

### Requirement: Hoisted locals are named after their slot

A hoisted local SHALL be named after the slot its `Value` materialises — the target field
(`TargetLocation`), the last source path segment (`SourceLocation`), or the element role
(`ElementLocation`) given by `Location.slotName()` — rather than an opaque counter (`v0`, `v1`). A
container lambda parameter SHALL be named after its element type. When `slotName()` is empty the local
SHALL fall back to a stable default (`value`); when the element type is unavailable the lambda
parameter SHALL fall back to `element`.

Names SHALL be made unique within the method body so that no local shadows a method parameter, no two
declarations collide, and reserved words are sanitised — colliding names get a numeric suffix. The
method's parameter names SHALL be reserved before any local is named.

#### Scenario: A constructor argument is named after its target field
- **WHEN** the argument `Value`s of `new Thing(String status, int count)` are hoisted
- **THEN** the locals are `String status = ...;` and `int count = ...;`, not `v0` / `v1`

#### Scenario: A shared source value is named after its source segment
- **WHEN** the source `Value` `person.name` is shared by two target slots and hoisted once
- **THEN** the local is `String name = person.getName();` (the last path segment), referenced at both sites

#### Scenario: A name that would shadow a parameter is uniquified
- **WHEN** a slot name equals a method parameter name already in scope
- **THEN** the local is given a suffixed unique name so it does not shadow the parameter

### Requirement: Local declaration style is configurable

The generate stage SHALL render hoisted local declarations according to two independent compile-time
processor options, both defaulting to off, and both advertised by `getSupportedOptions()`:

- `percolate.locals.final` — when `true`, each hoisted local is declared `final`.
- `percolate.locals.var` — when `true`, each hoisted local is declared with `var` in place of its
  explicit type.

The two options SHALL compose (`final var <name> = <expr>;`). Neither option SHALL change *which*
`Value`s hoist, the declaration order, or the chosen names — only the declaration syntax. The style
SHALL be invisible to strategies (a strategy still receives operand `CodeBlock`s through
`IncomingValues` and cannot observe the declaration syntax of any local).

#### Scenario: Default style is an explicit-type, non-final declaration
- **WHEN** neither option is set
- **THEN** a hoisted local renders as `String first = person.getFirst();`

#### Scenario: percolate.locals.final prefixes final
- **WHEN** `percolate.locals.final=true`
- **THEN** a hoisted local renders as `final String first = person.getFirst();`

#### Scenario: percolate.locals.var uses var
- **WHEN** `percolate.locals.var=true`
- **THEN** a hoisted local renders as `var first = person.getFirst();`

#### Scenario: Both options compose
- **WHEN** both `percolate.locals.final=true` and `percolate.locals.var=true`
- **THEN** a hoisted local renders as `final var first = person.getFirst();`

### Requirement: Child scopes render as lambda bodies

A scope-owning `Operation` in the plan SHALL render its child scope's extracted plan as the
per-element lambda body: the lambda **parameter name** SHALL come from the child scope's **element input
declaration** (its `ElementLocation` slot / element type), **independent of whether an element source
`Value` was materialised** — so an element that the child plan never sources still binds its lambda
parameter. The child return-root expression is the lambda result, and the owning Operation's codegen
weaves the container operation (`map`/`flatMap`/`mapPresence`) around it. Lambda-parameter naming is
otherwise unchanged (named after the element type, falling back to `element` when the type is
unavailable; made unique within the method body).

#### Scenario: Stream element mapping renders a lambda
- **WHEN** the plan contains a `map` Operation for `Stream<A> → Stream<B>` owning a child scope
- **THEN** the generated body contains the stream operation applied with a lambda whose body is
  the rendered child plan

#### Scenario: An unused element still binds its lambda parameter
- **WHEN** the plan contains a scope-owning Operation whose child plan maps each element to a value that does not reference the element
- **THEN** the generated lambda still declares its parameter, named from the element input declaration (e.g. `stream.map(element -> <constant>)`), even though no element param-root `Value` was materialised

### Requirement: Stream stages render as a threaded pipeline

Each plain container Operation in the plan (`iterate`, `collect`, `wrap`, `unwrap`) SHALL render by
threading its single container snippet onto the rendered expression of its operand, so a chain of such
Operations composes into one fluent pipeline expression. The generator SHALL NOT fuse
`iterate`/`map`/`collect` into a single Operation's codegen.

The threaded pipeline expression SHALL carry a soft wrap point (JavaPoet `$Z`, a zero-width space)
immediately before each chained call's leading `.`, so that once the fully-threaded expression exceeds
JavaPoet's column limit it wraps at a call boundary onto a continuation line, rather than rendering as
one unbroken line. Below the column limit the wrap point SHALL have no visible effect — the rendered
text SHALL be identical to today's unwrapped form. This is a purely textual property of each
Operation's own codegen snippet; the composer (`BuildMethodBodies`) SHALL NOT itself insert, remove, or
otherwise manage wrap points — it continues to only thread operand expressions together.

#### Scenario: Cross-kind pipeline threads stage by stage
- **WHEN** the plan for `List<Optional<A>> → Optional<Set<B>>` is
  `wrap ⟵ collect ⟵ map ⟵ flatMap ⟵ iterate`
- **THEN** the rendered expression is a single chain
  `Optional.ofNullable(src.stream().flatMap(…).map(…).collect(…))`, each stage's snippet threaded onto
  its operand

#### Scenario: A short pipeline renders unwrapped
- **WHEN** the fully-threaded pipeline expression for a method body fits within JavaPoet's column limit
- **THEN** the generated source renders it on one line, identical to its pre-wrap-point rendering (no
  stray whitespace introduced by the wrap point)

#### Scenario: A long pipeline wraps at a call boundary
- **WHEN** the fully-threaded pipeline expression for a method body would exceed JavaPoet's column limit
  on one line
- **THEN** the generated source breaks the line immediately before a chained call's `.`, continuing on
  an indented line, rather than emitting one line longer than the column limit

### Requirement: Any chain-continuation codegen rendering carries a wrap point

Every first-party `OperationCodegen`/`ScopeCodegen` rendering SHALL carry a `$Z` immediately before the
leading `.` of any call it chains onto a rendered operand (its format string appends `.methodName(...)`
after the spliced-in operand) — not only container/stream pipelines, but any capability's strategy.
This covers, without limitation: the nullness `[coalesce]` crossing's `Optional.orElse(D)` form, the
accessor path resolvers (`GetterPathResolver`, `MethodPathResolver`, `FieldPathResolver`),
`MethodCallBridge`'s method-call rendering, `PrimitiveWrapperConversion`'s unbox accessor, and the
`reactor`/`reactor-blocking` scalar bridges (`CollectList`, `FluxSingle`, `SingleOptional`, and the
blocking crossings). A rendering that prepends rather than chains (a cast `"($T) $L"`, a bare `"this"`,
a `wrap`-style `"$T.of($L)"`) has no leading `.` to mark and is unaffected. This requirement governs
rendered-text composition uniformly; it does not change any of those other capabilities' matching,
weighting, or port semantics.

#### Scenario: A nullness coalesce crossing carries a wrap point
- **WHEN** the `[coalesce]` Operation's `Optional<T> → T` form is rendered
- **THEN** its `orElse(...)` call's leading `.` carries a `$Z`, so a long coalesce expression composed
  with other operand expressions wraps at that boundary rather than overflowing one line

#### Scenario: An accessor path segment carries a wrap point
- **WHEN** `GetterPathResolver`, `MethodPathResolver`, or `FieldPathResolver` renders its accessor call
  or field reference onto the parent operand
- **THEN** the leading `.` of that accessor carries a `$Z`, so a long source-path descent composed of
  several accessor segments wraps at a segment boundary rather than overflowing one line

### Requirement: Nullness handling renders as ordinary Operations

Code generation SHALL contain no nullability weaving: `[requireNonNull]` and `[coalesce]` are plan
Operations rendered through the same codegen contract as any other Operation. The generated output
for a `NULLABLE → NON_NULL` crossing remains `java.util.Objects.requireNonNull(expr, message)` with
the existing message format, or the coalescing form when the binding declares a default.

#### Scenario: Crossing renders via its Operation
- **WHEN** a nullable source feeds a non-null port without a default
- **THEN** the rendered expression is produced by the `[requireNonNull]` Operation's codegen, not by
  generator-side wrapping

### Requirement: Documentation include tags are emitted under an opt-in option

When the `docTags` processor option is enabled, the generator SHALL bracket each generated **method as a
whole** — from its leading annotations/signature through its closing brace — with AsciiDoc include-tag
comments (`// tag::<methodName>[]` before the method, `// end::<methodName>[]` after the closing brace), so a
documentation build can `include::` that method's *complete* generated source by tag (signature and body, not
the body alone). When the option is absent (the default), the generator SHALL emit no such comments, leaving
generated source byte-for-byte as it is today — code a consumer compiles SHALL never carry documentation tags
unless the consumer opts in. Tag emission SHALL NOT alter the generated code's behaviour; it adds only comment
lines outside the method, which an AsciiDoc `include::` strips from the rendered snippet.

#### Scenario: Whole-method tags are emitted only with the option on
- **WHEN** a mapper is generated with the `docTags` option enabled
- **THEN** each generated method is bracketed by a `// tag::<methodName>[]` line preceding its annotations/signature and a `// end::<methodName>[]` line following its closing brace
- **AND** an `include::[tag=<methodName>]` of that source yields the complete method including its signature

#### Scenario: Default output carries no tags
- **WHEN** a mapper is generated without the `docTags` option
- **THEN** the generated source contains no documentation tag comments and is identical to the pre-option
  output

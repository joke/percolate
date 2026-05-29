# Code Generation Spec

## Purpose

`GenerateStage` is the final pipeline stage. It walks the realised subgraph each earlier stage has produced and emits a `<Mapper>Impl` Java source file via JavaPoet, one per fully-realised `@Mapper`-annotated type. The stage is read-only with respect to `MapperGraph` and skips entire mappers on validation errors or exceptions so the processor never ships broken code. This spec pins the class shape, body composition algorithm, failure policy, and SPI implementations that downstream slices will additively extend.

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

### Requirement: Method body composition algorithm

For each abstract method `m` on a `@Mapper` interface `<Name>`, `BuildMethodBodies` SHALL produce a `CodeBlock` representing the method body by recursing over `m`'s **plan view** — the subgraph returned by `MapperContext.getGraph().planView()` scoped to `m`'s `MethodScope`. The plan view contains only the edges of the chosen plan: `REALISED` edges that belong to a `SAT`-outcome group, with multi-fire OR-choices resolved to the cheapest branch (see the `graph-debug-output` capability's PlanView requirement). Because the plan view contains only the chosen plan, **every node in it has exactly one producer** (one inbound plan edge, or one producing group); `BuildMethodBodies` SHALL NOT guess among sibling groups or sibling inbound edges.

The recursion `render(node)` SHALL behave as follows:

1. **Leaf base case** — if `node` has no inbound plan edge, then `node` MUST be a `SourceLocation` whose `path.first()` matches the simple name of one of `m`'s parameters. The render result is `CodeBlock.of("$N", parameter.getSimpleName())`.
2. **Single-edge inductive case** — if `node` has exactly one inbound plan edge `e`, recurse on `e.getFrom()` to produce a child `CodeBlock`, then return `e.getCodegen().get().render(varNames, IncomingValues.of(childCodeBlock))` where `IncomingValues.of(...)` is the `IncomingValues` implementation backed by `single() = childCodeBlock`.
3. **Group-target inductive case** — if `node` carries (or is the root of) a group with a `GroupCodegen`, render each slot's incoming child `CodeBlock` (recursing on each predecessor), wrap them in an `IncomingValues` implementation whose `byName(slotName)` returns the matching child, and return `groupCodegen.render(varNames, incomingValues)`. Within the plan view at most one group is rooted at `node`.

When deriving the `slotName` key for a group slot in the group-target inductive case, `BuildMethodBodies` SHALL apply this rule on the slot Node's `Location`:

- If the `Location` is a `TargetLocation` with a non-empty path, the slot name is the last path segment.
- If the `Location` is a `SourceLocation` with a non-empty path (a container-unwrap group's source-side slot, e.g. `src[person.addresses]`), the slot name is the last path segment.
- If the `Location` is an `ElementLocation` (a container sub-group's element slot), the slot name is the location's `role` (e.g. `"element"`; `"key"` / `"value"` for map-shaped containers).
- Otherwise `BuildMethodBodies` SHALL raise an `IllegalStateException` naming the offending node.

The slot name is a stable per-slot map key. Container `GroupCodegen`s (`ListCollect`, `SetCollect`, `ArrayCollect`, `OptionalCollect`, the `*Wrap` family, …) read their input positionally via `inputs.single()` and do not depend on the key value; the rule's only obligation for them is to be total (never throw) and unique per slot. The `role` value satisfies both and stays compatible with name-based access (`inputs.byName("key")`) for multi-axis containers.

The full method body SHALL be `CodeBlock.builder().addStatement("return $L", renderedRoot).build()` for non-void methods. (Void methods are out of scope for slice 1.)

A `MethodImpl` value record holding `(ExecutableElement method, CodeBlock body, Set<TypeElement> requiredMapperDeps)` SHALL be produced per abstract method, where `requiredMapperDeps` is the empty set in slice 1.

#### Scenario: Leaf parameter renders by parameter name

- **WHEN** the plan view for method `Human map(Person person)` is walked and the leaf is the source node `src[person]:Person`
- **THEN** the rendered `CodeBlock` for that leaf is equivalent to `CodeBlock.of("$N", "person")`

#### Scenario: DirectAssign + single-segment path renders into a direct invocation

- **WHEN** the plan view for `Human map(Person person)` contains a path-segment plan edge `src[person] → src[person.firstName]:String` produced by `GetterPathResolver`, followed by a `DirectAssign` plan edge `src[person.firstName] → tgt[firstName]`
- **THEN** the rendered `CodeBlock` for `tgt[firstName]` is equivalent to `CodeBlock.of("$N.getFirstName()", "person")`

#### Scenario: ConstructorCall group assembles slot children

- **WHEN** the plan view for `Human map(Person person)` has a root group target whose `GroupCodegen` is `ConstructorCall`'s `new Human($L, $L)`, with two slots `firstName` and `lastName` each fed by a single-segment path
- **THEN** the rendered method body is equivalent to `return new Human(person.getFirstName(), person.getLastName());`

#### Scenario: Container group slot named by element role

- **WHEN** the plan view for a method contains a container group whose root is a `List`-typed (or `Set`/`Array`/`Optional`-typed) `TargetLocation` node and whose single slot is an `ElementLocation` element node
- **THEN** `BuildMethodBodies` derives the slot name as the `ElementLocation` role (`"element"`)
- **AND** renders the group via its `GroupCodegen` without raising `cannot derive slot name from node`

#### Scenario: Nested container groups compose without slot-name failure

- **WHEN** the plan view for a method has a target chain through nested containers — e.g. `tgt[addresses]:Optional<Set<Address>>` produced through an `OptionalCollect` group whose element slot is itself the root of a `SetCollect` group with an `ElementLocation` element slot
- **THEN** `render` derives a slot name for every `ElementLocation` slot in the nested chain
- **AND** the method body is produced without any `IllegalStateException`

#### Scenario: Dead multi-fire sibling is not rendered

- **WHEN** a target node has two sibling producing groups — one `SAT` (reaches a source parameter) and one `UNSAT_NO_PLAN` (a dead branch, e.g. an inverse-bridge match) — and the method is rendered
- **THEN** `BuildMethodBodies` renders the `SAT` group's branch
- **AND** never descends into the `UNSAT_NO_PLAN` branch (no `leaf node is not a SourceLocation` failure)

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

- **WHEN** `BuildMethodBodies` invokes any `EdgeCodegen.render(varNames, …)` or `GroupCodegen.render(varNames, …)`
- **THEN** the `varNames` argument is a non-null `VarNames` instance

### Requirement: Slice 1 scope — single-segment paths and ConstructorCall-style group targets

This change SHALL realise code generation for mappers whose realised subgraph uses only:
- `DirectAssign`-style bridges,
- single-segment source paths resolved via `PathSegmentResolver`s (path strings without a `.`, or whose entire path is one segment beyond a parameter),
- `ConstructorCall`-style group targets that assemble a constructor invocation.

Mappers whose realised subgraph requires multi-segment paths beyond a single appended segment, container scope transitions (`*Unwrap` / `*Collect` pairs), or nested mapper composition are OUT OF SCOPE for this slice. When such a mapper is encountered, the realised subgraph MAY still pass `BuildMethodBodies`' algorithm correctly because the algorithm's recursion is uniform; however, this spec does not pin the resulting `CodeBlock` shape for those out-of-scope cases.

#### Scenario: A trivial Person → Human mapper compiles

- **WHEN** the processor runs against a Java 11 fixture `@Mapper interface PersonMapper { @Map(target = "firstName", source = "person.firstName") Human map(Person person); }` where `Person` and `Human` are plain final classes each exposing a public single-arg constructor `(String firstName)` and a `getFirstName()` accessor
- **THEN** `Filer` receives a `PersonMapperImpl.java` source file
- **AND** that file compiles without errors via `com.google.testing.compile`
- **AND** the generated `map` body is exactly `return new Human(person.getFirstName());`

Implicit name-matching (deriving directives from getter/parameter name agreement, with no `@Map` annotation) is OUT OF SCOPE for this slice. Without an explicit `@Map` directive, the realised subgraph has no producer for the target slot and no class is emitted.

### Requirement: Nullability-aware slot wiring at GroupTarget composition

When `BuildMethodBodies` assembles the expressions feeding a group target's slots (the third inductive case of the method body composition algorithm), it SHALL compare the slot Node's producer-stamped nullability against the consumer contract derived on demand from the slot's underlying `Slot.producedFrom` `AnnotatedConstruct`. The comparison drives one of three emission patterns:

1. **`NULLABLE → NON_NULL` (producer commits nullable, consumer accepts only non-null)**: wrap the slot's expression in `java.util.Objects.requireNonNull(expr, msg)` where `msg` is a string literal identifying both the source path and the target slot name, e.g. `"source 'person.address' is null but target slot 'address' is non-null"`.
2. **`NULLABLE → NULLABLE`**: emit a null-safe propagation chain so that a null at the source produces a null at the target without intermediate NPEs. The chain shape is a code-generation detail (see "Null-safe propagation form").
3. **All other combinations** (`NON_NULL → *`, `UNKNOWN → *`, `* → UNKNOWN`): emit the slot's expression unchanged from today's behaviour. No guard, no propagation wrapper.

The wrapped/unwrapped slot expression SHALL be passed to `GroupCodegen.render(varNames, incomingValues)` exactly as today; the codegen lambda SHALL NOT see or reason about nullability.

The producer-stamped nullability is read from `slotNode.getNullability().orElseThrow()` — for a slot reached by `BuildMethodBodies`, the realised subgraph guarantees the slot is typed (and therefore nullability-stamped). The consumer contract is computed via `NullabilityResolver.resolve(slot.getProducedFrom().asType() | similar, slot.getProducedFrom())` — `BuildMethodBodies` SHALL inject `NullabilityResolver` via Dagger and call it on demand.

`BuildMethodBodies` SHALL NOT emit a guard or propagation wrapper for slots reached via single-edge paths where the slot is itself the inductive root (the slot expression `is` the final expression of the chain — its guard/propagation has already been applied at the GroupTarget composition site that owns it).

#### Scenario: Nullable source feeding a non-null target slot emits requireNonNull
- **WHEN** `BuildMethodBodies` walks a slot whose producer-stamped `nullability` is `NULLABLE`
- **AND** the slot's `producedFrom` element resolves to `NON_NULL` via `NullabilityResolver`
- **THEN** the rendered slot expression is wrapped: `Objects.requireNonNull(<expr>, "<msg>")`
- **AND** the `<msg>` string identifies both the source path and the target slot name

#### Scenario: Nullable source feeding a nullable target slot emits null-safe propagation
- **WHEN** `BuildMethodBodies` walks a slot whose producer-stamped `nullability` is `NULLABLE`
- **AND** the slot's `producedFrom` element resolves to `NULLABLE` via `NullabilityResolver`
- **THEN** the rendered slot expression is a null-safe chain (form per "Null-safe propagation form")
- **AND** no `requireNonNull` is emitted

#### Scenario: Non-null producer for a non-null target slot is unchanged
- **WHEN** `BuildMethodBodies` walks a slot whose producer-stamped `nullability` is `NON_NULL`
- **AND** the slot's `producedFrom` element resolves to `NON_NULL`
- **THEN** the rendered slot expression matches today's emission exactly — no guard wrapper added

#### Scenario: UNKNOWN source for any target slot is unchanged
- **WHEN** `BuildMethodBodies` walks a slot whose producer-stamped `nullability` is `UNKNOWN`
- **THEN** the rendered slot expression matches today's emission exactly — no guard wrapper added
- **AND** no NullAway-style strictness is applied

#### Scenario: Guard message identifies source and target
- **WHEN** a `Objects.requireNonNull` guard is emitted for slot `address` whose source path is `person.contact.address`
- **THEN** the rendered message string contains both `"person.contact.address"` and `"address"` (or the equivalent identifier the spec pins in implementation)

### Requirement: Null-safe propagation form

The form of the null-safe propagation chain emitted in the `NULLABLE → NULLABLE` case (see "Nullability-aware slot wiring at GroupTarget composition") SHALL be one of:

- A nested ternary chain `expr == null ? null : expr.next()` composed over each nullable hop in the path, OR
- An equivalent `Optional.ofNullable(expr).map(...).orElse(null)` chain.

The specific choice between the two forms is an implementation detail of `BuildMethodBodies`; this spec pins the *behaviour* (null at any nullable hop produces null at the target slot without intermediate NPEs) but not the syntax. Tests SHALL assert the runtime behaviour rather than the source-level form.

#### Scenario: Null at any nullable hop propagates without NPE
- **WHEN** a generated mapper assigns a target slot fed by a multi-hop nullable chain `a.b.c` where any of `a`, `b`, or `c` may return null
- **AND** at runtime one of those hops returns null
- **THEN** the generated code stores `null` in the target slot
- **AND** does not throw `NullPointerException`

### Requirement: BuildMethodBodies injects NullabilityResolver

`BuildMethodBodies` (the phase under `processor/src/main/java/io/github/joke/percolate/processor/stages/generate/`) SHALL declare a constructor-injected `NullabilityResolver` field via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`. The resolver SHALL be invoked exactly when consumer-contract derivation is required (the slot-wiring case above) — never for un-annotated or non-decision-bearing call sites.

#### Scenario: BuildMethodBodies has a NullabilityResolver dependency
- **WHEN** the source of `BuildMethodBodies` is inspected
- **THEN** it declares a `private final NullabilityResolver` field
- **AND** the field is constructor-injected via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`

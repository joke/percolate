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

For each abstract method `m` on a `@Mapper` interface `<Name>`, `BuildMethodBodies` SHALL produce a `CodeBlock` representing the method body by recursing over `m`'s **plan view** — the subgraph returned by `MapperContext.getGraph().planView()` scoped to `m`'s `MethodScope`. The plan view contains only the edges of the chosen plan: `REALISED` edges that belong to a `SAT`-outcome demand, with multi-fire OR-choices resolved to the cheapest branch. Because the plan view contains only the chosen plan, **every node in it has exactly one producer**; `BuildMethodBodies` SHALL NOT guess among siblings.

**`BuildMethodBodies` SHALL traverse only `Node`s and `Edge`s of the plan view. It SHALL NOT read any `ExpansionGroup`** — no `group.getRoot()`, `group.getSlots()`, `group.getCodegen()`, `group.consumerContractFor(...)`, nor any group-by-root index. The n-ary producer (constructor / multi-arg method) is reconstructed from the output node's **fan-in**: its incoming REALISED plan edges, which share a producer.

The recursion `render(node)` SHALL return both a `CodeBlock` and a boolean **`isStream`** — whether the rendered expression is an open element stream — and SHALL behave as follows, dispatching on the node's inbound plan edges:

1. **Leaf base case** — if `node` has no inbound plan edge, then `node` MUST be a `SourceLocation` whose `path.first()` matches the simple name of one of `m`'s parameters. The result is `CodeBlock.of("$N", parameter.getSimpleName())`, `isStream = false`.
2. **Scalar single-edge case** — if `node`'s single inbound plan edge `e` carries a scalar `EdgeCodegen`, recurse on `e.getFrom()` and return `e.getCodegen().render(varNames, IncomingValues.of(child))`. When the child is an open stream this scalar transform SHALL be applied per element (`mapElements`, via the stream's container handle) rather than to the stream as a whole; otherwise it is applied inline. The result preserves the child's `isStream`.
3. **Container single-edge case** — if `node`'s single inbound plan edge `e` carries a **container provider + operation** (see the `graph-model` Edge requirement), recurse on `e.getFrom()` and weave per the container-codegen weaving rules (see the `container-codegen-spi` capability): an `ENTERING` sequence opens a stream via the provider's `iterate`; an `ENTERING` wrapper drops empties via `flatMapElements` when the child is a stream, or renders a top-level `unwrap` otherwise; an `EXITING` hop closes the stream via `collect`. The single-element `wrap` (`PRESERVING`) is a scalar `EdgeCodegen` step rendered by case 2, not a container-provider edge. Every container `CodeBlock` SHALL come from the provider's handle; `BuildMethodBodies` SHALL hardcode no container syntax.
4. **Assembly fan-in case** — if `node` has **multiple** inbound plan edges (or a single inbound edge carrying an n-ary producer `Codegen`) that share a producer, render each operand child and apply the **edge-carried** producer `Codegen`. The operands are the `from` nodes of `node`'s incoming REALISED plan edges; each operand is keyed by the slot-name rule below and the producer is rendered once via `producerCodegen.render(varNames, incomingValues)`, `isStream = false`. A **single-operand** producer whose operand renders as an open element stream (a scalar bridge such as a conversion or method call) SHALL apply the producer codegen **per element** — `mapElements(operandStream, v, producerCodegen.render(single = v))` via the threaded stream handle — and preserve `isStream = true`; otherwise it renders inline with `isStream = false`. The container provider hops (iterate/collect/unwrap) are **not** rendered here — they are single-edge container cases (case 3) intercepted before the fan-in lookup.

When deriving the `slotName` key for an operand, `BuildMethodBodies` SHALL apply this rule on the operand Node's `Location`: a `TargetLocation`/`SourceLocation` with a non-empty path → its last segment; an `ElementLocation` → its `role`; otherwise raise an `IllegalStateException` naming the node.

The full method body SHALL be `CodeBlock.builder().addStatement("return $L", renderedRoot).build()` for non-void methods. A `MethodImpl` value record holding `(ExecutableElement method, CodeBlock body, Set<TypeElement> requiredMapperDeps)` SHALL be produced per abstract method, where `requiredMapperDeps` is the empty set in this change.

`BuildMethodBodies` SHALL NOT contain any literal container syntax (`stream`/`collect`/`map`/`ofNullable`/`orElse`/…); all such fragments originate from container handles.

#### Scenario: Generator reads no ExpansionGroup

- **WHEN** the source of `BuildMethodBodies` (and the composer it uses) is inspected
- **THEN** it contains no reference to `ExpansionGroup`, `getCodegen`, `getSlots`, or a group-by-root index
- **AND** every code fragment is derived from `Node` and `Edge` of the plan view

#### Scenario: Leaf parameter renders by parameter name

- **WHEN** the plan view for `Human map(Person person)` is walked and the leaf is `src[person]:Person`
- **THEN** the rendered `CodeBlock` is equivalent to `CodeBlock.of("$N", "person")` with `isStream = false`

#### Scenario: DirectAssign + single-segment path renders into a direct invocation

- **WHEN** the plan view contains `src[person] → src[person.firstName]` (GetterPathResolver) then a `DirectAssign` edge into `tgt[firstName]`
- **THEN** the rendered `CodeBlock` for `tgt[firstName]` is equivalent to `CodeBlock.of("$N.getFirstName()", "person")`

#### Scenario: Constructor fan-in assembles operand children

- **WHEN** the plan view has an output node `tgt[]:Human` with two incoming REALISED operand edges from `firstName` and `lastName`, each fed by a single-segment path, sharing a `ConstructorCall` producer `new Human($L, $L)`
- **THEN** the rendered body is equivalent to `return new Human(person.getFirstName(), person.getLastName());`
- **AND** the producer codegen is taken from the operand edges, not from any group

#### Scenario: Single-operand scalar producer over a stream maps per element

- **WHEN** a single-operand producer whose codegen is a scalar conversion (e.g. a method call) has its operand fed by an open element stream
- **THEN** the producer codegen is applied per element via the stream handle's `mapElements`, and the result stays a stream (`isStream = true`)

#### Scenario: Sequence container target weaves via the container handle

- **WHEN** the plan view threads a source `List<Optional<Address>>` through an iterate hop, an `Optional` element hop, an element map, and a `Set` collect, into an outer `Optional` wrap
- **THEN** the emitted body opens the list stream, `flatMap`s the optional element (dropping empties), maps the element, collects into the set, and wraps in the outer optional
- **AND** every `stream`/`flatMap`/`collect`/`ofNullable` fragment originates from a container handle, not from `BuildMethodBodies`

#### Scenario: Composer holds no container syntax

- **WHEN** the source of `BuildMethodBodies` (and the composer it uses) is inspected
- **THEN** it contains no literal container-syntax template; container fragments are obtained from `ContainerCodegen`/`WrapperCodegen` handles off the edges

#### Scenario: Dead multi-fire sibling is not rendered

- **WHEN** a target node has two sibling producers — one `SAT` and one `UNSAT_NO_PLAN` — and the method is rendered
- **THEN** `BuildMethodBodies` renders the `SAT` branch and never descends into the `UNSAT_NO_PLAN` branch

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

### Requirement: Slice 1 scope — single-segment paths and ConstructorCall-style group targets

This change SHALL realise code generation for mappers whose realised subgraph uses only:
- `DirectAssign`-style bridges,
- single-segment source paths resolved via the path-resolver strategies (path strings without a `.`, or whose entire path is one segment beyond a parameter),
- `ConstructorCall`-style group targets that assemble a constructor invocation.

Mappers whose realised subgraph requires multi-segment paths beyond a single appended segment, container scope transitions (`*Unwrap` / `*Collect` pairs), or nested mapper composition are OUT OF SCOPE for this slice. When such a mapper is encountered, the realised subgraph MAY still pass `BuildMethodBodies`' algorithm correctly because the algorithm's recursion is uniform; however, this spec does not pin the resulting `CodeBlock` shape for those out-of-scope cases.

#### Scenario: A trivial Person → Human mapper compiles

- **WHEN** the processor runs against a Java 11 fixture `@Mapper interface PersonMapper { @Map(target = "firstName", source = "person.firstName") Human map(Person person); }` where `Person` and `Human` are plain final classes each exposing a public single-arg constructor `(String firstName)` and a `getFirstName()` accessor
- **THEN** `Filer` receives a `PersonMapperImpl.java` source file
- **AND** that file compiles without errors via `com.google.testing.compile`
- **AND** the generated `map` body is exactly `return new Human(person.getFirstName());`

Implicit name-matching (deriving directives from getter/parameter name agreement, with no `@Map` annotation) is OUT OF SCOPE for this slice. Without an explicit `@Map` directive, the realised subgraph has no producer for the target slot and no class is emitted.

### Requirement: Nullability-aware slot wiring at GroupTarget composition

When `BuildMethodBodies` assembles the expressions feeding an n-ary producer's operands (the assembly fan-in case of the method body composition algorithm), it SHALL compare the operand Node's producer-stamped nullability against the consumer contract derived on demand from the **operand edge's** `Slot.producedFrom` `AnnotatedConstruct`. The comparison drives one of three emission patterns:

1. **`NULLABLE → NON_NULL` (producer commits nullable, consumer accepts only non-null)**: wrap the operand's expression in `java.util.Objects.requireNonNull(expr, msg)` where `msg` is a string literal identifying both the source path and the target slot name, e.g. `"source 'person.address' is null but target slot 'address' is non-null"`.
2. **`NULLABLE → NULLABLE`**: emit a null-safe propagation chain so that a null at the source produces a null at the target without intermediate NPEs. The chain shape is a code-generation detail (see "Null-safe propagation form").
3. **All other combinations** (`NON_NULL → *`, `UNKNOWN → *`, `* → UNKNOWN`): emit the operand's expression unchanged. No guard, no propagation wrapper.

The wrapped/unwrapped operand expression SHALL be passed to the producer `Codegen.render(varNames, incomingValues)` exactly as before; the codegen lambda SHALL NOT see or reason about nullability.

The producer-stamped nullability is read from `operandNode.getNullability().orElseThrow()` — for an operand reached by `BuildMethodBodies`, the plan view guarantees the node is typed (and therefore nullability-stamped). The consumer contract is computed via `NullabilityResolver` from the **operand edge's** `Slot.producedFrom` — `BuildMethodBodies` SHALL inject `NullabilityResolver` via Dagger and call it on demand. The contract SHALL NOT be read from any `ExpansionGroup`.

#### Scenario: Nullable source feeding a non-null operand emits requireNonNull
- **WHEN** `BuildMethodBodies` walks an operand whose producer-stamped `nullability` is `NULLABLE`
- **AND** the operand **edge's** `Slot.producedFrom` resolves to `NON_NULL` via `NullabilityResolver`
- **THEN** the rendered operand expression is wrapped: `Objects.requireNonNull(<expr>, "<msg>")`
- **AND** the `<msg>` string identifies both the source path and the target slot name

#### Scenario: Nullable source feeding a nullable operand emits null-safe propagation
- **WHEN** `BuildMethodBodies` walks an operand whose producer-stamped `nullability` is `NULLABLE`
- **AND** the operand edge's `Slot.producedFrom` resolves to `NULLABLE` via `NullabilityResolver`
- **THEN** the rendered operand expression is a null-safe chain (form per "Null-safe propagation form")
- **AND** no `requireNonNull` is emitted

#### Scenario: Consumer contract comes from the edge, not a group
- **WHEN** the consumer contract for an operand is resolved during nullability-aware wiring
- **THEN** it is obtained from the operand edge's `Slot.producedFrom`
- **AND** no `ExpansionGroup.consumerContractFor(...)` call is made

#### Scenario: Non-null producer for a non-null operand is unchanged
- **WHEN** `BuildMethodBodies` walks an operand whose producer-stamped `nullability` is `NON_NULL`
- **AND** the operand edge's `Slot.producedFrom` resolves to `NON_NULL`
- **THEN** the rendered operand expression matches today's emission exactly — no guard wrapper added

#### Scenario: UNKNOWN source for any operand is unchanged
- **WHEN** `BuildMethodBodies` walks an operand whose producer-stamped `nullability` is `UNKNOWN`
- **THEN** the rendered operand expression matches today's emission exactly — no guard wrapper added
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

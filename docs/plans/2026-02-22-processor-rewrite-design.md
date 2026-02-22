# Percolate Annotation Processor — Rewrite Design

## Overview

Complete rewrite of the annotation processor with a graph-first architecture. The dependency graph is the single source of truth. Every pipeline stage either builds, transforms, or reads the graph.

Package: `io.github.joke.percolate.processor`

## Architecture: Graph-First Pipeline

Six stages process mapper definitions into generated implementations:

```
Parse → Resolve → Graph Build → Validate → Optimize → CodeGen
```

### Graph Model

JGraphT `DirectedWeightedGraph` with typed nodes and edges.

**Nodes:**

| Node | Represents | Example |
|------|-----------|---------|
| TypeNode | A Java type | `FlatTicket`, `Ticket`, `String` |
| PropertyNode | A property on a type | `Ticket.ticketId`, `Order.venue.name` |
| ConstructorNode | An object creation point | `new FlatTicket(...)` |
| MethodNode | A mapper method | `mapPerson(Ticket, Order)` |

**Edges:**

| Edge | From → To | Meaning |
|------|----------|---------|
| PropertyAccessEdge | TypeNode → PropertyNode | Getting a property (via getter/field) |
| MethodCallEdge | TypeNode → TypeNode | Calling a mapper method to convert |
| ConstructorParamEdge | PropertyNode → ConstructorNode | Feeding value into constructor param |
| ConstructorResultEdge | ConstructorNode → TypeNode | Constructor produces a type |
| GenericPlaceholderEdge | TypeNode → TypeNode | Unresolved generic mapping |

## Pipeline Stages

### Stage 1: Parse

**Input:** `@Mapper`-annotated elements from `RoundEnvironment`
**Output:** `ParseResult`

Extracts from javax.lang.model API:
- `MapperDefinition` — interface name, package, methods
- `MethodDefinition` — name, parameters, return type, abstract/default flag
- `MapDirective` — raw target/source strings from `@Map`

No validation — faithful extraction only.

### Stage 2: Resolve

**Input:** `ParseResult`
**Output:** `ResolveResult`

Enriches the model with implicit mappings:
- Same-name matching for single-parameter methods without full `@Map` coverage
- Wildcard expansion (`source.*` → individual property directives)

Uses `PropertyDiscoveryStrategy` (SPI) to determine type properties.

Result: every abstract method has a complete, explicit set of `MapDirective`s.

### Stage 3: Graph Build

**Input:** `ResolveResult`
**Output:** `GraphResult`

Constructs the JGraphT graph:
- For each mapper method: create MethodNode
- For each parameter type: create TypeNode
- For each return type: create TypeNode + ConstructorNode
- Walk source paths: create PropertyAccessEdges
- Walk target paths: create ConstructorParamEdges
- Generic types (`List<A>` → `List<B>`): insert GenericPlaceholderEdges

Uses `ObjectCreationStrategy` (SPI) to determine how ConstructorNodes are created.

### Stage 4: Validate

**Input:** `GraphResult`
**Output:** `ValidationResult`

- Expand GenericPlaceholderEdges using `GenericMappingStrategy` (SPI)
- Verify all ConstructorNode inputs reachable from method parameters
- Report missing paths, type mismatches, ambiguous paths
- Include ASCII graph visualization in error messages via `GraphRenderer`
- Output: diagnostics list + fatal error flag

### Stage 5: Optimize

**Input:** Validated `GraphResult`
**Output:** `OptimizedGraphResult`

- Remove unreachable nodes
- Collapse trivial single-hop chains
- Minimize generated method count
- Weight edges for shortest-path traversal in codegen

### Stage 6: CodeGen

**Input:** `OptimizedGraphResult`
**Output:** Generated Java source files via `Filer`

- Generate `{MapperName}Impl` for each mapper interface
- Traverse shortest path from input nodes to return type
- Each traversal step → a statement in the generated method
- Uses Palantir JavaPoet for code generation

## SPI Extension Points

Three pluggable strategy interfaces via `java.util.ServiceLoader`:

### PropertyDiscoveryStrategy

Determines what properties a type has. Used in Resolve stage.

```java
public interface PropertyDiscoveryStrategy {
    Set<Property> discoverProperties(TypeElement type, ProcessingEnvironment env);
}
```

Built-in: `GetterPropertyStrategy`, `FieldPropertyStrategy`

### ObjectCreationStrategy

Determines how to instantiate a target type. Used in Graph Build stage.

```java
public interface ObjectCreationStrategy {
    boolean canCreate(TypeElement type, ProcessingEnvironment env);
    CreationDescriptor describe(TypeElement type, ProcessingEnvironment env);
}
```

Built-in: `ConstructorCreationStrategy` (future: `BuilderCreationStrategy`)

### GenericMappingStrategy

Handles generic/wrapper type mappings. Used in Validate stage (lazy expansion).

```java
public interface GenericMappingStrategy {
    boolean canHandle(TypeMirror source, TypeMirror target, ProcessingEnvironment env);
    GraphFragment expand(TypeMirror source, TypeMirror target, ProcessingEnvironment env);
}
```

Built-in: `ListMappingStrategy`, `OptionalMappingStrategy`, `EnumMappingStrategy`

## Dagger Dependency Injection

### Scopes

- **`@ProcessorScoped`** — Entire compilation. Holds ProcessingEnvironment bindings and SPI strategy registries.
- **`@RoundScoped`** — Per annotation processing round. Holds pipeline stages and current graph.

### Component Hierarchy

```
ProcessorComponent (@ProcessorScoped)
  ├── Elements, Types, Filer, Messager
  ├── SPI strategy registries (loaded once via ServiceLoader)
  └── RoundComponent.Factory
        └── RoundComponent (@RoundScoped)
              ├── Pipeline
              ├── ParseStage
              ├── ResolveStage
              ├── GraphBuildStage
              ├── ValidateStage
              ├── OptimizeStage
              └── CodeGenStage
```

### Pipeline Executor

```java
@RoundScoped
public class Pipeline {
    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        var parsed = parse.execute(annotations, roundEnv);
        var resolved = resolve.execute(parsed);
        var graph = graphBuild.execute(resolved);
        var validated = validate.execute(graph);
        if (validated.hasFatalErrors()) return;
        var optimized = optimize.execute(validated);
        codeGen.execute(optimized);
    }
}
```

## Error Handling

All errors reported via `Messager` (compiler diagnostics):

- **Parse:** Non-interface `@Mapper`, invalid signatures
- **Resolve:** Unknown properties, ambiguous wildcards
- **Validate:** Unreachable nodes, type mismatches, missing converters, cycles

Error messages include:
- The offending element (for IDE integration)
- ASCII graph visualization via `GraphRenderer` showing current state
- Actionable suggestions (e.g., "Add method `TicketActor mapActor(Actor)` to enable conversion")

Example error output:
```
error: No path from Ticket.actors (List<Actor>) to FlatTicket.actors (List<TicketActor>)

  ConstructorNode(FlatTicket):
    ticketId ← Ticket.ticketId ✓
    actors   ← ???              ✗  (no converter: List<Actor> → List<TicketActor>)
    orderId  ← Order.orderId   ✓

  Suggestion: Add method `TicketActor mapActor(Actor actor)`
```

Fatal errors stop the pipeline after validation. Warnings proceed.

## Package Structure

```
io.github.joke.percolate.processor
├── PercolateProcessor.java
├── Pipeline.java
├── di/
│   ├── ProcessorComponent.java
│   ├── ProcessorModule.java
│   ├── RoundComponent.java
│   └── RoundModule.java
├── model/
│   ├── MapperDefinition.java
│   ├── MethodDefinition.java
│   ├── MapDirective.java
│   └── Property.java
├── stage/
│   ├── ParseStage.java
│   ├── ResolveStage.java
│   ├── GraphBuildStage.java
│   ├── ValidateStage.java
│   ├── OptimizeStage.java
│   └── CodeGenStage.java
├── graph/
│   ├── node/
│   ├── edge/
│   └── GraphRenderer.java
└── spi/
    ├── PropertyDiscoveryStrategy.java
    ├── ObjectCreationStrategy.java
    ├── GenericMappingStrategy.java
    └── impl/
```

## Testing Strategy

- **Unit tests** (Spock): Individual stages, strategy implementations, graph operations
- **Integration tests** (Google Compile Testing): End-to-end processing — compile test sources, verify generated output
- TicketMapper as primary integration test case

## Future Considerations

- SAT solver as potential replacement for JGraphT graph traversal
- Builder creation strategy via SPI
- Additional property discovery strategies
- Additional generic mapping strategies

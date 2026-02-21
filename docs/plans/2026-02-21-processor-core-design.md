# Percolate Processor Core Design

**Date:** 2026-02-21

## Overview

The Percolate annotation processor implements a MapStruct-style bean mapper generator using a Dagger-powered, multi-stage pipeline. Each stage produces an immutable output that flows into the next. The processor is extensible via SPI-registered strategies for property discovery and type mapping.

---

## Architecture

```
PercolateProcessor (@AutoService)
    └── ProcessorComponent (Dagger @Singleton, created in init())
            └── RoundComponent (Dagger sub-component, created per process() call)
                    │
                    ├── AnalysisStage   → AnalysisResult
                    ├── ValidationStage → ValidationResult
                    ├── GraphStage      → GraphResult
                    └── CodeGenStage    → JavaFile (written via Filer)
```

`PercolateProcessor.process()` delegates to a thin `Pipeline` orchestrator that runs stages in order and short-circuits on validation failure.

---

## Stage 1: Analysis

Reads all `@Mapper`-annotated interfaces from `RoundEnvironment` and builds an `AnalysisResult` — a list of `MapperDescriptor` objects, one per mapper interface.

**`MapperDescriptor` contains:**
- The mapper `TypeElement`
- A list of `MappingMethod`, one per abstract method, each holding:
  - Return type (target)
  - Parameters (sources)
  - Resolved `@Map` annotations (target path → source path)
  - `default` methods on the interface (candidate converter methods)

**Property discovery** is performed by all active `PropertyDiscoveryStrategy` implementations simultaneously. Results are merged per type, deduplicated by property name with getter access winning over field access.

```java
interface PropertyDiscoveryStrategy {
    List<Property> discover(TypeElement type, ProcessingEnvironment env);
}
```

Built-in strategies (registered via `@AutoService`):
- `GetterPropertyStrategy` — discovers properties via `getX()` / `isX()` methods
- `FieldPropertyStrategy` — discovers properties via direct field access

`Property` carries: `name`, `type` (`TypeMirror`), and `accessor` (`ExecutableElement` for getters, `VariableElement` for fields).

---

## Stage 2: Validation

Takes `AnalysisResult` and verifies the mapping graph can be built. Emits compiler errors via `Messager`. Returns `ValidationResult` (same structure, distinct type signalling safety) or aborts the round on fatal errors.

**Checks per `MappingMethod`:**
1. All `@Map` source paths resolve to existing source properties/parameters
2. All `@Map` target paths resolve to existing target properties
3. Every target property is covered — by explicit `@Map`, name-match, or a converter method
4. No ambiguous converters (two methods satisfying the same type conversion)
5. No cycles in the type-resolution graph

**Error output** includes a partial resolution graph rendered via JGraphT depth-first traversal from the target type, marking each property ✓ (resolved) or ✗ (unresolved). Example:

```
[Percolate] TicketMapper.mapPerson: validation failed.

Partial resolution graph (from target FlatTicket):
  FlatTicket
  ├── ticketId      ✓  ← ticket.ticketId
  ├── ticketNumber  ✓  ← ticket.ticketNumber
  ├── orderId       ✓  ← order.orderId
  ├── orderNumber   ✓  ← order.orderNumber
  └── ticketActors  ✗  ← unresolved (List<Actor> → List<TicketActor>)
        └── TicketActor
              └── name  ✗  ← unresolved (Actor → TicketActor)

Consider adding: TicketActor mapActor(Actor actor)
```

The "Consider adding" suggestion is generated from the first unresolved leaf node.

---

## Stage 3: Graph

Takes `ValidationResult` and builds two JGraphT graphs used by code generation.

**Type-resolution graph** (`DirectedGraph<TypeMirror, MethodEdge>`)
- Nodes: Java types (`TypeMirror`)
- Edges: labelled with the mapper method that converts between them
- Used to look up which method to call when a property type requires conversion

**Method-ordering graph** (`DirectedAcyclicGraph<MappingMethod, DefaultEdge>`)
- Nodes: mapper methods
- Edge `A → B` means "A's generated body calls B"
- Topological sort gives call structure; DAG constraint detects cycles

`GraphResult` exposes:
- `resolverFor(TypeMirror source, TypeMirror target) → MappingMethod`
- `callOrderFor(MappingMethod) → List<MappingMethod>`

All graph operations (cycle detection, traversal, shortest-path resolution) use JGraphT — no manual graph logic.

---

## Stage 4: Code Generation

Takes `GraphResult` and generates one implementation class per `@Mapper` interface via JavaPoet.

**Generated class:** `{MapperName}Impl implements {MapperName}`

**Per abstract method**, the generated body:
1. Iterates target properties (constructor order for immutable types, setter order for mutable)
2. For each property:
   - `@Map(target, source)` → emit the source expression directly
   - Name-match → emit `source.getX()`
   - Needs converter → look up `resolverFor(...)` and emit `this.mapX(source.getX())`
3. Wraps result in a constructor call or builder chain based on the target's construction strategy

**Type mapping strategies** handle non-trivial conversions:

```java
interface TypeMappingStrategy {
    boolean supports(TypeMirror source, TypeMirror target, ProcessingEnvironment env);
    CodeBlock generate(String sourceExpr, TypeMirror source, TypeMirror target);
}
```

Built-in strategies (registered via `@AutoService`):
- `OptionalMappingStrategy` — wraps/unwraps `Optional<T>`
- `CollectionMappingStrategy` — maps `List<A> → List<B>` via `.stream().map(this::mapX).toList()`
- `EnumMappingStrategy` — maps same-named enum constants, errors on unmatched ones

The code gen stage queries active `Set<TypeMappingStrategy>` before falling back to the type-resolution graph. An unresolved property at this stage emits a defensive `Messager` error (validation should have already caught it).

---

## Dagger DI Structure

**`ProcessorComponent`** (`@Singleton`, created in `init()`)
- Module inputs: `ProcessingEnvironment`
- Provides: `Messager`, `Types`, `Elements`, `Filer`
- Provides: `Set<PropertyDiscoveryStrategy>` and `Set<TypeMappingStrategy>` via multibindings
- SPI strategies are loaded via `ServiceLoader` inside a `@Module @Provides` method and contributed into the multibinding sets

**`RoundComponent`** (sub-component, created per `process()` call)
- Module inputs: `RoundEnvironment`
- Provides: all four stages, each `@RoundScoped`
- Provides: `Pipeline` orchestrator

SPI loading occurs once at `ProcessorComponent` creation using the processor classloader, so user-provided strategies on the annotation processor classpath are discovered alongside built-ins.

---

## SPI Extension Points

Both strategy interfaces are SPI extension points:

| Interface                  | Built-ins                                          | Registration          |
|----------------------------|----------------------------------------------------|-----------------------|
| `PropertyDiscoveryStrategy`| `GetterPropertyStrategy`, `FieldPropertyStrategy`  | `@AutoService` + SPI  |
| `TypeMappingStrategy`      | `OptionalMappingStrategy`, `CollectionMappingStrategy`, `EnumMappingStrategy` | `@AutoService` + SPI |

Users provide custom strategies by implementing the interface, registering via `META-INF/services`, and adding the JAR to the annotation processor classpath.

---

## Package Structure (proposed)

```
io.github.joke.caffeinate
├── processor/
│   ├── PercolateProcessor.java
│   ├── Pipeline.java
│   ├── ProcessorComponent.java
│   └── RoundComponent.java
├── analysis/
│   ├── AnalysisStage.java
│   ├── AnalysisResult.java
│   ├── MapperDescriptor.java
│   ├── MappingMethod.java
│   └── property/
│       ├── PropertyDiscoveryStrategy.java
│       ├── Property.java
│       ├── GetterPropertyStrategy.java
│       └── FieldPropertyStrategy.java
├── validation/
│   ├── ValidationStage.java
│   └── ValidationResult.java
├── graph/
│   ├── GraphStage.java
│   ├── GraphResult.java
│   └── MethodEdge.java
└── codegen/
    ├── CodeGenStage.java
    └── strategy/
        ├── TypeMappingStrategy.java
        ├── OptionalMappingStrategy.java
        ├── CollectionMappingStrategy.java
        └── EnumMappingStrategy.java
```

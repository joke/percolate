# Pre-Pipeline Separation of Concerns — Design

**Date:** 2026-03-01

**Goal:** Fix the separation of concerns in the processor entry point and discovery stage so that (1) `ParseStage` only discovers mapper interfaces, (2) mapper-specific parsing lives inside the pipeline, and (3) `PercolateProcessor` delegates to a single round-level entry point.

---

## Problem with the current design

- `ParseStage` does two things: discovers all `@Mapper` interfaces across the round, and builds a `MapperDefinition` per mapper (parsing methods, parameters, directives). The latter is mapper-specific and belongs in the pipeline.
- `PercolateProcessor.process` manually reaches into `RoundComponent` to extract `parseStage()` and `pipeline()` separately, then orchestrates them by hand. Orchestration leaks into the processor.
- `RoundComponent` exposes two provision methods (`parseStage()`, `pipeline()`) that are only meaningful when composed together — forcing every caller to know their relationship.

---

## Design decisions

### 1. `ParseStage` is renamed `MapperDiscoveryStage`

Responsibility: discover all elements annotated with `@Mapper` in the round and validate that each is an interface. Returns `List<TypeElement>`.

Drops: `parseMapper`, `parseMethod`, `extractMapDirectives` (moved to pipeline).

### 2. New `ParseMapperStage` — first stage in the pipeline

Responsibility: build a `MapperDefinition` from a single `TypeElement`. Contains what was `parseMapper`, `parseMethod`, `extractMapDirectives` from the old `ParseStage`.

Unscoped (stateless pure function).

### 3. `Pipeline` entry point becomes `process(TypeElement)`

The pipeline now owns the full per-mapper lifecycle from raw element to code generation. `ParseMapperStage` is the first call inside `process`.

### 4. New `RoundProcessor` — single round-level entry point

A new `@RoundScoped` class that owns the round loop. Injected with `MapperDiscoveryStage` and `Pipeline`. Exposes `process(annotations, roundEnv)` which discovers all mapper `TypeElement`s and delegates each to `Pipeline`.

### 5. `RoundComponent` exposes only `RoundProcessor processor()`

Removes `parseStage()` and `pipeline()` provision methods. The single `processor()` provision is the only public surface of the round component.

### 6. `PercolateProcessor` reduces to a single delegation call

```java
component.roundComponentFactory().create().processor().process(annotations, roundEnv);
```

No orchestration logic remains in the processor.

---

## Data flow

```
PercolateProcessor.process(annotations, roundEnv)
  └─ RoundComponent.processor().process(annotations, roundEnv)
       │
       ├─ MapperDiscoveryStage.execute(annotations, roundEnv)  →  List<TypeElement>
       │    (find @Mapper interfaces, validate each is an interface)
       │
       └─ forEach typeElement:
            Pipeline.process(TypeElement)
              ├─ ParseMapperStage.execute(typeElement)   →  MapperDefinition
              ├─ RegistrationStage.execute(mapper)       →  MethodRegistry
              ├─ BindingStage.execute(registry)          →  MethodRegistry
              ├─ WiringStage.execute(registry)           →  MethodRegistry
              └─ … (ValidateStage, OptimizeStage, CodeGenStage)
```

---

## Scoping

| Class | Scope | Reason |
|---|---|---|
| `MapperDiscoveryStage` | `@RoundScoped` | Uses `RoundEnvironment` |
| `RoundProcessor` | `@RoundScoped` | Round-level coordinator |
| `Pipeline` | `@RoundScoped` | Coordinator, injected with all stage instances |
| `ParseMapperStage` | unscoped | Stateless pure function |
| `RegistrationStage` | unscoped | Stateless pure function |
| `BindingStage` | unscoped | Stateless pure function |
| `WiringStage` | unscoped | Stateless pure function |

---

## What is deleted / removed

- `ParseStage.java` — renamed to `MapperDiscoveryStage.java`
- `parseMapper`, `parseMethod`, `extractMapDirectives` from `ParseStage` — moved to `ParseMapperStage`
- `parseStage()` and `pipeline()` provision methods from `RoundComponent`
- Manual orchestration (`parseStage().execute().forEach(pipeline()::process)`) in `PercolateProcessor`

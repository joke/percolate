# Per-Mapper Pipeline Isolation — Design

**Date:** 2026-03-01

**Goal:** Restructure the annotation processor pipeline so each mapper interface is processed in isolation through all stages, with clean separation of concerns and no cross-mapper state leakage.

---

## Problem with the current design

- `ParseStage` produces a `ParseResult` bundling all mappers and their registries together
- `BindingStage` takes all mappers at once and merges all registries into one flat `MethodRegistry` — mapper identity is lost
- `WiringStage` operates on the merged registry with no knowledge of which entries belong to which mapper
- `ParseStage` builds the `MethodRegistry` (initial registration) — a concern that belongs in `BindingStage`
- `MethodRegistry` merging leaks cross-mapper method knowledge into each mapper's wiring phase
- Stages are annotated `@RoundScoped` even though most are stateless and have no round-level lifecycle concern

---

## Design decisions

### 1. Pipeline receives a single MapperDefinition

The pipeline is a **single-mapper processing unit**. The loop over all mappers in a round happens outside the pipeline, in `PercolateProcessor`.

```java
// PercolateProcessor
parseStage.execute(annotations, roundEnv)   // List<MapperDefinition>
          .forEach(pipeline::process);

// Pipeline
public void process(MapperDefinition mapper) { ... }
```

### 2. ParseResult is eliminated

`ParseStage.execute()` returns `List<MapperDefinition>` directly. The `ParseResult` wrapper is removed.

### 3. New RegistrationStage

A new `RegistrationStage` is introduced before `BindingStage`. Its sole responsibility is creating a fresh `MethodRegistry` and registering all eligible methods from the mapper interface.

Registration criteria:
- Public methods
- Non-void return type
- Any number of parameters (no single-param restriction)

All entries are initially registered as opaque (`graph = null`). This is correct — no graphs exist yet.

### 4. BindingStage focuses only on graph building

`BindingStage` iterates the registry and builds a flow graph (`DirectedWeightedMultigraph`) only for entries where `getSignature().isAbstract()`. Default method entries remain opaque permanently — they are pre-implemented by the developer and require no code generation.

### 5. isOpaque() is retained

`RegistryEntry.isOpaque()` (`graph == null`) distinguishes:
- **Default methods**: permanently opaque — pre-implemented, no wiring needed
- **Abstract methods after binding**: non-opaque — have a graph, proceed through wiring and codegen

### 6. MethodRegistry is per-mapper only

No merging of registries across mappers ever happens. Each mapper's `MethodRegistry` contains only its own methods.

### 7. No cross-mapper method detection

`MapperMethodProvider` searches only the **current mapper's own public methods** (via the per-mapper `MethodRegistry`). Automatic detection of methods in other mappers is explicitly prohibited.

Cross-mapper reuse is a future feature requiring an explicit developer declaration (annotation or similar). It is not designed or implemented here.

### 8. DI scoping: unscoped stages, method parameters for data

Stages are stateless — their injected fields are pure infrastructure (`processingEnv`, strategies, providers). The rule is:

- **DI for services**: `processingEnv`, strategies, providers, `Messager` — inject these
- **Method parameters for data**: `MapperDefinition`, `MethodRegistry`, results — pass these explicitly

| Component | Scope | Reason |
|---|---|---|
| `ParseStage` | `@RoundScoped` | Round-level concern, uses `RoundEnvironment` |
| `Pipeline` | `@RoundScoped` | Coordinator, injected with all stage instances |
| `RegistrationStage` | unscoped | Stateless pure function |
| `BindingStage` | unscoped | Stateless pure function |
| `WiringStage` | unscoped | Stateless pure function |
| `ValidateStage` | unscoped | Stateless pure function |
| `OptimizeStage` | unscoped | Stateless pure function |
| `CodeGenStage` | unscoped | Stateless pure function |

---

## Data flow

```
PercolateProcessor.process(annotations, roundEnv)
  │
  └─ ParseStage.execute(annotations, roundEnv)  →  List<MapperDefinition>
       │
       └─ forEach mapper:
            Pipeline.process(MapperDefinition)
              │
              ├─ RegistrationStage.execute(mapper)     →  MethodRegistry
              ├─ BindingStage.execute(registry)         →  MethodRegistry
              ├─ WiringStage.execute(registry)          →  MethodRegistry
              ├─ ValidateStage.execute(registry)        →  ValidationResult
              ├─ OptimizeStage.execute(validResult)     →  OptimizedResult
              └─ CodeGenStage.execute(optimizedResult)  →  (writes source file)
```

---

## What is deleted / removed

- `ParseResult` class — eliminated
- Cross-mapper `MethodRegistry` merging in `BindingStage` — eliminated
- `BindingStage.mergeRegistries()` — eliminated
- `ParseStage.buildRegistry()` — moved to `RegistrationStage`
- `MapperMethodProvider` cross-mapper list — replaced with per-mapper `MethodRegistry`
- Single-param restriction in method registration — removed (registration allows any param count)
- `@RoundScoped` from all pipeline stages except `ParseStage` and `Pipeline`

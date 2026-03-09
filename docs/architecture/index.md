# Architecture Overview

Percolate is a Java annotation processor that generates mapper implementations at compile time,
in the style of MapStruct. Given a `@Mapper`-annotated interface, the processor analyses its
abstract methods, builds a typed data-flow graph for each method, and emits a concrete `*Impl`
class that wires source properties to a target constructor.

## Module Structure

| Module | Description |
|--------|-------------|
| `annotations` | `@Mapper` and related annotation types consumed by user code |
| `processor` | The annotation processor implementation (Dagger 2, JavaPoet, JGraphT) |
| `dependencies` | Java platform BOM for centralised version management |
| `test-classes` | Example domain classes for manual/integration testing — **do not modify** |
| `test-mapper` | Example mapper interface for manual/integration testing — **do not modify** |

## Pipeline Stage Sequence

Each `@Mapper` interface found during a compilation round is processed through the following
linear pipeline:

```
Input: TypeElement (@Mapper interface)
         │
         ▼
  ┌─────────────────────┐
  │   ParseMapperStage  │  parses @Mapper → MapperDefinition
  └──────────┬──────────┘
             │
             ▼
  ┌─────────────────────┐
  │  RegistrationStage  │  creates MethodRegistry + RegistryEntry per method
  └──────────┬──────────┘
             │
             ▼
  ┌─────────────────────┐
  │    BindingStage     │  builds binding Graph<MappingNode, FlowEdge> per method
  └──────────┬──────────┘
             │
             ▼
  ┌─────────────────────┐
  │    WiringStage      │  resolves types, splices conversion fragments, stabilises graph
  └──────────┬──────────┘
             │
             ▼
  ┌─────────────────────┐
  │   ValidateStage     │  backward reachability; reports unmapped params / dead-ends
  └──────────┬──────────┘
             │
             ▼
  ┌─────────────────────┐
  │   CodeGenStage      │  (to be implemented) emits *Impl source via JavaPoet
  └─────────────────────┘
         │
         ▼
Output: Generated *Impl Java source file
```

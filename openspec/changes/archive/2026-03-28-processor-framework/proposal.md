# Processor Framework

## Summary

Set up the basic annotation processing framework for Percolate. This is the foundation that all future processing stages will plug into: the `PercolateProcessor` entry point, Dagger component wiring, and an empty `Pipeline` skeleton.

## Motivation

The `rewrite` branch has the annotation API (`@Mapper`, `@Map`, `@MapList`) but no processor. This change establishes the minimal processing framework so that stages can be added incrementally.

## Design Decisions

- **Single Dagger component** — No round subcomponent. `RoundEnvironment` flows as a method parameter, not a DI binding. Percolate doesn't generate annotations, so multi-round complexity is unnecessary.
- **Isolated mapper processing** — Each `@Mapper`-annotated type is processed independently through the pipeline. No shared state between mappers.
- **Explicit pipeline composition** — Stages will be called sequentially in the pipeline method body (option A). No generic `Stage<I,O>` abstraction. Types change at each step; the code reads top-to-bottom.
- **Immutable model flow** — Each future stage will take an immutable model and return a new one. Side effects (diagnostics via `Messager`) are acceptable alongside the pure data transformation.
- **Compiler services injected via Dagger** — `Elements`, `Types`, `Messager`, `Filer` provided by the component, injected where needed.
- **Pipeline returns `JavaFile`** — Placeholder return type signaling intent. Body is empty for now.

## Scope

### In scope
- `PercolateProcessor` — `@AutoService`-registered, discovers `@Mapper` types, delegates to `Pipeline`
- `ProcessorComponent` — Dagger component providing compiler services and `Pipeline`
- `ProcessorModule` — Dagger module extracting `Elements`, `Types`, `Messager`, `Filer` from `ProcessingEnvironment`
- `Pipeline` — `process(TypeElement) -> JavaFile`, empty body, stages deferred

### Out of scope
- Processing stages (parsing, resolving, binding, validation, code generation)
- Model classes
- Graph model
- SPI / extensibility
- Error handling strategy beyond `Messager` injection

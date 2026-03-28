## Context

The `rewrite` branch has annotations (`@Mapper`, `@Map`, `@MapList`) but no processor module code. We need the minimal annotation processing skeleton that future stages plug into.

Existing `processor/build.gradle` already declares dependencies on Dagger, AutoService, Lombok, JavaPoet, JGraphT, and Spock — so the build is ready.

## Goals / Non-Goals

**Goals:**
- Working annotation processor that javac discovers and invokes
- Dagger-wired dependency injection for compiler services
- Empty pipeline skeleton that processes each `@Mapper` type in isolation
- Compilable and testable

**Non-Goals:**
- Any real processing logic (parsing, resolving, binding, code generation)
- Model classes or graph structures
- SPI / extensibility points
- Multi-round processing support

## Decisions

### Single Dagger component, no round subcomponent

The processor creates one `ProcessorComponent` at `init()` time. `RoundEnvironment` is not a DI binding — it flows as a method parameter to `process()`.

**Why:** Percolate generates mapper implementations, not annotations. Only round 1 has annotated elements. A round subcomponent adds two scope annotations, a factory method, and a second module for no practical benefit.

### Pipeline as a concrete class, not an interface

`Pipeline` is a Dagger-injectable class with a `process(TypeElement)` method. No interface — there's exactly one implementation.

**Why:** YAGNI. If we ever need to swap pipeline implementations (unlikely), we can extract an interface then.

### JavaFile as pipeline return type

`Pipeline.process()` returns `JavaFile` (from JavaPoet). This is a placeholder — the body returns `null` for now.

**Why:** Signals the intent that the pipeline's job is to produce generated source code. Gives future stages a concrete target to work toward.

### ProcessorModule provides compiler services

A Dagger `@Module` extracts `Elements`, `Types`, `Messager`, and `Filer` from `ProcessingEnvironment` via `@Provides` methods.

**Why:** Compiler services need to be injectable into the pipeline and future stages. Centralizing extraction in one module keeps the processor class thin.

### Package structure

All classes in `io.github.joke.percolate.processor` for now. Sub-packages (model, stage, etc.) will emerge as stages are added.

**Why:** Four classes don't need package organization. Premature packaging creates empty hierarchies.

## Risks / Trade-offs

- **`JavaFile` return with null body** — Mildly awkward, but short-lived. The first real stage will replace it with actual code generation.
- **No error handling pattern yet** — Deliberately deferred. The right pattern will emerge from the first stages that need it.

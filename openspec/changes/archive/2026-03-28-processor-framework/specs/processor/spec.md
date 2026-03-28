# Processor Framework Spec

## PercolateProcessor

- Extends `javax.annotation.processing.AbstractProcessor`
- Registered via `@AutoService(Processor.class)`
- Supports annotation: `io.github.joke.percolate.Mapper`
- `init()`: creates Dagger `ProcessorComponent` from `ProcessingEnvironment`
- `process()`: iterates elements annotated with `@Mapper`, calls `pipeline.process(element)` for each
- Obtains `Pipeline` from the Dagger component

## ProcessorComponent

- Dagger `@Component` with `modules = ProcessorModule.class`
- Exposes `Pipeline pipeline()`
- Factory method: takes `ProcessingEnvironment` as parameter (via `@Component.Factory` or `@BindsInstance`)

## ProcessorModule

- Dagger `@Module`
- `@Provides` methods extracting from `ProcessingEnvironment`:
  - `Elements` via `getElementUtils()`
  - `Types` via `getTypeUtils()`
  - `Messager` via `getMessager()`
  - `Filer` via `getFiler()`

## Pipeline

- Constructor-injected by Dagger
- Method: `process(TypeElement element)` returning `JavaFile`
- Body: returns `null` (placeholder)
- No constructor dependencies yet (empty, stages added later)

## Package

All classes in `io.github.joke.percolate.processor`.

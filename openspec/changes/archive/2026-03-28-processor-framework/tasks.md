# Tasks

- [x] Create `ProcessorModule` — Dagger module that provides `Elements`, `Types`, `Messager`, `Filer` from `ProcessingEnvironment`
- [x] Create `ProcessorComponent` — Dagger component with factory taking `ProcessingEnvironment`, exposes `Pipeline`
- [x] Create `Pipeline` — class with `process(TypeElement)` returning `JavaFile`, empty body (returns null)
- [x] Create `PercolateProcessor` — `AbstractProcessor` with `@AutoService`, creates component in `init()`, discovers `@Mapper` types and delegates to pipeline in `process()`
- [x] Create `package-info.java` — JSpecify `@NullMarked` for the processor package
- [x] Add integration test — Spock test using compile-testing to verify the processor runs without errors on a simple `@Mapper` interface

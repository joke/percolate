## Why

The processor currently has a stub `Pipeline` that returns `null` for every `@Mapper` element. To make any progress toward MapStruct-like code generation, we need the foundation: a way to discover what to map (abstract methods + `@Map` directives) and a way to surface errors with IDE-quality location information. The processor framework also needs to move off `AbstractProcessor` to `BasicAnnotationProcessor` now — adopting it later, after multiple stages and cross-mapper deferral concerns have crept in, would be a much larger migration.

## What Changes

- Migrate `PercolateProcessor` from `javax.annotation.processing.AbstractProcessor` to `com.google.auto.common.BasicAnnotationProcessor`. **BREAKING** for the `processor` capability spec (the framework structure changes).
- Add Google `auto-common` as a dependency for both `BasicAnnotationProcessor` and the helper utilities (`MoreElements.getLocalAndInheritedMethods`, `AnnotationMirrors.getAnnotationValue`).
- Introduce `MapperStep` (implements `auto-common`'s `Step`) declaring `@Mapper` and dispatching elements to `Pipeline`.
- Add `Diagnostics`, a `@Singleton`-scoped side-channel that wraps `Messager` and always passes `Element`, `AnnotationMirror`, and `AnnotationValue` so IDEs underline the exact offending token. Includes a per-element scarring predicate so later stages skip elements rejected by earlier ones.
- Add three pipeline stages, each `@Inject`-constructed:
  - `DiscoverAbstractMethods`: `TypeElement` → `MapperShape` (abstract methods, including inherited and with generic substitution applied).
  - `DiscoverMappings`: `MapperShape` → `MapperMappings` (`@Map` directives via `AnnotationMirror` walking — proxy `getAnnotation` is forbidden).
  - `ValidateNoDuplicateTargets`: emits a Tier-1 syntactic error per duplicate `target` on a method, pointing at the exact `target = "..."` annotation value. Diagnostic-style: does not stop the pipeline.
- Compose the three stages in `Pipeline.process(TypeElement)`. `Pipeline.process` continues to return `null` (no codegen yet); the observable output is the diagnostics emitted.
- Carrier types are immutable Lombok `@Value` classes (Java 11 release target — no records).

Out of scope for this change: any strategy implementation, graph data structures, code generation, cross-mapper resolution, structural / semantic validators, buffered or sorted diagnostics, ServiceLoader-style third-party SPI. The longer-term graph-based resolution model is captured in `design.md` as architectural context only, to keep the early stages from painting us into a corner.

## Capabilities

### New Capabilities
- `diagnostics`: side-channel for emitting compile-time errors with `Element` + `AnnotationMirror` + `AnnotationValue` for IDE-quality positioning, with a per-element scarring predicate so later tiers/stages can skip already-rejected elements.
- `mapper-discovery`: discovering the set of abstract methods on a `@Mapper` type, with inheritance and generic substitution resolved.
- `mapping-discovery`: discovering `@Map` directives on a method via `AnnotationMirror` walking, preserving mirrors and values for later error reporting.
- `mapping-validation`: validating `@Map` directives. This change introduces only the duplicate-`target` rule; later validators (Tier 2 / Tier 3) extend the same capability.

### Modified Capabilities
- `processor`: framework switches from `AbstractProcessor` to `BasicAnnotationProcessor`; `MapperStep` is introduced; `Pipeline` gains stage dependencies.
- `unit-testing`: enumerates the new classes (`MapperStep`, `Diagnostics`, the three stages) that require unit specs.

## Impact

- **Code**: `processor/src/main/java/io/github/joke/percolate/processor/` — `PercolateProcessor` rewritten, new `MapperStep`, `Diagnostics`, three stage classes, four `@Value` carrier types.
- **Tests**: `processor/src/test/groovy/...` — unit specs for each new class; one integration spec (Google Compile Testing) covering the duplicate-`target` end-to-end path with location assertions.
- **Build**: add `com.google.auto:auto-common` to `processor/build.gradle` (`implementation`); declare its version in `dependencies/build.gradle` BOM if applicable.
- **Public API (annotations module)**: unchanged.
- **Generated output**: still none — the value of this change is the diagnostics pipeline, not generated code.

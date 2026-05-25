## Why

Mapper directives, mapper parameters, and source/target POJO fields can express
nullability intent today only by convention. The processor ignores `@Nullable`
on method signatures and fields, so generated code dereferences nullable sources
without any contract awareness and cannot distinguish "null is legal here" from
"null is a bug here". Adopting JSpecify gives users a standard way to declare
the intent and lets the processor emit code that propagates or rejects null
according to the declared contract.

## What Changes

- Recognise JSpecify annotations on directive return types, directive
  parameters, and source/target POJO fields:
  `@org.jspecify.annotations.Nullable`,
  `@org.jspecify.annotations.NullMarked`,
  `@org.jspecify.annotations.NullUnmarked`.
- Resolve the effective nullability of any element by walking the JSpecify
  scope hierarchy (type → enclosing class → enclosing package via
  `package-info.java`). Inside a `@NullMarked` scope, the absence of
  `@Nullable` means non-null; outside any marked scope, nullability is
  treated as **unknown**.
- Carry a `Nullability` value through the existing pipeline so that
  `CallableMethodDiscovery`, `SourcePathResolution`, and `GenerateStage` all
  see the same information about every type they touch.
- `GenerateStage` SHALL emit code per the following contract:
  - `@Nullable` source → `@Nullable` target: propagate `null`.
  - `@Nullable` source → non-null target: throw `NullPointerException` with a
    helpful message identifying the source path and target slot.
  - Non-null source → any target: today's behaviour, no extra guards.
  - Unknown source: today's behaviour, no extra guards (consistent with the
    "no NullAway-style enforcement" stance).
- Architect the annotation-recognition layer behind a small SPI / strategy
  surface so a follow-up change can wire processor options like
  `-Apercolate.nullable.annotations=foo.bar.Nullable,baz.Nullable` without
  reshaping the consumers. JSpecify is the only built-in detector shipped in
  this change; per-mapper switching is explicitly out of scope.
- The proposal explicitly does **not** make a `@Nullable` source feeding a
  non-null target a compile-time error. Users wanting that strictness add
  NullAway as a second annotation processor. (Future: when a `@Map(default =
  …)` is introduced, declaring a default on a non-nullable target becomes a
  compile-time error — tracked separately.)

## Capabilities

### New Capabilities
- `nullability`: JSpecify-aware nullability model. Owns the annotation
  detection mechanism (with a hook for custom `@Nullable` annotations later),
  the `@NullMarked` / `@NullUnmarked` scope walker (incl. `package-info.java`),
  and the `Nullability` value consumed by downstream specs.

### Modified Capabilities
- `callable-method-discovery`: directive method discovery SHALL capture the
  nullability of each parameter and of the return type and attach it to the
  discovered method model.
- `source-path-resolution`: `ResolvedSegment` SHALL carry the nullability of
  the value the segment produces (field, getter, or container element), so
  resolved source paths expose their end-to-end nullability.
- `code-generation`: `GenerateStage` SHALL emit null-propagating assignments
  when both endpoints are nullable, and SHALL emit NPE-throwing guards when a
  nullable source feeds a non-null target. Non-null sources retain current
  emission behaviour.
- `processor-options`: SHALL grow a documented (but not necessarily wired in
  this change) hook for configuring additional `@Nullable` annotation FQNs,
  matching the SPI introduced in the `nullability` capability.

## Impact

- **Code**: new `io.github.joke.percolate.processor.nullability` package
  (detector + scope walker + value type); `CallableMethodDiscovery`,
  `SourcePathResolution`, and `GenerateStage` updated to read and use the
  nullability value; `ProcessorOptions` grows a (likely) `Set<String>
  customNullableAnnotations` field.
- **APIs**: SPI-level types `ResolvedSegment` and the callable-method model
  gain a `Nullability` field. These are processor-internal, not user-facing,
  but downstream strategies (builtin Bridge / GroupTarget implementations) may
  observe them.
- **Dependencies**: add `org.jspecify:jspecify` (annotation jar) as a
  `compileOnly`/test dependency. The processor itself reads JSpecify via
  `AnnotationMirror` and does not need the artefact at runtime, but tests do.
- **Generated code**: existing mappers in user projects continue to compile
  unchanged. Behaviour changes only for users who annotate their directives or
  POJOs with JSpecify. No `@NullMarked`, no behaviour change.
- **Teams**: processor + generator authors (Joke). No external team
  coordination required for this experimental processor.

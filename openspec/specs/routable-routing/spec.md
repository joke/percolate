# routable-routing Specification

## Purpose
TBD - created by archiving change demand-driven-graph-expansion. Update Purpose after archive.
## Requirements
### Requirement: @Routable annotation is defined on the public API surface

A new annotation `@Routable` SHALL be added to the `annotations` module:

```java
@Documented
@Target(METHOD)
@Retention(CLASS)
public @interface Routable {}
```

The annotation is a **surface declaration only** in this slice — no processor stage consults it yet. It exists so that follow-up slices can introduce `AnalyzeStage` discovery, a per-mapper `RoutableIndex`, and a `RoutableMethodStrategy` that dispatches `TYPE_TRANSFORM` demands via it.

`@Routable` is intended to be placed on `default` methods of `@Mapper` interfaces. Validation of placement (accept on default methods, reject on abstract methods, reject duplicate `(input, output)` signatures) is NOT in scope for this slice.

#### Scenario: @Routable is importable from the annotations module

- **WHEN** a developer imports `io.github.joke.percolate.Routable`
- **THEN** the import SHALL resolve to the defined annotation

#### Scenario: @Routable on a default method compiles without processor action

- **WHEN** a mapper interface has `@Routable default String normalize(String s) { return s.trim(); }`
- **THEN** the project SHALL compile; `@Routable` SHALL NOT alter processor behaviour in this slice

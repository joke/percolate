## ADDED Requirements

### Requirement: Nullability enum in percolate-spi

The `percolate-spi` module SHALL define an enum `io.github.joke.percolate.spi.Nullability` with exactly three values, in this declaration order:

- `NULLABLE` — the value may be null.
- `NON_NULL` — the value is contractually never null.
- `UNKNOWN` — nullability is not declared and no enclosing scope marks it.

`Nullability` SHALL declare a static method `Nullability join(Nullability a, Nullability b)` implementing the absorbing/uncertain-propagating lattice:

| a \ b | NULLABLE | NON_NULL | UNKNOWN |
|---|---|---|---|
| **NULLABLE** | NULLABLE | NULLABLE | NULLABLE |
| **NON_NULL** | NULLABLE | NON_NULL | UNKNOWN |
| **UNKNOWN** | NULLABLE | UNKNOWN | UNKNOWN |

`Nullability` SHALL reside in the package `io.github.joke.percolate.spi` (covered by the existing `@NullMarked` package declaration).

#### Scenario: Nullability has exactly three values in declaration order
- **WHEN** `Nullability.values()` is invoked
- **THEN** the result contains exactly `NULLABLE`, `NON_NULL`, `UNKNOWN` in that order

#### Scenario: join absorbs NULLABLE
- **WHEN** `Nullability.join(NULLABLE, x)` is called for any `x`
- **THEN** the result is `NULLABLE`
- **AND** `Nullability.join(x, NULLABLE)` is also `NULLABLE`

#### Scenario: join propagates uncertainty
- **WHEN** `Nullability.join(NON_NULL, UNKNOWN)` is called
- **THEN** the result is `UNKNOWN`
- **AND** `Nullability.join(UNKNOWN, NON_NULL)` is also `UNKNOWN`

#### Scenario: join of two equal non-NULLABLE values is identity
- **WHEN** `Nullability.join(NON_NULL, NON_NULL)` or `Nullability.join(UNKNOWN, UNKNOWN)` is called
- **THEN** the result equals the input

### Requirement: NullabilityResolver is processor-internal

The processor SHALL define an interface `io.github.joke.percolate.processor.nullability.NullabilityResolver` with the following shape:

```java
public interface NullabilityResolver {
    Nullability resolve(TypeMirror type, Element scope);
}
```

`NullabilityResolver` SHALL NOT reside in `percolate-spi`. Strategy authors SHALL NOT call the resolver — instead, strategies surface the `AnnotatedConstruct` they matched and the engine invokes the resolver opaquely. This boundary is structural: `percolate-spi` declares no compile dependency on the processor module.

The processor module SHALL provide exactly one `NullabilityResolver` binding via Dagger as `@Singleton NullabilityResolver`. The binding SHALL be `JspecifyNullabilityResolver` (see next requirement).

#### Scenario: NullabilityResolver resides in processor.nullability package
- **WHEN** the location of `NullabilityResolver.java` is inspected
- **THEN** the file resides under `processor/src/main/java/io/github/joke/percolate/processor/nullability/`
- **AND** does not reside under `spi/src/main/java/`

#### Scenario: Strategy SPI does not import NullabilityResolver
- **WHEN** the source of any class under `spi/src/main/java/io/github/joke/percolate/spi/` or `strategies-builtin/src/main/java/` is inspected
- **THEN** no source line imports `io.github.joke.percolate.processor.nullability.NullabilityResolver`

#### Scenario: Dagger binds exactly one NullabilityResolver
- **WHEN** the processor module's bindings are inspected
- **THEN** exactly one `@Provides`-or-`@Binds` method returns `NullabilityResolver`
- **AND** the bound implementation is `JspecifyNullabilityResolver`

### Requirement: JspecifyNullabilityResolver resolution algorithm

The processor SHALL ship `io.github.joke.percolate.processor.nullability.JspecifyNullabilityResolver` implementing `NullabilityResolver`. Given inputs `(TypeMirror type, Element scope)`, the resolver SHALL execute the following algorithm in order, returning at the first matching step:

1. **Direct type-use check.** If `type.getAnnotationMirrors()` contains an annotation whose FQN is in the `nullableFqns` configured set, return `NULLABLE`.
2. **Scope walk.** Starting at `scope` and walking via `Element.getEnclosingElement()` until null:
   - If the current element has an annotation whose FQN is in the configured `unmarkedFqns` set, return `UNKNOWN`.
   - If the current element has an annotation whose FQN is in the configured `markedFqns` set, return `NON_NULL`.
3. **Package-info check.** If the scope walk did not match, consult `Elements.getPackageOf(scope).getAnnotationMirrors()`:
   - If any annotation FQN is in the `unmarkedFqns` set, return `UNKNOWN`.
   - If any annotation FQN is in the `markedFqns` set, return `NON_NULL`.
4. **Default.** Return `UNKNOWN`.

`JspecifyNullabilityResolver` SHALL NOT throw for any non-null input. A null `type` or `scope` argument is a programming error; the implementation MAY throw `NullPointerException` only on null input.

The resolver SHALL handle type-use annotations on TypeMirrors uniformly. This includes — without special-casing — the cases `List<@Nullable String>` (type argument), `@Nullable List<String>` (outer container), array element types, and wildcard bounds. All flow through step 1 above because `TypeMirror` is an `AnnotatedConstruct`.

#### Scenario: Direct @Nullable on a type-use position returns NULLABLE
- **WHEN** `resolve(typeMirrorAnnotatedWithJSpecifyNullable, anyScope)` is invoked
- **THEN** the result is `NULLABLE`
- **AND** the scope walk is not performed

#### Scenario: @NullMarked on the enclosing class returns NON_NULL for an un-annotated type
- **WHEN** `resolve(unannotatedTypeMirror, methodElementWhoseEnclosingClassIsNullMarked)` is invoked
- **THEN** the result is `NON_NULL`

#### Scenario: @NullUnmarked nested inside @NullMarked returns UNKNOWN
- **WHEN** `resolve(unannotatedTypeMirror, scopeWhoseImmediateEnclosingMethodIsNullUnmarkedAndOuterClassIsNullMarked)` is invoked
- **THEN** the result is `UNKNOWN`
- **AND** the `@NullMarked` on the outer class is NOT consulted (closest enclosing scope wins)

#### Scenario: @NullMarked on package-info.java returns NON_NULL when no closer marker exists
- **WHEN** `resolve(unannotatedTypeMirror, scopeInsideAPackageWhosePackageInfoIsNullMarked)` is invoked
- **AND** no enclosing class or method carries `@NullMarked` or `@NullUnmarked`
- **THEN** the result is `NON_NULL`

#### Scenario: Type-use on a generic argument is detected
- **WHEN** `resolve(typeArgumentMirrorAnnotatedWithJSpecifyNullable, scope)` is invoked — for example, the `T` of `List<@Nullable String>`
- **THEN** the result is `NULLABLE` (via direct type-use check)

#### Scenario: No annotation anywhere returns UNKNOWN
- **WHEN** `resolve(unannotatedTypeMirror, scopeWithNoEnclosingMarker)` is invoked and the enclosing package-info carries no `@NullMarked` / `@NullUnmarked`
- **THEN** the result is `UNKNOWN`

### Requirement: NullabilityAnnotations configuration

The processor SHALL define a Lombok `@Value` configuration class `io.github.joke.percolate.processor.nullability.NullabilityAnnotations` holding three FQN sets:

- `Set<String> nullableFqns` — annotation FQNs treated as `@Nullable`.
- `Set<String> markedFqns` — annotation FQNs treated as `@NullMarked`.
- `Set<String> unmarkedFqns` — annotation FQNs treated as `@NullUnmarked`.

The processor SHALL ship a default instance pre-seeded with the JSpecify FQNs:
- `nullableFqns` ⊇ `{ "org.jspecify.annotations.Nullable" }`
- `markedFqns`  ⊇ `{ "org.jspecify.annotations.NullMarked" }`
- `unmarkedFqns` ⊇ `{ "org.jspecify.annotations.NullUnmarked" }`

`NullabilityAnnotations` SHALL be provided to `JspecifyNullabilityResolver` via Dagger. The provider SHALL merge the JSpecify defaults with any FQNs read from `ProcessorOptions.customNullableAnnotations` (see the `processor-options` capability).

`NullabilityAnnotations` SHALL be immutable — the FQN sets are wrapped via `Set.copyOf(...)` (or equivalent) before storage so external mutation of input collections has no effect.

#### Scenario: Default NullabilityAnnotations includes JSpecify FQNs
- **WHEN** the default `NullabilityAnnotations` instance is inspected
- **THEN** `nullableFqns` contains `"org.jspecify.annotations.Nullable"`
- **AND** `markedFqns`   contains `"org.jspecify.annotations.NullMarked"`
- **AND** `unmarkedFqns` contains `"org.jspecify.annotations.NullUnmarked"`

#### Scenario: Custom annotation FQNs from ProcessorOptions are merged
- **WHEN** `ProcessorOptions.customNullableAnnotations` contains `"com.example.Nullable"`
- **THEN** the resolver-bound `NullabilityAnnotations` includes both `"com.example.Nullable"` and the JSpecify default in `nullableFqns`

#### Scenario: NullabilityAnnotations is immutable
- **WHEN** a `NullabilityAnnotations` is constructed from a mutable input set
- **AND** the caller subsequently mutates that input set
- **THEN** the configuration's stored set is unchanged

### Requirement: Engine stamps Nullability paired with Node typing

Whenever the expansion engine calls `Node.setTyping(TypeMirror, Nullability)` (see the `graph-model` capability for the paired one-shot accessor), the `Nullability` value SHALL be obtained from `NullabilityResolver.resolve(typeMirror, scopeElement)` where:

- `typeMirror` is the type being assigned to the Node.
- `scopeElement` is the `Element` whose lexical context anchors the JSpecify scope walk — typically the underlying `ExecutableElement` (for callable-method matches), `VariableElement` (for parameters, fields, slot consumer Elements), or the enclosing `TypeElement`.

Strategy code SHALL NOT pre-compute, look up, or otherwise reason about nullability. Strategies surface the `AnnotatedConstruct` they matched on their result types (see the `source-path-resolution` and `expansion-strategy-spi` capabilities); the engine performs the resolver invocation.

#### Scenario: Engine pairs setTyping with a resolver call at every typing site
- **WHEN** the source of every class in `processor/src/main/java/io/github/joke/percolate/processor/stages/expand/` is inspected
- **THEN** every call to `Node.setTyping(...)` passes a `Nullability` argument obtained from `NullabilityResolver.resolve(...)`

#### Scenario: No strategy class calls NullabilityResolver
- **WHEN** the source of every class under `spi/src/main/java/` and `strategies-builtin/src/main/java/` is inspected
- **THEN** no source line invokes `NullabilityResolver.resolve(...)`

## MODIFIED Requirements

### Requirement: ResolveCtx is the narrow type-query seam

The percolate-spi module SHALL define `io.github.joke.percolate.spi.ResolveCtx` as a single narrow,
mockable **type-query seam**: beyond `callableMethods()`, it exposes the purpose-built type and
member-reflection questions the engine and strategies actually ask — realised as ~35 methods, not the
originally-measured ~13, once type-algebra (`isSameType`/`isAssignable`/`erasure`/`isPrimitive`/`isArray`/
`isDeclared`/`typeArgument`/`typeArgumentCount`/`arrayComponent`/`declaredType`/`arrayType`/`boxed`/
`unboxed`/`simpleName`/`qualifiedName`/…), higher-level container/type predicates (`isList`/`isSet`/
`isOptional`/`isStream`/`isCollection`/`isIterable`/`isEnum`/`isReferenceType`/`isType`/`typeElementNamed`),
and member reflection (`membersOf`/`isField`/`isMethod`/`isConstructor`/`isPrivate`/`isStatic`/
`superclassOf`) are all counted. It SHALL NOT expose `typeSpace()`, `mapperType()`, or `currentMethod()`,
nor any owned type-value model, nor any reference to `MapperGraph`, `Edge`, `Node`, `EdgeKind`,
`MapperStep`, or any other type from `processor.graph` or `processor.stages.*`.

`ResolveCtx` SHALL still declare `types()`/`elements()` — this is a **deliberate delegation seam**, not an
oversight: the production real-javac implementation (`CompileResolveCtx`) answers every seam question by
delegating through them, so a single `Types`/`Elements` pair supplies the whole surface for free. No
**test** constructs a `ResolveCtx` over a `Types`/`Elements` pair any more — `ResolveCtxBuilder` is deleted
and the `strategies-builtin` unit specs mock the seam directly (change `cutover-strategies-to-mock-seam`).
Engine and strategy *production* code SHALL NOT call `types()`/`elements()` directly — every type question
routes through the seam methods above instead — and the architecture suite confines the accessors' own
`javax.lang.model.util` imports to the `ResolveCtx` interface plus the enumerated boundary packages (see
`module-boundaries`). Removing `types()`/`elements()` from the interface entirely (so the production impl
overrides each seam method directly) is a separate later phase, out of scope here.

A method that returns a type SHALL return another opaque token (see "TypeMirror is an opaque pass-through
token"). `callableMethods()` SHALL return the per-mapper index produced by the discovery stage. The
`ResolveCtx` SHALL be constructed **per mapper**, binding its `callableMethods` at construction time; the
processor SHALL NOT use a `ThreadLocal` to back any accessor.

The production implementation (`CompileResolveCtx`) SHALL be the **only** engine-side type code that
touches real javac to answer a seam question — it delegates each seam method to `Types`/`Elements`. A
strategy author SHALL be able to write a complete strategy by importing only
`io.github.joke.percolate.spi.*`, `com.palantir.javapoet.*`, and JDK types — **no `javax.lang.model` import
is needed to ask a type question** (though a strategy MAY still hold a `TypeMirror`/`Element` value as an
opaque token without importing `Types`/`Elements`).

#### Scenario: The seam answers a type question without exposing Types or Elements to callers
- **WHEN** a strategy calls `ctx.isSameType(a, b)` or `ctx.typeArgumentCount(t)`
- **THEN** the seam returns the answer (a `boolean`, an `int`, or another opaque token) without the caller
  needing to call `types()`/`elements()` itself

#### Scenario: types()/elements() remain only as the production-impl delegation seam
- **WHEN** the `ResolveCtx` interface is inspected
- **THEN** it still declares `types()` and `elements()`, and it declares no `typeSpace()`, `mapperType()`,
  or `currentMethod()` method, and no method returning a `processor.graph` or `processor.stages.*` type
- **AND** no engine or strategy production class other than `CompileResolveCtx` calls `types()`/`elements()`
  directly to answer a type question
- **AND** no test constructs a `ResolveCtx` over a `Types`/`Elements` pair (`ResolveCtxBuilder` does not exist)

#### Scenario: A type-returning question yields an opaque token
- **WHEN** a strategy calls `ctx.typeArgument(t, 0)` on a declared `List<String>` token
- **THEN** it receives a `TypeMirror` token it passes back to the seam or to codegen emission, without interrogating it directly

#### Scenario: The seam provides the callable-method index
- **WHEN** `resolveCtx.callableMethods()` is invoked
- **THEN** it returns the `CallableMethods` instance produced by the discovery stage for the current mapper

#### Scenario: Only the production impl touches javac to answer a seam question
- **WHEN** the source of `CompileResolveCtx` is inspected
- **THEN** each seam method delegates to `Types`/`Elements`
- **AND** no other engine or strategy class imports `javax.lang.model.util` to answer a type question

#### Scenario: No ThreadLocal backs ResolveCtx
- **WHEN** the `ProcessorModule` source is inspected
- **THEN** no `ThreadLocal` is used to supply any `ResolveCtx` value; `callableMethods` is bound when
  the per-mapper `ResolveCtx` is constructed

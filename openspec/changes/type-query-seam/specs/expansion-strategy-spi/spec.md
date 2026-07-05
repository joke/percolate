## RENAMED Requirements

- FROM: `### Requirement: ResolveCtx exposes Types, Elements, callableMethods`
- TO: `### Requirement: ResolveCtx is the narrow type-query seam`

## MODIFIED Requirements

### Requirement: ResolveCtx is the narrow type-query seam

The percolate-spi module SHALL define `io.github.joke.percolate.spi.ResolveCtx` as the single narrow,
mockable **type-query seam**: it exposes the measured ~13 purpose-built type questions the engine and
strategies actually ask, plus the per-mapper callable-method index — and nothing else. It SHALL NOT expose
`types()`, `elements()`, or `typeSpace()`, nor any owned type-value model, nor any reference to `MapperGraph`,
`Edge`, `Node`, `EdgeKind`, or any other type from `processor.graph` or `processor.stages.*`.

The seam SHALL expose at least these questions, each taking and returning `javax.lang.model.type.TypeMirror`
(or `Element`) values as **opaque tokens**:

`isSameType(a,b)` · `isAssignable(a,b)` · `erasure(t)` · `isPrimitive(t)`/`isArray(t)`/`isDeclared(t)` ·
`typeArgument(t,i)` / `typeArgumentCount(t)` · `arrayComponent(t)` · `declaredType(elem, args…)` ·
`arrayType(t)` · `boxed(t)`/`unboxed(t)` · `simpleName(t)`/`qualifiedName(t)`.

A method that returns a type SHALL return another opaque token. `callableMethods()` SHALL return the per-mapper
index produced by the discovery stage. The `ResolveCtx` SHALL be constructed **per mapper**, binding its
`callableMethods` at construction time; the processor SHALL NOT use a `ThreadLocal` to back any accessor.

The production implementation (`CompileResolveCtx`) SHALL be the **only** engine/strategy-side type code that
touches real javac — it delegates each seam method to `Types`/`Elements`. A strategy author SHALL be able to
write a complete strategy by importing only `io.github.joke.percolate.spi.*`, `com.palantir.javapoet.*`, and
JDK types — **no `javax.lang.model` import is needed to ask a type question**.

#### Scenario: The seam answers a type question without exposing Types or Elements
- **WHEN** a strategy calls `ctx.isSameType(a, b)` or `ctx.typeArgumentCount(t)`
- **THEN** the seam returns the answer (a `boolean`, an `int`, or another opaque token)
- **AND** the `ResolveCtx` interface declares no `types()`, `elements()`, or `typeSpace()` method

#### Scenario: Retired ResolveCtx accessors do not exist
- **WHEN** the `ResolveCtx` interface is inspected
- **THEN** it declares no `types()`, `elements()`, `typeSpace()`, `mapperType()`, or `currentMethod()` method, and no method returning a `processor.graph` or `processor.stages.*` type

#### Scenario: A type-returning question yields an opaque token
- **WHEN** a strategy calls `ctx.typeArgument(t, 0)` on a declared `List<String>` token
- **THEN** it receives a `TypeMirror` token it passes back to the seam or to codegen emission, without interrogating it directly

#### Scenario: The seam provides the callable-method index
- **WHEN** `resolveCtx.callableMethods()` is invoked
- **THEN** it returns the `CallableMethods` instance produced by the discovery stage for the current mapper

#### Scenario: Only the production impl touches javac
- **WHEN** the source of `CompileResolveCtx` is inspected
- **THEN** each seam method delegates to `Types`/`Elements`
- **AND** no other engine or strategy class imports `javax.lang.model` to answer a type question

#### Scenario: No ThreadLocal backs ResolveCtx
- **WHEN** the `ProcessorModule` source is inspected
- **THEN** no `ThreadLocal` is used to supply any `ResolveCtx` value; `callableMethods` is bound when the per-mapper `ResolveCtx` is constructed

### Requirement: TypeProbe type-introspection helper

The `percolate-spi` module SHALL ship the type-introspection helpers `io.github.joke.percolate.spi.TypeProbe`
(`asTypeElement`, `isType`, `isEnum`, `simpleName`) and `io.github.joke.percolate.spi.Containers`
(`isOptional`/`isStream`/`isList`/`isSet` and element extraction). Both SHALL answer their higher-level
questions **through the `ResolveCtx` type-query seam**, holding no direct `javax.lang.model` type/element/mirror
interrogation of their own, and treating every `TypeMirror` as an opaque token. `Containers` SHALL delegate its
declared-type checks through the seam (via `TypeProbe` or directly), never re-implementing FQN/erasure matching
against raw mirrors.

The helpers SHALL be carved so a unit test exercising a strategy or container stubs only the **1–2** seam
questions the helper asks (e.g. a `ListContainer` test stubs `isList(target) >> true` and
`typeArgument(target, 0) >> elementToken`), and never stands up a compiler. Whether the helpers fold onto
`ResolveCtx` or remain injectable instances the mock returns is an implementation choice; either way they are
mockable over the seam.

#### Scenario: Helpers hold no direct javax.lang.model interrogation
- **WHEN** the sources of `TypeProbe` and `Containers` are inspected
- **THEN** neither calls a `TypeMirror`/`Element` method or a `Types`/`Elements` method directly; each type question is asked of the `ResolveCtx` seam

#### Scenario: A container test stubs one or two seam questions
- **WHEN** a `ListContainer` unit test decides whether a target is a list and reads its element type
- **THEN** it stubs `isList(target) >> true` and `typeArgument(target, 0) >> elementToken` on a mocked `ResolveCtx`, with no javac in the test path

## ADDED Requirements

### Requirement: TypeMirror is an opaque pass-through token

Engine and strategy code SHALL treat every `javax.lang.model.type.TypeMirror` (and `Element`) it handles as an
**opaque pass-through token**: it MAY hold one, store it in an `OperationSpec` / `Port`, and hand it back to the
`ResolveCtx` seam or to codegen emission, but SHALL NOT invoke any `TypeMirror` / `Element` method on it
(`getKind`, `getTypeArguments`, a cast to `DeclaredType`, …) and SHALL NOT call `Types` / `Elements` directly.
Every type question SHALL be routed through the seam. Consequently a unit test's mocked `ResolveCtx` never stubs
a method **on** a `TypeMirror`; the mirror is a never-stubbed opaque token, exactly as
`ValidateNoDuplicateTargetsStageSpec` treats the `javax.lang.model` values it passes through.

#### Scenario: Engine and strategy code ask the seam, not the mirror
- **WHEN** a strategy or engine stage needs a type fact about a `TypeMirror` it holds
- **THEN** it calls a `ResolveCtx` seam method, and never calls a method on the `TypeMirror` or casts it to a `javax.lang.model` subtype

#### Scenario: A mocked ResolveCtx passes mirrors as never-stubbed tokens
- **WHEN** a unit test drives a strategy or stage with a mocked `ResolveCtx`
- **THEN** the `TypeMirror` values handed in are plain tokens with no stubbed interactions, and the test stubs only seam methods

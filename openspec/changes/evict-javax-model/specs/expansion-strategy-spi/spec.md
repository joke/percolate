## ADDED Requirements

### Requirement: ResolveCtx exposes the type space and callableMethods

The percolate-spi module SHALL define an interface `io.github.joke.percolate.spi.ResolveCtx` with
exactly these methods:

```java
public interface ResolveCtx {
    TypeSpace typeSpace();
    @Nullable CallableMethods callableMethods();
}
```

`typeSpace()` SHALL return the immutable `TypeSpace` snapshot materialised by the discovery adapter for
the current mapper (see `type-model`). The interface SHALL NOT expose `javax.lang.model` types
(`Types`, `Elements`, `TypeMirror`, `Element`), SHALL NOT expose `mapperType()` or `currentMethod()`,
and SHALL NOT expose any reference to `MapperGraph`, `Edge`, `Node`, `EdgeKind`, `MapperStep`, or any
other type from `processor.graph` or `processor.stages.*`. A strategy author SHALL be able to write a
complete strategy by importing only `io.github.joke.percolate.spi.*`, `com.palantir.javapoet.*`, and
JDK types — **no `javax.lang.model` import**.

`callableMethods()` SHALL return the per-mapper index produced by `DiscoverCallableMethodsStage`. The
`ResolveCtx` SHALL be constructed **per mapper**, binding its `typeSpace` and `callableMethods` at
construction time; the processor SHALL NOT use a `ThreadLocal` to back any `ResolveCtx` accessor.

#### Scenario: ResolveCtx provides the type space
- **WHEN** `resolveCtx.typeSpace()` is invoked
- **THEN** it returns the immutable `TypeSpace` snapshot the discovery adapter materialised for the current mapper

#### Scenario: ResolveCtx provides the callable-method index
- **WHEN** `resolveCtx.callableMethods()` is invoked
- **THEN** it returns the `CallableMethods` instance produced by `DiscoverCallableMethodsStage` for the current mapper

#### Scenario: ResolveCtx exposes no compiler types
- **WHEN** the `ResolveCtx` interface is inspected
- **THEN** it declares no method returning or accepting any `javax.lang.model` type, no `mapperType()`, and no `currentMethod()`

#### Scenario: A strategy is authorable without javax.lang.model
- **WHEN** the imports of any built-in strategy are inspected after the migration
- **THEN** no `javax.lang.model` import appears; the strategy reads types as `TypeRef` values and queries `resolveCtx.typeSpace()`

#### Scenario: No ThreadLocal backs ResolveCtx
- **WHEN** the `ProcessorModule` source is inspected
- **THEN** no `ThreadLocal` is used to supply any `ResolveCtx` value; `typeSpace` and `callableMethods` are bound when the per-mapper `ResolveCtx` is constructed

## MODIFIED Requirements

### Requirement: OperationSpec result type

A strategy match SHALL produce an `OperationSpec`: a **required, human-readable `label`** describing
the production, the operation's codegen, its weight, its ordered port signature (per port: name,
declared `TypeRef`, declared `Nullability`), the produced output type and nullness, optionally
a child-scope declaration (container element mapping: element-in and element-out types), and
**optionally a neutral call-target identity** — the method signature (`MethodSig`) a method-call
production invokes. The `label` SHALL be a fully-typed description the strategy composes from its
match (e.g. `int→long`, `new Address(int, String)`, `getStreet()`, `"ACTIVE"`, `map`); conversions
SHALL use the glyph arrow `→`. The call-target field SHALL be **additive and optional**: existing
factory entry points that build a production without one SHALL remain source-compatible, and a
production that is not a method call SHALL carry no call target. The call target is a **neutral
structural fact** ("this op calls this method"), recorded by a method-call strategy from identity it
already holds — never a "self-call" marker, which would require a strategy to know the
method-under-generation (it cannot: the demand context exposes no current method). Interpreting the
fact (self-call vs delegation) is the driver's concern, where the `MethodScope` is known. The spec is
plain data; the driver turns it into one atomic `AddOperation` delta. Strategies receive no graph
access and stay myopic.

#### Scenario: Spec is plain data
- **WHEN** a strategy returns an `OperationSpec`
- **THEN** it contains a label, codegen, weight, ports, output typing, optional child-scope
  declaration, and an optional call-target identity, and exposes no graph or engine surface

#### Scenario: Label is a typed production description
- **WHEN** `WidenPrimitive` produces an `int`-to-`long` widening spec
- **THEN** the spec's `label` is `int→long` (using the glyph arrow), not a codegen class name

#### Scenario: A method-call production records its call target
- **WHEN** the method-call strategy produces the demanded type by calling a single-argument method
- **THEN** the resulting `OperationSpec` carries that method's `MethodSig` as its call target, so the driver can apply the binding-time self-call rule without inspecting the `label`

#### Scenario: A non-method production carries no call target
- **WHEN** a conversion, accessor, constant, constructor, wrap, iterate, collect, or element-map spec is produced
- **THEN** the resulting `OperationSpec` carries no call target (the field is absent)

#### Scenario: Codegen render contract survives
- **WHEN** an Operation's codegen renders
- **THEN** it implements `render(IncomingValues)` with incoming values keyed by port name

### Requirement: TypeProbe type-introspection helper

The `percolate-spi` module SHALL ship a public utility `io.github.joke.percolate.spi.TypeProbe` exposing the general type-introspection primitives every strategy otherwise re-rolls, expressed over the `TypeRef` model: `decl(TypeRef)` (the declaration behind a declared reference, if any), `isType(TypeRef, fqn)`, `isEnum(TypeRef, TypeSpace)`, and `simpleName(TypeRef)`. `TypeProbe` SHALL hold only general primitives; the container-flavoured `Containers` helper SHALL delegate its declared-type checks to it without duplicating the FQN-match logic.

#### Scenario: Containers delegates to TypeProbe
- **WHEN** `Containers.isOptional`/`isStream`/`isList`/`isSet` resolve a type
- **THEN** the FQN/erasure match is performed by `TypeProbe.isType`, not re-implemented in `Containers`

#### Scenario: TypeProbe operates on model values
- **WHEN** the `TypeProbe` signatures are inspected
- **THEN** every parameter and return type is a `TypeRef`/`TypeDecl`/`TypeSpace` or JDK type — no `javax.lang.model` type appears

## REMOVED Requirements

### Requirement: ResolveCtx exposes Types, Elements, callableMethods
**Reason**: `javax.lang.model` is evicted from the SPI; `Types`/`Elements` are replaced by the immutable
`TypeSpace` snapshot (see `type-model` and the ADDED "ResolveCtx exposes the type space and
callableMethods" requirement).
**Migration**: Strategies replace `ctx.types()`/`ctx.elements()` calls with `ctx.typeSpace()` queries;
type parameters and returns become `TypeRef` values.

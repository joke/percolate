## ADDED Requirements

### Requirement: javax.lang.model is confined to the type-boundary packages

The architecture suite SHALL enforce that `javax.lang.model` types (`Types`, `Elements`, `TypeMirror`,
`Element`, and their sub-interfaces) are imported **only** in the enumerated type-boundary packages:

- the type-query seam implementation (`CompileResolveCtx`),
- the discovery adapter,
- codegen emission (the `TypeName.get(mirror)` sites — `AssembleMapperType`, `BuildMethodBodies`,
  `ConstructorCall` codegen),
- diagnostics emission (the `Messager` sites), and
- the nullability resolver.

Everywhere else — the engine graph/stages/plan-extraction, the strategies (`strategies-builtin`, `reactor`,
`reactor-blocking`), and the `Containers` / `TypeProbe` helpers — SHALL be free of `javax.lang.model` imports;
each of those regions asks its type questions through the `ResolveCtx` seam and treats every `TypeMirror` as an
opaque token. A newly introduced `javax.lang.model` import outside the enumerated boundary SHALL fail the build.

#### Scenario: Engine and strategies import no javax.lang.model
- **WHEN** the architecture suite analyses the engine graph/stages/plan packages, every strategy module, and the `Containers` / `TypeProbe` helpers
- **THEN** none of them imports a `javax.lang.model` type; type questions are routed through the `ResolveCtx` seam

#### Scenario: The boundary packages may import javax.lang.model
- **WHEN** the architecture suite analyses the seam implementation, the discovery adapter, the codegen-emit sites, the diagnostics sites, and the nullability resolver
- **THEN** their `javax.lang.model` imports are permitted

#### Scenario: A new leak outside the boundary fails the build
- **WHEN** a `javax.lang.model` import is added to an engine stage, a strategy, or a type-introspection helper
- **THEN** the architecture suite fails, flagging the out-of-boundary import

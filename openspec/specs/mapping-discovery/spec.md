# Mapping Discovery Spec

## Purpose

`DiscoverMappingsStage` is the second pipeline stage. It owns no annotation knowledge: it invokes the service-loaded `DirectiveReader`s (see `directive-reading`) and assembles one `MethodDirectives` per abstract method from what they declare through their `DirectiveSink` — the ordered bindings, the inputs keyed by target path, the published scope inputs, and the attached constraints. Every binding and input retains the underlying `AnnotationMirror` and the `AnnotationValue` of the member it came from, so later validation and diagnostic stages can point at the exact source token rather than the enclosing method.

## Requirements

### Requirement: @Map directives SHALL be discovered for every abstract method

`DiscoverMappingsStage` SHALL produce one `MethodDirectives` for each abstract method of the `MapperShape`, in
the same order. Each `MethodDirectives` carries the `ExecutableElement` together with what the readers declared
for it: the ordered bindings, the inputs keyed by target path, the published scope inputs, and the attached
constraints. The stage SHALL assemble these from `DirectiveSink` calls and SHALL read no annotation itself, so
what a binding means is the reader's knowledge and never the stage's.

#### Scenario: A method with one @Map produces one binding

- **WHEN** an abstract method is annotated with `@Map(target = "lastName", source = "lastName")`
- **THEN** the corresponding `MethodDirectives` carries exactly one binding, at target path `["lastName"]` with source path `["lastName"]`

#### Scenario: A method with multiple @Maps produces multiple bindings in source order

- **WHEN** an abstract method is annotated with `@Map(target = "lastName", source = "lastName")` followed by `@Map(target = "firstName", source = "firsty")`
- **THEN** the corresponding `MethodDirectives` carries two bindings in that order

### Requirement: @MapList container SHALL be unwrapped transparently

A repeatable mapping annotation's compiler-generated container SHALL be unwrapped by the reader that owns the
annotation, which exposes each contained entry as an individual binding; the container itself SHALL NOT appear
as one. The unwrapping SHALL be generic — derived from the annotation's own `@Repeatable` declaration rather
than from a hardcoded `@MapList`/`@MapEnumList` pair — so a third-party repeatable annotation unwraps on the
same path with no core change.

#### Scenario: Two @Maps result in two bindings, not one container binding

- **WHEN** an abstract method has two `@Map` annotations (which the compiler aggregates into `@MapList`)
- **THEN** the resulting `MethodDirectives` carries two bindings
- **AND** no binding references the `@MapList` annotation mirror

#### Scenario: A third-party repeatable annotation unwraps identically

- **WHEN** a third-party reader reads a repeatable annotation of its own
- **THEN** the container is unwrapped by the same generic `@Repeatable` path, with no core change

### Requirement: Methods without @Map directives SHALL produce empty directive lists

A method for which no reader declares anything SHALL produce a `MethodDirectives` whose bindings, inputs,
scope-input overrides and constraints are all empty (and never `null`), so a mapper method carrying no mapping
annotation is an ordinary, fully-assembled unit of work rather than a special case.

#### Scenario: Unannotated method has empty directives

- **WHEN** an abstract method carries no annotation any registered reader owns
- **THEN** the corresponding `MethodDirectives` carries an empty binding list

### Requirement: Declared-bindings goal spec derived during discovery

The discovery phase SHALL derive, per abstract method, the per-level **declared-bindings goal spec**
from that method's discovered `@Map` directives, and make it available to expansion via the per-mapper
context (keyed by method scope). It SHALL group directives by dotted target-path level: every prefix
of a directive's target contributes its next segment as a declared child at that level, and the full
target path binds the leaf directive. Directives carrying `constant` or `defaultValue` SHALL appear as
bindings like any other. This derivation SHALL NOT be performed by a seed stage (there is none); it is
a pure reshaping of already-discovered directives.

#### Scenario: Nested target paths group by level

- **WHEN** directives declare `address.street` and `address.zip`
- **THEN** the root-level goal declares `{address}` and the `address`-level goal declares
  `{street, zip}`

#### Scenario: Constant directive participates as a binding

- **WHEN** a directive declares `constant = "42"` for target `number`
- **THEN** `number` appears in the derived goal spec as a binding

#### Scenario: Goal spec is available to expansion without a seed stage

- **WHEN** expansion processes a method's return-root demand
- **THEN** the method's goal spec is obtained from the per-mapper context (derived during discovery),
  not produced by any seed stage

### Requirement: Discovery runs directive readers and owns no annotation knowledge

The discovery stage SHALL, for each abstract mapper method, invoke every registered `DirectiveReader` with a
`DirectiveSink` and assemble the resulting bindings, inputs, scope inputs and constraints into the per-mapper
state the engine consumes. It SHALL interpret no annotation itself.

What a reader **rejects** SHALL be reported here as a permanent diagnostic carrying the reader's own message
verbatim, positioned at the `Subject` the reader supplied. Discovery SHALL neither reword a rejection nor make
reporting it conditional on anything demanding the declaration it concerns.

Discovery SHALL remain the place where `javax.lang.model` reading is confined relative to the engine, but the
reading is now performed by readers, which live outside the `processor` module.

#### Scenario: Discovery invokes every registered reader
- **WHEN** an abstract mapper method is discovered
- **THEN** each registered `DirectiveReader` is invoked exactly once for that method with a sink

#### Scenario: Discovery interprets no annotation
- **WHEN** the discovery stage's sources are inspected
- **THEN** no class reads `@Map`, `@MapList`, `@MapEnum`, `@MapEnumList`, or `@Ambient`

#### Scenario: A reader's rejection is reported verbatim
- **WHEN** a reader calls `reject` while reading a method
- **THEN** a permanent error carrying that message and subject is recorded against the mapper

#### Scenario: Bindings are ordered by declaration
- **WHEN** a reader declares several bindings for one method
- **THEN** the assembled state preserves the order in which the sink received them

### Requirement: Presence is decided by written members, not a sentinel

An author-declared value SHALL be **present** exactly when the author wrote it, as reported by
`AnnotationMirror.getElementValues()`. An empty string written by the author SHALL be present. A member left
at its declared default SHALL be absent and SHALL carry no `AnnotationValue`. No sentinel value SHALL
participate in the presence decision, and `String.isEmpty()` SHALL NOT be used to decide presence.

#### Scenario: A written empty string is present
- **WHEN** a method declares an annotation member as the empty string
- **THEN** the assembled input is present with the empty string as its value

#### Scenario: An unwritten member is absent with no token
- **WHEN** a method leaves an annotation member at its default
- **THEN** no input is attached for it, and no `AnnotationValue` is retained

#### Scenario: No sentinel remains in the presence path
- **WHEN** the discovery and reader sources are searched
- **THEN** no comparison against a sentinel constant decides presence

### Requirement: Every binding and input SHALL preserve mirror and value references
Every binding and every attached input SHALL carry an opaque `Subject` resolving to the `AnnotationMirror` and
the `AnnotationValue` of the exact member the author wrote, so that a downstream diagnostic can underline that
token. An absent member SHALL carry no input and therefore no subject, there being no written token to point
at. Subjects SHALL be constructed only through the SPI's `Subjects` factory.

#### Scenario: A written member's subject resolves to its token
- **WHEN** a reader attaches an input for a written member
- **THEN** that input's subject resolves to the method `Element`, the annotation `AnnotationMirror`, and that member's `AnnotationValue`

#### Scenario: A binding carries a subject
- **WHEN** a reader declares a binding
- **THEN** the binding carries a subject suitable for positioning a duplicate-binding or source-root diagnosticEvery binding and every attached input SHALL carry an opaque `Subject` resolving to the `AnnotationMirror` and
the `AnnotationValue` of the exact member the author wrote, so that a downstream diagnostic can underline that
token. An absent member SHALL carry no input and therefore no subject, there being no written token to point
at. Subjects SHALL be constructed only through the SPI's `Subjects` factory.

#### Scenario: A written member's subject resolves to its token
- **WHEN** a reader attaches an input for a written member
- **THEN** that input's subject resolves to the method `Element`, the annotation `AnnotationMirror`, and that member's `AnnotationValue`

#### Scenario: A binding carries a subject
- **WHEN** a reader declares a binding
- **THEN** the binding carries a subject suitable for positioning a duplicate-binding or source-root diagnostic

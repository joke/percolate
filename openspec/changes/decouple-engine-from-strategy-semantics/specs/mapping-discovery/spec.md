## ADDED Requirements

### Requirement: Discovery runs directive readers and owns no annotation knowledge

The discovery stage SHALL, for each abstract mapper method, invoke every registered `DirectiveReader` with a
`DirectiveSink` and assemble the resulting bindings, inputs, scope inputs and constraints into the per-mapper
state the engine consumes. It SHALL interpret no annotation itself.

Discovery SHALL remain the place where `javax.lang.model` reading is confined relative to the engine, but the
reading is now performed by readers, which live outside the `processor` module.

#### Scenario: Discovery invokes every registered reader
- **WHEN** an abstract mapper method is discovered
- **THEN** each registered `DirectiveReader` is invoked exactly once for that method with a sink

#### Scenario: Discovery interprets no annotation
- **WHEN** the discovery stage's sources are inspected
- **THEN** no class reads `@Map`, `@MapList`, `@MapEnum`, `@MapEnumList`, or `@Ambient`

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

## MODIFIED Requirements

### Requirement: Each MappingDirective SHALL preserve mirror and value references

Every binding and every attached input SHALL carry an opaque `Subject` resolving to the `AnnotationMirror` and
the `AnnotationValue` of the exact member the author wrote, so that a downstream diagnostic can underline that
token. An absent member SHALL carry no input and therefore no subject, there being no written token to point
at. Subjects SHALL be constructed only through the SPI's `Subjects` factory.

#### Scenario: A written member's subject resolves to its token
- **WHEN** a reader attaches an input for a written member
- **THEN** that input's subject resolves to the method `Element`, the annotation `AnnotationMirror`, and that member's `AnnotationValue`

#### Scenario: A binding carries a subject
- **WHEN** a reader declares a binding
- **THEN** the binding carries a subject suitable for positioning a duplicate-binding or source-root diagnostic

## REMOVED Requirements

### Requirement: @Map constant and defaultValue members SHALL be discovered against the UNSET sentinel

**Reason**: The sentinel existed only because the reader resolved members through a helper that fills in
declared defaults, so a defaulted member and a written one were indistinguishable by value.
`AnnotationMirror.getElementValues()` reports only members the author wrote, which yields presence,
empty-string-is-present, and the absence of a positioning token directly. The requirement's name states the
sentinel rule it exists to enforce, so it is withdrawn rather than reworded. `Map.UNSET` is deleted.

**Migration**: Presence is now decided by "Presence is decided by written members, not a sentinel" above.
Callers testing `!Map.UNSET.equals(value)` test for the presence of the input instead. `constant` and
`defaultValue` become ordinary directive inputs keyed by their member names.

### Requirement: @Map format and zone members SHALL be discovered against the UNSET sentinel

**Reason**: Same as above, and additionally the requirement enumerated specific member names in a core
capability. Under the open directive bag the core neither names nor counts the members an annotation carries,
so a requirement naming `format` and `zone` cannot remain.

**Migration**: `format` and `zone` become ordinary directive inputs keyed by their member names, surfaced by
the generic `@Map` reader with no per-member code. Strategies read them by key, as before.

### Requirement: Discovery SHALL use AnnotationMirror walking, not annotation proxies

**Reason**: The rule is preserved and strengthened, but it no longer belongs to this capability: annotation
reading has moved out of the `processor` module into the `DirectiveReader` role. The mirror-walking obligation
is restated for readers in the `directive-reading` capability, together with the stronger requirement that
only explicitly written members are read.

**Migration**: The obligation now applies to every `DirectiveReader`. No behaviour changes; the diagnostic
positioning it exists to protect is preserved by the `Subject` carried on each binding and input.

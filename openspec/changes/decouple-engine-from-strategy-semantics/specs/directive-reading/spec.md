## ADDED Requirements

### Requirement: DirectiveReader is a service-loaded SPI role

The `percolate-spi` module SHALL define `io.github.joke.percolate.spi.DirectiveReader`, discovered by
`ServiceLoader` exactly as `ExpansionStrategy` and `SourceProjection` are. A reader SHALL be handed one mapper
method and a `DirectiveSink`, and SHALL translate the annotations it owns into sink calls.

A reader SHALL be the only party that reads a user-facing mapping annotation. The `processor` module SHALL NOT
read `@Map`, `@MapEnum`, or `@Ambient`; `@Mapper` remains core, read by the processor's own step, because it
decides **what to generate** rather than how a mapping behaves.

#### Scenario: Readers are discovered like strategies
- **WHEN** the processor starts a mapper
- **THEN** every `DirectiveReader` on the processor path is loaded via `ServiceLoader` and invoked once per abstract mapper method

#### Scenario: The processor reads no mapping annotation
- **WHEN** the `processor` module's sources are inspected
- **THEN** no class imports `@Map`, `@MapEnum`, or `@Ambient`, and only the mapper step imports `@Mapper`

#### Scenario: With no reader registered, mapping annotations are inert
- **WHEN** no `DirectiveReader` is on the processor path
- **THEN** a `@Map`-annotated method declares no bindings, and the mapper's realisation outcome reflects that — no core stage interprets the annotation

### Requirement: Three built-in readers cover the built-in vocabulary

`strategies-builtin` SHALL ship exactly three readers, one per annotation: a `@Map`/`@MapList` reader, a
`@MapEnum`/`@MapEnumList` reader, and an `@Ambient` reader. Each SHALL live in the feature package of the
strategies it serves.

#### Scenario: One reader per annotation
- **WHEN** the built-in readers are enumerated
- **THEN** exactly three are registered, reading `@Map`, `@MapEnum`, and `@Ambient` respectively

#### Scenario: A reader ships beside its strategies
- **WHEN** the `@MapEnum` reader is located
- **THEN** it resides in the same package as `EnumConversion`

### Requirement: Readers SHALL read only explicitly written annotation members

A reader SHALL determine a member's presence from `AnnotationMirror.getElementValues()`, which reports only
members the author wrote. A member written as the empty string SHALL be **present**; a member left at its
default SHALL be **absent** and SHALL carry no `AnnotationValue`. Presence SHALL NOT be decided by comparing
against a sentinel value, and SHALL NOT be decided by `String.isEmpty()`.

A repeatable annotation's container SHALL be unwrapped generically, by reading `@Repeatable` from the
annotation being sought rather than by naming each container type at every call site.

#### Scenario: A written empty string is present
- **WHEN** a method declares `@Map(target = "note", constant = "")`
- **THEN** the reader reports `constant` present with the value `""`

#### Scenario: An unwritten member is absent and carries no token
- **WHEN** a method declares `@Map(target = "x", source = "in.x")`
- **THEN** the reader reports `constant`, `defaultValue`, `format`, and `zone` absent, each with no `AnnotationValue`

#### Scenario: A repeated annotation is unwrapped generically
- **WHEN** a method declares two `@Map` annotations, which javac wraps in `@MapList`
- **THEN** the reader yields two bindings in declaration order, having derived the container type from `@Repeatable`

#### Scenario: A new annotation member needs no reader change
- **WHEN** a `String` member is added to `@Map`
- **THEN** the reader surfaces it as a directive input keyed by the member's own name, with no edit to the reader, the sink, or any core class

### Requirement: DirectiveSink accepts bindings, inputs, scope inputs, constraints and rejections

`DirectiveSink` SHALL expose exactly five entry points:

- **bind** — declares a target binding at a target path, optionally pinned to a source path, with a `Subject`;
- **input** — attaches an author-declared configuration value to a binding, keyed by a name the core does not
  interpret, carrying a `Subject`; a structured input MAY carry several named members;
- **scopeInput** — publishes a mapper-method parameter as a named scope input with a declared visibility;
- **constrain** — attaches a demand-scoped admissibility constraint to a target path (see `demand-constraints`);
- **reject** — states that what the author wrote is malformed on the reader's own terms, carrying a `Subject`
  and a message the core reports verbatim as a permanent error.

`constrain` and `reject` SHALL remain distinct. A constraint is **conditional**: it refuses candidates, so it is
heard only if some strategy offers one, and a demand nothing can serve records no constraint refusal at all. A
rejection is **unconditional**: it is reported whether or not anything ever demands the path the declaration
names. A rule about a declaration's own well-formedness SHALL therefore be a rejection, never a constraint.

The set of declared children at a target level SHALL be **derived** from the bound target paths, not declared
separately, so it cannot disagree with the bindings.

#### Scenario: A binding carries its source path and position
- **WHEN** a reader processes `@Map(target = "street", source = "person.address.street")`
- **THEN** it calls `bind` with the target path, the split source path, and a `Subject` positioned at the annotation

#### Scenario: Declared children are derived
- **WHEN** a method declares bindings at `address.street` and `address.city`
- **THEN** the declared-children set at `address` is `{street, city}`, derived from the bound paths with no separate sink call

#### Scenario: A structured input carries named members
- **WHEN** a reader processes `@MapEnum(source = "NEW", target = "CREATED")`
- **THEN** it attaches one input whose members are `source = "NEW"` and `target = "CREATED"`, carrying that annotation's `Subject`

#### Scenario: An ambient parameter is published as a named inherited scope input
- **WHEN** a reader processes an `@Ambient Order order` parameter
- **THEN** it calls `scopeInput` naming the parameter `"order"` with inherited visibility

### Requirement: Two bindings at one target path SHALL be an error

The core SHALL reject two bindings declared at the same target path on one method, regardless of which reader
declared them, positioned at the second binding's `Subject`. This is a property of the sink, not of any
annotation: it holds for a third-party reader and for two different readers colliding.

#### Scenario: Duplicate targets from one annotation
- **WHEN** a method declares `@Map(target = "name", source = "a")` and `@Map(target = "name", source = "b")`
- **THEN** one error is reported, positioned at the second binding's subject

#### Scenario: Duplicate targets from two readers
- **WHEN** two different readers each bind the target path `name` on one method
- **THEN** one error is reported, and the message does not name either annotation

### Requirement: A source path SHALL root at a scope input

The core SHALL reject a bound source path whose first segment names no scope input of the method, positioned at
the binding's `Subject`. This is a property of the engine's own forward walk — a path it cannot begin — not of
`@Map`'s shape.

#### Scenario: A source path rooted at an unknown name is rejected
- **WHEN** a binding on `Human map(Person person)` declares the source path `["custmer", "name"]`
- **THEN** one error is reported naming the unresolvable first segment

#### Scenario: A binding with no source path is skipped
- **WHEN** a binding declares no source path
- **THEN** no source-root check applies

### Requirement: Annotation shape rules belong to the owning reader

A rule about how an annotation's own members combine SHALL be enforced by the reader that owns the annotation,
which SHALL decline to bind and SHALL `reject` with the reason positioned at the offending member. The core
SHALL hold no such rule, and SHALL report the reader's message verbatim without rewording it or naming the
annotation itself.

The report SHALL NOT be contingent on the malformed path being demanded: a violated shape rule usually leaves
nothing able to produce that path, so a candidate-scoped refusal would be silently swallowed and the author
would see only a generic "no plan" line, or an unrelated refusal at a shallower miss.

#### Scenario: Mutually exclusive members are rejected by the reader
- **WHEN** a method declares `@Map(target = "x", source = "in.x", constant = "5")`
- **THEN** the `@Map` reader declines the binding and reports the contradiction, positioned at the `constant` value

#### Scenario: A dependent member is rejected by the reader
- **WHEN** a method declares `@Map(target = "x", constant = "5", defaultValue = "0")`
- **THEN** the `@Map` reader reports that `defaultValue` requires a source, positioned at the `defaultValue` value

#### Scenario: The core holds no annotation shape rule
- **WHEN** the processor's validation stages are enumerated
- **THEN** none of them tests how the members of a mapping annotation combine

#### Scenario: A shape violation is reported even when nothing demands the path
- **WHEN** a violated shape rule leaves no strategy able to offer any candidate for that target path
- **THEN** the rule's own message is still reported, rather than only the generic no-plan diagnostic

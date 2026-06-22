## MODIFIED Requirements

### Requirement: OperationSpec result type

A strategy match SHALL produce an `OperationSpec`: a **required, human-readable `label`** describing
the production, the operation's codegen, its weight, its ordered port signature (per port: name,
declared `TypeMirror`, declared `Nullability`), the produced output type and nullness, optionally
a child-scope declaration (container element mapping: element-in and element-out types), and
**optionally a neutral call-target identity** — the `ExecutableElement` a method-call production
invokes. The `label` SHALL be a fully-typed description the strategy composes from its match (e.g.
`int→long`, `new Address(int, String)`, `getStreet()`, `"ACTIVE"`, `map`); conversions SHALL use the
glyph arrow `→`. The call-target field SHALL be **additive and optional**: existing factory entry
points that build a production without one SHALL remain source-compatible, and a production that is
not a method call SHALL carry no call target. The call target is a **neutral structural fact** ("this
op calls this method"), recorded by a method-call strategy from identity it already holds — never a
"self-call" marker, which would require a strategy to know the method-under-generation (it cannot:
the demand context exposes no current method). Interpreting the fact (self-call vs delegation) is the
driver's concern, where the `MethodScope` is known. The spec is plain data; the driver turns it into
one atomic `AddOperation` delta. Strategies receive no graph access and stay myopic.

#### Scenario: Spec is plain data
- **WHEN** a strategy returns an `OperationSpec`
- **THEN** it contains a label, codegen, weight, ports, output typing, optional child-scope
  declaration, and an optional call-target identity, and exposes no graph or engine surface

#### Scenario: Label is a typed production description
- **WHEN** `WidenPrimitive` produces an `int`-to-`long` widening spec
- **THEN** the spec's `label` is `int→long` (using the glyph arrow), not a codegen class name

#### Scenario: A method-call production records its call target
- **WHEN** the method-call strategy produces the demanded type by calling a single-argument method
- **THEN** the resulting `OperationSpec` carries that method's `ExecutableElement` as its call target, so the driver can apply the binding-time self-call rule without inspecting the `label`

#### Scenario: A non-method production carries no call target
- **WHEN** a conversion, accessor, constant, constructor, wrap, iterate, collect, or element-map spec is produced
- **THEN** the resulting `OperationSpec` carries no call target (the field is absent)

#### Scenario: Codegen render contract survives
- **WHEN** an Operation's codegen renders
- **THEN** it implements `render(IncomingValues)` with incoming values keyed by port name

## MODIFIED Requirements

### Requirement: OperationSpec may request a deduplicated class member

An `OperationSpec` MAY declare one or more **member requests**, each describing a class-level member the generated mapper needs plus a way to reference it from the operation's codegen. `MemberRequest` SHALL be an interface exposing only a content **dedup key**, with exactly two implementations reached through two static factories:

- `MemberRequest.field(TypeName, CodeBlock, String)` — a field, described by its type, its initializer, and its dedup key.
- `MemberRequest.method(TypeName, List<Parameter>, CodeBlock, String)` — a method, described by its return type, its ordered parameter list, its body, and its dedup key. Each parameter carries a type and a name.

The request SHALL be **additive and optional**: a spec that needs only an inline expression declares none. The code-generation stage SHALL deduplicate member requests by their dedup key across all method bodies and emit each distinct member once (see the `code-generation` capability). A strategy that needs an inline (non-shared) value SHALL declare no member request and render it inline instead — this is how a thread-unsafe formatter stays per-call.

A method request lets a strategy emit a statement sequence without a composable statement shape in the SPI: the statements live in the requested member and the operation still renders one expression. The code-generation stage SHALL dispatch on the kind the strategy declared and SHALL NOT select a kind of its own, in the same way `OperationCodegen` and `BodyCodegen` coexist.

#### Scenario: A strategy requests a shared field member
- **WHEN** a `java.time` format strategy produces a value using a shared `DateTimeFormatter`
- **THEN** the emitted `OperationSpec` declares `MemberRequest.field` whose type is `DateTimeFormatter`, whose initializer is `DateTimeFormatter.ofPattern("…")`, and whose codegen references the member

#### Scenario: A strategy requests a method member
- **WHEN** an assembly strategy produces a value through a statement sequence
- **THEN** the emitted `OperationSpec` declares `MemberRequest.method` carrying the return type, the ordered parameters, and the body
- **AND** its `OperationCodegen` renders one call to that member

#### Scenario: A strategy that inlines requests no member
- **WHEN** a legacy-`Date` format strategy produces a value using a per-call `SimpleDateFormat`
- **THEN** the emitted `OperationSpec` declares no member request and renders `new SimpleDateFormat("…")` inline

#### Scenario: Both kinds share one dedup namespace
- **WHEN** a field request and a method request declare the same dedup key
- **THEN** the code-generation stage reports the conflict, as it does for two conflicting requests of one kind

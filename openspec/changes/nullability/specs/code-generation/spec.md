## ADDED Requirements

### Requirement: Nullability-aware slot wiring at GroupTarget composition

When `BuildMethodBodies` assembles the expressions feeding a group target's slots (the third inductive case of the method body composition algorithm), it SHALL compare the slot Node's producer-stamped nullability against the consumer contract derived on demand from the slot's underlying `Slot.producedFrom` `AnnotatedConstruct`. The comparison drives one of three emission patterns:

1. **`NULLABLE → NON_NULL` (producer commits nullable, consumer accepts only non-null)**: wrap the slot's expression in `java.util.Objects.requireNonNull(expr, msg)` where `msg` is a string literal identifying both the source path and the target slot name, e.g. `"source 'person.address' is null but target slot 'address' is non-null"`.
2. **`NULLABLE → NULLABLE`**: emit a null-safe propagation chain so that a null at the source produces a null at the target without intermediate NPEs. The chain shape is a code-generation detail (see "Null-safe propagation form").
3. **All other combinations** (`NON_NULL → *`, `UNKNOWN → *`, `* → UNKNOWN`): emit the slot's expression unchanged from today's behaviour. No guard, no propagation wrapper.

The wrapped/unwrapped slot expression SHALL be passed to `GroupCodegen.render(varNames, incomingValues)` exactly as today; the codegen lambda SHALL NOT see or reason about nullability.

The producer-stamped nullability is read from `slotNode.getNullability().orElseThrow()` — for a slot reached by `BuildMethodBodies`, the realised subgraph guarantees the slot is typed (and therefore nullability-stamped). The consumer contract is computed via `NullabilityResolver.resolve(slot.getProducedFrom().asType() | similar, slot.getProducedFrom())` — `BuildMethodBodies` SHALL inject `NullabilityResolver` via Dagger and call it on demand.

`BuildMethodBodies` SHALL NOT emit a guard or propagation wrapper for slots reached via single-edge paths where the slot is itself the inductive root (the slot expression `is` the final expression of the chain — its guard/propagation has already been applied at the GroupTarget composition site that owns it).

#### Scenario: Nullable source feeding a non-null target slot emits requireNonNull
- **WHEN** `BuildMethodBodies` walks a slot whose producer-stamped `nullability` is `NULLABLE`
- **AND** the slot's `producedFrom` element resolves to `NON_NULL` via `NullabilityResolver`
- **THEN** the rendered slot expression is wrapped: `Objects.requireNonNull(<expr>, "<msg>")`
- **AND** the `<msg>` string identifies both the source path and the target slot name

#### Scenario: Nullable source feeding a nullable target slot emits null-safe propagation
- **WHEN** `BuildMethodBodies` walks a slot whose producer-stamped `nullability` is `NULLABLE`
- **AND** the slot's `producedFrom` element resolves to `NULLABLE` via `NullabilityResolver`
- **THEN** the rendered slot expression is a null-safe chain (form per "Null-safe propagation form")
- **AND** no `requireNonNull` is emitted

#### Scenario: Non-null producer for a non-null target slot is unchanged
- **WHEN** `BuildMethodBodies` walks a slot whose producer-stamped `nullability` is `NON_NULL`
- **AND** the slot's `producedFrom` element resolves to `NON_NULL`
- **THEN** the rendered slot expression matches today's emission exactly — no guard wrapper added

#### Scenario: UNKNOWN source for any target slot is unchanged
- **WHEN** `BuildMethodBodies` walks a slot whose producer-stamped `nullability` is `UNKNOWN`
- **THEN** the rendered slot expression matches today's emission exactly — no guard wrapper added
- **AND** no NullAway-style strictness is applied

#### Scenario: Guard message identifies source and target
- **WHEN** a `Objects.requireNonNull` guard is emitted for slot `address` whose source path is `person.contact.address`
- **THEN** the rendered message string contains both `"person.contact.address"` and `"address"` (or the equivalent identifier the spec pins in implementation)

### Requirement: Null-safe propagation form

The form of the null-safe propagation chain emitted in the `NULLABLE → NULLABLE` case (see "Nullability-aware slot wiring at GroupTarget composition") SHALL be one of:

- A nested ternary chain `expr == null ? null : expr.next()` composed over each nullable hop in the path, OR
- An equivalent `Optional.ofNullable(expr).map(...).orElse(null)` chain.

The specific choice between the two forms is an implementation detail of `BuildMethodBodies`; this spec pins the *behaviour* (null at any nullable hop produces null at the target slot without intermediate NPEs) but not the syntax. Tests SHALL assert the runtime behaviour rather than the source-level form.

#### Scenario: Null at any nullable hop propagates without NPE
- **WHEN** a generated mapper assigns a target slot fed by a multi-hop nullable chain `a.b.c` where any of `a`, `b`, or `c` may return null
- **AND** at runtime one of those hops returns null
- **THEN** the generated code stores `null` in the target slot
- **AND** does not throw `NullPointerException`

### Requirement: BuildMethodBodies injects NullabilityResolver

`BuildMethodBodies` (the phase under `processor/src/main/java/io/github/joke/percolate/processor/stages/generate/`) SHALL declare a constructor-injected `NullabilityResolver` field via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`. The resolver SHALL be invoked exactly when consumer-contract derivation is required (the slot-wiring case above) — never for un-annotated or non-decision-bearing call sites.

#### Scenario: BuildMethodBodies has a NullabilityResolver dependency
- **WHEN** the source of `BuildMethodBodies` is inspected
- **THEN** it declares a `private final NullabilityResolver` field
- **AND** the field is constructor-injected via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`

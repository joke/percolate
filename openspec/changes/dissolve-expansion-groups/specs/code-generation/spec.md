## MODIFIED Requirements

### Requirement: Method body composition algorithm

For each abstract method `m` on a `@Mapper` interface `<Name>`, `BuildMethodBodies` SHALL produce a `CodeBlock` representing the method body by recursing over `m`'s **plan view** — the subgraph returned by `MapperContext.getGraph().planView()` scoped to `m`'s `MethodScope`. The plan view contains only the edges of the chosen plan: `REALISED` edges that belong to a `SAT`-outcome demand, with multi-fire OR-choices resolved to the cheapest branch. Because the plan view contains only the chosen plan, **every node in it has exactly one producer**; `BuildMethodBodies` SHALL NOT guess among siblings.

**`BuildMethodBodies` SHALL traverse only `Node`s and `Edge`s of the plan view. It SHALL NOT read any `ExpansionGroup`** — no `group.getRoot()`, `group.getSlots()`, `group.getCodegen()`, `group.consumerContractFor(...)`, nor any group-by-root index. The n-ary producer (constructor / multi-arg method) is reconstructed from the output node's **fan-in**: its incoming REALISED plan edges, which share a producer.

The recursion `render(node)` SHALL return both a `CodeBlock` and a boolean **`isStream`** — whether the rendered expression is an open element stream — and SHALL behave as follows, dispatching on the node's inbound plan edges:

1. **Leaf base case** — if `node` has no inbound plan edge, then `node` MUST be a `SourceLocation` whose `path.first()` matches the simple name of one of `m`'s parameters. The result is `CodeBlock.of("$N", parameter.getSimpleName())`, `isStream = false`.
2. **Scalar single-edge case** — if `node`'s single inbound plan edge `e` carries a scalar `EdgeCodegen`, recurse on `e.getFrom()` and return `e.getCodegen().render(varNames, IncomingValues.of(child))`. When the child is an open stream this scalar transform SHALL be applied per element (`mapElements`, via the stream's container handle) rather than to the stream as a whole; otherwise it is applied inline. The result preserves the child's `isStream`.
3. **Container single-edge case** — if `node`'s single inbound plan edge `e` carries a **container provider + operation** (see the `graph-model` Edge requirement), recurse on `e.getFrom()` and weave per the container-codegen weaving rules (see the `container-codegen-spi` capability): an `ENTERING` sequence opens a stream via the provider's `iterate`; an `ENTERING` wrapper drops empties via `flatMapElements` when the child is a stream, or renders a top-level `unwrap` otherwise; an `EXITING` hop closes the stream via `collect`. The single-element `wrap` (`PRESERVING`) is a scalar `EdgeCodegen` step rendered by case 2, not a container-provider edge. Every container `CodeBlock` SHALL come from the provider's handle; `BuildMethodBodies` SHALL hardcode no container syntax.
4. **Assembly fan-in case** — if `node` has **multiple** inbound plan edges (or a single inbound edge carrying an n-ary producer `Codegen`) that share a producer, render each operand child and apply the **edge-carried** producer `Codegen`. The operands are the `from` nodes of `node`'s incoming REALISED plan edges; each operand is keyed by the slot-name rule below and the producer is rendered once via `producerCodegen.render(varNames, incomingValues)`, `isStream = false`. A **single-operand** producer whose operand renders as an open element stream (a scalar bridge such as a conversion or method call) SHALL apply the producer codegen **per element** — `mapElements(operandStream, v, producerCodegen.render(single = v))` via the threaded stream handle — and preserve `isStream = true`; otherwise it renders inline with `isStream = false`. The container provider hops (iterate/collect/unwrap) are **not** rendered here — they are single-edge container cases (case 3) intercepted before the fan-in lookup.

When deriving the `slotName` key for an operand, `BuildMethodBodies` SHALL apply this rule on the operand Node's `Location`: a `TargetLocation`/`SourceLocation` with a non-empty path → its last segment; an `ElementLocation` → its `role`; otherwise raise an `IllegalStateException` naming the node.

The full method body SHALL be `CodeBlock.builder().addStatement("return $L", renderedRoot).build()` for non-void methods. A `MethodImpl` value record holding `(ExecutableElement method, CodeBlock body, Set<TypeElement> requiredMapperDeps)` SHALL be produced per abstract method, where `requiredMapperDeps` is the empty set in this change.

`BuildMethodBodies` SHALL NOT contain any literal container syntax (`stream`/`collect`/`map`/`ofNullable`/`orElse`/…); all such fragments originate from container handles.

#### Scenario: Generator reads no ExpansionGroup
- **WHEN** the source of `BuildMethodBodies` (and the composer it uses) is inspected
- **THEN** it contains no reference to `ExpansionGroup`, `getCodegen`, `getSlots`, or a group-by-root index
- **AND** every code fragment is derived from `Node` and `Edge` of the plan view

#### Scenario: Leaf parameter renders by parameter name
- **WHEN** the plan view for `Human map(Person person)` is walked and the leaf is `src[person]:Person`
- **THEN** the rendered `CodeBlock` is equivalent to `CodeBlock.of("$N", "person")` with `isStream = false`

#### Scenario: Constructor fan-in assembles operand children
- **WHEN** the plan view has an output node `tgt[]:Human` with two incoming REALISED operand edges from `firstName` and `lastName`, each fed by a single-segment path, sharing a `ConstructorCall` producer `new Human($L, $L)`
- **THEN** the rendered body is equivalent to `return new Human(person.getFirstName(), person.getLastName());`
- **AND** the producer codegen is taken from the operand edges, not from any group

#### Scenario: Single-operand scalar producer over a stream maps per element
- **WHEN** a single-operand producer whose codegen is a scalar conversion (e.g. a method call) has its operand fed by an open element stream
- **THEN** the producer codegen is applied per element via the stream handle's `mapElements`, and the result stays a stream (`isStream = true`)

#### Scenario: Dead multi-fire sibling is not rendered
- **WHEN** a target node has two sibling producers — one `SAT` and one `UNSAT_NO_PLAN` — and the method is rendered
- **THEN** `BuildMethodBodies` renders the `SAT` branch and never descends into the `UNSAT_NO_PLAN` branch

### Requirement: Nullability-aware slot wiring at GroupTarget composition

When `BuildMethodBodies` assembles the expressions feeding an n-ary producer's operands (the assembly fan-in case of the method body composition algorithm), it SHALL compare the operand Node's producer-stamped nullability against the consumer contract derived on demand from the **operand edge's** `Slot.producedFrom` `AnnotatedConstruct`. The comparison drives one of three emission patterns:

1. **`NULLABLE → NON_NULL` (producer commits nullable, consumer accepts only non-null)**: wrap the operand's expression in `java.util.Objects.requireNonNull(expr, msg)` where `msg` is a string literal identifying both the source path and the target slot name, e.g. `"source 'person.address' is null but target slot 'address' is non-null"`.
2. **`NULLABLE → NULLABLE`**: emit a null-safe propagation chain so that a null at the source produces a null at the target without intermediate NPEs. The chain shape is a code-generation detail (see "Null-safe propagation form").
3. **All other combinations** (`NON_NULL → *`, `UNKNOWN → *`, `* → UNKNOWN`): emit the operand's expression unchanged. No guard, no propagation wrapper.

The wrapped/unwrapped operand expression SHALL be passed to the producer `Codegen.render(varNames, incomingValues)` exactly as before; the codegen lambda SHALL NOT see or reason about nullability.

The producer-stamped nullability is read from `operandNode.getNullability().orElseThrow()` — for an operand reached by `BuildMethodBodies`, the plan view guarantees the node is typed (and therefore nullability-stamped). The consumer contract is computed via `NullabilityResolver` from the **operand edge's** `Slot.producedFrom` — `BuildMethodBodies` SHALL inject `NullabilityResolver` via Dagger and call it on demand. The contract SHALL NOT be read from any `ExpansionGroup`.

#### Scenario: Nullable source feeding a non-null operand emits requireNonNull
- **WHEN** `BuildMethodBodies` walks an operand whose producer-stamped `nullability` is `NULLABLE`
- **AND** the operand **edge's** `Slot.producedFrom` resolves to `NON_NULL` via `NullabilityResolver`
- **THEN** the rendered operand expression is wrapped: `Objects.requireNonNull(<expr>, "<msg>")`
- **AND** the `<msg>` string identifies both the source path and the target slot name

#### Scenario: Consumer contract comes from the edge, not a group
- **WHEN** the consumer contract for an operand is resolved during nullability-aware wiring
- **THEN** it is obtained from the operand edge's `Slot.producedFrom`
- **AND** no `ExpansionGroup.consumerContractFor(...)` call is made

#### Scenario: Non-null producer for a non-null operand is unchanged
- **WHEN** `BuildMethodBodies` walks an operand whose producer-stamped `nullability` is `NON_NULL`
- **AND** the operand edge's `Slot.producedFrom` resolves to `NON_NULL`
- **THEN** the rendered operand expression matches today's emission exactly — no guard wrapper added

#### Scenario: UNKNOWN source for any operand is unchanged
- **WHEN** `BuildMethodBodies` walks an operand whose producer-stamped `nullability` is `UNKNOWN`
- **THEN** the rendered operand expression matches today's emission exactly — no guard wrapper added

## MODIFIED Requirements

### Requirement: Method body composition algorithm

For each abstract method `m` on a `@Mapper` interface `<Name>`, `BuildMethodBodies` SHALL produce a `CodeBlock` representing the method body by recursing over `m`'s **plan view** — the subgraph returned by `MapperContext.getGraph().planView()` scoped to `m`'s `MethodScope`. The plan view contains only the edges of the chosen plan: `REALISED` edges that belong to a `SAT`-outcome group, with multi-fire OR-choices resolved to the cheapest branch. Because the plan view contains only the chosen plan, **every node in it has exactly one producer**; `BuildMethodBodies` SHALL NOT guess among siblings.

The recursion `render(node)` SHALL return both a `CodeBlock` and a boolean **`isStream`** — whether the rendered expression is an open element stream — and SHALL behave as follows, dispatching on the node's single producer:

1. **Leaf base case** — if `node` has no inbound plan edge, then `node` MUST be a `SourceLocation` whose `path.first()` matches the simple name of one of `m`'s parameters. The result is `CodeBlock.of("$N", parameter.getSimpleName())`, `isStream = false`.
2. **Scalar single-edge case** — if `node`'s single inbound plan edge `e` carries a scalar `EdgeCodegen`, recurse on `e.getFrom()` and return `e.getCodegen().render(varNames, IncomingValues.of(child))`. When the child is an open stream this scalar transform SHALL be applied per element (`mapElements`) rather than to the stream as a whole; otherwise it is applied inline. The result preserves the child's `isStream`.
3. **Container single-edge case** — if `node`'s single inbound plan edge `e` carries a **container provider + operation** (see the `graph-model` Edge modification), recurse on `e.getFrom()` and weave per the container-codegen weaving rules (see the `container-codegen-spi` capability): an `ENTERING` sequence opens a stream via the provider's `iterate`; an `ENTERING` wrapper drops empties via `flatMapElements` when the child is a stream, or renders a top-level `unwrap`/`mapPresence` otherwise; an `EXITING` hop closes the stream via `collect`. Every container `CodeBlock` SHALL come from the provider's handle; `BuildMethodBodies` SHALL hardcode no container syntax.
4. **Group-target case (`ConstructorCall`-style only)** — if `node` is the root of a multi-slot group carrying a `GroupCodegen`, render each slot's child, key each by the slot-name rule below, and return `groupCodegen.render(varNames, incomingValues)`, `isStream = false`. Container collect/unwrap hops are **not** rendered here — they are single-edge container cases (case 3).

When deriving the `slotName` key for a `GroupCodegen` slot, `BuildMethodBodies` SHALL apply this rule on the slot Node's `Location`: a `TargetLocation`/`SourceLocation` with a non-empty path → its last segment; an `ElementLocation` → its `role`; otherwise raise an `IllegalStateException` naming the node.

The full method body SHALL be `CodeBlock.builder().addStatement("return $L", renderedRoot).build()` for non-void methods. A `MethodImpl` value record holding `(ExecutableElement method, CodeBlock body, Set<TypeElement> requiredMapperDeps)` SHALL be produced per abstract method, where `requiredMapperDeps` is the empty set in this change.

`BuildMethodBodies` SHALL NOT contain any literal container syntax (`stream`/`collect`/`map`/`ofNullable`/`orElse`/…); all such fragments originate from container handles.

#### Scenario: Leaf parameter renders by parameter name
- **WHEN** the plan view for `Human map(Person person)` is walked and the leaf is `src[person]:Person`
- **THEN** the rendered `CodeBlock` is equivalent to `CodeBlock.of("$N", "person")` with `isStream = false`

#### Scenario: DirectAssign + single-segment path renders into a direct invocation
- **WHEN** the plan view contains `src[person] → src[person.firstName]` (GetterPathResolver) then a `DirectAssign` edge into `tgt[firstName]`
- **THEN** the rendered `CodeBlock` for `tgt[firstName]` is equivalent to `CodeBlock.of("$N.getFirstName()", "person")`

#### Scenario: ConstructorCall group assembles slot children
- **WHEN** the plan view has a root `GroupCodegen` `new Human($L, $L)` with slots `firstName` and `lastName` each fed by a single-segment path
- **THEN** the rendered body is equivalent to `return new Human(person.getFirstName(), person.getLastName());`

#### Scenario: Sequence container target weaves via the container handle
- **WHEN** the plan view threads a source `List<Optional<Address>>` through an iterate hop, an `Optional` element hop, an element map, and a `Set` collect, into an outer `Optional` wrap
- **THEN** the emitted body opens the list stream, `flatMap`s the optional element (dropping empties), maps the element, collects into the set, and wraps in the outer optional
- **AND** every `stream`/`flatMap`/`collect`/`ofNullable` fragment originates from a container handle, not from `BuildMethodBodies`

#### Scenario: Composer holds no container syntax
- **WHEN** the source of `BuildMethodBodies` (and the composer it uses) is inspected
- **THEN** it contains no literal container-syntax template; container fragments are obtained from `ContainerCodegen`/`WrapperCodegen` handles off the edges

#### Scenario: Dead multi-fire sibling is not rendered
- **WHEN** a target node has two sibling producing groups — one `SAT` and one `UNSAT_NO_PLAN` — and the method is rendered
- **THEN** `BuildMethodBodies` renders the `SAT` branch and never descends into the `UNSAT_NO_PLAN` branch

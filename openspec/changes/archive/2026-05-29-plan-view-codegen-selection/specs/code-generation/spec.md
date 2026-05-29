## MODIFIED Requirements

### Requirement: Method body composition algorithm

For each abstract method `m` on a `@Mapper` interface `<Name>`, `BuildMethodBodies` SHALL produce a `CodeBlock` representing the method body by recursing over `m`'s **plan view** — the subgraph returned by `MapperContext.getGraph().planView()` scoped to `m`'s `MethodScope`. The plan view contains only the edges of the chosen plan: `REALISED` edges that belong to a `SAT`-outcome group, with multi-fire OR-choices resolved to the cheapest branch (see the `graph-debug-output` capability's PlanView requirement). Because the plan view contains only the chosen plan, **every node in it has exactly one producer** (one inbound plan edge, or one producing group); `BuildMethodBodies` SHALL NOT guess among sibling groups or sibling inbound edges.

The recursion `render(node)` SHALL behave as follows:

1. **Leaf base case** — if `node` has no inbound plan edge, then `node` MUST be a `SourceLocation` whose `path.first()` matches the simple name of one of `m`'s parameters. The render result is `CodeBlock.of("$N", parameter.getSimpleName())`.
2. **Single-edge inductive case** — if `node` has exactly one inbound plan edge `e`, recurse on `e.getFrom()` to produce a child `CodeBlock`, then return `e.getCodegen().get().render(varNames, IncomingValues.of(childCodeBlock))` where `IncomingValues.of(...)` is the `IncomingValues` implementation backed by `single() = childCodeBlock`.
3. **Group-target inductive case** — if `node` carries (or is the root of) a group with a `GroupCodegen`, render each slot's incoming child `CodeBlock` (recursing on each predecessor), wrap them in an `IncomingValues` implementation whose `byName(slotName)` returns the matching child, and return `groupCodegen.render(varNames, incomingValues)`. Within the plan view at most one group is rooted at `node`.

When deriving the `slotName` key for a group slot in the group-target inductive case, `BuildMethodBodies` SHALL apply this rule on the slot Node's `Location`:

- If the `Location` is a `TargetLocation` with a non-empty path, the slot name is the last path segment.
- If the `Location` is a `SourceLocation` with a non-empty path (a container-unwrap group's source-side slot, e.g. `src[person.addresses]`), the slot name is the last path segment.
- If the `Location` is an `ElementLocation` (a container sub-group's element slot), the slot name is the location's `role` (e.g. `"element"`; `"key"` / `"value"` for map-shaped containers).
- Otherwise `BuildMethodBodies` SHALL raise an `IllegalStateException` naming the offending node.

The slot name is a stable per-slot map key. Container `GroupCodegen`s (`ListCollect`, `SetCollect`, `ArrayCollect`, `OptionalCollect`, the `*Wrap` family, …) read their input positionally via `inputs.single()` and do not depend on the key value; the rule's only obligation for them is to be total (never throw) and unique per slot. The `role` value satisfies both and stays compatible with name-based access (`inputs.byName("key")`) for multi-axis containers.

The full method body SHALL be `CodeBlock.builder().addStatement("return $L", renderedRoot).build()` for non-void methods. (Void methods are out of scope for slice 1.)

A `MethodImpl` value record holding `(ExecutableElement method, CodeBlock body, Set<TypeElement> requiredMapperDeps)` SHALL be produced per abstract method, where `requiredMapperDeps` is the empty set in slice 1.

#### Scenario: Leaf parameter renders by parameter name

- **WHEN** the plan view for method `Human map(Person person)` is walked and the leaf is the source node `src[person]:Person`
- **THEN** the rendered `CodeBlock` for that leaf is equivalent to `CodeBlock.of("$N", "person")`

#### Scenario: DirectAssign + single-segment path renders into a direct invocation

- **WHEN** the plan view for `Human map(Person person)` contains a path-segment plan edge `src[person] → src[person.firstName]:String` produced by `GetterPathResolver`, followed by a `DirectAssign` plan edge `src[person.firstName] → tgt[firstName]`
- **THEN** the rendered `CodeBlock` for `tgt[firstName]` is equivalent to `CodeBlock.of("$N.getFirstName()", "person")`

#### Scenario: ConstructorCall group assembles slot children

- **WHEN** the plan view for `Human map(Person person)` has a root group target whose `GroupCodegen` is `ConstructorCall`'s `new Human($L, $L)`, with two slots `firstName` and `lastName` each fed by a single-segment path
- **THEN** the rendered method body is equivalent to `return new Human(person.getFirstName(), person.getLastName());`

#### Scenario: Container group slot named by element role

- **WHEN** the plan view for a method contains a container group whose root is a `List`-typed (or `Set`/`Array`/`Optional`-typed) `TargetLocation` node and whose single slot is an `ElementLocation` element node
- **THEN** `BuildMethodBodies` derives the slot name as the `ElementLocation` role (`"element"`)
- **AND** renders the group via its `GroupCodegen` without raising `cannot derive slot name from node`

#### Scenario: Nested container groups compose without slot-name failure

- **WHEN** the plan view for a method has a target chain through nested containers — e.g. `tgt[addresses]:Optional<Set<Address>>` produced through an `OptionalCollect` group whose element slot is itself the root of a `SetCollect` group with an `ElementLocation` element slot
- **THEN** `render` derives a slot name for every `ElementLocation` slot in the nested chain
- **AND** the method body is produced without any `IllegalStateException`

#### Scenario: Dead multi-fire sibling is not rendered

- **WHEN** a target node has two sibling producing groups — one `SAT` (reaches a source parameter) and one `UNSAT_NO_PLAN` (a dead branch, e.g. an inverse-bridge match) — and the method is rendered
- **THEN** `BuildMethodBodies` renders the `SAT` group's branch
- **AND** never descends into the `UNSAT_NO_PLAN` branch (no `leaf node is not a SourceLocation` failure)

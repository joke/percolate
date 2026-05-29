## MODIFIED Requirements

### Requirement: Method body composition algorithm

For each abstract method `m` on a `@Mapper` interface `<Name>`, `BuildMethodBodies` SHALL produce a `CodeBlock` representing the method body by recursing over `m`'s realised subgraph (the subgraph of `MapperContext.getGraph()` reachable through `REALISED` edges and scoped to `m`'s `MethodScope`).

The recursion `render(node)` SHALL behave as follows:

1. **Leaf base case** — if `node` has no inbound `REALISED` edge in the realised subgraph, then `node` MUST be a `SourceLocation` whose `path.first()` matches the simple name of one of `m`'s parameters. The render result is `CodeBlock.of("$N", parameter.getSimpleName())`.
2. **Single-edge inductive case** — if `node` has exactly one inbound `REALISED` edge `e`, recurse on `e.getFrom()` to produce a child `CodeBlock`, then return `e.getCodegen().get().render(varNames, IncomingValues.of(childCodeBlock))` where `IncomingValues.of(...)` is the `IncomingValues` implementation backed by `single() = childCodeBlock`.
3. **Group-target inductive case** — if `node` carries (or is the root of) a group with a `GroupCodegen`, render each slot's incoming child `CodeBlock` (recursing on each predecessor), wrap them in an `IncomingValues` implementation whose `byName(slotName)` returns the matching child, and return `groupCodegen.render(varNames, incomingValues)`.

When deriving the `slotName` key for a group slot in the group-target inductive case, `BuildMethodBodies` SHALL apply this rule on the slot Node's `Location`:

- If the `Location` is a `TargetLocation` with a non-empty path, the slot name is the last path segment.
- If the `Location` is an `ElementLocation` (a container sub-group's element slot), the slot name is the location's `role` (e.g. `"element"`; `"key"` / `"value"` for map-shaped containers).
- Otherwise `BuildMethodBodies` SHALL raise an `IllegalStateException` naming the offending node.

The slot name is a stable per-slot map key. Container `GroupCodegen`s (`ListCollect`, `SetCollect`, `ArrayCollect`, `OptionalCollect`, the `*Wrap` family, …) read their input positionally via `inputs.single()` and do not depend on the key value; the rule's only obligation for them is to be total (never throw) and unique per slot. The `role` value satisfies both and stays compatible with name-based access (`inputs.byName("key")`) for multi-axis containers.

The full method body SHALL be `CodeBlock.builder().addStatement("return $L", renderedRoot).build()` for non-void methods. (Void methods are out of scope for slice 1.)

A `MethodImpl` value record holding `(ExecutableElement method, CodeBlock body, Set<TypeElement> requiredMapperDeps)` SHALL be produced per abstract method, where `requiredMapperDeps` is the empty set in slice 1.

#### Scenario: Leaf parameter renders by parameter name

- **WHEN** the realised subgraph for method `Human map(Person person)` is walked and the leaf is the source node `src[person]:Person`
- **THEN** the rendered `CodeBlock` for that leaf is equivalent to `CodeBlock.of("$N", "person")`

#### Scenario: DirectAssign + single-segment path renders into a direct invocation

- **WHEN** the realised subgraph for `Human map(Person person)` contains a path-segment REALISED edge `src[person] → src[person.firstName]:String` produced by `GetterPathResolver`, followed by a `DirectAssign` REALISED edge `src[person.firstName] → tgt[firstName]`
- **THEN** the rendered `CodeBlock` for `tgt[firstName]` is equivalent to `CodeBlock.of("$N.getFirstName()", "person")`

#### Scenario: ConstructorCall group assembles slot children

- **WHEN** the realised subgraph for `Human map(Person person)` has a root group target whose `GroupCodegen` is `ConstructorCall`'s `new Human($L, $L)`, with two slots `firstName` and `lastName` each fed by a single-segment path
- **THEN** the rendered method body is equivalent to `return new Human(person.getFirstName(), person.getLastName());`

#### Scenario: Container group slot named by element role

- **WHEN** the realised subgraph for a method contains a container group whose root is a `List`-typed (or `Set`/`Array`/`Optional`-typed) `TargetLocation` node and whose single slot is an `ElementLocation` element node
- **THEN** `BuildMethodBodies` derives the slot name as the `ElementLocation` role (`"element"`)
- **AND** renders the group via its `GroupCodegen` without raising `cannot derive slot name from node`

#### Scenario: Nested container groups compose without slot-name failure

- **WHEN** the realised subgraph for a method has a target chain through nested containers — e.g. `tgt[addresses]:Optional<Set<Address>>` produced through an `OptionalCollect` group whose element slot is itself the root of a `SetCollect` group with an `ElementLocation` element slot
- **THEN** `render` derives a slot name for every `ElementLocation` slot in the nested chain
- **AND** the method body is produced without any `IllegalStateException`

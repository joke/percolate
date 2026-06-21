## MODIFIED Requirements

### Requirement: Method bodies render by walking the extracted plan

`BuildMethodBodies` SHALL compose each method body by walking the extracted plan view
(`plan-extraction`) from the method's **seeded return-root `Value`** (the graph-recorded return root
per `graph-expansion` — the one Value matching the declared return type, never an over-emitted typed
sibling at the return location): render the Value's chosen producer `Operation` by invoking its codegen
with `IncomingValues` keyed by **port name**, where each incoming value is the recursively materialised
port `Value` — a **variable reference** when that `Value` is hoisted to a local (see "Assembly
arguments hoist to local variables"), otherwise its inline expression. A **direct container-return**
method SHALL render from this root exactly like any other: the root's producer is the container
`collect`/`wrap` Operation and the per-element transform is its child scope. Producer identity is
structural — the generator SHALL NOT infer it from shared codegen instances, edge labels, or any
grouping label, and SHALL NOT read `Nullability` to decide wiring.

#### Scenario: Fan-in renders from the chosen producer
- **WHEN** the return-root's chosen producer is `new Address(int,String)` with ports `number`,
  `street`
- **THEN** the body renders that Operation's codegen once, with incoming values keyed `number` and
  `street`

#### Scenario: No group or label reads
- **WHEN** the source of `BuildMethodBodies` is inspected
- **THEN** it references no grouping label, no group id, and no edge-carried consumer slot

#### Scenario: A direct container-return body renders from the seeded root
- **WHEN** `List<DAO> mapAddresses(Set<DTO>)` has a plan rooted at the seeded `List<DAO>` Value whose
  producer is `collect` over a `map` delegating to `mapAddress`
- **THEN** the body renders `return src.stream().map(e -> mapAddress(e)).collect(...)`, selecting the
  seeded root and ignoring dead typed siblings at the return location

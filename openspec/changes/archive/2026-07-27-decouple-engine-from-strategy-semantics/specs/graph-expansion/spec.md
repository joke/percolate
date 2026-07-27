## MODIFIED Requirements

### Requirement: Scopes declare base-case inputs uniformly

Each `Scope` SHALL declare its base-case inputs as a lazy sequence of input declarations, where an
input declaration is a scope-relative `(Location, type, nullness)` — an `AddValue` lacking only its
scope — plus a **name** and a **visibility** (`LOCAL` or `INHERITED`). A `MethodScope` SHALL declare one per
method parameter (a single-segment `SourceLocation`, named after the parameter); a `ChildScope` SHALL declare
its single element input (an `ElementLocation` with the element-in type/nullness); the mapper-root scope SHALL
declare none. There SHALL be exactly **one** declaration type and one declaration stream per scope.

An input declaration SHALL carry its nullness already resolved. `Scope` SHALL NOT accept a nullness-resolving
callback, and SHALL be plain data.

Source-binding and grounding-by-match SHALL consume this declaration **uniformly**, with no
`instanceof` test on the scope kind:

- the in-scope source **types** offered to grounding SHALL include the declared input types, available
  **without** materialising any `Value`;
- a `BY_TYPE` port whose `(type, nullness)` matches a declaration of the **scope's own** declarations SHALL be
  fed by materialising that declaration as a `LEAF` source `Value` on demand, idempotent through the `Value`
  dedup index. Visibility SHALL NOT widen type matching: an ancestor scope's `INHERITED` declaration is **not**
  a `BY_TYPE` candidate in a descendant scope;
- a `BY_NAME` port SHALL be fed by the declaration of that name in the scope's own declarations, or failing
  that in the nearest ancestor scope declaring it `INHERITED`, materialised at that declaration's own location
  so both access paths yield the identical `Value`.

The single intentional exception is the self-call rule, which is method-scope-only **by meaning** (a
named self-call) and remains so — see "A method never calls itself on its own whole parameter".

#### Scenario: Grounding sees a declared input type without materialising it

- **WHEN** grounding-by-match gathers the in-scope source types for a type-variable port in a scope
- **THEN** the scope's declared input types are included even when no corresponding `Value` has yet been materialised

#### Scenario: A matched input declaration is materialised on demand

- **WHEN** a `BY_TYPE` port's `(type, nullness)` matches a scope input declaration and no in-scope source `Value` yet exists
- **THEN** the declaration is materialised as a `LEAF` source `Value` in that scope
- **AND** a second match for the same declaration returns the same `Value` (dedup-idempotent)

#### Scenario: Input sourcing does not branch on scope kind

- **WHEN** the driver sources a port's input in a method scope versus in a child (element) scope
- **THEN** the same declaration-driven path is taken for both, with no `instanceof MethodScope` branch in source-binding, grounding, or accessor-root typing

#### Scenario: An inherited declaration is not a type-match candidate in a descendant

- **WHEN** a child (element) scope sources a `BY_TYPE` port whose type matches an ancestor method scope's `INHERITED` declaration
- **THEN** the ancestor's declaration is not offered, and the port is sourced or missed using only the child scope's own declarations

#### Scenario: One declaration backs both access paths, at the requesting scope

- **WHEN** a parameter declared `INHERITED` is reached once by a `BY_TYPE` port in its own scope and once by a `BY_NAME` port from a child scope
- **THEN** both materialise a `Value` at that declaration's location; a same-scope match dedups to the identical `Value`, while the child scope's match is its own `Value` at that location — a `Dep` edge never crosses a scope boundary, so the same declaration cannot back a single `Value` shared across scopes

### Requirement: A method never calls itself on its own whole parameter

The engine SHALL refuse to land a self-call operation whose argument port binds to the calling method's **own parameter-root `Value`** — the whole, unchanged parameter (`this.m(src)`), which is always degenerate (runtime infinite recursion). A self-call on a **strict sub-part** of the parameter (an accessor result such as `src.getNext()`, or a container element) SHALL remain available, because structural recursion over a shrinking input terminates. The decision SHALL be made **per binding** at land time, not per scope or per site: at one target site the engine over-emits both bindings and the degenerate one is *strictly cheaper* (the parameter-root costs nothing, an accessor costs `ACCESS`), so over-emit + cost-prune alone cannot choose correctly — the binding must be refused outright.

The refusal SHALL be expressed through the engine's single admissibility mechanism (see `demand-constraints`), not through a bespoke guard collaborator, so that the engine has exactly one way to refuse a candidate.

The engine SHALL recognise a self-call structurally by comparing the operation's **call target** (carried on its `OperationSpec`, see `expansion-strategy-spi`) against the current `MethodScope`'s method, matched by signature (name + parameter types); it SHALL NOT infer identity from the spec's `label`. The refusal SHALL apply only when the call target is the scope's own method **and** the bound argument is that scope's parameter-root `Value`; it SHALL NOT apply to a container's per-element transform (a separate child scope), and delegation to a *different* method returning the same type SHALL remain available. There SHALL be no change to the `CallableMethods` / `ResolveCtx` SPI and no loss of strategy myopia.

#### Scenario: A container-return method does not self-bridge
- **WHEN** a method returning a container over a mapped element type is expanded and its own signature appears among the callable candidates
- **THEN** the degenerate self-call binding on the whole parameter is refused and no such operation is landed

#### Scenario: Structural recursion on a sub-part remains available
- **WHEN** a method calls itself on an accessor result or a container element
- **THEN** the operation lands, because the bound argument is not the scope's parameter-root `Value`

#### Scenario: The refusal uses the one admissibility mechanism
- **WHEN** the expansion collaborators are inspected
- **THEN** the self-call rule is expressed as a constraint applied where every other candidate refusal is applied, and no separate guard class exists

### Requirement: Type-variable ports are sourced by grounding-by-match

When an `OperationSpec` port carries a type variable, the driver SHALL source it by **matching** the port type against the in-scope source `Value`s, grounding the variable to each matching source's concrete type, substituting it across the Operation's output and child scope, and landing one concrete Operation per match through the `Applier`. The driver SHALL NOT enqueue an unbound type. This is the same port-sourcing step as for a concrete port, generalised from exact-type matching to unification; it remains strictly target→source (`never_forward` holds) and over-emit-and-prune.

When a type variable carries a **bound** (see `strategy-refusals`), the driver SHALL consult it before binding the variable and SHALL NOT instantiate a spec for a grounding the bound refuses. A refused grounding SHALL contribute a refusal on the demanded `Value` and no Operation, so it never competes on cost and never reaches code generation. An unbounded variable SHALL behave exactly as before.

#### Scenario: A type-variable port grounds and lands concretely
- **WHEN** the driver sources a `Set<A>` port with a `Set<Person>` source in scope
- **THEN** it grounds `A := Person`, substitutes into the Operation (output and child scope), and lands a concrete `Set<Person> → …` Operation via the Applier
- **AND** it never enqueues a Value typed `Set<A>`

#### Scenario: Grounding-by-match preserves target-driven order
- **WHEN** grounding-by-match sources a type-variable port
- **THEN** the produced concrete inputs are re-demanded target→source like any other port; no forward sweep from sources occurs

#### Scenario: A bound refuses a grounding before it competes
- **WHEN** a bounded variable port is offered a source its bound refuses
- **THEN** no concrete Operation is instantiated for that source, and the bound's refusal is recorded on the demanded `Value`

#### Scenario: An unbounded variable grounds as before
- **WHEN** a variable port carries no bound
- **THEN** every declared or array source type in the widened match set grounds it, unchanged

## ADDED Requirements

### Requirement: A demand records the productions that were refused

A demanded `Value` SHALL carry, beside its producer `Operation`s, the **refusals** recorded against it: the
productions that were considered and found inadmissible, each with a `Subject` and a message. A refusal SHALL
arise from a strategy's refusal `Offer`, from a bound rejecting a grounding, from a `REQUIRE` port that could
not be sourced, or from a contributed constraint.

A refused production SHALL NOT become an `Operation`, SHALL NOT participate in the cost fold, and SHALL NOT be
reachable by plan extraction. The engine SHALL emit no diagnostic during expansion.

#### Scenario: A refused candidate leaves no operation
- **WHEN** any of the four refusal sources rejects a candidate
- **THEN** the demand acquires a refusal and no `Operation` vertex

#### Scenario: Expansion emits no diagnostic
- **WHEN** the expansion stage completes with refusals recorded
- **THEN** no diagnostic has been emitted; the refusals are rendered later by the realisation renderer

### Requirement: A REQUIRE port that cannot be sourced is refused with an engine-worded reason

The engine SHALL refuse the candidate and record a refusal naming the port, the binding name, and (for a type
mismatch) both types, when a port declares on-miss `REQUIRE` and no feeding `Value` can be sourced — because the
binding name is not published in scope, or the published binding's type is not assignable to the port's declared
type. The refusal SHALL be positioned at the operation's call target when it carries one, else at the mapper
type.

The message SHALL be written in port vocabulary and SHALL name no annotation, strategy, or feature. The engine
SHALL NOT re-derive which candidates a strategy considered: it reports only about specs a strategy actually
offered.

#### Scenario: An unpublished binding name is refused
- **WHEN** a candidate declares a `BY_NAME`/`REQUIRE` port for a binding name no enclosing scope publishes
- **THEN** the candidate is refused with a message naming the port and the missing binding name

#### Scenario: A type-incompatible binding is refused
- **WHEN** a `BY_NAME`/`REQUIRE` port's binding is published with a type not assignable to the port's declared type
- **THEN** the candidate is refused with a message naming both types

#### Scenario: The engine reports only about offered specs
- **WHEN** a callable method carrying an unsatisfiable named parameter is never offered by any strategy for a demand
- **THEN** no refusal is recorded for it, because the engine walks no candidate set of its own

#### Scenario: The message names no feature
- **WHEN** a `REQUIRE`-port refusal message is inspected
- **THEN** it names the port, the binding name and the types, and mentions no annotation or strategy

## REMOVED Requirements

### Requirement: Scopes carry an ambient environment that child scopes inherit

**Reason**: The requirement gave the engine a second, near-duplicate declaration channel named after one
user-facing feature. An ambient declaration was an input declaration plus a name, built by `MethodScope` from
the same parameters, at the same `SourceLocation`, with the same type and nullness — its own scenarios note
that both paths materialise the identical `Value`. The two real distinctions are orthogonal and feature-neutral:
how a port selects (by type or by name) and whether a declaration is visible to descendant scopes. Both are now
axes of the single input-declaration model, so the engine's vocabulary contains no feature name and
`internal/graph` reads no annotation.

**Migration**: A `MethodScope`'s `@Ambient` parameters are declared through the ordinary input-declaration
stream with `INHERITED` visibility and their published name; a `ChildScope` inherits by the `BY_NAME` lookup
walking ancestors rather than by re-exporting a second stream. The four scenarios are preserved under "Scopes
declare base-case inputs uniformly" and the `REQUIRE`-port requirement above: per-parameter declaration,
descendant resolution, no scope-kind branch, and an unresolved name reported loudly.

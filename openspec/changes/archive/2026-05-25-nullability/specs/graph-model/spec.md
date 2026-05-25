## MODIFIED Requirements

### Requirement: Node value type

The processor SHALL define a class `Node` in `io.github.joke.percolate.processor.graph` with the following fields and contract:

- `Optional<TypeMirror> type` — the type of the value the node represents; empty when the type is not yet known.
- `Optional<Nullability> nullability` — the nullability of the value the node represents; empty when the type is not yet known. **Always paired with `type`**: both are empty before producer commit; both are populated after.
- `Location loc` — where the node sits in the mapping (`SourceLocation`, `TargetLocation`, or `ElementLocation` for phantom container element nodes). Immutable.
- `Scope scope` — the method or mapper-level scope this node belongs to. Immutable.
- `Optional<Node> parent` — set ONLY for nodes whose `loc` is `ElementLocation`; empty for every other node. Immutable.
- `String id()` — a deterministic identity string for debug rendering and sorted iteration:
  - if `loc` is `ElementLocation`: `id()` = `parent.orElseThrow().id() + "::" + loc.segment() + "::" + typeEncode() + "@" + System.identityHashCode(this)`.
  - otherwise: `id()` = `scope.encode() + "::" + loc.segment() + "::" + typeEncode() + "@" + System.identityHashCode(this)`.
  - `typeEncode()` is the qualified type name when `type` is present, or `"?"` when absent.

Both `type` and `nullability` are **mutable after construction** under one paired one-shot accessor:

```java
public void setTyping(TypeMirror type, Nullability nullability);
```

`setTyping` SHALL throw if **either** `type` or `nullability` is already present at the call site. After a successful call, both fields are populated. The legacy single-field `setType(TypeMirror)` accessor SHALL be removed; callers SHALL migrate to `setTyping(...)`.

The `@<identityHashCode>` suffix disambiguates same-shape instances in DOT output and sorted iteration. It is the visible reflection of the underlying instance-identity rule.

`Node.equals(Object)` SHALL return `this == other`. `Node.hashCode()` SHALL return `System.identityHashCode(this)`. Two `Node` instances with field-equal `(loc, type, nullability, scope, parent)` are distinct nodes if they are different objects in memory.

The `Node` class SHALL implement `Comparable<Node>` via `id()` so that sorted iteration of nodes is well-defined.

Instance-identity is the load-bearing structural property that makes cross-sub-group cycles impossible during expansion: a `Node` freshly allocated by one sub-group's bridge expansion is a distinct object from any same-shape `Node` allocated by a sibling sub-group, so candidate scans within a sub-group's view cannot pick up downstream instances even when their `(scope, loc, type)` would match.

`SeedGraph` is responsible for emitting **one `Node` instance per directive-segment-prefix key** via its own internal deduplication (a `Map<KeyTuple, Node>` populated as the seed is walked). Idempotence at the graph level (`MapperGraph.addNode(node)` being a no-op on `node == existingNode`) means `SeedGraph`'s own dedup is the convergence mechanism for seed nodes; bridge expansion creates fresh distinct instances.

#### Scenario: Two field-equal Nodes are distinct under instance identity
- **WHEN** two `Node` instances are constructed with field-equal `loc`, `type`, `nullability`, `scope`, and `parent`
- **THEN** they compare unequal under `equals`
- **AND** their `hashCode`s differ
- **AND** their `id()` strings differ in the `@<identityHashCode>` suffix

#### Scenario: SeedGraph dedup yields a single Node instance per directive prefix
- **WHEN** two directives `@Map(source = "person.addresses")` and `@Map(source = "person.lastName")` are processed by `SeedGraph` for the same method
- **THEN** exactly one `Node` instance exists for `SourceLocation(["person"])` in the resulting graph
- **AND** both directives' downstream SEED edges share that single `Node` object

#### Scenario: setTyping updates both type and nullability in place
- **WHEN** a `Node` constructed with both `type` and `nullability` empty has `setTyping(<List<Opt<PA>>>, NULLABLE)` invoked
- **THEN** `getType()` returns `Optional.of(<List<Opt<PA>>>)`
- **AND** `getNullability()` returns `Optional.of(NULLABLE)`

#### Scenario: setTyping is one-shot — calling twice throws
- **WHEN** a `Node` has had `setTyping(...)` invoked once
- **AND** `setTyping(...)` is invoked again
- **THEN** an `IllegalStateException` is thrown

#### Scenario: setTyping rejects partial prior state
- **WHEN** a hypothetical Node has `type` set but `nullability` empty (or vice versa)
- **AND** `setTyping(...)` is invoked
- **THEN** an `IllegalStateException` is thrown — both fields must be empty before the call

#### Scenario: Legacy setType is removed
- **WHEN** the public API of `Node` is inspected
- **THEN** no method named `setType(TypeMirror)` exists
- **AND** the only typing-mutation accessor is `setTyping(TypeMirror, Nullability)`

#### Scenario: Source-rooted node id includes identity suffix
- **WHEN** a `Node` is constructed with `loc = SourceLocation(["person"])`, `type = Optional.of(<Person>)`, `nullability = Optional.of(NON_NULL)`, `scope = MethodScope(<map(Person)>)`, `parent = Optional.empty()`
- **THEN** `id()` is the string `"v::map(Person)::person@" + identityHashCode` (or the equivalent documented encoding)
- **AND** repeated invocations of `id()` return the same string

#### Scenario: Target-rooted node id with unknown type
- **WHEN** a `Node` is constructed with `loc = TargetLocation(["lastName"])`, `type = Optional.empty()`, `nullability = Optional.empty()`, `scope = MethodScope(<map(Person)>)`, `parent = Optional.empty()`
- **THEN** `id()` includes the `?` marker for the unknown type so that later type discovery (via `setTyping`) does not collide

#### Scenario: Phantom node id derives from parent, role, and type
- **WHEN** a phantom `Node` is constructed with `loc = ElementLocation("element")`, `type = Optional.of(<String>)`, `nullability = Optional.of(NON_NULL)`, `scope` matching its parent, and `parent = Optional.of(<container node>)`
- **THEN** `id()` starts with the parent's `id()` prefix, followed by `::elem(element)::String@<identityHashCode>`

#### Scenario: Phantom node without parent throws
- **WHEN** a `Node` is constructed with `loc = ElementLocation` and `parent = Optional.empty()`, then `id()` is invoked
- **THEN** an unchecked exception is thrown

#### Scenario: Two phantoms with different parents are distinct
- **WHEN** two `Node` instances are constructed with the same `loc = ElementLocation("element")`, same `type`, same `nullability`, same `scope`, but different `parent` values
- **THEN** they compare unequal under `equals`

#### Scenario: Two phantoms with same parent and role but different types are distinct
- **WHEN** two phantom `Node` instances are constructed with the same `loc = ElementLocation("element")`, same `scope`, same `parent`, but different `type` values
- **THEN** they compare unequal under `equals` (which is true vacuously under instance identity, but the contract holds without relying on it)

#### Scenario: Two phantoms with same parent and type but different roles are distinct
- **WHEN** two phantom `Node` instances are constructed with `loc = ElementLocation("key")` and `loc = ElementLocation("value")` respectively, same `scope`, same `parent`, same `type`
- **THEN** they compare unequal under `equals`

## MODIFIED Requirements

### Requirement: ElementLocation case

The processor SHALL define a `Location` implementation `ElementLocation` for phantom container element nodes. `ElementLocation` SHALL carry a single `String role` field that discriminates between scopes within multi-role containers; the default value for single-element-scope containers is the literal string `"element"`.

`ElementLocation` SHALL be a Lombok `@Value` (or equivalent immutable) type. Its `segment()` SHALL return the string `"elem(" + role + ")"` — e.g. `"elem(element)"` for the default role, `"elem(key)"` / `"elem(value)"` for future `Map<K,V>` element scopes.

A no-argument public constructor SHALL be preserved (whether via a static factory or a secondary constructor) that produces an `ElementLocation` with `role = "element"`. This preserves call-site compatibility with existing test fixtures and node-construction code that does not yet thread a role.

Two `ElementLocation` instances SHALL compare equal iff their `role` fields are equal.

#### Scenario: ElementLocation carries role
- **WHEN** an `ElementLocation` is constructed with `role = "key"`
- **THEN** `getRole()` returns `"key"`
- **AND** `segment()` returns `"elem(key)"`

#### Scenario: ElementLocation default role is "element"
- **WHEN** an `ElementLocation` is constructed via the no-argument factory / constructor
- **THEN** `getRole()` returns `"element"`
- **AND** `segment()` returns `"elem(element)"`

#### Scenario: ElementLocation equality by role
- **WHEN** two `ElementLocation` instances are constructed with the same `role`
- **THEN** they are `equal` and have equal `hashCode`s

#### Scenario: ElementLocation inequality by role
- **WHEN** two `ElementLocation` instances are constructed with different `role` strings (e.g. `"key"` vs `"value"`)
- **THEN** they are NOT `equal`

### Requirement: Node value type

The processor SHALL define a Lombok `@Value` class `Node` in the `io.github.joke.percolate.processor.graph` package with the following fields and contract:
- `Optional<TypeMirror> type` — the type of the value the node represents; empty when the type is not yet known (directive-seeded source/target nodes at seed time).
- `Location loc` — where the node sits in the mapping (`SourceLocation`, `TargetLocation`, or `ElementLocation` for phantom container element nodes).
- `Scope scope` — the method or mapper-level scope this node belongs to.
- `Optional<Node> parent` — set ONLY for nodes whose `loc` is `ElementLocation` (phantom container element nodes); empty for every other node. Carries the live reference to the container node from which this phantom was derived.
- `String id()` — a deterministic, stable identity string assembled by `Node` (NOT delegated to `Location`):
  - if `loc` is `ElementLocation`: `id()` = `parent.orElseThrow().id() + "::" + loc.segment() + "::" + typeEncode()` — the parent-derived prefix, followed by `"::elem(<role>)"`, followed by `"::"` and the type encoding.
  - otherwise: `id()` = `scope.encode() + "::" + loc.segment() + "::" + typeEncode()` where `typeEncode()` is the qualified type name when `type` is present, or `"?"` when absent.

The element-node id SHALL include the type encoding. This is required so that two element nodes sharing the same parent and role but holding different types (e.g., chain intermediates inside an element scope) produce distinct ids and do not collide under the identity rule.

For non-phantom nodes, `id()` SHALL produce the same string as prior implementations produced for the equivalent `(scope, loc, type)` triple — non-phantom id stability is required so existing seed-graph tests are unaffected.

The `Node` class SHALL implement `Comparable<Node>` via `id()` so that sorted iteration of nodes is well-defined.

`Node.parent` SHALL participate in `equals` and `hashCode` (default Lombok `@Value` behaviour). Two non-phantom nodes both with `parent = Optional.empty()` therefore compare equal under the same conditions as in prior implementations.

#### Scenario: Source-rooted node id
- **WHEN** a `Node` is constructed with `loc = SourceLocation(["person"])`, `type = Optional.of(<Person>)`, `scope = MethodScope(<map(Person)>)`, `parent = Optional.empty()`
- **THEN** `id()` is the string `"v::map(Person)::person"` (or the equivalent encoding documented in the implementation)
- **AND** repeated invocations of `id()` return the same string

#### Scenario: Target-rooted node id with unknown type
- **WHEN** a `Node` is constructed with `loc = TargetLocation(["lastName"])`, `type = Optional.empty()`, `scope = MethodScope(<map(Person)>)`, `parent = Optional.empty()`
- **THEN** `id()` includes the `?` marker for the unknown type so that later type discovery does not collide with the seed-time identity

#### Scenario: Phantom node id derives from parent, role, and type
- **WHEN** a phantom `Node` is constructed with `loc = ElementLocation("element")`, `type = Optional.of(<String>)`, `scope` matching its parent, and `parent = Optional.of(<container node with id "v::map(Foo)::input">)`
- **THEN** `id()` is the string `"v::map(Foo)::input::elem(element)::String"` (qualified type name as produced by `typeEncode()`)

#### Scenario: Phantom node without parent throws
- **WHEN** a `Node` is constructed with `loc = ElementLocation(...)` and `parent = Optional.empty()`, then `id()` is invoked
- **THEN** an unchecked exception is thrown (the construction violates the schema invariant)

#### Scenario: Two non-phantom nodes with equal data have equal ids
- **WHEN** two `Node` instances are constructed with field-equal `loc`, `type`, `scope`, and both `parent = Optional.empty()`
- **THEN** they compare equal under `equals` and produce identical `id()` values

#### Scenario: Two phantoms with different parents have different ids
- **WHEN** two `Node` instances are constructed with the same `loc = ElementLocation("element")`, same `type`, same `scope`, but different `parent` values
- **THEN** they produce different `id()` values and compare unequal under `equals`

#### Scenario: Two phantoms with same parent and role but different types have different ids
- **WHEN** two phantom `Node` instances are constructed with the same `loc = ElementLocation("element")`, same `scope`, same `parent`, but different `type` values
- **THEN** they produce different `id()` values and compare unequal under `equals`

#### Scenario: Two phantoms with same parent and type but different roles have different ids
- **WHEN** two phantom `Node` instances are constructed with `loc = ElementLocation("key")` and `loc = ElementLocation("value")` respectively, same `scope`, same `parent`, same `type`
- **THEN** they produce different `id()` values and compare unequal under `equals`

### Requirement: Location interface and cases

The processor SHALL define a `Location` interface (package-private in the `graph` sub-package) with three implementations:
- `SourceLocation(AccessPath path)` — rooted at a method parameter; the first segment of `path` is the parameter name.
- `TargetLocation(TargetPath path)` — rooted at the method return type; an empty path denotes the return-type root itself.
- `ElementLocation(String role)` — marker for phantom container element nodes; the `role` field discriminates between element scopes within multi-role containers. The default role for single-element-scope containers is `"element"`. The parent reference lives on `Node.parent`.

All cases SHALL be Lombok `@Value`. The interface SHALL declare:
```java
interface Location {
    String segment();   // this location's contribution to Node.id()
}
```

`SourceLocation.segment()` SHALL return a stable encoding of the access path's segments. `TargetLocation.segment()` SHALL return a stable encoding of the target path's segments (with empty path encoded as `"[]"` or equivalent). `ElementLocation.segment()` SHALL return the string `"elem(" + role + ")"`. The renderer is permitted to use `segment()` as a label fallback for DOT output.

#### Scenario: Empty target path denotes the return-type root
- **WHEN** a `TargetLocation` is constructed with an empty `TargetPath`
- **THEN** the location's text-encoding identifies the return-type root unambiguously (e.g., `[]`)
- **AND** a node with this location is the destination of any chain of target slots produced by a `@Map` directive on the same method

#### Scenario: Multi-segment access path
- **WHEN** a `SourceLocation` is constructed with `AccessPath` of `["person", "address", "street"]`
- **THEN** the location's text-encoding preserves the segment order

#### Scenario: ElementLocation segment includes role
- **WHEN** an `ElementLocation("element").segment()` is invoked
- **THEN** the returned string is exactly `"elem(element)"`
- **WHEN** an `ElementLocation("key").segment()` is invoked
- **THEN** the returned string is exactly `"elem(key)"`

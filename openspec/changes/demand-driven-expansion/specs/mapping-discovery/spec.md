## ADDED Requirements

### Requirement: Declared-bindings goal spec derived during discovery

The discovery phase SHALL derive, per abstract method, the per-level **declared-bindings goal spec**
from that method's discovered `@Map` directives, and make it available to expansion via the per-mapper
context (keyed by method scope). It SHALL group directives by dotted target-path level: every prefix
of a directive's target contributes its next segment as a declared child at that level, and the full
target path binds the leaf directive. Directives carrying `constant` or `defaultValue` SHALL appear as
bindings like any other. This derivation SHALL NOT be performed by a seed stage (there is none); it is
a pure reshaping of already-discovered directives.

#### Scenario: Nested target paths group by level

- **WHEN** directives declare `address.street` and `address.zip`
- **THEN** the root-level goal declares `{address}` and the `address`-level goal declares
  `{street, zip}`

#### Scenario: Constant directive participates as a binding

- **WHEN** a directive declares `constant = "42"` for target `number`
- **THEN** `number` appears in the derived goal spec as a binding

#### Scenario: Goal spec is available to expansion without a seed stage

- **WHEN** expansion processes a method's return-root demand
- **THEN** the method's goal spec is obtained from the per-mapper context (derived during discovery),
  not produced by any seed stage

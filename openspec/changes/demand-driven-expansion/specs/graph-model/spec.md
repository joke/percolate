## MODIFIED Requirements

### Requirement: Location carries a role

`Location` SHALL expose a `role()` returning its **resolution mode** — one of `FREE` (target values
and conversion intermediates), `ACCESS` (multi-segment source-path values), `LEAF` (single-segment
source-path parameter roots and container element roots), or `CONSTANT`. The mode SHALL be derivable
from the concrete `Location` (kind plus access-path length): a `TargetLocation` is `FREE`; a
`SourceLocation` is `ACCESS` when its access path has more than one segment and `LEAF` when it has
exactly one; an `ElementLocation` is `LEAF`; a `ConstantLocation` is `CONSTANT`. The work-list
dispatch and the cost base-case rule SHALL be made through `role()`, not through `instanceof` on the
concrete `Location` implementations, so the distinction lives in one place.

#### Scenario: Base case keys off LEAF

- **WHEN** the cost fold determines whether a producerless `Value` is a base case
- **THEN** it consults `value.getLoc().role() == LEAF`, not an `instanceof` chain

#### Scenario: A multi-segment source location reports ACCESS

- **WHEN** a `SourceLocation` with access path `[p, address, street]` reports its `role()`
- **THEN** it returns `ACCESS`

#### Scenario: A single-segment source location reports LEAF

- **WHEN** a `SourceLocation` with access path `[p]` reports its `role()`
- **THEN** it returns `LEAF`

#### Scenario: Each remaining Location implementation reports its mode

- **WHEN** `TargetLocation`, `ElementLocation`, and `ConstantLocation` are inspected
- **THEN** they report `FREE`, `LEAF`, and `CONSTANT` respectively

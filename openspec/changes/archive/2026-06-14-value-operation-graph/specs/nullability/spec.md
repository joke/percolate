## ADDED Requirements

### Requirement: Nullness is part of Value identity

A `Value`'s nullness SHALL be part of its identity key (`scope`, `location`, `type`, `nullness`):
every Value has exactly one definite nullness, set with its type, never resolved per chosen
producer. `NullabilityResolver` is consulted at expansion commit sites when Operations declare their
output and port nullness; code generation never consults it.

#### Scenario: Divergent nullness yields distinct Values
- **WHEN** one producer yields `street:String` NULLABLE and a port demands `street:String` NON_NULL
- **THEN** two distinct Values exist, connected only through an explicit crossing Operation

### Requirement: The NULLABLE to NON_NULL crossing is an explicit Operation

The only bridged nullness crossing SHALL be `NULLABLE → NON_NULL`, represented as a unary plan
Operation: `[requireNonNull]` rendering
`java.util.Objects.requireNonNull(expr, "source for slot '<name>' is null but target is non-null")`
by default, or `[coalesce <literal>]` when the binding's directive declares a `defaultValue` (see
`default-values`). Exactly one of the two is emitted per crossing. All other combinations
(including `UNKNOWN` in either position) SHALL pass through without an Operation, preserving the
shipped lenient acceptance.

#### Scenario: Crossing without default emits requireNonNull
- **WHEN** a NULLABLE source Value feeds a NON_NULL port and no default is declared
- **THEN** the plan contains a `[requireNonNull]` Operation between the two Values

#### Scenario: UNKNOWN passes through
- **WHEN** a producer's nullness is UNKNOWN and the port demands NON_NULL
- **THEN** no crossing Operation is emitted and the value passes through unchanged

## REMOVED Requirements

### Requirement: Engine stamps Nullability paired with Node typing
**Reason**: Nullness is identity, not a stamped attribute; there is no late pairing step.
**Migration**: See ADDED "Nullness is part of Value identity".

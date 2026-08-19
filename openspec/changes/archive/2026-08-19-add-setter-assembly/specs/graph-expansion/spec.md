## MODIFIED Requirements

### Requirement: Assembly is gated by the declared-bindings goal spec

Assembly strategies SHALL interpret the demand's declared bindings (`{child name → directive}`) at
Operation-emission time, each declaring the gate its own construction form requires. For constructors (all
parameters mandatory) the gate is exact consumption: a constructor is a candidate iff its parameter-name set
equals the declared-children name set. A zero-parameter constructor is therefore never a candidate when
bindings are declared — vacuous SAT cannot drop user mappings.

For the forms whose members are optional the gate is containment. A builder is a candidate iff the
declared-children name set is a **subset** of the names its convention resolves to single-value setters on the
builder type; a JavaBean is a candidate iff that set is a subset of the names its `setX` methods resolve to (see
the `setter-assembly` capability). Equality would be wrong for either, since a builder and a bean alike commonly
expose far more setters than a given mapping declares. Every assembly strategy SHALL nonetheless preserve the
empty-declaration bail — a demand declaring no children is never assembled — so the empty set cannot vacuously
satisfy a leaf demand under any of these gates.

The gate is a strategy-local emission decision. The driver SHALL NOT read the declared-children set when
binding ports, and SHALL NOT arbitrate between assembly forms; where more than one form passes its own gate,
every such Operation is emitted and cost extraction selects between them.

#### Scenario: Subset constructor rejected at emission
- **WHEN** `Address()` and `Address(int number, String street)` exist and `number`, `street` are
  declared
- **THEN** only the two-parameter constructor is emitted as an Operation

#### Scenario: A builder with surplus setters passes the containment gate
- **WHEN** `number` and `street` are declared and `Address`'s builder exposes setters for `number`,
  `street`, and `country`
- **THEN** the builder is emitted as an Operation with sub-target ports for `number` and `street` only

#### Scenario: A declared child outside the builder's setters is rejected
- **WHEN** `number` and `country` are declared and `Address`'s builder exposes setters for `number` and
  `street` only
- **THEN** no builder Operation is emitted for that demand

#### Scenario: An empty declaration is never assembled under any gate
- **WHEN** a demand declares no children and the target has a no-argument constructor, a builder, and JavaBean
  setters
- **THEN** no assembly Operation of any form is emitted

#### Scenario: Overloaded constructors coexist structurally
- **WHEN** `Address(int number, String street)` and `Address(long number, String street)` both pass
  the gate
- **THEN** both Operations are emitted, sharing the `street:String` port Value, with distinct
  `number:int` / `number:long` port Values, and plan extraction selects between them

#### Scenario: Operations of several assembly forms coexist and are priced, not arbitrated
- **WHEN** a target passes the constructor's equality gate and a builder's and a bean's containment gates
- **THEN** all three Operations are emitted and the driver applies no preference of its own
- **AND** cost extraction selects the one whose strategy priced itself lowest

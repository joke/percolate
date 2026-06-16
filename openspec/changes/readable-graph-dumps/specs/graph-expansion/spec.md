## ADDED Requirements

### Requirement: Operation label comes from the strategy spec, not the codegen class

When the driver lands an `OperationSpec` as an `Operation`, the Operation's `label` SHALL be the
spec's strategy-supplied `label`; the driver SHALL NOT derive any label (or a strategy FQN) from the
codegen handle's runtime class. The accessor handler, which emits accessor Operations directly, SHALL
supply an equivalent typed label (e.g. `getStreet()`).

#### Scenario: Landed Operation carries the spec's label
- **WHEN** the driver lands a `WidenPrimitive` spec whose `label` is `int→long`
- **THEN** the resulting `Operation.label` is `int→long`
- **AND** no `$$Lambda` codegen class name appears in the Operation's label

#### Scenario: Accessor operations are labelled by their access
- **WHEN** the work-list resolves the `street` segment of a source path via a getter
- **THEN** the landed accessor Operation's label is the access form (e.g. `getStreet()`), not a
  codegen class name

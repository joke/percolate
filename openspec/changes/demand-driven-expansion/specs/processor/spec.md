## ADDED Requirements

### Requirement: Stage classes follow the *Stage naming convention

Every processor pipeline stage that `implements Stage` SHALL have a class name ending in `Stage`,
reflecting that each stage progressively refines the single `MapperGraph` and that there is no
separate intermediate graph artifact. Internal `*Phase` orchestration classes (which do not
`implement Stage`) are exempt.

#### Scenario: All Stage implementations end in Stage

- **WHEN** every class implementing `Stage` under `processor/src/main/java/.../stages/` is inspected
- **THEN** each class name ends with the suffix `Stage`

#### Scenario: Phase classes are exempt

- **WHEN** an internal orchestration class that does NOT implement `Stage` is inspected
- **THEN** it is permitted to use the `*Phase` suffix and is not subject to the `*Stage` rule

## MODIFIED Requirements

### Requirement: Engine internals are encapsulated from other modules

No production or test class outside the `processor` module SHALL depend on any `processor` `internal`
package. Other modules SHALL reach the engine only through its public surface and through `spi`. This makes
"reaching into engine internals" a build-checkable violation rather than a matter of convention. The check
SHALL run in each module that can see the engine's test classpath without cross-project probing —
`strategies-builtin`, `reactor`, and `reactor-blocking` each run it against their own main and test classes
— built on a single shared ArchUnit rule published as `architecture-tests`' `testFixtures`, rather than as
one central check in `architecture-tests` reaching into sibling modules' build output directories.

#### Scenario: External code may not import engine internals
- **WHEN** each of `strategies-builtin`, `reactor`, and `reactor-blocking` runs its own architecture check
  against its own main and test classes
- **THEN** no class in that module — production or test — depends on a `processor` `internal` package, and a
  newly introduced such dependency fails that module's own build

#### Scenario: The rule is shared, not duplicated per module
- **WHEN** the rule-construction logic backing this check is located
- **THEN** it exists once, published as `architecture-tests`' `testFixtures`, and each consuming module's
  own spec calls into it rather than re-implementing the ArchUnit rule

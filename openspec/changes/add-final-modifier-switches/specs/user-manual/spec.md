## MODIFIED Requirements

### Requirement: The manual documents each compile-time processor option

The manual SHALL contain a compile-time-switches reference, co-located in the `processor` module, that
documents each processor option (e.g. `docTags`, `locals.final`/`locals.var`, `parameters.final`,
`methods.final`, `classes.final`, `nullable.annotations`, `debug.graphs`) with a worked example and the
**generated output that shows the option's effect**, single-sourced from real generation. The
`classes.final` entry SHALL note that its default changed from the previously unconditional final class.

#### Scenario: Each switch shows its effect
- **WHEN** the compile-time-switches page is read
- **THEN** each documented option shows an example and its materialised generated output demonstrating the
  option's effect

#### Scenario: The three finality switches appear in the switches reference
- **WHEN** the compile-time-switches reference is inspected
- **THEN** it documents `-Apercolate.parameters.final`, `-Apercolate.methods.final`, and
  `-Apercolate.classes.final`, each with a worked example and its materialised generated output
- **AND** the `classes.final` entry states that the generated class defaults to non-final, a change from
  prior behavior

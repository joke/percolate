## ADDED Requirements

### Requirement: javax.lang.model is confined to the processor boundary

An ArchUnit rule in the `architecture-tests` module SHALL confine `javax.lang.model` imports to the
processor's boundary packages: the annotation-processing entry points (`PercolateProcessor`, `MapperStep`,
`ProcessorModule` wiring), the discovery/adapter packages (where the `TypeSpace` and model values are
materialised), the nullability resolver, and the diagnostics emission (where `Origin` tokens resolve back to
`Element`s). All other production code — the `spi` module in its entirety, every strategy module
(`strategies-builtin`, `reactor`, `reactor-blocking`), and the engine internals (`graph`, `stages.expand`,
`stages.generate`, `stages.validate`, `stages.dump`) — SHALL NOT import any `javax.lang.model` type. The
rule exists so the evicted currency can never leak back.

#### Scenario: The SPI is javax.lang.model-free
- **WHEN** the ArchUnit suite runs over `percolate-spi`'s production classes
- **THEN** no class imports any `javax.lang.model` type

#### Scenario: Strategy modules are javax.lang.model-free
- **WHEN** the ArchUnit suite runs over `strategies-builtin`, `reactor`, and `reactor-blocking` production classes
- **THEN** no class imports any `javax.lang.model` type

#### Scenario: Engine internals are javax.lang.model-free
- **WHEN** the ArchUnit suite runs over `processor`'s internal graph and non-boundary stage packages
- **THEN** no class outside the declared boundary packages imports any `javax.lang.model` type

#### Scenario: A regression fails the build
- **WHEN** a new strategy or engine class adds a `javax.lang.model` import outside the boundary packages
- **THEN** the `architecture-tests` check fails, naming the offending class and rule

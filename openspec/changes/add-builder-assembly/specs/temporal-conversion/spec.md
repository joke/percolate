## MODIFIED Requirements

### Requirement: Zone resolution precedence

The zone used by the bridge SHALL be resolved by precedence: (1) a present `@Map(zone = "…")` renders
`ZoneId.of("…")` frozen in the generated code; else (2) a `-Apercolate.time.zone=…` processor option renders
`ZoneId.of("…")` frozen; else (3) the generated code renders `ZoneId.systemDefault()`, resolved at the
consumer's runtime. The processor SHALL NOT read its own build-JVM zone and freeze it into generated code.

The strategy SHALL obtain the option value at step (2) through the generic seam lookup
`ResolveCtx.option("percolate.time.zone")` and interpret the raw value itself. It SHALL NOT read a
feature-named accessor; `ResolveCtx.configuredTimeZone()` no longer exists. The resolution precedence and every
rendered outcome are unchanged — only the read path moves.

#### Scenario: Directive zone wins over the option

- **WHEN** `-Apercolate.time.zone=UTC` is set and a binding declares `@Map(zone = "Europe/Berlin")`
- **THEN** the generated bridge code uses `ZoneId.of("Europe/Berlin")`

#### Scenario: The option zone is read through the generic seam

- **WHEN** `-Apercolate.time.zone=UTC` is set and no binding declares a zone
- **THEN** the strategy reads the value via `ResolveCtx.option("percolate.time.zone")`
- **AND** the generated bridge code uses `ZoneId.of("UTC")`

#### Scenario: Unset zone defers to runtime systemDefault

- **WHEN** neither `@Map(zone = …)` nor `-Apercolate.time.zone` is set for a cross-family conversion
- **THEN** the generated bridge code uses `ZoneId.systemDefault()`
- **AND** no literal zone id from the build machine appears in the generated source

#### Scenario: No feature-named zone accessor remains

- **WHEN** the temporal strategy's source is inspected
- **THEN** it contains no call to `configuredTimeZone()`

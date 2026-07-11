## ADDED Requirements

### Requirement: @Map format and zone members SHALL be discovered against the UNSET sentinel

`DiscoverMappingsStage` SHALL read the `@Map` members `format` and `zone`, each treated as **present** when its value is not equal to `Map.UNSET` and **absent** otherwise, exactly as `constant` and `defaultValue` are discovered. Discovery SHALL NOT use `String.isEmpty()` to decide presence. The discovered `format` and `zone` SHALL be carried on the `MappingDirective` and surfaced to strategies through the `Directive` SPI type as options.

#### Scenario: A format directive is discovered

- **WHEN** an abstract method is annotated with `@Map(target = "day", source = "in.ts", format = "yyyy-MM-dd")`
- **THEN** the resulting `MappingDirective` reports `format` present with value `"yyyy-MM-dd"`
- **AND** reports `zone` absent (it equals `Map.UNSET`)

#### Scenario: A zone directive is discovered

- **WHEN** an abstract method is annotated with `@Map(target = "at", source = "in.local", zone = "Europe/Berlin")`
- **THEN** the resulting `MappingDirective` reports `zone` present with value `"Europe/Berlin"`

#### Scenario: Absent format and zone are reported absent

- **WHEN** an abstract method is annotated with `@Map(target = "name", source = "in.name")`
- **THEN** the resulting `MappingDirective` reports both `format` and `zone` absent (each equal to `Map.UNSET`)

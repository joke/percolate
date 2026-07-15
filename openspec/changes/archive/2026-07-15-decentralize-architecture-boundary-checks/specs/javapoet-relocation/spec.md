## MODIFIED Requirements

### Requirement: Upstream JavaPoet is fully swallowed

`percolate-javapoet` SHALL fully absorb upstream JavaPoet: its published POM and Gradle metadata SHALL
declare **zero** dependency on `com.palantir.javapoet`. This guarantees percolate's own published artifact
is immune to another annotation processor contributing a different `com.palantir.javapoet` version to a
shared processorpath.

#### Scenario: Published metadata declares no upstream dependency

- **WHEN** the `percolate-javapoet` POM and Gradle metadata are inspected
- **THEN** they declare no dependency on `com.palantir.javapoet:javapoet`

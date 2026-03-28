## MODIFIED Requirements

### Requirement: Test variables use final

All local variables in Spock specifications SHALL be declared with `final` instead of `def`.

#### Scenario: Local variables in feature methods use final
- **WHEN** a Spock feature method declares a local variable
- **THEN** it SHALL use `final` (e.g., `final result = ...`) instead of `def` (e.g., `def result = ...`)

### Requirement: State assertions in trailing expect blocks

State assertions SHALL appear in trailing `expect:` blocks, not in `then:` blocks. `then:` blocks SHALL contain only interaction verifications and `0 * _`.

#### Scenario: PipelineSpec separates interactions from state
- **WHEN** `PipelineSpec` verifies a pipeline failure
- **THEN** interaction verifications (e.g., `1 * messager.printMessage(...)`) SHALL be in `then:` and state assertions (e.g., `result == null`) SHALL be in a trailing `expect:` block

#### Scenario: Tests without interactions use expect only
- **WHEN** a feature method has no mock interactions to verify (only state assertions)
- **THEN** it SHALL use `expect:` blocks instead of `when:/then:` blocks

### Requirement: Strict interaction verification

Every `then:` block that contains interaction verifications SHALL end with `0 * _` to prevent unexpected interactions.

#### Scenario: PipelineSpec then blocks end with strict verifier
- **WHEN** `PipelineSpec` verifies mock interactions in a `then:` block
- **THEN** the block SHALL end with `0 * _`

### Requirement: Groovy property access in stubs

Stub and mock configurations SHALL use Groovy property access syntax for JavaBean getter methods.

#### Scenario: Stub uses property access for getKind
- **WHEN** a stub configures `getKind()` behavior
- **THEN** it SHALL use `kind >> value` instead of `getKind() >> value`

#### Scenario: Stub uses property access for getModifiers
- **WHEN** a stub configures `getModifiers()` behavior
- **THEN** it SHALL use `modifiers >> value` instead of `getModifiers() >> value`

#### Scenario: Stub uses property access for other getters
- **WHEN** a stub configures any JavaBean getter (`getX()`, `isX()`)
- **THEN** it SHALL use property access syntax (`x >> value`)

### Requirement: Single-quoted strings for plain literals

All plain string literals (no interpolation) in Groovy test code SHALL use single quotes.

#### Scenario: Feature method names use single quotes
- **WHEN** a feature method is defined
- **THEN** its name SHALL use single-quoted strings

#### Scenario: String values use single quotes
- **WHEN** a plain string value is used in assertions or stub configuration
- **THEN** it SHALL use single quotes (not double quotes)

### Requirement: Call sites updated for record accessors

Test code that accesses properties on classes converted from Lombok `@Value` to records SHALL use the record accessor naming convention via Groovy property access.

#### Scenario: Groovy property access works with records
- **WHEN** test code accesses `result.value().methods` on a record
- **THEN** Groovy SHALL resolve property access `methods` to the record accessor `methods()` transparently

#### Scenario: Lombok getter call sites updated
- **WHEN** test code previously called Lombok-generated getters like `getMapperType()`, `getMethods()`, `getDirectives()`
- **THEN** these SHALL be updated to use Groovy property access (`mapperType`, `methods`, `directives`) which resolves to record accessors

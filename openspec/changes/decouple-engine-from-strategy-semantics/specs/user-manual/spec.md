## MODIFIED Requirements

### Requirement: The manual covers the bean-mapping consumer topics

The manual SHALL contain pages that document, for a Java developer mapping beans: project integration with
**both Maven and Gradle**; a quick-start minimal mapper; basic mapper structure (which methods are
discovered versus skipped), including a worked example of an **abstract-class `@Mapper`** and a worked
example of **cross-supertype method discovery** (an unannotated interface's abstract and `default` methods,
implemented by an `@Mapper` abstract class that adds its own abstract and concrete methods); the `@Map`
annotation including `target`, `source`, `constant`, and `defaultValue`, and stating that a member is in
effect exactly when it is **written** — an empty string being a written value, distinct from omitting the
member; nested target and source path chains; **path access over getters, record accessors, and public
fields**; **collection mapping shown as a progression with a worked example per container mechanism** —
same-kind (`List<X>→List<Y>`), cross-kind conversion (`Set→List`), a stream intermediate (`Stream→Set`), and
presence composed inside/outside a container; **Optional mapping** (wrapping, unwrapping, and composed with
containers); **default values and JSpecify nullability crossings**; conversion methods; and default-method
conversions. Each feature section SHALL show a worked example, not a prose-only assertion of support.

The manual SHALL NOT document a sentinel constant as part of the presence rule.

#### Scenario: Integration documents both build tools
- **WHEN** the integration page is read
- **THEN** it shows adding percolate via the BOM, starter, and annotations for **both** Maven and Gradle

#### Scenario: The presence rule is documented without a sentinel
- **WHEN** the `@Map` page's presence discussion is read
- **THEN** it explains that a member is in effect when written, with an empty string being a written value
- **AND** it mentions no sentinel constant

### Requirement: The manual includes an Extending section for strategy authors

The manual SHALL contain an Extending / SPI section aimed at strategy authors, co-located in the `spi`
module, that presents a **real, compiled custom strategy as its worked example** — the shipped `reactor`
container strategy — rather than a synthetic or prose-only description. The example SHALL be backed by a
behavioural e2e so the extension surface shown cannot drift from a working strategy.

The section SHALL additionally document the two extension roles a third party can implement and how they
differ: an `ExpansionStrategy`, which answers a single demand locally and may **refuse** it with a reason, and
a `DirectiveReader`, which reads an annotation the author wrote and declares bindings, inputs, scope inputs and
constraints through a sink. It SHALL state that a third-party annotation is supported through a reader with no
change to percolate itself.

#### Scenario: Extending section is reachable and uses a real strategy
- **WHEN** the manual's navigation is read
- **THEN** it contains an Extending (SPI) page whose worked example is the real `reactor` custom strategy,
  backed by a compiled behavioural e2e

#### Scenario: Both extension roles are documented
- **WHEN** the Extending section is read
- **THEN** it documents `ExpansionStrategy` and `DirectiveReader`, when to reach for each, and how a strategy
  refuses a demand with a reason rather than emitting a diagnostic

#### Scenario: Third-party annotation support is documented
- **WHEN** the Extending section is read
- **THEN** it shows that a third-party annotation is supported by shipping a `DirectiveReader`, with no change
  to percolate

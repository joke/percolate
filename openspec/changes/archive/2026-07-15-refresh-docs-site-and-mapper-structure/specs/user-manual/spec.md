## ADDED Requirements

### Requirement: The site navbar carries no placeholder demo content

The site's top navbar SHALL NOT contain the stock `antora-ui-default` UI bundle's placeholder demo content
(the "Home" link, "Products" dropdown, "Services" dropdown, and "Download" button, each pointing at a dead
`href="#"`), nor the mobile burger control that toggled that content open (removed with it, since its
target no longer exists and the bundle's own toggle script dereferences that target unconditionally). The
real navbar brand link (site title) and the search box SHALL be preserved unchanged. No replacement nav item
SHALL be added in place of the removed placeholder content; the site's version selector (see "Versioning is
derived from git refs, not a separate publish tool") remains the sole mechanism for surfacing releases, and
it is unaffected by this requirement.

#### Scenario: The built site's navbar has no placeholder demo links
- **WHEN** the Antora site is built and its rendered header is inspected
- **THEN** it contains no "Products" dropdown, no "Services" dropdown, no "Download" button, and no mobile
  burger control
- **AND** the site title/brand link and the search box are still present

#### Scenario: The navbar override does not touch version-dropdown chrome
- **WHEN** the UI bundle overlay used to remove the placeholder navbar is inspected
- **THEN** it replaces only the header partial containing the placeholder content, leaving the UI bundle's
  version-dropdown partials on their stock, unmodified path

## MODIFIED Requirements

### Requirement: The manual covers the bean-mapping consumer topics

The manual SHALL contain pages that document, for a Java developer mapping beans: project integration with
**both Maven and Gradle**; a quick-start minimal mapper; basic mapper structure (which methods are
discovered versus skipped), including a worked example of an **abstract-class `@Mapper`** and a worked
example of **cross-supertype method discovery** (an unannotated interface's abstract and `default` methods,
implemented by an `@Mapper` abstract class that adds its own abstract and concrete methods); the `@Map`
annotation including `target`, `source`, `constant`, `defaultValue` and the `UNSET` presence rule; nested
target and source path chains; **path access over getters, record accessors, and public fields**;
**collection mapping shown as a progression with a worked example per container mechanism** — same-kind
(`List<X>→List<Y>`), cross-kind conversion (`Set→List`), a stream intermediate (`Stream→Set`), and presence
composed inside/outside a container; **Optional mapping** (wrapping, unwrapping, and composed with
containers); **default values and JSpecify nullability crossings**; conversion methods; and default-method
conversions. Each feature section SHALL show a worked example, not a prose-only assertion of support.

#### Scenario: Integration documents both build tools
- **WHEN** the integration page is read
- **THEN** it shows adding percolate via the BOM, starter, and annotations for **both** Maven and Gradle

#### Scenario: @Map members are fully documented
- **WHEN** the `@Map` page is read
- **THEN** it documents `target`, `source`, `constant`, and `defaultValue`, and states the `UNSET`
  presence rule (an empty string is present, not absent)

#### Scenario: Collections are shown by worked example per mechanism
- **WHEN** the collections page is read
- **THEN** it shows a worked example with generated output for same-kind mapping (`List<X>→List<Y>`),
  cross-kind conversion (`Set→List`), a stream intermediate (`Stream→Set`), and presence composed with a
  container — not a prose-only table of supported kinds

#### Scenario: Optionals, defaults, and nullness are documented with examples
- **WHEN** the manual's navigation is read
- **THEN** it includes an Optionals page and a defaults-and-nullness page, each with a worked,
  single-sourced example

#### Scenario: Path access covers fields and records
- **WHEN** the path-access content is read
- **THEN** it shows percolate reading a getter, a record accessor, and a public field, in one section
  named for the user-facing capability

#### Scenario: Conversion and default-method topics are present
- **WHEN** the manual's navigation is read
- **THEN** it includes a conversion-methods page and a default-method-conversions page

#### Scenario: An abstract-class mapper is documented with a worked example
- **WHEN** the mapper-structure page's abstract-class section is read
- **THEN** it shows a real, compiled `@Mapper abstract class` example and the generated `*Impl` that
  `extends` it, single-sourced from a compiled fixture

#### Scenario: Cross-supertype method discovery is documented with a worked example
- **WHEN** the mapper-structure page's hierarchies section is read
- **THEN** it shows an unannotated interface with one abstract method and one `default` method, implemented
  by an `@Mapper` abstract class that adds its own abstract method and its own concrete method
- **AND** the shown complete generated impl implements both abstract methods (the inherited one and the
  class's own), while the `default` method and the concrete class method are not regenerated

# User Manual Spec

## Purpose

Defines the hosted, single-version Antora user manual for percolate. The manual is the integrated place a Java developer learns how to add percolate to a project (Maven and Gradle) and write a bean mapper, and it secondarily serves strategy authors via an Extending / SPI section. It builds as an Antora AsciiDoc site from `HEAD` alone — deliberately unversioned, with no version selector and no version segment in page URLs (no `mike`, `gh-pages`, committed HTML, or tag aggregation) — and deploys to GitHub Pages on every push to `main`. Install snippets name the latest release tag, derived at build time, so a single-version site still points readers at a published artifact. The documented feature set drives the end-to-end test set (see `e2e-test-architecture`), not the other way around: every feature section is backed by a compiled, behaviourally-asserted fixture, and every documented code example — input and generated output alike — is single-sourced via `include::` from that fixture, so a published snippet cannot silently drift from the code CI compiles. Each feature page is co-located in the module that owns its strategy, reaching the single Antora component via a collector `scan` import; `docs/` itself retains only the spine.

## Requirements

### Requirement: The user manual builds as an Antora site

The repository SHALL define an Antora documentation component under `docs/` (a `docs/antora.yml`
component descriptor and a `docs/modules/ROOT/` module with `nav.adoc` and `pages/`) plus a root
`antora-playbook.yml`, such that running the Antora site generator produces a static HTML site. A build
that contains an unresolved `include::`, a broken cross-reference, or a missing navigation target SHALL
fail rather than emit a silently incomplete site. The site generator's failure tolerance SHALL be
configured so that a reported problem at warning level or above causes a non-zero exit; reporting a
problem while exiting successfully SHALL NOT be permitted. A build that does not exit successfully SHALL
NOT publish.

#### Scenario: A clean tree builds a site
- **WHEN** the Antora site generator runs against `antora-playbook.yml` on a clean checkout
- **THEN** it exits successfully and emits a site whose landing page and navigation resolve

#### Scenario: A broken include fails the build
- **WHEN** a page references an `include::` target or `xref:` that does not resolve
- **THEN** the Antora build reports the failure and does not exit successfully

#### Scenario: A reported problem cannot pass silently
- **WHEN** the site generator logs a problem at warning level or above
- **THEN** the build exits non-zero rather than emitting a site and reporting success

#### Scenario: A failed docs build does not deploy
- **WHEN** the documentation build does not exit successfully
- **THEN** no site artifact is published to GitHub Pages and the previously published site remains in place

### Requirement: The site navbar carries no placeholder demo content

The site's top navbar SHALL NOT contain the stock `antora-ui-default` UI bundle's placeholder demo content
(the "Home" link, "Products" dropdown, "Services" dropdown, and "Download" button, each pointing at a dead
`href="#"`), nor the mobile burger control that toggled that content open (removed with it, since its
target no longer exists and the bundle's own toggle script dereferences that target unconditionally). The
real navbar brand link (site title) and the search box SHALL be preserved unchanged. No replacement nav item
SHALL be added in place of the removed placeholder content. The site carries no version selector at all
(see "The site is single-version and unversioned"), so this requirement has no interaction with one; the
bundle's version-dropdown partials are left on their stock, upstream-maintained path regardless.

#### Scenario: The built site's navbar has no placeholder demo links
- **WHEN** the Antora site is built and its rendered header is inspected
- **THEN** it contains no "Products" dropdown, no "Services" dropdown, no "Download" button, and no mobile
  burger control
- **AND** the site title/brand link and the search box are still present

#### Scenario: The navbar override does not touch version-dropdown chrome
- **WHEN** the UI bundle overlay used to remove the placeholder navbar is inspected
- **THEN** it replaces only the header partial containing the placeholder content, leaving the UI bundle's
  version-dropdown partials on their stock, unmodified path

### Requirement: The site is single-version and unversioned

The user manual SHALL be published as exactly one version — the current state of the default branch — with
no version selector and no version segment in page URLs. `antora-playbook.yml` SHALL source content only
from `HEAD`, and `docs/antora.yml` SHALL declare an unversioned component. The playbook SHALL NOT filter or
aggregate release tags, and the site SHALL NOT be assembled from a `gh-pages` branch, committed HTML, or
any non-git versioning tool.

#### Scenario: Only HEAD is aggregated
- **WHEN** the playbook's content sources are inspected
- **THEN** the sole source is this repository at `HEAD` with `start_path` pointing at `docs`
- **AND** no tag filter is configured

#### Scenario: Release tags do not produce additional versions
- **WHEN** the site is built in a repository that has release tags
- **THEN** exactly one version is produced and no tag appears as a selectable version

#### Scenario: Page URLs carry no version segment
- **WHEN** a built page's URL is inspected
- **THEN** it contains no version path segment between the component name and the page name

### Requirement: The site deploys to GitHub Pages on every push to main

The repository SHALL define `.github/workflows/docs.yml` (or an equivalent pipeline) that, on every push
to `main`, builds the Antora site and publishes it to GitHub Pages via `actions/upload-pages-artifact` and
`actions/deploy-pages`. The deploy SHALL be **gated on `./gradlew check` passing**: the Pages deploy job
SHALL run only after the check job succeeds, so a build whose tests fail never publishes the site and no
"roll back the docs" recovery is needed — the previously published site is simply left in place. The job
SHALL grant `pages: write` and `id-token: write` permissions and SHALL check out full git history
(`fetch-depth: 0`) so the latest release tag is reachable for the install-snippet version derivation (see
"Install snippets advertise the latest released version") — a shallow checkout would silently render a
non-release placeholder. The advertised documentation URL SHALL stay consistent across `README.md` and
`.github/settings.yml`.

#### Scenario: Push to main triggers a deploy
- **WHEN** the docs pipeline is inspected
- **THEN** it triggers on push to `main`, builds the Antora site, and uploads + deploys a Pages artifact

#### Scenario: Workflow has the permissions Pages requires
- **WHEN** the deploy job is inspected
- **THEN** it declares `pages: write` and `id-token: write` and checks out with `fetch-depth: 0`

#### Scenario: The advertised URL is consistent
- **WHEN** `README.md`'s documentation link and `.github/settings.yml`'s `homepage` are compared
- **THEN** both point at the same GitHub Pages base URL

#### Scenario: A failing build does not publish
- **WHEN** `./gradlew check` fails on a push to `main`
- **THEN** the Pages deploy does not run and the previously published site is left untouched

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

### Requirement: Install snippets advertise the latest released version

The version rendered into the manual's dependency and install snippets SHALL be derived at build time from
the repository's latest release tag, not hand-maintained in a descriptor. It SHALL be the latest released
version rather than the in-development version, so that a snippet never advertises an unpublished artifact.
Where no release tag is reachable, the rendered value SHALL be visibly not a release version rather than a
plausible-looking one.

#### Scenario: The rendered version matches the latest release tag
- **WHEN** the site is built in a repository whose latest release tag is `v1.2.3`
- **THEN** the manual's install snippets render `1.2.3`

#### Scenario: An in-development version is never advertised
- **WHEN** the site is built from a commit past the latest release tag
- **THEN** the install snippets still render the latest released version, not a `-SNAPSHOT` version

#### Scenario: No hardcoded version remains in the component descriptor
- **WHEN** `docs/antora.yml` is inspected
- **THEN** it declares no literal version value for the install-snippet attribute

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

### Requirement: The manual includes an Extending section for strategy authors

The manual SHALL contain an Extending / SPI section aimed at strategy authors, co-located in the `spi`
module, that presents a **real, compiled custom strategy as its worked example** — the shipped `reactor`
container strategy — rather than a synthetic or prose-only description. The example SHALL be backed by a
behavioural e2e so the extension surface shown cannot drift from a working strategy.

#### Scenario: Extending section is reachable and uses a real strategy
- **WHEN** the manual's navigation is read
- **THEN** it contains an Extending (SPI) page whose worked example is the real `reactor` custom strategy,
  backed by a compiled behavioural e2e

### Requirement: Code examples are single-sourced from compiling fixtures

Every code example that demonstrates mapper behavior SHALL be brought into the manual via AsciiDoc
`include::` from a fixture that the build compiles, rather than inlined as literal prose. The fixture SHALL
be **owned by the module that compiles it** (under that module's `src/test/resources`) and reach the Antora
content catalog via the **antora-collector `scan`** import — not via a cross-tree Gradle `srcDir` that
reaches from one module into another module's or the docs tree. A change that breaks such a fixture SHALL
break the build, so a published example cannot silently diverge from compiled code.

#### Scenario: Behavioral examples are included, not inlined
- **WHEN** a page presents a mapper example that asserts runtime behavior
- **THEN** the example body is an `include::` of a fixture source file, not hand-inlined code

#### Scenario: A broken fixture breaks the build
- **WHEN** an included fixture no longer compiles
- **THEN** the build fails before the site is published

#### Scenario: Fixtures are owned by their module, not the docs tree
- **WHEN** a behavioural example's fixture is located
- **THEN** it resides in the owning module's test sources and reaches the site via the collector, with no
  cross-module `srcDir` reaching into the docs tree

### Requirement: Conversion-method and default-method examples are backed by end-to-end tests

The change SHALL add two end-to-end Spock specs that compile a real `@Mapper`: one using a second
single-parameter `@Mapper` method as a conversion bridge, and one using a `default` method as a conversion
bridge. Each SHALL assert that the generated mapper invokes the corresponding method. The manual's
conversion-methods and default-method-conversions examples SHALL `include::` the fixtures these tests
exercise.

#### Scenario: Conversion-method e2e proves the bridge is called
- **WHEN** the conversion-method end-to-end spec compiles its mapper
- **THEN** generation succeeds and the generated source invokes the conversion method

#### Scenario: Default-method e2e proves the default is called
- **WHEN** the default-method end-to-end spec compiles its mapper
- **THEN** generation succeeds and the generated source invokes the `default` conversion method

### Requirement: The documentation toolchain is a Gradle-provisioned Antora build

The site build SHALL be provisioned and run by the `org.antora` Gradle plugin on a managed Node runtime, so
a contributor builds the manual with `./gradlew antora` using the same toolchain CI uses, requiring no
system Node. The plugin SHALL declare the Antora version and SHALL install the `@antora/collector-extension`
via its `packages` map (in Antora's own Node context). `.mise.toml` SHALL NOT pin an Antora npm tool.

#### Scenario: Antora is provisioned by the Gradle plugin
- **WHEN** the root build is inspected
- **THEN** it applies `org.antora`, configures the Antora version, the playbook, and the collector extension
  in `packages`, and `.mise.toml` declares no Antora npm tool

#### Scenario: The manual builds via Gradle
- **WHEN** `./gradlew antora` runs
- **THEN** the plugin provisions Node, installs Antora and the collector, and produces the static site

### Requirement: Documented generated output is single-sourced from real generation

Where a page shows the code percolate generates for an example, that output SHALL be brought in via
`include::` from real generated source — produced by compiling the example with documentation tags enabled
— and never hand-typed as prose. The displayed region SHALL be selected by an AsciiDoc tag the generator
emits around **whole generated methods**, so a per-method snippet renders the **complete method** (its
signature and body), not a bare body fragment, and SHALL be brought in with indentation normalised to the
left margin (`indent=0`). Where a page instead shows a whole generated class listing, it SHALL be brought in
such that the generator's `// tag::`/`// end::` marker comments do not appear in the rendered snippet (for
example via `tags=**`). A page SHALL either single-source its shown output this way or show no output at all;
a hand-written block claimed to be generated code is not permitted. This SHALL hold for **every** page in the
manual, not a subset.

#### Scenario: Shown output comes from real generation
- **WHEN** a page shows the code generated for an example
- **THEN** the shown block is an `include::` (by tag) of generated source materialised from compiling that
  example, not hand-inlined code

#### Scenario: A per-method snippet shows the complete, correctly-indented method
- **WHEN** a page shows a single generated method via a tagged `include::`
- **THEN** the rendered snippet includes the method's signature line (not only its body statements)
- **AND** its first line begins at the left margin (no leaked in-class indentation)

#### Scenario: A whole-class listing carries no tag-marker noise
- **WHEN** a page shows a complete generated class listing single-sourced from real generation
- **THEN** no `// tag::…[]` or `// end::…[]` marker comment appears in the rendered snippet

#### Scenario: No hand-typed generated output remains on any page
- **WHEN** every page in the manual is inspected
- **THEN** no page hand-writes a block presented as percolate's generated output — the previously hand-typed
  output blocks (conversion-methods, collections, map-annotation, reactive, and the rest) are all replaced
  by materialised `include::`s

### Requirement: Documented generated output survives a build-cache hit

Generated example output that pages `include::` SHALL be produced by a task that declares the directory it
writes as a task output, so that a build-cache hit restores those files rather than silently skipping their
creation. A build in which the producing tasks are satisfied from the build cache SHALL yield the same
complete site as a build in which they execute.

#### Scenario: A cache-satisfied build still produces a complete site
- **WHEN** the tasks that materialise generated doc examples are satisfied from the build cache, with their
  project build directories otherwise absent
- **THEN** the materialised example files are present before the site is generated
- **AND** the site builds successfully with every generated `include::` resolved

#### Scenario: Materialisation directories are declared outputs
- **WHEN** a task that materialises generated documentation examples is inspected
- **THEN** the directory it writes is registered as a declared output of that task

### Requirement: Feature pages are co-located in their owning module

Each feature's documentation page SHALL live in the module that owns the feature's strategy — `reactor` for
reactive, `reactor-blocking` for the blocking bridge, `strategies-builtin` for the basic features, `spi` for
Extending, `processor` for compile-time switches — reaching the single Antora `percolate` component via a
collector `scan` import. The `docs/` tree SHALL retain only the spine: the introduction, getting-started,
mapper-structure, and the navigation. Relocating a page SHALL NOT change the rendered site.

#### Scenario: A feature page lives with its module
- **WHEN** the source of the reactive-containers page is located
- **THEN** it resides under the `reactor` module and is imported into the manual by the collector, not
  authored under `docs/`

#### Scenario: The docs tree holds only the spine
- **WHEN** `docs/modules/ROOT/pages/` is inspected
- **THEN** it contains only the introduction, getting-started, mapper-structure, and navigation — every
  feature page lives in its owning module

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

### Requirement: Every documented feature is backed by a behavioural example

Every feature section in the manual SHALL be backed by a compiled fixture that is behaviourally asserted
(see `e2e-test-architecture`). A documented capability without a compiled, behaviourally-tested example
SHALL NOT ship, and a supported capability with no documentation section SHALL be recorded as a
documentation gap or a removal candidate via the change's feature→example census.

#### Scenario: A documented feature has a behavioural example
- **WHEN** any feature section is inspected
- **THEN** its example is a compiled fixture exercised by a behavioural e2e in the owning module

#### Scenario: An undocumented capability is flagged
- **WHEN** the feature→example census is reviewed
- **THEN** every supported user-facing mechanism either has a documentation section with an example or is
  explicitly recorded as a gap or removal candidate

### Requirement: The manual documents temporal (date/time) mapping

The manual SHALL contain a temporal-mapping feature page, co-located in the `strategies-builtin` module that
owns the temporal strategies and reaching the Antora component via the collector `scan` import. The page SHALL
document, at a user level: (1) automatic conversion across `java.util.Date`, `java.sql.*`, and `java.time.*`
including the two-hub / zone-bridge behaviour and the no-truncation guarantee (a hub never silently drops a
time-of-day; a `00:00:00` only ever comes from a date-only source); (2) `@Map(format = "…")` for `String ↔
temporal` parsing and rendering; (3) `@Map(zone = "…")` and the `-Apercolate.time.zone` compile-time switch,
including the fallback to the consumer's runtime `ZoneId.systemDefault()`. The page SHALL be named by the
user-facing feature, not by any implementation class. Every input snippet and every generated-output snippet on
the page SHALL be single-sourced via `include::` from the backing fixture (input) and from real generated
output (produced under `-Apercolate.docTags`), never hand-typed.

#### Scenario: The temporal page is co-located and single-sourced
- **WHEN** the temporal-mapping page is inspected
- **THEN** it resides in the `strategies-builtin` module's sources and reaches the site via the collector
- **AND** each shown input and generated-output block is an `include::` of a compiled fixture / real generated
  source, with no hand-typed block claimed to be generated

#### Scenario: The temporal feature is backed by a behavioural example
- **WHEN** the temporal-mapping page's example is built
- **THEN** a compiled fixture instantiates the generated mapper and asserts its runtime behaviour (a temporal
  conversion and a `@Map(format = …)` round-trip), and the page includes the real generated output

#### Scenario: The time.zone switch appears in the switches reference
- **WHEN** the compile-time-switches reference is inspected
- **THEN** it documents `-Apercolate.time.zone` with an example and the generated effect (a frozen
  `ZoneId.of("…")` vs the default runtime `ZoneId.systemDefault()`)

### Requirement: Reactive container mapping has a compiled example in the reactor module

The manual SHALL document reactive container mapping with a behavioural example whose fixture is owned by
and compiled in the `reactor` module — the only module where `Flux`/`Mono` mappers compile — backed by an
end-to-end spec, and imported into the manual via the collector like every other example.

#### Scenario: A reactive example is present and compiled in reactor
- **WHEN** the manual's reactive container content is read
- **THEN** it `include::`s a `Flux`/`Mono` mapper fixture owned by the `reactor` module and exercised by a
  reactor end-to-end spec

### Requirement: The manual documents enum mapping

The manual SHALL contain an enum-mapping feature page under the Conversions section, co-located in the module that
owns the enum conversion strategy and reaching the Antora component via the collector `scan` import. The page SHALL
document, at a user level: (1) that enum-to-enum mapping is declared as an abstract conversion method
(`Target toX(Source s)` with both types `enum`) whose body percolate generates, and that bean members of the target
enum bridge through it automatically; (2) automatic same-name constant matching for mirrored enums, needing no
directive; (3) `@MapEnum(source = "…", target = "…")` for per-constant overrides where names differ, and that extra
target constants are allowed while an uncovered source constant fails the build; (4) the `-Apercolate.switch.style`
compile-time switch (`AUTO` / `CLASSIC` / `ARROW`), the Java 11 classic-statement vs Java 14+ modern-expression
output, and that the modern expression omits `default` so a forgotten constant fails to compile. The page SHALL be
named by the user-facing feature, not by any implementation class. Every input snippet and every generated-output
snippet on the page SHALL be single-sourced via `include::` from the backing fixture (input) and from real generated
output (produced under `-Apercolate.docTags`), never hand-typed.

#### Scenario: The enum-mapping page is co-located and single-sourced
- **WHEN** the enum-mapping page is inspected
- **THEN** it resides in the enum-conversion strategy's owning module sources and reaches the site via the collector
- **AND** each shown input and generated-output block is an `include::` of a compiled fixture / real generated
  source, with no hand-typed block claimed to be generated

#### Scenario: The enum feature is backed by a behavioural example
- **WHEN** the enum-mapping page's example is built
- **THEN** a compiled fixture instantiates the generated mapper and asserts its runtime behaviour (a same-name
  mapping and a `@MapEnum` override mapping), and the page includes the real generated output

#### Scenario: The switch.style switch appears in the switches reference
- **WHEN** the compile-time-switches reference is inspected
- **THEN** it documents `-Apercolate.switch.style` with an example and the generated effect (a classic switch
  statement on Java 11 vs a `default`-free modern switch expression on Java 14+)

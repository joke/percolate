# User Manual Spec

## Purpose

Defines the hosted, versioned Antora user manual for percolate. The manual is the integrated place a Java developer learns how to add percolate to a project (Maven and Gradle) and write a bean mapper, and it secondarily serves strategy authors via an Extending / SPI section. It builds as an Antora AsciiDoc site, derives its versions natively from this repository's git refs (no `mike`, `gh-pages`, or committed HTML), and deploys to GitHub Pages on every push to `main`. The documented feature set drives the end-to-end test set (see `e2e-test-architecture`), not the other way around: every feature section is backed by a compiled, behaviourally-asserted fixture, and every documented code example — input and generated output alike — is single-sourced via `include::` from that fixture, so a published snippet cannot silently drift from the code CI compiles. Each feature page is co-located in the module that owns its strategy, reaching the single Antora component via a collector `scan` import; `docs/` itself retains only the spine.

## Requirements

### Requirement: The user manual builds as an Antora site

The repository SHALL define an Antora documentation component under `docs/` (a `docs/antora.yml`
component descriptor and a `docs/modules/ROOT/` module with `nav.adoc` and `pages/`) plus a root
`antora-playbook.yml`, such that running the Antora site generator produces a static HTML site. A build
that contains an unresolved `include::`, a broken cross-reference, or a missing navigation target SHALL
fail rather than emit a silently incomplete site.

#### Scenario: A clean tree builds a site
- **WHEN** the Antora site generator runs against `antora-playbook.yml` on a clean checkout
- **THEN** it exits successfully and emits a site whose landing page and navigation resolve

#### Scenario: A broken include fails the build
- **WHEN** a page references an `include::` target or `xref:` that does not resolve
- **THEN** the Antora build reports the failure and does not exit successfully

### Requirement: Versioning is derived from git refs, not a separate publish tool

`antora-playbook.yml` SHALL source content from this repository's git refs: `HEAD` of the default branch
as the current version, plus release tags as additional versions. The site's version selector SHALL be
produced by Antora from those refs — without `mike`, a `gh-pages` branch, or committed HTML. With no
release tags present, the site SHALL contain exactly one version.

#### Scenario: Current docs come from HEAD
- **WHEN** the playbook content sources are inspected
- **THEN** they include this repository with `HEAD` (or the default branch) as a content source, with
  `start_path` pointing at `docs`

#### Scenario: No tags yields a single version
- **WHEN** the site is built and no release tags exist
- **THEN** exactly one version is produced and the build does not require any non-git versioning tool

#### Scenario: A release tag becomes an additional version
- **WHEN** a release tag matching the playbook's tag filter exists
- **THEN** the built site includes that tag as a selectable version alongside the current one

### Requirement: The site deploys to GitHub Pages on every push to main

The repository SHALL define `.github/workflows/docs.yml` (or an equivalent pipeline) that, on every push
to `main`, builds the Antora site and publishes it to GitHub Pages via `actions/upload-pages-artifact` and
`actions/deploy-pages`. The deploy SHALL be **gated on `./gradlew check` passing**: the Pages deploy job
SHALL run only after the check job succeeds, so a build whose tests fail never publishes the site and no
"roll back the docs" recovery is needed — the previously published site is simply left in place. The job
SHALL grant `pages: write` and `id-token: write` permissions and SHALL check out full git history
(`fetch-depth: 0`) so tags are available to Antora. The advertised documentation URL SHALL stay consistent
across `README.md` and `.github/settings.yml`.

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
discovered versus skipped); the `@Map` annotation including `target`, `source`, `constant`, `defaultValue`
and the `UNSET` presence rule; nested target and source path chains; **path access over getters, record
accessors, and public fields**; **collection mapping shown as a progression with a worked example per
container mechanism** — same-kind (`List<X>→List<Y>`), cross-kind conversion (`Set→List`), a stream
intermediate (`Stream→Set`), and presence composed inside/outside a container; **Optional mapping**
(wrapping, unwrapping, and composed with containers); **default values and JSpecify nullability
crossings**; conversion methods; and default-method conversions. Each feature section SHALL show a worked
example, not a prose-only assertion of support.

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

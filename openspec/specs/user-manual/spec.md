# User Manual Spec

## Purpose

Defines the hosted, versioned Antora user manual for percolate. The manual is the integrated place a Java developer learns how to add percolate to a project (Maven and Gradle) and write a bean mapper, and it secondarily serves strategy authors via an Extending / SPI section. It builds as an Antora AsciiDoc site, derives its versions natively from this repository's git refs (no `mike`, `gh-pages`, or committed HTML), and deploys to GitHub Pages on every push to `main`. Every documented code example is single-sourced via `include::` from a fixture the build compiles, so a published snippet cannot silently drift from the code CI compiles; the conversion-method and default-method examples are additionally backed by end-to-end Spock specs.

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

The repository SHALL define `.github/workflows/docs.yml` that, on every push to `main`, builds the Antora
site and publishes it to GitHub Pages via `actions/upload-pages-artifact` and `actions/deploy-pages`. The
job SHALL grant `pages: write` and `id-token: write` permissions and SHALL check out full git history
(`fetch-depth: 0`) so tags are available to Antora. The advertised documentation URL SHALL stay consistent
across `README.md` and `.github/settings.yml`.

#### Scenario: Push to main triggers a deploy
- **WHEN** `.github/workflows/docs.yml` is inspected
- **THEN** it triggers on push to `main`, builds the Antora site, and uploads + deploys a Pages artifact

#### Scenario: Workflow has the permissions Pages requires
- **WHEN** the deploy job is inspected
- **THEN** it declares `pages: write` and `id-token: write` and checks out with `fetch-depth: 0`

#### Scenario: The advertised URL is consistent
- **WHEN** `README.md`'s documentation link and `.github/settings.yml`'s `homepage` are compared
- **THEN** both point at the same GitHub Pages base URL

### Requirement: The manual covers the bean-mapping consumer topics

The manual SHALL contain pages that document, for a Java developer mapping beans: project integration with
**both Maven and Gradle**; a quick-start minimal mapper; basic mapper structure (which methods are
discovered versus skipped); the `@Map` annotation including `target`, `source`, `constant`, `defaultValue`
and the `UNSET` presence rule; nested target and source path chains; collection mapping with an explicit
statement of the supported container kinds; conversion methods; and default-method conversions.

#### Scenario: Integration documents both build tools
- **WHEN** the integration page is read
- **THEN** it shows adding percolate via the BOM, starter, and annotations for **both** Maven and Gradle

#### Scenario: @Map members are fully documented
- **WHEN** the `@Map` page is read
- **THEN** it documents `target`, `source`, `constant`, and `defaultValue`, and states the `UNSET`
  presence rule (an empty string is present, not absent)

#### Scenario: Supported container kinds are stated
- **WHEN** the collections page is read
- **THEN** it names the supported container kinds (`List`, `Set`, `Optional`, and reactive `Flux`/`Mono`
  via the optional module) and shows how element mapping composes

#### Scenario: Conversion and default-method topics are present
- **WHEN** the manual's navigation is read
- **THEN** it includes a conversion-methods page and a default-method-conversions page

### Requirement: The manual includes an Extending section for strategy authors

The manual SHALL contain an Extending / SPI section aimed at strategy authors, derived from the existing
`spi` and `strategies-builtin` module documentation, so the site serves both consumers and extenders.

#### Scenario: Extending section is reachable
- **WHEN** the manual's navigation is read
- **THEN** it contains an Extending (SPI) page describing the strategy-author surface

### Requirement: Code examples are single-sourced from compiling fixtures

Every code example that demonstrates mapper behavior SHALL be brought into the manual via AsciiDoc
`include::` from a fixture that the build compiles, rather than inlined as literal prose. A change that
breaks such a fixture SHALL break the build, so a published example cannot silently diverge from compiled
code.

#### Scenario: Behavioral examples are included, not inlined
- **WHEN** a page presents a mapper example that asserts runtime behavior
- **THEN** the example body is an `include::` of a fixture source file, not hand-inlined code

#### Scenario: A broken fixture breaks the build
- **WHEN** an included fixture no longer compiles
- **THEN** the build fails before the site is published

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

### Requirement: The documentation toolchain is declared for local builds

`.mise.toml` SHALL declare the Antora CLI and site generator so a contributor can build the manual locally
with the same toolchain CI uses. No new language runtime SHALL be required beyond the Node runtime already
present for the existing tooling.

#### Scenario: Antora tools are provisioned by mise
- **WHEN** `.mise.toml` is inspected
- **THEN** it declares the Antora toolchain (the `antora` umbrella package, which bundles the CLI and site
  generator), and adds no Python or other new language runtime

## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Documented generated output is single-sourced from real generation

Where a page shows the code percolate generates for an example, that output SHALL be brought in via
`include::` from real generated source — produced by compiling the example with documentation tags enabled
— and never hand-typed as prose. The displayed region SHALL be selected by an AsciiDoc tag the generator
emits around generated methods. A page SHALL either single-source its shown output this way or show no
output at all; a hand-written block claimed to be generated code is not permitted.

#### Scenario: Shown output comes from real generation
- **WHEN** a page shows the code generated for an example
- **THEN** the shown block is an `include::` (by tag) of generated source materialised from compiling that
  example, not hand-inlined code

#### Scenario: No hand-typed generated output remains
- **WHEN** the manual's pages are inspected
- **THEN** no page hand-writes a block presented as percolate's generated output (the former hand-typed
  `nested-paths` output block is replaced by an `include::`)

### Requirement: Reactive container mapping has a compiled example in the reactor module

The manual SHALL document reactive container mapping with a behavioural example whose fixture is owned by
and compiled in the `reactor` module — the only module where `Flux`/`Mono` mappers compile — backed by an
end-to-end spec, and imported into the manual via the collector like every other example.

#### Scenario: A reactive example is present and compiled in reactor
- **WHEN** the manual's reactive container content is read
- **THEN** it `include::`s a `Flux`/`Mono` mapper fixture owned by the `reactor` module and exercised by a
  reactor end-to-end spec

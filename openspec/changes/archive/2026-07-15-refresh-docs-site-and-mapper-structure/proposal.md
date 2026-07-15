## Why

The published site still ships `antora-ui-default`'s stock demo navbar — a "Home / Products / Services /
Download" menu with `href="#"` dead links and a Download button that goes nowhere, because no version has
ever been tagged. Separately, the manual's mapper-structure page asserts abstract-class mappers and
inherited-hierarchy discovery are supported but shows no worked example of either — both are real,
already proven at the compile level internally (`AssembleMapperTypeFeatureSpec`, `AbstractMethodReader`'s
use of `MoreElements.getLocalAndInheritedMethods`), but that proof has never been surfaced as a manual
example or an e2e fixture the manual can single-source from.

## What Changes

- Remove the stock UI bundle's placeholder top navbar (Home/Products/Services dropdowns, Download button)
  via a small `ui.supplemental_files` overlay that replaces only `partials/header-content.hbs`, keeping the
  real brand link, search box, and mobile burger — no new nav item replaces it.
- No new release/tag UI is added: Antora's git-tag-driven version dropdown (`content.sources[0].tags:
  [v*.*.*]`) already self-populates once a `v*.*.*` tag exists; this change adds no version-selector work.
- Add an "Abstract classes" section to `mapper-structure.adoc` with a real, compiled abstract-class `@Mapper`
  example (impl `extends` the mapper), backed by a new doc-example fixture + e2e Spock spec in
  `strategies-builtin`, following the module's existing real-source `docs/<topic>/` pattern.
- Add a "Hierarchies" section to `mapper-structure.adoc` with a real, compiled example: a plain (unannotated)
  interface declaring one abstract method and one `default` method, implemented by an `@Mapper` abstract
  class that adds its own abstract method and its own concrete method. The generated impl SHALL implement
  both abstract methods (proving cross-supertype discovery) while both the `default` and the concrete class
  method are left alone. The page shows the complete generated impl class via an untagged `include::`.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `user-manual`: the "builds as an Antora site" site-chrome shape changes to exclude the stock UI bundle's
  placeholder navbar; the "covers the bean-mapping consumer topics" mapper-structure coverage gains two
  worked, compiled examples (abstract-class mapper, cross-supertype hierarchy discovery).

## Impact

- **Docs site chrome (`antora-playbook.yml`, new `docs/supplemental-ui/partials/header-content.hbs`):** adds
  a `ui.supplemental_files` entry; no other playbook behavior changes.
- **Docs content (`docs/modules/ROOT/pages/mapper-structure.adoc`):** two new sections, each with an
  `include::`-sourced example and generated output.
- **New fixtures + specs (`strategies-builtin/src/test/java/.../docs/<topic>/`,
  `strategies-builtin/src/test/groovy/.../docs/<topic>/`):** two new doc-example packages, each a real
  `@Mapper` compiled by the ordinary `compileTestJava` task plus a Spock e2e spec asserting generated
  behavior — no production/processor code changes; this is new test-only coverage of already-supported
  behavior.
- **Teams affected:** solo maintainer (Joke) — docs-only, no consumer-facing API or generated-output change
  for existing mappers.

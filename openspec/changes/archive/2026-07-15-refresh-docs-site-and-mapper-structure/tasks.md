## 1. Navbar cleanup (D1)

- [x] 1.1 Fetch the stock `antora-ui-default` `src/partials/header-content.hbs` and create
      `docs/supplemental-ui/partials/header-content.hbs` with the `topbar-nav`/`navbar-end` block (Home,
      Products dropdown, Services dropdown, Download button) removed **together with** the `navbar-burger`
      button (it targets `#topbar-nav` via `aria-controls`; the bundle's `05-mobile-navbar.js` dereferences
      that element unconditionally on click, so keeping the burger without its target throws), keeping
      `navbar-brand` and the conditional search box.
- [x] 1.2 Add a `ui.supplemental_files: docs/supplemental-ui` entry to `antora-playbook.yml`.
- [x] 1.3 Build the site (`./gradlew antora`) and inspect `build/site/index.html` (or equivalent rendered
      page) to confirm no "Products", "Services", "Download", or burger-button markup remains, and that the
      site title link and search box still render.

## 2. Abstract-class mapper example (D2)

- [x] 2.1 Add `strategies-builtin/src/test/java/io/github/joke/percolate/docs/abstractclass/` with a
      `package-info.java` and an `@Mapper abstract class` fixture (`tag=mapper` around the mapper type,
      `tag=model` around its source/target types), following the shape already proven by
      `AssembleMapperTypeFeatureSpec` but as manual-owned real source.
- [x] 2.2 Add `strategies-builtin/src/test/groovy/io/github/joke/percolate/docs/abstractclass/AbstractClassMapperDocExampleSpec.groovy`
      (`@Tag('integration')`) instantiating the generated `*Impl` and asserting correct mapped output.
- [x] 2.3 Add an "Abstract classes" section to `docs/modules/ROOT/pages/mapper-structure.adoc` with
      `include::` blocks for the mapper source, the model types, and the generated method (tag=<method
      name>, `indent=0`).

## 3. Hierarchy example (D2, D3, D4)

- [x] 3.1 Add `strategies-builtin/src/test/java/io/github/joke/percolate/docs/hierarchy/` with a
      `package-info.java`, a plain (unannotated) interface declaring one `@Map`-annotated abstract method
      and one `default` method, and an `@Mapper abstract class` implementing that interface with its own
      `@Map`-annotated abstract method and its own concrete (non-generated) method.
- [x] 3.2 Add `strategies-builtin/src/test/groovy/io/github/joke/percolate/docs/hierarchy/HierarchyDocExampleSpec.groovy`
      (`@Tag('integration')`) asserting: the generated impl correctly maps via both the inherited abstract
      method and the class's own abstract method; calling the `default` method and the concrete class method
      directly still returns their hand-written behavior (proving percolate left them alone).
- [x] 3.3 Add a "Hierarchies" section to `docs/modules/ROOT/pages/mapper-structure.adoc`: `include::` the
      interface source, the abstract-class source, and — via `include::…InvoiceMapperImpl.java[tags=**]` —
      the complete generated mapper with no leaked tag-marker comments (an untagged include was tried first
      and found to leak the markers; `tags=**` is the correct mechanism, matching `temporal-mapping.adoc`).

## 4. Verification

- [x] 4.1 Confirm the new `abstractclass`/`hierarchy` doc packages are picked up by the existing
      `strategies-builtin` collector `scan` entries in `docs/antora.yml` (no new scan entry expected).
- [x] 4.2 Build the site (`./gradlew antora`) and visually check both new mapper-structure sections render
      correctly, with no unresolved `include::`, no leaked `// tag::`/`// end::` marker comments in the
      hierarchy whole-class listing, and no leaked in-class indentation on tagged method snippets.
- [x] 4.3 Run `./gradlew check` and confirm it passes with no violations.
- [x] 4.4 Commit the completed change with `/commit-commands:commit`.

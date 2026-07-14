## Why

The published manual's generated-output snippets are broken in three ways: they show a bare, mis-indented
`return …;` fragment instead of the whole generated method, and the temporal page additionally leaks raw
`// tag::` / `// end::` marker comments into its full-class listing. The root cause is systemic, not
temporal-specific — `docTags` brackets each method's **body** (the only span JavaPoet lets a `CodeBlock`
reach), so every `include::[tag=…]` snippet on every page is headless and carries its in-brace indentation.

## What Changes

- Change the `docTags` codegen contract: the include-tag region SHALL bracket the **whole generated method**
  (leading annotations/signature through the closing brace), not just the body — so a documentation build
  single-sources the *complete* method by tag.
- Realise whole-method bracketing by turning `percolate-javapoet` from a source-free relocation into a
  **relocation + selective source overlay**: vendor a minimal `com.palantir.javapoet.MethodSpec` overlay that
  can emit a leading/trailing include-tag comment *outside* the braces (JavaPoet exposes no other hook), and
  let it shadow the upstream class via *project-files-first* + `DuplicatesStrategy.EXCLUDE` on the shaded
  `MethodSpec*` entry. The relocation and full-swallow invariants are unchanged.
- Rewire `BuildMethodBodies`: stop wrapping the body `CodeBlock`; drive whole-method bracketing through the
  overlaid `MethodSpec` when `docTags` is on. Retire the now-unused `spi` `DocTags` body-wrap utility.
- **Behaviour-preserving guarantee retained:** with `docTags` off (the default — every consumer), generated
  source stays byte-for-byte identical. Only tag-on (docs) output changes shape.
- Fix the doc-authoring side so the corrected generation actually renders well: every generated-impl
  `include::[tag=…]` normalises indentation (`indent=0`), and the temporal whole-class listing strips marker
  comments (`tags=**`).

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `code-generation`: the "Documentation include tags are emitted under an opt-in option" requirement changes
  from bracketing each method's **body** to bracketing the **whole method** (signature through closing brace).
- `javapoet-relocation`: permit a minimal, relocated **source overlay** in `percolate-javapoet` that shadows a
  named upstream JavaPoet class (here `MethodSpec`) via `DuplicatesStrategy.EXCLUDE` + project-files-first,
  while preserving the relocation and zero-upstream-dependency (swallow) invariants.
- `user-manual`: documented generated output SHALL be shown as the **complete method** (signature + body),
  correctly indented, and any whole-class generated listing SHALL carry no `// tag::`/`// end::` marker noise.

## Impact

- **Production (`percolate-javapoet`):** gains its first source file — a vendored, Apache-2.0-attributed
  `MethodSpec` overlay — plus a `filesMatching` + `DuplicatesStrategy.EXCLUDE` rule on `shadowJar`. Module
  purpose narrows from "source-free" to "relocation + selective overlay".
- **Production (`processor`):** `BuildMethodBodies` (and the generate render leaf) rewired from body-level to
  method-level tag emission; `spi` `DocTags` utility retired.
- **Docs (`*/src/docs/*.adoc`):** `indent=0` added to every generated-impl tagged include across all feature
  pages; `tags=**` on `temporal-mapping.adoc`'s whole-class listing.
- **Tests:** the `docTags`-on generation e2e/doc fixtures re-pin whole-method output; a small overlay spec
  pins the leading/trailing tag emission; `docTags`-off byte-identical invariant re-verified.
- **Quality gates:** `./gradlew check` green; `processor` pitest floors held; relocation swallow/ArchUnit
  guards from `relocate-javapoet-as-spi-api` still pass; no jqwik.
- **Teams affected:** solo maintainer (Joke) — no consumer-facing API or default-output change (docs-only
  visible change; consumer bytecode unchanged).

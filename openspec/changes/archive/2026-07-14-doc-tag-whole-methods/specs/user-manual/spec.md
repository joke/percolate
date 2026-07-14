## MODIFIED Requirements

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

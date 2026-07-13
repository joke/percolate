## MODIFIED Requirements

### Requirement: Documentation include tags are emitted under an opt-in option

When the `docTags` processor option is enabled, the generator SHALL bracket each generated **method as a
whole** — from its leading annotations/signature through its closing brace — with AsciiDoc include-tag
comments (`// tag::<methodName>[]` before the method, `// end::<methodName>[]` after the closing brace), so a
documentation build can `include::` that method's *complete* generated source by tag (signature and body, not
the body alone). When the option is absent (the default), the generator SHALL emit no such comments, leaving
generated source byte-for-byte as it is today — code a consumer compiles SHALL never carry documentation tags
unless the consumer opts in. Tag emission SHALL NOT alter the generated code's behaviour; it adds only comment
lines outside the method, which an AsciiDoc `include::` strips from the rendered snippet.

#### Scenario: Whole-method tags are emitted only with the option on
- **WHEN** a mapper is generated with the `docTags` option enabled
- **THEN** each generated method is bracketed by a `// tag::<methodName>[]` line preceding its annotations/signature and a `// end::<methodName>[]` line following its closing brace
- **AND** an `include::[tag=<methodName>]` of that source yields the complete method including its signature

#### Scenario: Default output carries no tags
- **WHEN** a mapper is generated without the `docTags` option
- **THEN** the generated source contains no documentation tag comments and is identical to the pre-option
  output

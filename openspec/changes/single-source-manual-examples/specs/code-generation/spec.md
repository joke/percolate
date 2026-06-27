## ADDED Requirements

### Requirement: Documentation include tags are emitted under an opt-in option

When the `docTags` processor option is enabled, the generator SHALL bracket each generated method's body
with AsciiDoc include-tag comments (`// tag::<methodName>[]` / `// end::<methodName>[]`) so a documentation
build can `include::` that method's generated body by tag. When the option is absent (the default), the
generator SHALL emit no such comments, leaving generated source byte-for-byte as it is today — code a
consumer compiles SHALL never carry documentation tags unless the consumer opts in. Tag emission SHALL NOT
alter the generated code's behaviour; it adds only comment lines, which an AsciiDoc `include::` strips from
the rendered snippet.

#### Scenario: Tags are emitted only with the option on
- **WHEN** a mapper is generated with the `docTags` option enabled
- **THEN** each generated method's body is bracketed by `// tag::<methodName>[]` and `// end::<methodName>[]`
  comment lines

#### Scenario: Default output carries no tags
- **WHEN** a mapper is generated without the `docTags` option
- **THEN** the generated source contains no documentation tag comments and is identical to the pre-option
  output

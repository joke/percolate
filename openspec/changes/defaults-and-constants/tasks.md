## 1. Annotation surface (percolate-annotations)

- [ ] 1.1 Add the `UNSET` sentinel constant to `io.github.joke.percolate.Map`, add `constant()` and `defaultValue()` String members defaulting to `UNSET`, and change `source()` to default to `UNSET`
- [ ] 1.2 Document on `@Map` the constant/default semantics and the empty-string-vs-absent rule (presence is `!Map.UNSET.equals(value)`, never `isEmpty()`)

## 2. Discovery (mapping-discovery)

- [ ] 2.1 Extend `MappingDirective` to carry `constant` and `defaultValue` plus their `AnnotationValue`s (populated only when the member is explicitly present)
- [ ] 2.2 Update `DiscoverMappings.toDirective` to read `source`/`constant`/`defaultValue` via `AnnotationMirror` walking, treating each as present iff `!= Map.UNSET`
- [ ] 2.3 Spock specs: constant directive discovered with no source; default discovered alongside source; empty-string value reported present (not `UNSET`)

## 3. Early validation (mapping-validation)

- [ ] 3.1 Modify `ValidateSourceParameters` to skip constant directives (no source to check)
- [ ] 3.2 Add a stage enforcing **constant XOR source** (error on both or neither), wired into the `Pipeline`, emitting via `Diagnostics` with the offending `AnnotationValue`
- [ ] 3.3 In the same stage, enforce **`defaultValue` requires `source`** (illegal with `constant` or with no source), positioned at the `defaultValue` `AnnotationValue`
- [ ] 3.4 Spock specs for all three rules including IDE-positioning (`AnnotationValue`) assertions, and that the pipeline does not halt

## 4. Shared literal coercion (percolate-spi)

- [ ] 4.1 Add a `LiteralCoercion` utility (reachable by both `strategies-builtin` and the processor) that coerces a raw string to a typed literal for the 8 primitives, their wrappers, and `String`, returning a success-or-failure result
- [ ] 4.2 Implement strictness: `char` exactly one character, strict `true`/`false` for `boolean`, numeric range-check + correct literal suffix (e.g. `long` → `<n>L`), no whitespace trimming
- [ ] 4.3 Unit specs for the coercion scope (in-scope success, out-of-scope failure) and each strictness rule

## 5. Directive SPI surface (expansion-strategy-spi)

- [ ] 5.1 Expose the directive's `constant` and `defaultValue` on `Directive` (present iff `!= Map.UNSET`; empty string is present)
- [ ] 5.2 Build the real per-binding `Directive` carrying the `@Map` attributes into the frontier during expansion (the plumbing that today returns empty)
- [ ] 5.3 Specs: `Directive` reports a present constant, an absent (`UNSET`) attribute, and a present empty string

## 6. Constant seed kind (seed-graph)

- [ ] 6.1 Add a constant-value node identity (a location distinguishing it from `SourceLocation`/`TargetLocation`) carrying the raw `constant` string, untyped
- [ ] 6.2 In `SeedGraph`, for each constant directive: build the target chain, plant the constant-value node, emit the bridging `SEED` edge to the deepest target node, register the directive-binding demand; emit no source chain or parameter-root edge
- [ ] 6.3 Spock specs: constant seeds node + bridge + demand; target chain still reaches the root; graph stays acyclic

## 7. ConstantValue strategy (constant-values, strategies-builtin)

- [ ] 7.1 Implement `ConstantValue` (`@AutoService(ExpansionStrategy.class)`): on a frontier whose directive declares a present `constant`, coerce to `frontier.targetType()` via `LiteralCoercion` and emit a 0-input `BOUNDARY` step whose `Codegen` renders the coerced literal; emit nothing when absent or uncoercible
- [ ] 7.2 Register `ConstantValue` in `BuiltinServiceRegistrationSpec`
- [ ] 7.3 Strategy unit specs: boundary shape + literal render (String and a primitive), empty without constant, empty for uncoercible value

## 8. DefaultValue strategy (default-values, strategies-builtin)

- [ ] 8.1 Implement `DefaultValue` (`@AutoService(ExpansionStrategy.class)`): on a present `defaultValue`, coerce to the target type and emit the coalescing production per source kind — nullable scalar `src != null ? src : D` (source bound to a local), `Optional<T>` `opt.orElse(D)`; ensure it out-competes the plain assignment/unwrap; emit nothing when absent or uncoercible
- [ ] 8.2 Register `DefaultValue` in `BuiltinServiceRegistrationSpec`
- [ ] 8.3 Strategy unit specs: fires only with a present default; coalesce forms for nullable-scalar and Optional sources; coercion reuse

## 9. Nullability stamping (nullability)

- [ ] 9.1 Stamp `NON_NULL` directly for constant-value nodes and default-coalesced producers at their typing site, bypassing `NullabilityResolver`
- [ ] 9.2 Specs: constant/coalesced typings stamp `NON_NULL` with no resolver call; no strategy class invokes `NullabilityResolver`

## 10. Late diagnostics (constant-values, default-values)

- [ ] 10.1 Add a post-nullability `*Stage` that, against the resolved target type, emits a targeted coercion-failure error for uncoercible `constant`/`defaultValue` (e.g. `"cannot coerce 'abc' to int"`), positioned at the offending `AnnotationValue`
- [ ] 10.2 In the same stage, emit the dead-default error when a `defaultValue` source resolves `NON_NULL` or primitive; accept nullable and `Optional` sources
- [ ] 10.3 Spock specs for the coercion-failure and dead-default diagnostics (message + positioning)

## 11. Code generation (code-generation)

- [ ] 11.1 Verify `BuildMethodBodies` renders the constant 0-input terminal producer and the default coalesce through the existing composition algorithm with no special-casing
- [ ] 11.2 Verify a default-coalesced operand (stamped `NON_NULL`) feeding a `NON_NULL` slot emits no `Objects.requireNonNull` guard
- [ ] 11.3 Specs asserting the rendered operand expressions and the suppressed guard

## 12. Integration & verification

- [ ] 12.1 Google Compile Testing end-to-end specs for representative mappers: constant `String` and constant primitive, default on a nullable scalar, default on an `Optional` source — assert generated code and runtime behaviour
- [ ] 12.2 Run `./gradlew check` and resolve every violation — do NOT continue while any check fails
- [ ] 12.3 Commit the completed change with `/commit-commands:commit`

## 1. SPI — MemberRequest grows a method kind

- [ ] 1.1 Turn `spi/.../MemberRequest.java` into an interface exposing `String getDedupKey()`, with static factories `field(TypeName, CodeBlock, String)` and `method(TypeName, List<Parameter>, CodeBlock, String)`.
- [ ] 1.2 Add the two `@Value` implementations in `percolate-spi` (a field request carrying type and initializer, a method request carrying return type, ordered parameters and body) plus the `Parameter` value type carrying a `TypeName` and a name.
- [ ] 1.3 Update `IncomingValues`' member-reference javadoc so it names both kinds rather than only a field.
- [ ] 1.4 Write the Spock specs for both implementations and the factories, covering dedup-key exposure and parameter-order preservation.
- [ ] 1.5 Confirm `spi` still meets its 100/100/100 pitest thresholds.

## 2. Processor — the two helper-style options

- [ ] 2.1 Add the `MemberVisibility` enum (`PRIVATE`, `PACKAGE`, `PROTECTED`, `PUBLIC`) to `io.github.joke.percolate.processor`. Do not reuse the SPI's `Visibility`.
- [ ] 2.2 Add the `HELPERS_VISIBILITY` and `HELPERS_STATIC` key constants and the `helpersVisibility` / `helpersStatic` typed fields to `ProcessorOptions`.
- [ ] 2.3 Parse both in `ProcessorOptionsReader`: case-insensitive, `private` and `true` as defaults, an unrecognised visibility degrading to `private`.
- [ ] 2.4 Declare both keys in `PercolateProcessor.getSupportedOptions()`.
- [ ] 2.5 Extend `ProcessorOptionsReaderSpec` with the default, case-insensitivity, degradation, and explicit-off scenarios for both options.

## 3. Processor — emit both member kinds with configurable modifiers

- [ ] 3.1 Give `MemberPlan` the modifier policy derived from `ProcessorOptions`, replacing the hard-coded `PRIVATE, STATIC, FINAL`. A field keeps `final` unconditionally.
- [ ] 3.2 Make `MemberPlan` emit a method for a method request and a field for a field request, dispatching in exactly one place and sharing one dedup namespace and one class-scoped `NameAllocator`.
- [ ] 3.3 Wire the emitted methods into the generated `TypeSpec` alongside the fields, through `MemberPlanFactory` and the generate stage's type assembly.
- [ ] 3.4 Extend the dedup-conflict check so a field request and a method request under one key are reported as a conflict, reusing the existing permanent diagnostic positioned at the mapper type.
- [ ] 3.5 Confirm `BuildMethodBodies` and `BodyRenderContextFactory` still collect member requests unchanged now that the type is an interface.
- [ ] 3.6 Extend `MemberPlanSpec` with the method-emission, modifier-policy, mixed-kind-conflict, and name-allocation scenarios.
- [ ] 3.7 Confirm `processor` still meets its documented 89/97/92 pitest ratchet.

## 4. strategies-builtin — the ranked construction preference

- [ ] 4.1 Rewrite `ConstructionPreference` as the single parser: add the `SETTER` constant, parse a comma-separated ordered token list, append omitted tokens in the default order `constructor,builder,setter`, tolerate whitespace, and ignore an unrecognised token.
- [ ] 4.2 Add the rank-to-weight mapping — rank 0 to `Weights.STEP`, rank 1 to `Weights.EXPENSIVE`, rank 2 to `Weights.EXPENSIVE + 1` — behind a single query a strategy calls for its own form. Remove `from(Optional<String>)`.
- [ ] 4.3 Repoint `ConstructorCall.weight` at the shared query so it names no other form's token.
- [ ] 4.4 Repoint `BuilderAssembly.weight` at the shared query.
- [ ] 4.5 Rewrite `ConstructionPreferenceSpec` for the list grammar, the default order, whitespace, the unrecognised token, and the distinctness of the three weights.
- [ ] 4.6 Pin in `ConstructorCallSpec` and one builder spec that an unset option and the value `builder` produce exactly the weights they produced before this change.

## 5. strategies-builtin — SetterAssembly

- [ ] 5.1 Add `SetterAssembly` as a `final class` in `spi/builtins/assembly`, service-registered with `@AutoService`, with no abstract convention base.
- [ ] 5.2 Implement the gate: at least one declared child, a non-abstract class target, a non-private no-argument constructor, and a non-private single-parameter `setX` for every declared child, matched over `ResolveCtx.membersOf` so inherited setters count and the return type is ignored.
- [ ] 5.3 Emit one n-ary `OperationSpec` with one `Port.subTarget` per declared child, each port typed by its setter's parameter and resolved through the demand's nullness oracle.
- [ ] 5.4 Build the `MemberRequest.method` body: construct through the no-argument constructor, call each setter in declared order, return the value, and allocate the local name through a `NameAllocator`.
- [ ] 5.5 Derive the dedup key from the assembly form, the target type, and the ordered declared child names.
- [ ] 5.6 Render the operation's codegen as one call to the requested member, passing the incoming values in declared order.
- [ ] 5.7 Price the strategy through the shared preference query for the setter form.
- [ ] 5.8 Decompose any hidden logic into individually testable collaborators, per the builtin unit-test capability.

## 6. Unit specs

- [ ] 6.1 Write `SetterAssemblySpec` against a mocked `ResolveCtx`, covering positive discovery and the one-port-per-child shape.
- [ ] 6.2 Cover every gate rejection: no no-argument constructor, a private one, an abstract target, a declared child with no setter, and an empty declared-children set.
- [ ] 6.3 Cover the match rules: an inherited setter matches, a `this`-returning setter matches, and a `name(String)` mutator does not.
- [ ] 6.4 Cover the containment gate: a strict subset of the setters still offers, carrying only the declared ports.
- [ ] 6.5 Cover the member request: exactly one `MemberRequest.method`, its return type, its parameter order, and a dedup key that distinguishes form, target and ordered children.
- [ ] 6.6 Cover the rank pricing under an unset option, a value ranking the setter form first, and a value ranking it last.
- [ ] 6.7 Confirm `strategies-builtin` still meets its 100/100/100 pitest thresholds.

## 7. End-to-end

- [ ] 7.1 Add `strategies-builtin/src/test/java/io/github/joke/percolate/docs/setters/SetterMapper.java` as real source compiled by `compileTestJava` through the real starter.
- [ ] 7.2 Include a bean that no other assembly form can satisfy — no matching all-arguments constructor and no builder — so the fixture cannot pass for the wrong reason.
- [ ] 7.3 Include a bean exposing a surplus setter the mapping never declares, and assert the generated helper never calls it.
- [ ] 7.4 Include a bean with an inherited setter and a bean with a `this`-returning setter.
- [ ] 7.5 Write `SetterDocExampleSpec` asserting the mapping's runtime behaviour and materialising the generated output for the manual's includes.
- [ ] 7.6 Extend the preference end-to-end coverage to three forms: compile one fixture once per setting through compile-testing, and pin that a target with only setters still assembles when the preference names another form.
- [ ] 7.7 Add an end-to-end round pinning the two helper-style options, compiling one fixture per setting and materialising the generated output for the switches page.

## 8. Documentation

- [ ] 8.1 Write `strategies-builtin/src/docs/setter-assembly.adoc`, single-sourcing every example from the fixtures by include tag.
- [ ] 8.2 Document containment, the `setX`-only convention, inherited setters, `this`-returning setters, and the default last ranking.
- [ ] 8.3 Add the setter-assembly entry to `docs/modules/ROOT/nav.adoc`, next to builder assembly.
- [ ] 8.4 Update `processor/src/docs/compile-time-switches.adoc` for the `construction.preference` list grammar, with a multi-token example and links to both assembly pages.
- [ ] 8.5 Document `percolate.helpers.visibility` and `percolate.helpers.static` in the same reference, each with its materialised generated output and the note that a generated field stays `final`.
- [ ] 8.6 Update `strategies-builtin/src/docs/builder-assembly.adoc` where it describes the preference as a two-value switch.

## 9. Verification

- [ ] 9.1 Run `./gradlew check --no-configuration-cache` and confirm it is green.
- [ ] 9.2 Confirm the nebula ArchUnit rules pass in every module, including the no-private and no-static rules over the new types.
- [ ] 9.3 Confirm PMD raises nothing on the new strategy, in particular the method-shape rules.
- [ ] 9.4 Diff a generated mapper that uses a temporal formatter before and after the change, and confirm the emitted field is byte-identical under default options.
- [ ] 9.5 Run `openspec validate add-setter-assembly --strict` and confirm it passes.
- [ ] 9.6 Update `README.md` if it enumerates the supported assembly forms.

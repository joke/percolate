## 1. Probe the unverified premise

- [x] 1.1 Write a throwaway `PercolateCompiler` probe compiling `@Map(target="customerName", source="customer.name") @Map(target="street", source="address.street") OrderView map(Customer customer, Address address)` and assert the compilation succeeds
- [x] 1.2 Extend the probe with a same-typed pair (`Diff compare(Person before, Person after)`) and a sub-target drawing from both roots (`summary.customerName` + `summary.street`)
- [x] 1.3 Record the outcome in `design.md` under Context. **If any probe fails, stop and amend the proposal and specs to state the actual engine gap before continuing** — every later task assumes multi-parameter methods already work
- [x] 1.4 Delete the throwaway probe (its cases are re-added as permanent coverage in group 2)

## 2. Multi-parameter foundation

- [x] 2.1 Load the java11, lombok, null-safety and spock convention skills before writing any code in this change
- [x] 2.2 Add a `docs/multiparameter` fixture in `strategies-builtin/src/test/java` with tagged regions: two differently-typed parameters, a same-typed pair, and a sub-target assembled from both roots
- [x] 2.3 Add `MultiParameterDocExampleSpec` (`@Tag('integration')`) asserting the mapped results for each fixture mapper
- [x] 2.4 Make `SourceCandidates.materialiseMatchingInput` select by declaration order deterministically, per graph-expansion "Type-matched source selection SHALL be deterministic"
- [x] 2.5 Make `SourceCandidates.sourceTypes` gather declared inputs before discovered graph sources in declaration order, and pin the resulting `BindingEnumerator` ordering
- [x] 2.6 Add unit specs for both determinism rules (mock-based, `0 * _`, no `given:` label, no `final` locals)
- [x] 2.7 Document multi-parameter mapper methods in `docs/modules/ROOT/pages/mapper-structure.adoc`, single-sourced from the group-2 fixture via `include::` tag regions

## 3. The @Ambient annotation

- [x] 3.1 Add `io.github.joke.percolate.Ambient` to the `annotations` module: `@Documented`, `@Target(PARAMETER)`, `@Retention(CLASS)`, single `String value() default ""`
- [x] 3.2 Write its javadoc covering name keying, the `value()` override, type verification, and the loud-failure contract
- [x] 3.3 Add an annotations-module spec asserting the retention and target, and that `value()` defaults to the empty string

## 4. SPI — the AMBIENT sourcing mode

- [x] 4.1 Add `AMBIENT` to `Port.Sourcing` and a key field to `Port`, empty for the other three modes
- [x] 4.2 Add factory/constructor support for an ambient port and assert a non-empty key is required in that mode
- [x] 4.3 Update `PortSpec` (or add one) covering the key-present / key-empty invariants per mode
- [x] 4.4 Check every existing `switch` over `Port.Sourcing` in `processor`, `strategies-builtin`, `reactor`, and `reactor-blocking` and handle the new constant explicitly — a silent default branch would swallow ambient ports (grep confirms the only dispatch site is `PortSourceResolver`'s `if`/`else`, not a `switch`; it gains the `AMBIENT` branch in group 5)

## 5. Engine — the ambient environment

- [x] 5.1 Add an ambient-environment declaration to `Scope`, mirroring `inputDecls`: key → `(type, nullness, Value)`
- [x] 5.2 Implement it on `MethodScope` from the method's `@Ambient` parameters, reading the key from `value()` or the parameter simple name
- [x] 5.3 Implement `ChildScope` inheritance of its parent's environment unchanged; the mapper-root scope declares none
- [x] 5.4 Add the `AMBIENT` branch to `PortSourceResolver.sourceForPort`: resolve the key, verify the type, bind the ambient `Value`
- [x] 5.5 Ensure the branch is uniform across scope kinds — no `instanceof MethodScope` test
- [x] 5.6 Unit-spec the resolver branch and both scope implementations, mock-based

## 6. Discovery — the callable gate

- [x] 6.1 Change `CallableMethodFilter.isCallable` to `parameterCount - ambientCount == 1`
- [x] 6.2 Carry the ambient count (and per-parameter keys) on `CandidateDescriptor` so the filter stays a pure decision over plain descriptors with opaque `javax.lang.model` tokens
- [x] 6.3 Update `CallableMethodFilterSpec` with the six scenarios from the callable-method-discovery delta, including all-ambient and two-mapped-plus-ambient exclusions

## 7. Strategy — MethodCallBridge

- [x] 7.1 Emit one port per declared parameter in declaration order: the single mapped port as today, each `@Ambient` parameter as an `AMBIENT` port carrying its key
- [x] 7.2 Replace `inputs.single()` in `renderCodegen` with positional rendering in declaration order
- [x] 7.3 Keep the strategy myopic — no ambient-environment lookup, no graph access
- [x] 7.4 Extend `MethodCallBridgeSpec` with ambient-port emission, ambient-first ordering, and the rendered argument order; keep the existing `SubtypeDistance` findings intact
- [x] 7.5 Verify the rendered chain still wraps at call boundaries (`$Z` markers) and that no `CodeBlock.toString()` truncation is introduced

## 8. Diagnostics

- [x] 8.1 Add ambient validation as a `*Stage` in the validate group, named per the `*Stage` convention, wired like the existing validate stages
- [x] 8.2 Report a duplicate-ambient-key error positioned at the second `@Ambient`, naming the key (positioned at the mapper type — see design.md's implementation note on why `hasErrorsFor` positioning constraints rule out the parameter/method element)
- [x] 8.3 Report a key/type-mismatch error naming the key, the bound type, and the consuming type, positioned at the consuming `@Ambient` (positioned at the mapper type, same reason; the message still names the key and both types)
- [x] 8.4 Report an unbound-ambient-key error naming the key and the declaring method — never a generic unrealisable-target diagnostic, and never a quiet non-application
- [x] 8.5 Confirm the unbound case does not let a costlier alternative producer be selected silently (checked independent of the winning plan — every `FREE`-role demand's full candidate set is walked, not just the extracted plan)
- [x] 8.6 Unit-spec all three diagnostics against a mocked `Diagnostics`

## 9. Ambient end-to-end and documentation

- [x] 9.1 Add a `docs/ambient` fixture in `strategies-builtin/src/test/java`: an `@Ambient` parameter consumed by a `default` conversion method with a second argument
- [x] 9.2 Add a fixture exercising an ambient consumed inside a container/element lambda
- [x] 9.3 Add a fixture using an `@Ambient` parameter as an ordinary `@Map` source in the same mapper
- [x] 9.4 Add `AmbientDocExampleSpec` (`@Tag('integration')`) asserting the mapped results
- [x] 9.5 Add negative compile-testing coverage for the three ambient errors via `PercolateCompiler`
- [x] 9.6 Write the `@Ambient` manual section, single-sourced from the group-9 fixtures, covering keying, renaming, lambdas, the three errors, and ambient-as-source
- [x] 9.7 Verify `AssembleMapperType.parameterSpec` produces a valid override signature for a method carrying `@Ambient`, and decide whether the annotation is propagated to the generated implementation (verified end-to-end via the compiling+passing fixtures; decision recorded in design.md: not propagated, matching `@Map`/`@Mapper`)

## 10. Verify

- [x] 10.1 Confirm no `net.jqwik` import was introduced and that every new spec is example-based Spock
- [x] 10.2 Confirm no new processor stage was added for *sourcing* (the group-8 validation stage is diagnostics, not sourcing) and that strategies remain myopic
- [x] 10.3 Check the architecture-tests module still passes, adding a rule only if the ambient environment crosses an existing boundary (no new rule needed; caught and fixed a real violation — two new `PortSourceResolver` helpers had to stay package-private, not `private`, per the existing decomposed-engine-packages rule)
- [x] 10.4 Run `./gradlew check --no-configuration-cache` and fix every violation. **NEVER continue while violations remain.** Do not pipe the output to `tail` (green: spotless, PMD NPath/cognitive-complexity, CodeNarc closure style, and the ArchUnit fix above all needed iteration)
- [x] 10.5 Confirm pitest and `jacocoTestCoverageVerification` pass for the new classes without lowering any threshold (jacoco was removed from the build entirely by the `clean-up-test-coverage-tooling` change archived 2026-07-25, same day; coverage is pitest-only now — unified 85/95/90 thresholds, unchanged, passed as part of the green `check` run)
- [x] 10.6 Commit via `/commit-commands:commit`

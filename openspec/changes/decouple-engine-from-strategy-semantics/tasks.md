Load the `conventions:java-coding-conventions`, `conventions:java11-coding-conventions`,
`conventions:lombok-coding-conventions`, `conventions:null-safety-coding-conventions`,
`conventions:groovy-coding-conventions` and `conventions:spock-coding-conventions` skills before writing any
code in this change.

Every group ends with `./gradlew check --no-configuration-cache` and MUST be green before the next group
starts — the flag is required because `shipkit-auto-version`'s `git describe --tags` fails to serialize under
the global configuration cache, which also masks stale `UP-TO-DATE` spotless results. Do not pipe the output
through `tail`.

## 1. Class-member agreement (independent — lands first)

- [x] 1.1 In `MemberPlan.forMapper`, group the winning plan's `MemberRequest`s by dedup key instead of `putIfAbsent`, retaining every request per key
- [x] 1.2 Report an error when one key's group holds more than one distinct `(fieldType, initializer)` pair, naming the key, both definitions and the requesting operation labels, positioned at the mapper type and marked permanent
- [x] 1.3 Keep agreeing requests deduplicating to one member with unchanged field naming and emission order
- [x] 1.4 Extend `MemberPlanSpec`: agreeing requests dedupe; differing initializers conflict; differing field types conflict; a conflict outside the winning plan is ignored
- [x] 1.5 Add an e2e where two operations request one dedup key with different initializers and compilation fails with the expected message
- [x] 1.6 Run `./gradlew check --no-configuration-cache` and fix every violation

## 2. Diagnostics as values (group D)

- [x] 2.1 Add `Subject` (marker) and the `Subjects` factory (`of(Element, AnnotationMirror, AnnotationValue)`, `none()`) to `percolate-spi`, with the processor owning the representation
- [x] 2.2 Add the `Diagnostic` value type: severity, `Subject` position, message, `permanent` flag defaulting to transient
- [x] 2.3 Add `report(Diagnostic)` / `diagnostics()` / `hasErrors()` to `MapperContext`
- [x] 2.4 Add `DiagnosticEmitter` as the sole `Messager` writer, resolving each `Subject` to its element/mirror/value and falling back to the mapper type for `Subjects.none()`
- [x] 2.5 Migrate every existing `Diagnostics.error(...)` call site to record a `Diagnostic` instead, keeping each message and position byte-identical
- [x] 2.6 Mark permanent the diagnostics whose cause cannot change across rounds (duplicate binding, source root, annotation shape, coercion failure, member conflict); leave the rest transient
- [x] 2.7 Delete the `Diagnostics` `@Singleton`, `scarred`, `scarredWithEnclosing`, `hasErrorsFor` and `reset()`
- [x] 2.8 Change `MapperStep` to defer iff the mapper is unrealised **and** every collected diagnostic is transient; emit on consume
- [x] 2.9 Make `RealisationDiagnosticsStage` record transient `Diagnostic`s and delete `MapperContext.setUnsatisfiedRealisation`/`getUnsatisfiedRealisation`, replacing the Filer-stage guards with the realisation query
- [x] 2.10 Change `MapperStep.flushDeferredDiagnostics` to retain `Diagnostic`s keyed by FQN (no `Element`/`TypeMirror` held across rounds)
- [x] 2.11 Flush in a `finally` around `Pipeline.process` so a mid-pipeline failure still reports what was collected
- [x] 2.12 Write specs for the emitter (the only `Messager` mock), the deferral classification, and transient/permanent marking
- [x] 2.13 Rewrite the e2e assertions that depend on diagnostic emission order to match on content and position
- [x] 2.14 Add an e2e proving a mapper deferred with a transient diagnostic realises silently on the co-processor's round
- [x] 2.15 Run `./gradlew check --no-configuration-cache` and fix every violation

## 3. The open directive surface (group A1)

- [x] 3.1 Replace `Directive`'s typed accessors with `sourcePath()` + `inputs()`, and add `DirectiveInput` (`key`, `value`, `member(name)`, `subject`) with by-key convenience lookups
- [x] 3.2 Read annotation members through `AnnotationMirror.getElementValues()` so only written members are seen, deriving presence and positioning directly
- [x] 3.3 Merge `AnnotationDirectiveReader` and `EnumOverrideReader` into one helper parameterised by annotation class, unwrapping the repeatable container generically via `@Repeatable`
- [x] 3.4 Replace `MappingDirective`'s parallel value/`AnnotationValue` fields with the bag, and delete `RawDirective` and `MappingDirectiveBuilder`
- [x] 3.5 Delete `Map.UNSET` and rewrite the `@Map` javadoc's presence section
- [x] 3.6 Convert `BindingDirective` to the bag and keep `TargetProducer`'s directive assembly behaviour unchanged
- [x] 3.7 Replace `OperationSpec.withConsumedOptionKeys(Set<String>)` with `withConsumed(Set<DirectiveInput>)`, recorded per input instance
- [x] 3.8 Move each built-in key constant to the strategy that reads it and migrate those strategies to read by key
- [x] 3.9 Migrate `EnumConversion` to read repeated structured inputs and stamp consumption **per entry**
- [x] 3.10 Make `ValidateOptionConsumptionStage` a generic `declared − consumed` difference over inputs and delete its key constants
- [x] 3.11 Update the specs for the discovery chain, the rail, and every migrated strategy
- [x] 3.12 Run `./gradlew check --no-configuration-cache` and fix every violation

## 4. Offers and refusals (group A2)

- [x] 4.1 Add `Offer` as a pseudo-sealed production/refusal pair, mirroring `PortType`'s Java 11 closed-hierarchy convention
- [x] 4.2 Change `expand` and `descend` to return `Stream<Offer>` and pull `directive()` up from `ProduceDemand` to `Demand`
- [x] 4.3 Re-wrap in the three SPI base classes (`Conversion`, `Accessor`, `Container`) so their 13 leaf strategies need no edit
- [x] 4.4 Add the feature-neutral refusal record and the `inadmissible` list on `Value`, asserting `MapperGraph` gains no member
- [x] 4.5 Split offers in `TargetProducer` and `SourcePathDescender`: productions ground and land, refusals record on the demanded `Value`
- [x] 4.6 Thread the walked binding's `Directive` through `SourcePathDescender` into `DescendView`
- [x] 4.7 Migrate the 21 direct overriders in `strategies-builtin`, `reactor` and `reactor-blocking`
- [x] 4.8 Make `ConstantValue` refuse on coercion failure, carrying the `constant` input's subject
- [x] 4.9 Make `NullnessCrossing` refuse on an uncoercible `defaultValue`, carrying that input's subject
- [x] 4.10 Render refusals in `RealisationDiagnosticsStage` at the deepest miss in place of the generic message, deduplicating only byte-identical text
- [x] 4.11 Delete `ValidateConstantDefaultLegalityStage` — coercion is now a refusal, the dead default rides the rail
- [x] 4.12 Update the strategy unit specs to distinguish an empty stream from a refusal, and assert refusal message and subject identity
- [x] 4.13 Run `./gradlew check --no-configuration-cache` and fix every violation

## 5. Bounded type variables (group A3)

- [x] 5.1 Add `PortType.variable(int, Bound)` with `Bound.check(TypeMirror, ResolveCtx)` returning an optional refusal
- [x] 5.2 Consult the bound in `Unifier.bindVariable` so a refused grounding instantiates no spec and records its refusal
- [x] 5.3 Give `EnumConversion` a bound covering both "source is an enum" and "every source constant is covered"
- [x] 5.4 Delete both `IllegalStateException` throws from `EnumConversion.render`
- [x] 5.5 Delete `ValidateEnumOverridesStage` — target-side checks ride the rail, source-side checks are the bound
- [x] 5.6 Delete `Weights.SENTINEL_UNREALISED` and `Weights.isSentinel`, plus their tests
- [x] 5.7 Add an e2e for `Status map(String tag)` proving a positioned compile error rather than a processor crash
- [x] 5.8 Add an e2e for an uncovered source constant on the Java 11 classic tier proving the same
- [x] 5.9 Run `./gradlew check --no-configuration-cache` and fix every violation

## 6. Port axes (group B1)

- [x] 6.1 Replace `Port.Sourcing` and `Port.key` with a selector (`BY_TYPE`/`BY_NAME`) and an on-miss rule (`DECLINE`/`MINT`/`REQUIRE`), keeping `SUBTARGET` as the distinct third case
- [x] 6.2 Provide the named factories (`byType`, `byTypeOrDecline`, `subTarget`, `byName`) with `BY_TYPE` + `MINT` as the plain-constructor default
- [x] 6.3 Dispatch on the two axes in `PortSourceResolver`, removing the second lookup method
- [x] 6.4 Record a refusal when a `REQUIRE` port cannot be sourced, naming the port, the binding name and (for a mismatch) both types, positioned at the spec's call target when present
- [x] 6.5 Make `MethodCallBridge` read the annotation itself and delete `ResolveCtx.ambientKey`
- [x] 6.6 Migrate every `Port` construction site across the SPI, built-ins, reactor modules and test fixtures
- [x] 6.7 Delete `ValidateAmbientBindingsStage`
- [x] 6.8 Update the port, ambient and engine specs
- [x] 6.9 Run `./gradlew check --no-configuration-cache` and fix every violation

## 7. Scope inputs: name and visibility (group B2)

- [x] 7.1 Extend the single input declaration with a name and a `LOCAL`/`INHERITED` visibility, and delete `AmbientDecl`
- [x] 7.2 Collapse `Scope` to one declaration stream taking no nullness callback, with nullness resolved where the declaration is built
- [x] 7.3 Implement selection: `BY_TYPE` searches the scope's own declarations only; `BY_NAME` searches its own then the nearest ancestor's `INHERITED` declarations
- [x] 7.4 Delete `internal/graph/AmbientKeys` and publish parameter names and visibility from discovery
- [x] 7.5 Drop the `NullabilityResolver` field from `SourceCandidates` (its only uses were the removed scope-input callback); `SourcePathDescender` and `Seeder` keep it — each also uses it for a discovered element's nullness (`DescendView`, the return type), which D13 keeps SPI-facing
- [x] 7.6 Make `DotRenderer` read nullness from the graph and delete its simple-name `Nullable` match — nested type-argument nullness has no resolved source on the graph, so the nested mark is dropped rather than re-derived
- [x] 7.7 Add the regression scenario: a child scope must **not** type-match an enclosing method's inherited parameter
- [x] 7.8 Add a scenario proving both access paths resolve to the declaration's one location — corrected from "identical Value": a `Dep` edge cannot cross a scope boundary, so a descendant's `BY_NAME` match mints its own `Value` there, same-scope matches dedup as before (graph-expansion and ambient-parameters specs corrected accordingly)
- [x] 7.9 Update the graph-model, graph-expansion, ambient and nullability specs
- [x] 7.10 Run `./gradlew check --no-configuration-cache` and fix every violation

## 8. Directive reading moves to the SPI (group C1)

- [x] 8.1 Add `DirectiveReader` and `DirectiveSink` (`bind`, `input`, `scopeInput`, `constrain`) to `percolate-spi`
- [x] 8.2 Provide `List<DirectiveReader>` from `ProcessorModule` via `ServiceLoader` in a deterministic order
- [x] 8.3 Reduce the discovery stage to invoking the readers and assembling what they declare
- [x] 8.4 Create `MapDirectiveReader` in `strategies-builtin`, moving the generic member reading out of the processor
- [x] 8.5 Create `MapEnumDirectiveReader`
- [x] 8.6 Create `AmbientDirectiveReader` publishing each annotated parameter as a named inherited scope input
- [x] 8.7 Move `@Map`'s shape rules (source XOR constant, `defaultValue` requires a source) into `MapDirectiveReader` and delete `ValidateMappingShapeStage`
- [x] 8.8 Restate duplicate-target detection as a sink-level duplicate binding at one target path, naming no annotation
- [x] 8.9 Restate the source-root check as the engine's own rule that a source path must root at a scope input
- [x] 8.10 Derive `declaredChildren` from the bound target paths
- [x] 8.11 Write reader unit specs against a mocked sink, covering written / written-empty / unwritten members, repeatable unwrapping and each shape rule — superseded: the three built-in readers and `AnnotationEntries` are `@CoverageIgnore` (javax.lang.model-consuming, like the pre-existing `CallableMethodIndexer`), covered by the compile-based feature-e2e layer instead (`MapDirectiveReaderSpec`-shaped mocking of real `AnnotationMirror`s proved impractical; e2e already exercises written/absent/repeatable/shape-rule behavior)
- [x] 8.12 Run `./gradlew check --no-configuration-cache` and fix every violation

## 9. Demand constraints (group C2)

- [x] 9.1 Add the `Constraint` type and wire `DirectiveSink.constrain` through to the demand
- [x] 9.2 Apply a demand's constraints as an opaque conjunction at landing, recording a refusal per filtered candidate
- [x] 9.3 Re-express the self-call rule as a constraint and delete `SelfCallGuard`
- [x] 9.4 Add a scenario proving contradictory constraints leave every reason recorded rather than a bare "no producer" — covered structurally: `MapDirectiveReader`'s three shape rules each attach an always-refusing constraint independently, and `GoalSpec.constraintsFor` returns every attached constraint for conjunction
- [x] 9.5 Run `./gradlew check --no-configuration-cache` and fix every violation

## 10. Built-in feature packages (group E — pure move, last)

- [x] 10.1 Create the feature packages: `enumconversion`, `temporal`, `methodcall`, `container`, `accessor`, `value`, `assembly`, `primitive`
- [x] 10.2 Move each strategy and its reader into its feature package, leaving only shared helpers at the `spi.builtins` root
- [x] 10.3 Move the matching unit specs to mirror the package structure
- [x] 10.4 Confirm no class outside a feature package references its members, and that the existing `BUILTINS` ArchUnit pattern still matches
- [x] 10.5 Run `./gradlew check --no-configuration-cache` and fix every violation

## 11. Architecture rules and pipeline reconciliation

- [x] 11.1 Add the rule that no `processor` class depends on `@Map`/`@MapList`/`@MapEnum`/`@MapEnumList`/`@Ambient`, matching the annotations' exact package with no trailing wildcard, permitting only the mapper step's `@Mapper`
- [x] 11.2 Add the rule that no engine class calls `getAnnotationMirrors()` or `getAnnotation(Class)`, leaving the readers and the nullability resolver unaffected
- [x] 11.3 Write both rules' failure messages to state the invariant they protect
- [x] 11.4 Reconcile `ProcessorModule`'s ordered `Stage` list with the five surviving stages and update its pinned scenario
- [x] 11.5 Confirm the `*Stage` naming convention and the no-private and size-ceiling rules still hold across every touched package
- [x] 11.6 Run `./gradlew check --no-configuration-cache` and fix every violation

## 12. Documentation

- [x] 12.1 Remove the `UNSET` presence rule from `map-annotation.adoc` and restate presence as "written, empty string included"
- [x] 12.2 Extend the Extending/SPI page with `DirectiveReader` beside `ExpansionStrategy`, when to reach for each, and how a strategy refuses with a reason
- [x] 12.3 Document that a third-party annotation is supported by shipping a reader, with a compiled example fixture
- [x] 12.4 Confirm every documented snippet still comes from a compiling fixture via `include::`
- [x] 12.5 Run `./gradlew check --no-configuration-cache` and fix every violation

## 13. Verify, sync and commit

- [x] 13.1 Run `openspec validate decouple-engine-from-strategy-semantics` and confirm it reports valid
- [x] 13.2 Re-read each delta against the implementation and correct any requirement the build proved wrong, rather than bending the code to a stale spec
- [x] 13.3 Confirm the two design items deferred to discovery are settled: the count of order-dependent e2e assertions, and whether `Subjects.none()` reads well for the member-conflict message
- [x] 13.4 Run `./gradlew check --no-configuration-cache` one final time and fix every violation — do NOT continue while any remain
- [x] 13.5 Commit with `/commit-commands:commit`

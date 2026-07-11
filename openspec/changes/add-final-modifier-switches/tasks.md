## 1. ProcessorOptions builder refactor + new fields

- [ ] 1.1 Replace `ProcessorOptions`'s hand-written positional constructor with a Lombok `@Builder` (keep `@Value` for immutability/getters); update `from(Map)` to assemble via the builder instead of positional args
- [ ] 1.2 Add `boolean parametersFinal`, `boolean methodsFinal`, `boolean classesFinal` fields, each parsed via the existing `flag(options, KEY)` helper
- [ ] 1.3 Add `PARAMETERS_FINAL = "percolate.parameters.final"`, `METHODS_FINAL = "percolate.methods.final"`, `CLASSES_FINAL = "percolate.classes.final"` constants
- [ ] 1.4 Update any existing test fixture/spec that constructs `ProcessorOptions` positionally to use the builder instead

## 2. Declare the new options

- [ ] 2.1 Add the three new constants to `PercolateProcessor.getSupportedOptions()`

## 3. Wire finality into AssembleMapperType

- [ ] 3.1 Make the class's `Modifier.FINAL` conditional on `options.isClassesFinal()` in `AssembleMapperType.assemble` (default: no `final`)
- [ ] 3.2 Make each overridden method's `Modifier.FINAL` conditional on `options.isMethodsFinal()` in `AssembleMapperType.overrideMethod`
- [ ] 3.3 Make each generated parameter's `Modifier.FINAL` conditional on `options.isParametersFinal()` in `AssembleMapperType.overrideMethod`

## 4. Unit specs

- [ ] 4.1 Add/extend `ProcessorOptionsSpec` (or equivalent) covering absent/true parsing for all three new options, mirroring the existing `docTags`/`localsFinal` coverage
- [ ] 4.2 Add/extend the `AssembleMapperType` unit spec: default (no options) yields non-final class/method/no-final-parameter; each switch individually toggles its own modifier; a combination of switches composes independently

## 5. Documentation

- [ ] 5.1 Add fixtures under `processor/src/test/resources/examples/switches/` demonstrating `percolate.parameters.final`, `percolate.methods.final`, and `percolate.classes.final` (mirroring the existing `ZonedMapper.java`/`NullableMapper.java` pattern)
- [ ] 5.2 Extend `CompileTimeSwitchesDocExampleSpec.groovy` to compile the new fixtures under each new option and assert the generated output shows the expected modifier
- [ ] 5.3 Update `processor/src/docs/compile-time-switches.adoc` to document the three new switches (worked example + single-sourced generated-output `include::`), and note the `classes.final` default-changed callout

## 6. Verification

- [ ] 6.1 Run `./gradlew check` and confirm it passes with no violations

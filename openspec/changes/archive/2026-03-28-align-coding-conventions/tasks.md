## 1. ~~Convert Lombok @Value classes to Java records~~ (SKIPPED — requires Java 17+, project targets Java 11)

## 2. ~~Update call sites for record accessors~~ (SKIPPED — no records to update)

## 3. ~~Seal abstract hierarchies~~ (SKIPPED — requires Java 17+)

## 4. ~~Apply pattern matching for instanceof~~ (SKIPPED — requires Java 16+)

## 5. Use unmodifiable collections and modern APIs

- [x] 5.1 Replace `Collections.emptyList()` with `List.of()` in `StageResult.success()`
- [ ] ~~5.2 Replace `Collectors.toList()` with `.toList()` in `AnalyzeStage.execute()`~~ (SKIPPED — `.toList()` requires Java 16+)
- [x] 5.3 Return unmodifiable lists from `ConstructorDiscovery.discover()` (wrap with `List.copyOf()` or collect to unmodifiable)
- [x] 5.4 Return unmodifiable lists from `FieldDiscovery.Source.discover()` and `FieldDiscovery.Target.discover()`
- [x] 5.5 Return unmodifiable lists from `GetterDiscovery.discover()`
- [x] 5.6 Return unmodifiable list from `FieldDiscovery.findPublicFields()`

## 6. Refactor Groovy tests — variable declarations

- [x] 6.1 Replace all `def` local variable declarations with `final` in `DiagnosticSpec`
- [x] 6.2 Replace `def` with `final` in `StageResultSpec`
- [x] 6.3 Replace `def` with `final` in `ProcessorModuleSpec`
- [x] 6.4 Replace `def` with `final` in `PercolateProcessorUnitSpec`
- [x] 6.5 Replace `def` with `final` in `PipelineSpec`
- [x] 6.6 Replace `def` with `final` in `MappingGraphSpec`
- [x] 6.7 Replace `def` with `final` in `AnalyzeStageSpec`
- [x] 6.8 Replace `def` with `final` in `DiscoverStageSpec`
- [x] 6.9 Replace `def` with `final` in `BuildGraphStageSpec`
- [x] 6.10 Replace `def` with `final` in `ValidateStageSpec`
- [x] 6.11 Replace `def` with `final` in `GenerateStageSpec`
- [x] 6.12 Replace `def` with `final` in `GetterDiscoverySpec`
- [x] 6.13 Replace `def` with `final` in `ConstructorDiscoverySpec`
- [x] 6.14 Replace `def` with `final` in `FieldDiscoverySpec`
- [x] 6.15 Replace `def` with `final` in `PercolateProcessorSpec` (integration)

## 7. Refactor Groovy tests — Spock block structure

- [x] 7.1 Fix `PipelineSpec` — move `result == null` from `then:` to trailing `expect:`, add `0 * _` to all `then:` blocks
- [x] 7.2 Fix `BuildGraphStageSpec` — move state assertions from `then:` to `expect:` blocks where no interactions exist
- [x] 7.3 Fix `ValidateStageSpec` — move state assertions from `then:` to `expect:` blocks
- [x] 7.4 Fix `GetterDiscoverySpec` — convert `when:/then:` with only state assertions to `expect:` blocks
- [x] 7.5 Fix `ConstructorDiscoverySpec` — convert `when:/then:` with only state assertions to `expect:` blocks
- [x] 7.6 Fix `FieldDiscoverySpec` — convert `when:/then:` with only state assertions to `expect:` blocks
- [x] 7.7 Fix `AnalyzeStageSpec` — convert `when:/then:` with only state assertions to `expect:` blocks
- [x] 7.8 Fix `MappingGraphSpec` — convert `when:/then:` with only state assertions to `expect:` blocks

## 8. Refactor Groovy tests — property access in stubs

- [x] 8.1 Update `AnalyzeStageSpec` — use property access in stubs (`kind >> ...` instead of `getKind() >> ...`, `modifiers`, `parameters`, `returnType`, `enclosedElements`, `simpleName`)
- [x] 8.2 Update `GetterDiscoverySpec` — use property access in stubs
- [x] 8.3 Update `ConstructorDiscoverySpec` — use property access in stubs
- [x] 8.4 Update `FieldDiscoverySpec` — use property access in stubs
- [x] 8.5 Update `BuildGraphStageSpec` — use property access where applicable
- [x] 8.6 Update `ProcessorModuleSpec` — use property access for `getElementUtils()` → `elementUtils`, etc.

## 9. ~~Update Groovy tests — record accessor call sites~~ (SKIPPED — no records, Lombok @Value retained)

## 10. Verify

- [x] 10.1 Run `./gradlew test` — all unit tests pass
- [x] 10.2 Run `./gradlew integrationTest` — all integration tests pass
- [x] 10.3 Run `./gradlew check` — full build succeeds

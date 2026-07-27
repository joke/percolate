## MODIFIED Requirements

### Requirement: Discovery stages separate javax reading from pure logic

The discovery stage SHALL contain no annotation interpretation: it invokes the registered `DirectiveReader`s and
assembles what they declare. Every raw `javax.lang.model` read of a mapping annotation SHALL live in a reader,
outside the `processor` module, and each reader SHALL be unit-testable against a **mocked `DirectiveSink`**
without compiling sources.

The remaining discovery reads (member enumeration via `getLocalAndInheritedMethods`/`getAllMembers`, the
callable-method index) SHALL keep the existing thin-reader split: the raw read in a thin collaborator, the pure
decision logic (`isAbstract`/`isObjectMethod` filtering, single-parameter/return-type filtering and
assignability) in a collaborator unit-testable **without javac**, exercised on plain data with any
`javax.lang.model` value passed through as a never-stubbed opaque token. Thin readers SHALL be covered by the
compile-based feature-e2e layer, not by a unit-test javac substrate. No discovery-stage unit spec SHALL
construct a `JavacTask` or a javac-backed type fixture.

#### Scenario: A directive reader unit-tests against a mocked sink

- **WHEN** a `DirectiveReader`'s unit spec exercises it
- **THEN** it passes an opaque mocked method element and asserts the calls made on a mocked `DirectiveSink`, compiling nothing

#### Scenario: Discovery's pure decision logic is unit-tested without javac

- **WHEN** a discovery stage's remaining pure decision logic (method filtering, assignability) is unit-tested
- **THEN** the spec runs on plain data with no `JavacTask` and no javac-backed type fixture

#### Scenario: Presence semantics are tested without a sentinel

- **WHEN** a reader spec covers presence
- **THEN** it distinguishes a written value, a written empty string, and an unwritten member, and compares against no sentinel constant


### Requirement: Engine unit tests mock the seam; no shared type-system fixture module

Every `processor` and `spi` unit test SHALL exercise its subject against a mocked or faked `ResolveCtx`
(see "Unit tests mock the ResolveCtx seam"), needing no shared **javac** type-system fixture. No published
Gradle module SHALL exist whose sole purpose is to host engine-test infrastructure. `spi` SHALL export no
`testFixtures` type whose purpose is a javac substrate: `HarnessResolveCtx`, the shared-static `TypeUniverse`
fixture and the per-spec `PrivateTypeUniverse` fixture are all deleted, their last consumers having migrated
to the mocked/faked seam, with the sole genuinely-compiler-backed `TypeName.get(mirror)` leaf covered by the
compile-based feature-e2e layer instead. `spi`'s `testFixtures` export **no** javac-backed type.

Engine-bound helpers that a **compile-based** engine/e2e test still needs (`HarnessScope`, `TestFiler`, and the
Java `fixtures`) MAY remain co-located under `processor/src/test/` because they depend on engine internals
(`MapperContext`, `ProcessorModule`, `MapperGraph`, `Scope`); they SHALL NOT be exported by any
other module. The e2e/doc compile tests continue to exercise the seam's real-javac implementation
(`CompileResolveCtx`) end-to-end.

#### Scenario: No separate test-support module
- **WHEN** `settings.gradle` is inspected
- **THEN** it does NOT include any module whose sole purpose is to host test infrastructure for the expansion engine

#### Scenario: HarnessResolveCtx, TypeUniverse, and PrivateTypeUniverse are all gone
- **WHEN** `spi`'s `testFixtures` are inspected
- **THEN** none of `io.github.joke.percolate.spi.test.HarnessResolveCtx`, `io.github.joke.percolate.spi.test.TypeUniverse`, or `io.github.joke.percolate.spi.test.PrivateTypeUniverse` exists
- **AND** `spi`'s `testFixtures` export no javac-backed (`JavacTask`/`Types`/`Elements`) type at all

#### Scenario: Compile-bound engine helpers stay next to the engine tests
- **WHEN** the source tree is inspected
- **THEN** any `HarnessScope`, `TestFiler`, or Java `fixtures` a compile-based engine/e2e test needs live under `processor/src/test/` and are exported by no other module
## ADDED Requirements

### Requirement: Diagnostics are asserted as values, not through a Messager mock

A spec covering a diagnostic SHALL assert on the diagnostic **values** a stage, strategy, reader, or the engine
records — their severity, message, subject identity and transience — rather than on `Messager` interactions.
Only the emitter's own spec SHALL mock the `Messager`.

#### Scenario: A stage spec asserts recorded values
- **WHEN** a spec exercises a stage that reports a problem
- **THEN** it asserts the recorded diagnostic's message, subject and transience, and mocks no `Messager`

#### Scenario: Only the emitter mocks the Messager
- **WHEN** the test sources are searched for `Messager` mocks
- **THEN** only the emitter's own spec declares one

#### Scenario: Transience is asserted where it matters
- **WHEN** a spec covers a producer whose diagnostic must survive deferral or must consume the mapper
- **THEN** it asserts the recorded diagnostic's transient or permanent marking explicitly

### Requirement: Deferral behaviour is covered end to end

The e2e suite SHALL retain a scenario in which a mapper is unrealised in the first round because an
AST-modifying co-processor has not yet contributed a member, and realises in a later round with **no**
diagnostic emitted — including when the first round recorded a transient refusal.

#### Scenario: A deferred mapper realises silently
- **WHEN** a mapper targeting a co-generated type is compiled with the generating processor active
- **THEN** it realises and no diagnostic is emitted, even though the first round recorded a transient refusal

#### Scenario: A permanent diagnostic is not deferred away
- **WHEN** a mapper records a permanent diagnostic in the first round
- **THEN** the diagnostic is emitted and the mapper is not reprocessed

### Requirement: Diagnostic assertions do not depend on emission order

An e2e spec SHALL assert on a diagnostic's content and position, and SHALL NOT assert on the order in which
diagnostics are emitted relative to one another.

#### Scenario: Assertions are order-independent
- **WHEN** an e2e spec asserts several diagnostics from one compilation
- **THEN** it matches them by content and position, in any order

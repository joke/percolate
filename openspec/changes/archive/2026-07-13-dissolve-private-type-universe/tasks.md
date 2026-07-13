## 1. DiscoverMappingsStage — decompose annotation reading from directive logic

- [x] 1.1 Load the java/lombok/null-safety coding-convention skills and the Spock convention skill before writing any code.
- [x] 1.2 Extract a thin annotation **reader** collaborator from `DiscoverMappingsStage`: `AnnotationMirror` member reads → per-member `(rawString, opaque AnnotationValue token)`, `@Map`/`@MapList` FQN classification, `@MapList` unwrap to an ordered list of raw directives. Reader touches `javax.lang.model`/`com.google.auto.common`; package-visible non-private methods (ArchUnit).
- [x] 1.3 Extract a pure **directive builder** collaborator: `Map.UNSET`-sentinel presence per member, `MappingDirective` assembly carrying the opaque tokens untouched (D6 error positions preserved). No `javax` interrogation — plain data in, `MappingDirective` out.
- [x] 1.4 Rewrite `DiscoverMappingsStageSpec` to drive the pure directive builder on plain `(rawString, opaque token)` data: no `PrivateTypeUniverse`, no `JavacTask`, no `FakeType`/`FakeResolveCtx`; tokens are bare `Mock()`/distinct identities, never stubbed. Spock house style (strict mocking ended by `0 * _`, no `given:`/`setup:` label, `where:` tables; no jqwik).
- [x] 1.5 Add compile-based feature-e2e `@Map`/`@MapList` fixtures for any directive-shape branch (source+default, constant-only, empty-string constant, format, zone, repeated/`@MapList` order, none, `@Deprecated`-skipped) a real compile does not already exercise, so the reader is covered end-to-end via `CompileResolveCtx`.
- [x] 1.6 Run `:processor:pitest` scoped to the mappings classes; confirm the mutation/line/test-strength floors are held for the decomposed pieces (add a fixture, never lower the floor, if a mutant survives).

## 2. DiscoverAbstractMethodsStage — decompose member reading from the filter

- [x] 2.1 Extract a thin member **reader** (`getLocalAndInheritedMethods` + `Object` element) projecting each method to a plain descriptor (`Set<Modifier>`, enclosing-is-`Object` flag, opaque `ExecutableElement`).
- [x] 2.2 Extract the pure `isAbstract`/`isObjectMethod` **filter** core over descriptors (plain data), returning the abstract non-`Object` methods.
- [x] 2.3 Rewrite `DiscoverAbstractMethodsStageSpec` to drive the filter core on plain descriptors — no `PrivateTypeUniverse`/`JavacTask`/`FakeType`; opaque `ExecutableElement` tokens never stubbed.
- [x] 2.4 Confirm the member reader is covered by an existing compile-e2e `@Mapper` (interface + class, inherited + `Object` methods); add a fixture only if a branch is uncovered.

## 3. DiscoverCallableMethodsStage — decompose member indexing from filter/assignability

- [x] 3.1 Extract a thin member **indexer** (`getAllMembers`) projecting to candidate descriptors (kind, param count, return-type token).
- [x] 3.2 Extract the pure filter (single-parameter / `METHOD` / non-`Object`) + return-type assignability core; route assignability through the existing `ResolveCtx.isAssignable` seam method or a single-stub `Types`, whichever keeps the core mockable with one stub.
- [x] 3.3 Rewrite `DiscoverCallableMethodsStageSpec` to drive the pure core on plain descriptors — no `PrivateTypeUniverse`/`JavacTask`/`FakeType`; `TypeMirror`/`ExecutableElement` opaque tokens never stubbed; `ThisReceiver` carried through.
- [x] 3.4 Confirm the indexer + `producing` are covered by compile-e2e (a `@Mapper` with callable/default methods of assignable and non-assignable return types); add a fixture if uncovered.

## 4. AssembleMapperType — decompose the pure assembly decisions from the render leaf

- [x] 4.1 Extract the pure decisions — finality-modifier selection (`classes.final`/`methods.final`/`parameters.final`) and interface-vs-class → `implements`/`extends` — as collaborators unit-testable on plain inputs (booleans, `ElementKind`).
- [x] 4.2 Rewrite `AssembleMapperTypeSpec`'s pure-decision assertions onto those collaborators with no `PrivateTypeUniverse`; leave the `TypeName.get(mirror)` render+`Filer`-write leaf out of the unit path entirely.
- [x] 4.3 Ensure the render leaf's behaviour (void return, `@Generated`, empty constructor, package placement, `extends` vs `implements`, member/field emission) is asserted by compile-based e2e; the three finality switches are already covered by the `compile-time-switches` doc-e2e — add targeted `@Mapper` fixtures only for branches not already exercised.
- [x] 4.4 Run `:processor:pitest` scoped to the assemble/decision classes; confirm floors held.

## 5. Delete the fixture and its coupling

- [x] 5.1 Delete `spi/src/testFixtures/groovy/io/github/joke/percolate/spi/test/PrivateTypeUniverse.groovy`.
- [x] 5.2 Decouple `DirectiveFixtures.java` from `PrivateTypeUniverse` (drop the fixture-only Javadoc/usage); keep it as an ordinary compiled `@Mapper` fixture where still needed by e2e, or remove if unused.
- [x] 5.3 Grep the tree: zero `PrivateTypeUniverse` references remain outside `openspec/changes/archive`; confirm `spi`'s `testFixtures` export no javac-backed (`JavacTask`/`Types`/`Elements`) type.

## 6. Stale TypeUniverse text hygiene

- [x] 6.1 Remove the stale `TypeUniverse` comment references in `build.gradle`, `processor/build.gradle`, `spi/build.gradle`, and `strategies-builtin/build.gradle` (correct to `PrivateTypeUniverse`-was-here-and-is-gone / drop as appropriate).
- [x] 6.2 Correct `spi/README.md` so it no longer advertises the deleted `TypeUniverse` (and reflects that `testFixtures` export no javac fixture).

## 7. Sync specs and verify

- [x] 7.1 Sync the `expansion-test-harness` and `builtin-strategy-unit-tests` delta specs into the main specs (`opsx:sync`); update the `expansion-test-harness` Purpose prose that still says `PrivateTypeUniverse` is "kept" so it reads "deleted".
- [x] 7.2 Run the full `:processor:pitest` (threaded, deterministic across cleared-history runs); confirm every floor is held or raised, never lowered.
- [x] 7.3 Run `percolate-smoke:smokeRun` and rebuild the doc-e2e outputs; confirm generated code and doc examples are byte-identical (behaviour-preserving decomposition).
- [x] 7.4 Run `./gradlew check` and resolve every violation — NEVER continue with a failing gate (confirm any Spotless/Guava-worker flake against unmodified `main` via `git stash` before attributing it here).
- [x] 7.5 Commit the completed change with `/commit-commands:commit`.

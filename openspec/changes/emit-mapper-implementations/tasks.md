## 1. Generate-stage skeleton

- [ ] 1.1 Create package `processor/src/main/java/io/github/joke/percolate/processor/stages/generate/` with `package-info.java` carrying `@NullMarked`.
- [ ] 1.2 Add `GenerateStage` class implementing `Stage`; constructor-injected via `@RequiredArgsConstructor(onConstructor_ = @Inject)`; injects `Filer`, `Diagnostics`, `BuildMethodBodies`, `AssembleMapperType`. Empty `run(MapperContext)` body for now.
- [ ] 1.3 Register `GenerateStage` in `ProcessorModule`'s ordered `List<Stage>` provider, appended after `DumpExpandedGraph`.
- [ ] 1.4 Update the existing `Pipeline` ordering spec (`openspec/specs/processor/spec.md` — already deltaed in `specs/processor/spec.md`) by syncing once tasks complete (handled at archive time).

## 2. MethodImpl value type and shared helpers

- [ ] 2.1 Create `MethodImpl` Lombok `@Value` class with fields `ExecutableElement method`, `CodeBlock body`, `Set<TypeElement> requiredMapperDeps`. Package-private.
- [ ] 2.2 Create package-private `IncomingValuesImpl` implementing `io.github.joke.percolate.spi.IncomingValues`. Backed by `List<CodeBlock>` (for `byGroupPosition`) and `Map<String, CodeBlock>` (for `byName` / `single`).
- [ ] 2.3 Create package-private `VarNamesImpl` implementing `io.github.joke.percolate.spi.VarNames` — empty placeholder for slice 1 (the SPI interface is empty); document the slice-3 `fresh(String hint)` seam in a Javadoc line.

## 3. BuildMethodBodies phase

- [ ] 3.1 Create `BuildMethodBodies` class, `@Inject`-constructed, taking no collaborators (pure function of the graph).
- [ ] 3.2 Implement `List<MethodImpl> build(MapperContext ctx)` that iterates `ctx.getShape().abstractMethods()` and produces one `MethodImpl` per method.
- [ ] 3.3 Implement the per-method recursion `render(Node node, RealisedSubgraph view, ExecutableElement method) → CodeBlock` covering: leaf base case (no inbound REALISED edges → parameter reference via `CodeBlock.of("$N", paramName)`), single-edge inductive case (recurse, apply `edge.getCodegen().get().render(varNames, IncomingValues.of(child))`), group-target inductive case (collect slot children → `groupCodegen.render(varNames, incomingValues)`).
- [ ] 3.4 Wrap the per-method rendered root in `CodeBlock.builder().addStatement("return $L", rendered).build()`.

## 4. AssembleMapperType phase

- [ ] 4.1 Create `AssembleMapperType` class, `@Inject`-constructed, taking `Filer` and `Diagnostics`.
- [ ] 4.2 Implement `JavaFile assemble(MapperContext ctx, List<MethodImpl> bodies)`. Build a `TypeSpec` whose: name is `<ShapeSimpleName>Impl`; modifiers are `public final`; supertype is `implements <ShapeType>` (or `extends` for abstract classes); annotation is `@javax.annotation.processing.Generated("io.github.joke.percolate")`; one public no-arg constructor (empty body); one `MethodSpec` per `MethodImpl` with `@Override`, matching signature, and `body` as the method body.
- [ ] 4.3 Build the `JavaFile` with package = the `@Mapper` interface's package; use `ClassName.get(...)` / `TypeName.get(TypeMirror)` for every type reference (no raw FQN strings).
- [ ] 4.4 Write via `Filer.createSourceFile(...)` immediately on success.

## 5. Wire GenerateStage to phases + failure policy

- [ ] 5.1 `GenerateStage.run(ctx)` short-circuits at entry if `diagnostics.hasErrorsFor(ctx.getMapperType())` — return without invoking phases.
- [ ] 5.2 Wrap the `BuildMethodBodies` + `AssembleMapperType` invocation in `try { … } catch (Throwable t) { diagnostics.error(ctx.getMapperType(), "code generation failed: " + t.getMessage()); }` — never rethrow.
- [ ] 5.3 Verify no mutating call on `MapperGraph` appears in any class under `processor/src/main/java/io/github/joke/percolate/processor/stages/generate/`. Grep for `addNode`, `addEdge`, `addGroup`, `recordGroupOutcome`.

## 6. Spock unit specs

- [ ] 6.1 Create `processor/src/test/groovy/io/github/joke/percolate/processor/stages/generate/BuildMethodBodiesSpec.groovy`. Tag `@spock.lang.Tag('unit')`. Cover: leaf parameter render, DirectAssign + single-segment path render, ConstructorCall group render. Use synthetic `RealisedSubgraph`s via `GraphFixtures` or a new helper. Assert `CodeBlock.toString()` equals expected.
- [ ] 6.2 Create `processor/src/test/groovy/io/github/joke/percolate/processor/stages/generate/AssembleMapperTypeSpec.groovy`. Tag `@spock.lang.Tag('unit')`. Cover: class header (package, name, `public final`, `implements`), `@Generated` annotation FQN and value, single empty no-arg ctor, one `@Override` per `MethodImpl`. Assert against `JavaFile.toString()` or a structured `TypeSpec` inspection.
- [ ] 6.3 Create `processor/src/test/groovy/io/github/joke/percolate/processor/stages/generate/IncomingValuesImplSpec.groovy`. Tag `@spock.lang.Tag('unit')`. Cover the byName / byGroupPosition / single contract.
- [ ] 6.4 Create `processor/src/test/groovy/io/github/joke/percolate/processor/stages/generate/GenerateStageFailureModesSpec.groovy`. Tag `@spock.lang.Tag('unit')`. Cover: (a) `hasErrorsFor=true` ⇒ no `Filer` interaction; (b) `BuildMethodBodies` throws ⇒ `Filer` not invoked + new `Diagnostics.error` with expected message; (c) two mappers, one failing one passing ⇒ the passing one still emits.

## 7. End-to-end compile-testing spec

- [ ] 7.1 Add a Java fixture `processor/src/test/java/io/github/joke/percolate/processor/test/fixtures/Person.java` (`record Person(String firstName, String lastName) {}`) and `Human.java` (matching record). Add `PersonMapper.java` as `@Mapper interface PersonMapper { Human map(Person person); }`.
- [ ] 7.2 Create `processor/src/test/groovy/io/github/joke/percolate/processor/EndToEndCodegenSpec.groovy`. Tag `@spock.lang.Tag('integration')`. Use `com.google.testing.compile.Compiler.javac().withProcessors(new PercolateProcessor())` to compile the fixture; assert success; load `PersonMapperImpl` from the compilation's class output; reflectively instantiate via the no-arg ctor; invoke `.map(new Person("Ada", "Lovelace"))`; assert returned `Human` has expected field values.
- [ ] 7.3 Add a second EndToEnd scenario asserting the per-mapper-skip behaviour: a fixture with a deliberately-unmatchable `@Map(source = "missing")` directive — the compilation succeeds but emits a validation diagnostic, and the generated source file is absent from the compilation output.

## 8. Spec audit and import-discipline grep

- [ ] 8.1 Grep every class under `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/` for `CodeBlock.of(` calls whose first argument contains a `.` followed by a capital letter (heuristic for FQN raw strings). Report any hits; refactor them to use `ClassName.get(...)` / `TypeName.get(TypeMirror)` before slice 1 ships.
- [ ] 8.2 Verify the generated `PersonMapperImpl.java` produced by the end-to-end fixture contains no `java.lang.` substring and no FQN reference for `com.example.fixtures.*` (or wherever the fixture package lives) — the imports section MUST cover them.

## 9. Final verification

- [ ] 9.1 Run `./gradlew :spi:test` and confirm green.
- [ ] 9.2 Run `./gradlew :processor:test` and confirm green — new generate-stage specs + EndToEndCodegenSpec all pass.
- [ ] 9.3 Run `./gradlew :strategies-builtin:test` and confirm green — any import-discipline refactors from 8.1 didn't regress per-strategy specs.
- [ ] 9.4 Run `./gradlew check` — zero violations. NEVER continue if there are violations.
- [ ] 9.5 Run `openspec validate emit-mapper-implementations --strict` and confirm valid.
- [ ] 9.6 Commit the implementation via `/commit-commands:commit`.

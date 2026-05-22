## 1. Preflight

- [x] 1.1 Confirm `./gradlew check` is green on the current branch before starting; abort and resolve any pre-existing failures first
- [x] 1.2 Note the current `MethodCallBridge` filter-relaxation commit on this branch; keep that change in scope

## 2. SPI: `PathSegmentResolver` and `ResolvedSegment`

- [x] 2.1 Create `spi/src/main/java/io/github/joke/percolate/spi/ResolvedSegment.java`. Fields: `TypeMirror returnType`, `EdgeCodegen codegen`, `int weight`. Lombok `@Value`. All-args constructor; field-by-field equality.
- [x] 2.2 Create `spi/src/main/java/io/github/joke/percolate/spi/PathSegmentResolver.java`. Single method: `Optional<ResolvedSegment> resolve(TypeMirror parentType, String segment, ResolveCtx ctx)`. Javadoc references the `source-path-resolution` capability for per-resolver semantics. `@NullMarked` inherited from `package-info.java`.
- [x] 2.3 Run `./gradlew :spi:compileJava` and confirm green; no new module dependencies introduced

## 3. Built-in: `GetterPathResolver`

- [x] 3.1 Create `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/GetterPathResolver.java`. `implements PathSegmentResolver`, `@AutoService(PathSegmentResolver.class)`, `@NoArgsConstructor`. Probe order: `get<Segment>()`, then `is<Segment>()` returning `boolean`/`Boolean`. Reject methods declared on `java.lang.Object`. Reject parameterized overloads (zero-param requirement). On match, return `ResolvedSegment(returnType, codegen, Weights.STEP)` where codegen renders `<slot-0>.<methodName>()` via `CodeBlock.of("$L.$N()", inputs.single(), methodName)`.
- [x] 3.2 Reuse the segment-name-to-`get`/`is` capitalization logic from the (deleted, but recoverable from git) `GetterRead`-as-`SourceStep` implementation. Inline the helper in `GetterPathResolver` — no shared base class with other resolvers.
- [x] 3.3 Run `./gradlew :strategies-builtin:compileJava` and confirm green

## 4. Built-in: `RecordPathResolver`

- [x] 4.1 Create `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/RecordPathResolver.java`. `implements PathSegmentResolver`, `@AutoService(PathSegmentResolver.class)`. Probe: zero-arg method named exactly `segment` whose enclosing element is `ElementKind.RECORD`. On match, return `ResolvedSegment(returnType, codegen, Weights.STEP)` with codegen `<slot-0>.<segment>()`.
- [x] 4.2 Reject when `parentType.getKind() != DECLARED` or the element kind is not `RECORD`.
- [x] 4.3 Run `./gradlew :strategies-builtin:compileJava` and confirm green

## 5. Built-in: `FieldPathResolver`

- [x] 5.1 Create `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/FieldPathResolver.java`. `implements PathSegmentResolver`, `@AutoService(PathSegmentResolver.class)`. Probe: `VariableElement` of `ElementKind.FIELD` whose `simpleName.contentEquals(segment)`, not `PRIVATE`, not `STATIC`. On match, return `ResolvedSegment(field.asType(), codegen, Weights.STEP)` with codegen rendering `<slot-0>.<segment>` (field read, no parens) via `CodeBlock.of("$L.$N", inputs.single(), segment)`.
- [x] 5.2 Reject when `parentType.getKind() != DECLARED`.
- [x] 5.3 Run `./gradlew :strategies-builtin:compileJava` and confirm green

## 6. Built-in unit specs

- [x] 6.1 Create `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/GetterPathResolverSpec.groovy`. `@Tag('unit')`, `extends spock.lang.Specification`. Five scenarios per the spec: `getX` match, `isX` match (boolean), parameterized-overload rejection, `Object` method rejection, non-declared-parent rejection. Use `ResolveCtxBuilder` and `TypeUniverse`.
- [x] 6.2 Create `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/RecordPathResolverSpec.groovy`. `@Tag('unit')`, `extends spock.lang.Specification`. Scenarios: canonical record accessor match, plain-class rejection. Add a record fixture to `strategies-builtin/src/test/java/.../fixtures/` if no record fixture exists yet.
- [x] 6.3 Create `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/FieldPathResolverSpec.groovy`. `@Tag('unit')`, `extends spock.lang.Specification`. Scenarios: public-field match, private-field rejection, static-field rejection. Add a `BoxFixture` with `public String value`, `private String secret`, `public static String DEFAULT` if no such fixture exists.
- [x] 6.4 Run `./gradlew :strategies-builtin:test` and confirm green

## 7. Module wiring: `ProcessorModule.pathSegmentResolvers()`

- [x] 7.1 Edit `processor/src/main/java/io/github/joke/percolate/processor/ProcessorModule.java`: add `@Singleton @Provides static List<PathSegmentResolver> pathSegmentResolvers()` mirroring the shape of `bridgeStrategies()` and `groupTargets()`. Use `ServiceLoader.load(PathSegmentResolver.class, ProcessorModule.class.getClassLoader())`, sort by `Class.getName()` ascending, return `List.copyOf(...)`.
- [x] 7.2 Run `./gradlew :processor:compileJava` and confirm green

## 8. `SeedGraph` source-path walking

- [x] 8.1 Edit `processor/src/main/java/io/github/joke/percolate/processor/stages/seed/SeedGraph.java`: add `final List<PathSegmentResolver> pathSegmentResolvers` (Dagger-injected via the existing `@RequiredArgsConstructor(onConstructor_ = @Inject)`).
- [x] 8.2 Inside `seedDirective(...)`, after building the untyped source chain and before adding the bridging seed edge: when `sourceSegments.size() > 1`, walk the path from the parameter root inward, calling resolvers per segment in registered order and taking the first non-empty match. For each successful match, allocate a typed source node, emit a `REALISED` edge from the previous typed node to the new node, emit a `MARKER` edge from the untyped seed-leaf to the new typed node, and register an `ExpansionGroup` (root = typed node, slot = previous typed node, codegen wrapping the resolved `EdgeCodegen`, strategyClassFqn = resolver class name).
- [x] 8.3 Implement per-method-scope reuse: maintain a `Map<List<String>, Node>` keyed by full path segments within the current method scope. Before registering a typed node for a path, check the cache; on hit, reuse the existing node and skip group registration for that segment.
- [x] 8.4 Update the bridging seed edge emission: when path resolution succeeded for the full source path, the edge's `from` is the deepest typed source node; otherwise it remains the deepest untyped seed leaf (today's behaviour).
- [x] 8.5 Add a helper `wrapAsGroupCodegen(EdgeCodegen edgeCodegen)` (or inline) producing a `GroupCodegen` whose render delegates to `edgeCodegen` for the 1-slot case.
- [x] 8.6 Run `./gradlew :processor:compileJava` and confirm green

## 9. `SeedGraph` spec updates

- [x] 9.1 Audit `processor/src/test/groovy/io/github/joke/percolate/processor/stages/seed/SeedGraphSpec.groovy` (and any related specs). Existing scenarios that assert no typed source nodes / no REALISED edges in seed output SHALL be updated to reflect the new typed-chain coexistence.
- [x] 9.2 Add scenarios per the modified `seed-graph` capability:
  - Typed source chain coexists with untyped chain when a resolver matches
  - Resolution failure leaves the untyped chain unchanged
  - Bridging edge originates from the typed source when full path resolves
  - Bridging edge falls back to untyped seed leaf when path resolution fails mid-chain
- [x] 9.3 Inject a controllable `List<PathSegmentResolver>` (a fake resolver list with deterministic behaviour) into `SeedGraph` from the spec, avoiding `ServiceLoader` magic in tests.
- [x] 9.4 Run `./gradlew :processor:test --tests '*SeedGraph*'` and confirm green

## 10. Integration verification — addresses chain

- [ ] 10.1 In `~/Projects/joke/percolate-integration` with `mapAddress` present in `PersonMapper.java`, run `./gradlew :mappers:clean :mappers:classes` and confirm the compile **succeeds**.
- [ ] 10.2 Inspect generated `PersonMapper.java`: confirm `mapHuman(...)` emits a call chain that walks `person.getAddresses()` (the typed source) into a `Set` / `Optional` construction with element conversion via `mapAddress(...)`.
- [ ] 10.3 Inspect generated `*.full.dot`: confirm typed source nodes appear (`src[person.addresses] : List<Optional<Person.Address>>`), the GetterCall cluster is rendered, and the bridging seed edge originates from the typed source.
- [ ] 10.4 Inspect generated `*.transforms.dot`: confirm the REALISED chain runs from `src[person]` through `src[person.addresses]` (typed) into the SetMap → OptionalWrap chain feeding `tgt[addresses]`.
- [ ] 10.5 Confirm the SetMap-driven nested element group resolves: at element scope, `OptionalUnwrap` feeds a `Person.Address` node which `MethodCallBridge` bridges into `Human.Address` via `mapAddress`.

## 11. Integration verification — scalar source-pick fix

- [ ] 11.1 In `~/Projects/joke/percolate-integration` with `PersonMapper.java` unchanged (`@Map(target = "firstName", source = "person2.first")`, `@Map(target = "lastName", source = "person.lastName")`), inspect generated `PersonMapper.java`.
- [ ] 11.2 Confirm `firstName` is read via `person2.getFirst()` and `lastName` is read via `person.getLastName()` — i.e., each directive uses its own receiver, not whichever `Person` candidate sorts first.
- [ ] 11.3 Inspect generated `*.transforms.dot`: confirm `tgt[firstName]` has an incoming REALISED edge from a typed `src[person2.first] : String` node and `tgt[lastName]` from a typed `src[person.lastName] : String` node.

## 12. Integration verification — closest-miss diagnostic unchanged

- [ ] 12.1 Comment out `mapAddress` in `PersonMapper.java`. Run `./gradlew :mappers:clean :mappers:classes`.
- [ ] 12.2 Confirm the compile **fails** with the existing closest-miss diagnostic format, now naming `Person.Address` (the deepest unresolved frontier) — equivalent to today's diagnostic on this branch with the `MethodCallBridge` filter relaxation applied. Restore `mapAddress` after verification.

## 13. Verify

- [ ] 13.1 Run `./gradlew check` from the repo root. All checks SHALL be green — every new resolver spec passes, the updated `SeedGraph` specs pass, no Spotless / NullAway / Errorprone violations. NEVER continue if there are violations.

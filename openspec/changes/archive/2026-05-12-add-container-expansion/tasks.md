## 1. Graph-model fixes (foundation)

- [x] 1.1 Widen `processor.graph.ElementLocation` to a Lombok `@Value` carrying `String role` with a no-arg static factory or secondary constructor that defaults the role to `"element"`; update `segment()` and `encode()` to return `"elem(" + role + ")"`
- [x] 1.2 Update `processor.graph.Node.id()`: for element nodes, return `parent.orElseThrow().id() + "::" + loc.segment() + "::" + typeEncode()` (was `parent.id() + "::elem"`)
- [x] 1.3 Update `processor.graph.NodeSpec.groovy:180` expected id string to the new form (`"v::map(Foo)::input::elem(element)::Foo"` or equivalent)
- [x] 1.4 Update `processor.graph.ElementLocationSpec.groovy`: `segment()` and `encode()` return `"elem(element)"` by default; add a new spec asserting `ElementLocation("key").segment() == "elem(key)"`; assert two `ElementLocation` instances with the same role are equal, two with different roles are unequal
- [x] 1.5 Update `processor.graph.DotRendererSpec.groovy`: confirm `new ElementLocation()` still works via the no-arg ctor / factory; no further DotRendererSpec changes expected (DOT renderer uses `instanceof` not segment text)
- [x] 1.6 Run the graph-model spec module to confirm all existing tests pass with the new id form

## 2. SPI widening

- [x] 2.1 Add `processor.spi.ElementSeed` Lombok `@Value` with fields `String role`, `TypeMirror inputType`, `TypeMirror outputType`; declare `@NullMarked` package-info present
- [x] 2.2 Widen `processor.spi.BridgeStep` Lombok `@Value` with a fifth field `List<ElementSeed> elementSeeds`; ensure defensive-copy on construction
- [x] 2.3 Update `processor.spi.builtins.DirectAssign` to construct its emitted step with `List.of()` for `elementSeeds`
- [x] 2.4 Update `processor.spi.builtins.MethodCallBridge` to construct its emitted steps with `List.of()` for `elementSeeds`
- [x] 2.5 Update `processor.spi.builtins.GetterRead`, `processor.spi.builtins.ConstructorCall`, and any other existing strategy builtins that construct `BridgeStep` or analogous structures to thread the new field
- [x] 2.6 Update any in-tree `Bridge`-related test fixtures that build `BridgeStep` instances by hand (Spock specs under `processor/src/test/groovy/.../spi`)
- [x] 2.7 Add `Weights.CONTAINER = 2` to `processor.spi.Weights`

## 3. Containers helper

- [x] 3.1 Create `processor.spi.Containers` final class with private constructor; expose static methods listed in the `container-expansion` spec: `isOptional`, `isList`, `isSet`, `isCollection`, `isIterable`, `isArray`, `typeArgument`, `arrayComponentType`
- [x] 3.2 Implement each `is*` predicate by looking up the corresponding `TypeElement` via `ctx.elements().getTypeElement(<fqn>)` and comparing erasures with `ctx.types().isSameType(types.erasure(t), types.erasure(typeElement.asType()))`
- [x] 3.3 Implement `isIterable` as the broad-subtype variant — true iff `ctx.types().isAssignable(types.erasure(t), types.erasure(<Iterable>.asType()))`
- [x] 3.4 Implement `typeArgument` and `arrayComponentType` with documented preconditions (caller `is*`-checks first)
- [x] 3.5 Spock spec `ContainersSpec` covering all eight methods, including the array/non-Iterable corner case and parameterised-vs-raw distinctions for the predicates

## 4. Built-in container strategies

- [x] 4.1 Create `processor.spi.builtins.OptionalWrap` per the `container-expansion` spec; `@AutoService(Bridge.class)`; emit `BridgeStep(in=elem, out=Optional<elem>, w=CONTAINER, codegen=Optional.ofNullable($1), seeds=[])`
- [x] 4.2 Create `processor.spi.builtins.OptionalUnwrap` per the spec; emit `BridgeStep(in=Optional<elem>, out=elem, w=CONTAINER, codegen=$1.orElse(null), seeds=[])`
- [x] 4.3 Create `processor.spi.builtins.OptionalMap` per the spec; emit `BridgeStep(in=from, out=to, w=CONTAINER, codegen=throws, seeds=[ElementSeed("element", innerIn, innerOut)])`
- [x] 4.4 Create `processor.spi.builtins.ListWrap` per the spec; emit `BridgeStep(in=elem, out=List<elem>, w=CONTAINER, codegen=List.of($1), seeds=[])`
- [x] 4.5 Create `processor.spi.builtins.ListMap` per the spec; accept input shapes Iterable<X>, X[], Optional<X>; emit one `BridgeStep` per accepted shape with the appropriate codegen template (throwing today) and `elementSeeds = [ElementSeed("element", innerIn, innerOut)]`
- [x] 4.6 Create `processor.spi.builtins.SetWrap` per the spec; symmetric to `ListWrap` with `Set.of($1)` codegen
- [x] 4.7 Create `processor.spi.builtins.SetMap` per the spec; symmetric to `ListMap` with `toSet()` codegen
- [x] 4.8 For each container `Map` strategy, the codegen lambda's `render(VarNames, IncomingValues)` SHALL throw `UnsupportedOperationException` with a message naming the codegen capability — verify via per-strategy Spock spec
- [x] 4.9 Confirm each new strategy class has `@NullMarked` package coverage (existing `processor.spi.builtins.package-info.java`)

## 5. Driver widening — BridgeSourceToTargetPhase

- [x] 5.1 Extend `BridgeSourceToTargetPhase.seeds(graph)` to add a third bucket collecting `EdgeKind.SEED` edges where both endpoints have `loc instanceof ElementLocation`
- [x] 5.2 Update `applyUnifiedEmissionRule` to inherit `parent` on intermediate allocation when the anchor's `loc instanceof ElementLocation`: for `inputNode` use `F.parent`, for `outputNode` use `T.parent` (which equals `F.parent` in same-loc allocations but differs in element-seed allocations)
- [x] 5.3 Update `applyUnifiedEmissionRule` to emit an output-side SUB_SEED when `outputNode != T` — mirror of the existing input-side rule; carries `Weights.SENTINEL_UNREALISED`, the originating directive's `AnnotationMirror`, and the strategy's class FQN
- [x] 5.4 Update `applyUnifiedEmissionRule` to handle non-empty `step.elementSeeds`: for each entry, allocate (or reuse) element nodes parented by `inputNode` and `outputNode` respectively at `ElementLocation(role)` with the seed's input/output types, and emit a `EdgeKind.SEED` between them (with empty directive, strategy FQN populated, sentinel weight)
- [x] 5.5 Update `applySubSeedEmissionRule` to use the same parent-inheritance rule when its allocations land at `ElementLocation` (e.g., element-scope chain intermediates allocated while processing an element-scope SUB_SEED)
- [x] 5.6 Extend `Node`-allocation helpers so the find-or-allocate identity key includes `parent` when `loc instanceof ElementLocation`; non-element nodes continue keying by `(scope, loc, type)` alone

## 6. Edge factory + graph plumbing

- [x] 6.1 If no factory currently exists for element-scope SEEDs, add `Edge.elementSeed(Node from, Node to, String strategyClassFqn)` returning `new Edge(from, to, SENTINEL_UNREALISED, SEED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(strategyClassFqn))` — OR widen the existing `Edge.seed` factory to accept `Optional<AnnotationMirror>` and use that for element-scope SEEDs; choice of factory shape is implementation-private
- [x] 6.2 Confirm `MapperGraph.apply(GraphDelta)` accepts the new element-scope SEEDs without bespoke handling (they are just SEED edges with element-location endpoints)
- [x] 6.3 Confirm `DotRenderer` continues to render element nodes as nested clusters; verify the role text now appears in node labels (extend the renderer if it currently labels phantom nodes via `segment()`)

## 7. Tests — per-strategy specs

- [x] 7.1 Spock spec for `OptionalWrap` covering: emits for `Optional<X>` target with bare `from`; declines non-Optional target; weight is `Weights.CONTAINER`; codegen renders `Optional.ofNullable($1)`; `elementSeeds` is empty
- [x] 7.2 Spock spec for `OptionalUnwrap` covering: emits for `Optional<X>` source; declines non-Optional source; codegen renders `$1.orElse(null)`; `elementSeeds` is empty
- [x] 7.3 Spock spec for `OptionalMap` covering: emits for `Optional<A> → Optional<B>`; declines mixed source/target; `elementSeeds` carries `role = "element"` with inner types; codegen lambda throws `UnsupportedOperationException`
- [x] 7.4 Spock spec for `ListWrap` mirroring `OptionalWrap` for `List<X>`
- [x] 7.5 Spock spec for `ListMap` covering: emits for `List → List`, `Set → List`, `Iterable → List`, `Collection → List`, `Optional → List`, `T[] → List`; declines non-List target; `elementSeeds` carries the correct inner types per input shape; codegen lambda throws
- [x] 7.6 Spock spec for `SetWrap` mirroring `ListWrap` for `Set<X>`
- [x] 7.7 Spock spec for `SetMap` mirroring `ListMap` for `Set<X>` target
- [x] 7.8 Confirm each generated `META-INF/services/io.github.joke.percolate.processor.spi.Bridge` lists the new strategy

## 8. Tests — Containers helper

- [x] 8.1 Per-method spec covering positive and negative matches for each `is*` predicate
- [x] 8.2 `typeArgument` and `arrayComponentType` accessor specs

## 9. Tests — driver rule additions

- [x] 9.1 Spock spec for `applyUnifiedEmissionRule`: parent inheritance on element-location intermediate allocation (a step processed against an element-scope F allocates an output element node whose `parent` equals `F.parent`)
- [x] 9.2 Spock spec: output-side SUB_SEED emission when `outputNode != T` (e.g., `OptionalUnwrap` step `Optional<Dog> → Dog` against a SEED `Optional<Dog> → Pet` allocates `tgt[]:Dog` and emits SUB_SEED `tgt[]:Dog → tgt[]:Pet`)
- [x] 9.3 Spock spec: element-seed emission for a container-map step (a single `OptionalMap` step against a SEED `Optional<Dog> → Optional<Pet>` emits the outer REALISED edge, allocates two parent-linked element nodes, and emits a SEED between them)
- [x] 9.4 Spock spec: element-scope SEED bucket is iterated by the phase (an element-scope SEED present at pass start is processed, and any matching `Bridge` queries are made for its endpoint types)
- [x] 9.5 Spock spec: cross-parent element-scope SUB_SEEDs are accepted (a SUB_SEED between two element nodes parented by different container nodes lands in the graph and is iterated by subsequent passes)
- [x] 9.6 Spock spec: deduplication on element-node identity (two strategies emitting the same `(scope, role, type, parent)` element node reuse the same node)

## 10. Integration tests — end-to-end

- [x] 10.1 Compile-Testing integration spec: a `@Mapper` with `Optional<Pet> findPet(Dog d)` and a sibling `Pet map(Dog)` produces a realised subgraph with the outer `Dog → Pet → Optional<Pet>` chain via OptionalWrap and MethodCallBridge
- [x] 10.2 Compile-Testing integration spec: a `@Mapper` with `Pet getPet(Optional<Dog> o)` and a sibling `Pet map(Dog)` produces a realised subgraph using OptionalUnwrap + MethodCallBridge (verifies output-side SUB_SEED rule)
- [x] 10.3 Compile-Testing integration spec: a `@Mapper` with `List<Pet> convertAll(List<Dog> xs)` and a sibling `Pet map(Dog)` produces the outer `ListMap` REALISED edge plus the element-scope subgraph; the realised subgraph contains the inner `Dog → Pet` REALISED edge in the element scope
- [x] 10.4 Compile-Testing integration spec: cross-container `List<Dog> → Set<Pet>` and `Set<Dog> → List<Pet>` resolve via `SetMap` / `ListMap` respectively
- [x] 10.5 Compile-Testing integration spec for the worked-example case: `@Mapper` with `Optional<List<Pet>> convert(List<Optional<GR>> xs)` and `Pet map(GR)` resolves to the chain shown in `design.md` — outer ListMap + outer OptionalWrap; element-scope OptionalUnwrap + MethodCallBridge chain
- [x] 10.6 Compile-Testing integration spec: golden DOT for one expanded mapper exercising at least one container map and one Wrap/Unwrap; assert the DOT clusters render the element scopes
- [x] 10.7 Verify existing seed-graph, expansion, and method-call-bridge specs remain green after the SPI / driver changes

## 11. Spec / project housekeeping

- [x] 11.1 Run `openspec validate add-container-expansion` and resolve any reported gaps (validates clean)
- [x] 11.2 Sync delta specs into `openspec/specs/` via `opsx:sync` after implementation is verified passing (synced graph-model spec; others already up to date)
- [x] 11.3 Conventional-commit message: `feat(processor): add container expansion strategies and element-scope graph layer`

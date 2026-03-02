# Wiring Stabilization Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix three bugs in `WiringStage` that prevent conversion nodes (`CollectionIterationNode`, `MethodCallNode`, etc.) from appearing in the wiring graph for collection and nested-type mappings.

**Architecture:** Fix type comparison (drop erasure), convert `MapperMethodProvider` and `ListProvider` to `@AutoService` SPI providers by adding `MethodRegistry` to `ConversionProvider.canHandle`, then fix `spliceFragment`'s edge labeling using a new `inTypeOf` function. Finally add a `stabilizeGraph` loop that re-expands incompatible edges until the graph is fully resolved.

**Tech Stack:** Java 11, JGraphT, Google Auto Service, Spock + Google Compile Testing.

---

### Task 1: Write failing collection-mapping test

**Files:**
- Modify: `processor/src/test/groovy/io/github/joke/percolate/stage/WiringStageSpec.groovy`

**Step 1: Add the failing test**

Add this test after the existing `"multi-property mapping..."` test (line 65), before the `@Ignore`d test:

```groovy
def "List<T> to List<U> — wiring graph contains iteration, method call, and collect nodes"() {
    given:
    def actor = JavaFileObjects.forSourceLines('test.Actor',
        'package test;',
        'public class Actor { public String getName() { return ""; } }')
    def ticketActor = JavaFileObjects.forSourceLines('test.TicketActor',
        'package test;',
        'public class TicketActor { private final String name;',
        '    public TicketActor(String name) { this.name = name; } }')
    def mapper = JavaFileObjects.forSourceLines('test.ListMapper',
        'package test;',
        'import io.github.joke.percolate.Mapper;',
        'import java.util.List;',
        '@Mapper public interface ListMapper {',
        '    List<TicketActor> map(List<Actor> actors);',
        '    TicketActor mapActor(Actor actor);',
        '}')

    when:
    Compiler.javac()
        .withProcessors(new PercolateProcessor())
        .compile(actor, ticketActor, mapper)
    def wiringDot = new File('/tmp/ListMapper-map-wiring.dot').text

    then: 'collection expansion nodes are present'
    wiringDot.contains('CollectionIteration(')
    wiringDot.contains('MethodCall(mapActor')
    wiringDot.contains('CollectionCollect(')
}
```

Also add this nested-type test immediately after:

```groovy
def "nested object mapping — wiring graph contains MethodCallNode for the sub-mapper"() {
    given:
    def inner = JavaFileObjects.forSourceLines('test.Inner',
        'package test;',
        'public class Inner { public String getValue() { return ""; } }')
    def mappedInner = JavaFileObjects.forSourceLines('test.MappedInner',
        'package test;',
        'public class MappedInner { private final String value;',
        '    public MappedInner(String value) { this.value = value; } }')
    def outer = JavaFileObjects.forSourceLines('test.Outer',
        'package test;',
        'public class Outer { public Inner getInner() { return null; } }')
    def mappedOuter = JavaFileObjects.forSourceLines('test.MappedOuter',
        'package test;',
        'public class MappedOuter { private final MappedInner inner;',
        '    public MappedOuter(MappedInner inner) { this.inner = inner; } }')
    def mapper = JavaFileObjects.forSourceLines('test.NestedMapper',
        'package test;',
        'import io.github.joke.percolate.Mapper;',
        '@Mapper public interface NestedMapper {',
        '    MappedOuter mapOuter(Outer outer);',
        '    MappedInner mapInner(Inner inner);',
        '}')

    when:
    Compiler.javac()
        .withProcessors(new PercolateProcessor())
        .compile(inner, mappedInner, outer, mappedOuter, mapper)
    def wiringDot = new File('/tmp/NestedMapper-mapOuter-wiring.dot').text

    then: 'method call node for mapInner is present'
    wiringDot.contains('MethodCall(mapInner')
}
```

**Step 2: Run to verify the collection test fails**

```bash
./gradlew :processor:test --tests 'WiringStageSpec'
```

Expected: the collection test fails (no `CollectionIteration(` in DOT). The nested test may or may not fail — note which.

---

### Task 2: Add `MethodRegistry` to `ConversionProvider.canHandle`

This is a pure refactoring — no behavior change. All implementations get a `MethodRegistry registry` parameter in `canHandle`. This unblocks making `MapperMethodProvider` and `ListProvider` into `@AutoService` no-arg providers.

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/spi/ConversionProvider.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/spi/impl/SubtypeProvider.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/spi/impl/PrimitiveWideningProvider.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/spi/impl/EnumProvider.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/spi/impl/OptionalProvider.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/spi/impl/MapperMethodProvider.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/spi/impl/ListProvider.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/WiringStage.java`

**Step 1: Update interface**

`ConversionProvider.java`:
```java
boolean canHandle(TypeMirror source, TypeMirror target, MethodRegistry registry, ProcessingEnvironment env);
```

**Step 2: Update `SubtypeProvider` — add unused `registry` parameter**

```java
@Override
public boolean canHandle(TypeMirror source, TypeMirror target, MethodRegistry registry, ProcessingEnvironment env) {
    return env.getTypeUtils().isSubtype(source, target)
            && !env.getTypeUtils().isSameType(source, target);
}
```

**Step 3: Update `PrimitiveWideningProvider` — add unused `registry` parameter**

```java
@Override
public boolean canHandle(TypeMirror source, TypeMirror target, MethodRegistry registry, ProcessingEnvironment env) {
    Types types = env.getTypeUtils();
    if (source.getKind().isPrimitive()) {
        return isWideningTarget(source, target) || isBoxedVersion(types, source, target);
    }
    return isUnboxedVersion(types, source, target);
}
```

**Step 4: Update `EnumProvider` — add unused `registry` parameter**

```java
@Override
public boolean canHandle(TypeMirror source, TypeMirror target, MethodRegistry registry, ProcessingEnvironment env) {
    return canConvertEnums(source, target);
}
```

**Step 5: Update `OptionalProvider` — add unused `registry` parameter**

```java
@Override
public boolean canHandle(TypeMirror source, TypeMirror target, MethodRegistry registry, ProcessingEnvironment env) {
    return isOptionalType(source) || isOptionalType(target);
}
```

**Step 6: Update `MapperMethodProvider` — use `registry` from parameter**

Replace the constructor and field with parameter usage:

```java
public final class MapperMethodProvider implements ConversionProvider {

    @Override
    public boolean canHandle(TypeMirror source, TypeMirror target, MethodRegistry registry, ProcessingEnvironment env) {
        return findMethod(env.getTypeUtils(), source, target, registry).isPresent();
    }

    @Override
    public ConversionFragment provide(
            TypeMirror source, TypeMirror target, MethodRegistry registry, ProcessingEnvironment env) {
        return findMethod(env.getTypeUtils(), source, target, registry)
                .map(m -> ConversionFragment.of(new MethodCallNode(m, source, target)))
                .orElse(ConversionFragment.of());
    }

    private static Optional<MethodDefinition> findMethod(
            Types types, TypeMirror source, TypeMirror target, MethodRegistry registry) {
        return registry.entries().values().stream()
                .map(RegistryEntry::getSignature)
                .filter(Objects::nonNull)
                .filter(m -> m.getParameters().size() == 1)
                .filter(m -> types.isSameType(
                        types.erasure(m.getParameters().get(0).getType()), types.erasure(source)))
                .filter(m -> types.isSameType(types.erasure(m.getReturnType()), types.erasure(target)))
                .findFirst();
    }
}
```

Remove the import for the old constructor field. The class still has no `@AutoService` yet — that comes in Task 3.

**Step 7: Update `ListProvider` — use `registry` from parameter**

```java
@Override
public boolean canHandle(TypeMirror source, TypeMirror target, MethodRegistry registry, ProcessingEnvironment env) {
    return isListType(source) && isListType(target);
}

@Override
public ConversionFragment provide(
        TypeMirror source, TypeMirror target, MethodRegistry registry, ProcessingEnvironment env) {
    @Nullable TypeMirror sourceElement = getFirstTypeArgument(source);
    @Nullable TypeMirror targetElement = getFirstTypeArgument(target);
    if (sourceElement == null || targetElement == null) {
        return ConversionFragment.of();
    }
    Types types = env.getTypeUtils();
    Optional<MethodDefinition> method = findMethod(types, sourceElement, targetElement, registry);
    if (!method.isPresent()) {
        return ConversionFragment.of();
    }
    return ConversionFragment.of(
            new CollectionIterationNode(source, sourceElement),
            new MethodCallNode(method.get(), sourceElement, targetElement),
            new CollectionCollectNode(target, targetElement));
}

private static Optional<MethodDefinition> findMethod(
        Types types, TypeMirror sourceElement, TypeMirror targetElement, MethodRegistry registry) {
    return registry.entries().values().stream()
            .map(RegistryEntry::getSignature)
            .filter(Objects::nonNull)
            .filter(m -> m.getParameters().size() == 1)
            .filter(m -> types.isSameType(
                    types.erasure(m.getParameters().get(0).getType()), types.erasure(sourceElement)))
            .filter(m -> types.isSameType(types.erasure(m.getReturnType()), types.erasure(targetElement)))
            .findFirst();
}
```

Remove the `mappers` field and constructor. Add imports for `RegistryEntry` and `Objects`.

**Step 8: Update `WiringStage.findFragment` to pass registry to `canHandle`**

```java
private Optional<ConversionFragment> findFragment(
        TypeMirror source, TypeMirror target, MethodRegistry registry, List<ConversionProvider> providers) {
    return providers.stream()
            .filter(p -> p.canHandle(source, target, registry, processingEnv))
            .findFirst()
            .map(p -> p.provide(source, target, registry, processingEnv));
}
```

**Step 9: Build to verify compilation**

```bash
./gradlew :processor:compileGroovy
```

Expected: BUILD SUCCESSFUL.

---

### Task 3: Make `MapperMethodProvider` and `ListProvider` `@AutoService`, simplify `WiringStage`

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/spi/impl/MapperMethodProvider.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/spi/impl/ListProvider.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/WiringStage.java`

**Step 1: Add `@AutoService` to `MapperMethodProvider`**

Add annotation and import:
```java
import com.google.auto.service.AutoService;
import io.github.joke.percolate.spi.ConversionProvider;

@AutoService(ConversionProvider.class)
public final class MapperMethodProvider implements ConversionProvider {
```

**Step 2: Add `@AutoService` to `ListProvider`**

Add annotation and import:
```java
import com.google.auto.service.AutoService;
import io.github.joke.percolate.spi.ConversionProvider;

@AutoService(ConversionProvider.class)
public final class ListProvider implements ConversionProvider {
```

**Step 3: Simplify `WiringStage.buildProviders`**

Remove the `buildProviders` method entirely and update `execute` to use `conversionProviders` directly:

```java
public void execute(MethodRegistry registry) {
    new ArrayList<>(registry.entries().values())
            .stream()
                    .filter(entry -> !entry.isOpaque() && entry.getGraph() != null && entry.getSignature() != null)
                    .forEach(entry -> wireMethod(entry, registry, conversionProviders));
}
```

Remove the `import io.github.joke.percolate.spi.impl.MapperMethodProvider;` import from `WiringStage.java`.

**Step 4: Build and verify**

```bash
./gradlew :processor:build
```

Expected: BUILD SUCCESSFUL (existing test failures are pre-existing — compare count with baseline 7).

---

### Task 4: Fix `typesCompatible` — drop erasure

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/WiringStage.java`

**Step 1: Fix `typesCompatible`**

Replace:
```java
private boolean typesCompatible(TypeMirror source, TypeMirror target) {
    return processingEnv
            .getTypeUtils()
            .isSameType(
                    processingEnv.getTypeUtils().erasure(source),
                    processingEnv.getTypeUtils().erasure(target));
}
```

With:
```java
private boolean typesCompatible(TypeMirror source, TypeMirror target) {
    return processingEnv.getTypeUtils().isSameType(source, target);
}
```

**Step 2: Run the collection test**

```bash
./gradlew :processor:test --tests 'WiringStageSpec'
```

Expected: the collection test (`List<T> to List<U>`) now passes. The nested test should also pass if it was failing. Check the total failure count — it must not exceed 7 (the pre-existing failures).

**Step 3: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/spi/ConversionProvider.java
git add processor/src/main/java/io/github/joke/percolate/spi/impl/SubtypeProvider.java
git add processor/src/main/java/io/github/joke/percolate/spi/impl/PrimitiveWideningProvider.java
git add processor/src/main/java/io/github/joke/percolate/spi/impl/EnumProvider.java
git add processor/src/main/java/io/github/joke/percolate/spi/impl/OptionalProvider.java
git add processor/src/main/java/io/github/joke/percolate/spi/impl/MapperMethodProvider.java
git add processor/src/main/java/io/github/joke/percolate/spi/impl/ListProvider.java
git add processor/src/main/java/io/github/joke/percolate/stage/WiringStage.java
git add processor/src/test/groovy/io/github/joke/percolate/stage/WiringStageSpec.groovy
git commit -m "feat: all ConversionProviders are SPI; fix typesCompatible to use exact type matching"
```

---

### Task 5: Add `optionalType` field to `OptionalUnwrapNode` and fix `OptionalProvider`

This enables the `inTypeOf` function (Task 6) to correctly label the edge going INTO an `OptionalUnwrapNode` with the full `Optional<T>` type rather than the element type.

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/graph/node/OptionalUnwrapNode.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/spi/impl/OptionalProvider.java`

**Step 1: Add `optionalType` field to `OptionalUnwrapNode`**

```java
/** Unwraps Optional<T> to T. Inline — no helper method registered. */
public final class OptionalUnwrapNode implements MappingNode {
    private final TypeMirror elementType;
    private final TypeMirror optionalType;

    public OptionalUnwrapNode(TypeMirror elementType, TypeMirror optionalType) {
        this.elementType = elementType;
        this.optionalType = optionalType;
    }

    public TypeMirror getElementType() {
        return elementType;
    }

    public TypeMirror getOptionalType() {
        return optionalType;
    }

    @Override
    public String toString() {
        return "OptionalUnwrap(" + elementType + ")";
    }
}
```

**Step 2: Fix `OptionalProvider.unwrapFragment` to pass the source Optional type**

Replace:
```java
private static ConversionFragment unwrapFragment(TypeMirror source) {
    List<? extends TypeMirror> args = ((DeclaredType) source).getTypeArguments();
    if (args.isEmpty()) {
        return ConversionFragment.of();
    }
    return ConversionFragment.of(new OptionalUnwrapNode(args.get(0)));
}
```

With:
```java
private static ConversionFragment unwrapFragment(TypeMirror source) {
    List<? extends TypeMirror> args = ((DeclaredType) source).getTypeArguments();
    if (args.isEmpty()) {
        return ConversionFragment.of();
    }
    return ConversionFragment.of(new OptionalUnwrapNode(args.get(0), source));
}
```

**Step 3: Build to verify**

```bash
./gradlew :processor:compileGroovy
```

Expected: BUILD SUCCESSFUL.

---

### Task 6: Add `inTypeOf`, fix `spliceFragment`, add `stabilizeGraph`

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/WiringStage.java`

**Step 1: Add `inTypeOf` static method**

Add this method directly below `outTypeOf` in `WiringStage`:

```java
private static TypeMirror inTypeOf(MappingNode node) {
    if (node instanceof SourceNode) return ((SourceNode) node).getType();
    if (node instanceof PropertyAccessNode) return ((PropertyAccessNode) node).getInType();
    if (node instanceof CollectionIterationNode) return ((CollectionIterationNode) node).getCollectionType();
    if (node instanceof CollectionCollectNode) return ((CollectionCollectNode) node).getElementType();
    if (node instanceof OptionalWrapNode) return ((OptionalWrapNode) node).getElementType();
    if (node instanceof OptionalUnwrapNode) return ((OptionalUnwrapNode) node).getOptionalType();
    if (node instanceof BoxingNode) return ((BoxingNode) node).getInType();
    if (node instanceof UnboxingNode) return ((UnboxingNode) node).getInType();
    if (node instanceof MethodCallNode) return ((MethodCallNode) node).getInType();
    throw new IllegalArgumentException("Unknown node type: " + node.getClass().getSimpleName());
}
```

**Step 2: Fix `spliceFragment` to use `inTypeOf` for edge target type**

Replace in `spliceFragment`:
```java
for (MappingNode node : nodes) {
    wiredGraph.addVertex(node);
    TypeMirror nodeOutType = outTypeOf(node);
    wiredGraph.addEdge(prev, node, FlowEdge.of(prevType, nodeOutType));
    prev = node;
    prevType = nodeOutType;
}
```

With:
```java
for (MappingNode node : nodes) {
    wiredGraph.addVertex(node);
    TypeMirror nodeInType = inTypeOf(node);
    TypeMirror nodeOutType = outTypeOf(node);
    wiredGraph.addEdge(prev, node, FlowEdge.of(prevType, nodeInType));
    prev = node;
    prevType = nodeOutType;
}
```

**Step 3: Change `buildWiredGraph` return type to mutable**

Change the return type from `Graph<MappingNode, FlowEdge>` to `DirectedWeightedMultigraph<MappingNode, FlowEdge>`:

```java
private DirectedWeightedMultigraph<MappingNode, FlowEdge> buildWiredGraph(
        Graph<MappingNode, FlowEdge> bindingGraph,
        TypeMirror returnType,
        MethodRegistry registry,
        List<ConversionProvider> providers) {
    // body unchanged
}
```

**Step 4: Add `stabilizeGraph` and `expandIncompatibleEdges` methods**

```java
private void stabilizeGraph(
        DirectedWeightedMultigraph<MappingNode, FlowEdge> graph,
        MethodRegistry registry,
        List<ConversionProvider> providers) {
    for (int i = 0; i < 10; i++) {
        if (!expandIncompatibleEdges(graph, registry, providers)) {
            break;
        }
    }
}

private boolean expandIncompatibleEdges(
        DirectedWeightedMultigraph<MappingNode, FlowEdge> graph,
        MethodRegistry registry,
        List<ConversionProvider> providers) {
    List<FlowEdge> incompatible = graph.edgeSet().stream()
            .filter(e -> !typesCompatible(e.getSourceType(), e.getTargetType()))
            .collect(toList());
    if (incompatible.isEmpty()) {
        return false;
    }
    for (FlowEdge edge : incompatible) {
        MappingNode src = graph.getEdgeSource(edge);
        MappingNode tgt = graph.getEdgeTarget(edge);
        graph.removeEdge(edge);
        Optional<ConversionFragment> fragment = findFragment(edge.getSourceType(), edge.getTargetType(), registry, providers);
        if (fragment.isPresent() && !fragment.get().isEmpty()) {
            spliceFragment(graph, src, tgt, edge, fragment.get());
        } else {
            graph.addEdge(src, tgt, edge);
        }
    }
    return true;
}
```

Add the import `import static java.util.stream.Collectors.toList;`.

**Step 5: Call `stabilizeGraph` from `wireMethod`**

Update `wireMethod` to call stabilization after building the wired graph:

```java
private void wireMethod(RegistryEntry entry, MethodRegistry registry, List<ConversionProvider> providers) {
    MethodDefinition signature = Objects.requireNonNull(entry.getSignature());
    Graph<MappingNode, FlowEdge> bindingGraph = Objects.requireNonNull(entry.getGraph());
    DirectedWeightedMultigraph<MappingNode, FlowEdge> wiredGraph =
            buildWiredGraph(bindingGraph, signature.getReturnType(), registry, providers);
    stabilizeGraph(wiredGraph, registry, providers);
    registry.register(signature, new RegistryEntry(signature, new AsUnmodifiableGraph<>(wiredGraph)));
}
```

**Step 6: Run all tests**

```bash
./gradlew :processor:test --tests 'WiringStageSpec'
```

Expected: all `WiringStageSpec` tests pass. Total failure count must not exceed 7 (pre-existing).

**Step 7: Run full check**

```bash
./gradlew :processor:check
```

Expected: no new errors.

**Step 8: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/graph/node/OptionalUnwrapNode.java
git add processor/src/main/java/io/github/joke/percolate/spi/impl/OptionalProvider.java
git add processor/src/main/java/io/github/joke/percolate/stage/WiringStage.java
git commit -m "feat: add inTypeOf, fix spliceFragment edge labels, add stabilizeGraph loop"
```

---

### Task 7: Verify final state

**Step 1: Run full build**

```bash
./gradlew build
```

**Step 2: Confirm new tests pass and pre-existing failures unchanged**

The following tests in `WiringStageSpec` must pass:
- `compatible types — no conversion needed`
- `multi-property mapping to same target type produces a single constructor node`
- `List<T> to List<U> — wiring graph contains iteration, method call, and collect nodes`
- `nested object mapping — wiring graph contains MethodCallNode for the sub-mapper`

Pre-existing failures (7, from disconnected stages) must be unchanged.

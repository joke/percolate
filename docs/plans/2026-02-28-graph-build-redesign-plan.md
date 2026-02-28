# Graph Build Redesign Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace `ResolveStage` + `GraphBuildStage` with `BindingStage` + `WiringStage`, implementing the typed data-flow graph model described in `docs/plans/2026-02-28-graph-build-redesign-design.md`.

**Architecture:** `ParseStage` pre-populates a `MethodRegistry`. `BindingStage` builds typed source property chains (owns `PropertyDiscoveryStrategy`). `WiringStage` traverses topologically target→source, resolves creation strategy, and inserts conversion nodes (owns `ObjectCreationStrategy` + `ConversionProvider`). The registry is the single output passed downstream.

**Tech Stack:** Java 11, JGraphT `DirectedWeightedMultigraph`, Dagger 2 (`@RoundScoped` + `@Inject`), Spock + Google Compile Testing

---

## Important Constraints

- **Do NOT modify:** `ValidateStage`, `OptimizeStage`, `CodeGenStage`, or any of their tests.
- **Pipeline will be truncated** after `WiringStage` for now — later stages are reconnected in a future redesign. Tests for later stages will fail; that is expected.
- **Do NOT delete:** `ResolveResult`, `GraphResult`, `GraphNode` hierarchy, or old edge types. Later stages depend on these and must still compile even if unused.
- **Delete:** `ResolveStage.java` and `GraphBuildStage.java` — they are replaced.
- **NullAway:** The build uses NullAway with `onlyNullMarked = true`. Only annotate `@NullMarked` classes. Use `@Nullable` for nullable fields/params in `@NullMarked` classes. Do not add `@NullMarked` to new classes unless you want NullAway to check them strictly.
- **ErrorProne + `-Werror`:** All warnings are errors. Keep code clean.
- **Tests:** Use `./gradlew :processor:test --tests 'SpecName'` to run a single spec.

---

## Task 1: MappingNode hierarchy

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/graph/node/MappingNode.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/node/SourceNode.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/node/PropertyAccessNode.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/node/TargetSlotPlaceholder.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/node/TargetAssignmentNode.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/node/ConstructorAssignmentNode.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/node/MethodCallNode.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/node/CollectionIterationNode.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/node/CollectionCollectNode.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/node/OptionalWrapNode.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/node/OptionalUnwrapNode.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/node/BoxingNode.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/node/UnboxingNode.java`

These are pure data classes — no logic, no tests required.

**Step 1: Create MappingNode marker interface**

```java
package io.github.joke.percolate.graph.node;

/** Marker interface for all nodes in the mapping data-flow graph. */
public interface MappingNode {}
```

**Step 2: Create SourceNode**

```java
package io.github.joke.percolate.graph.node;

import javax.lang.model.type.TypeMirror;

public final class SourceNode implements MappingNode {
    private final String paramName;
    private final TypeMirror type;

    public SourceNode(String paramName, TypeMirror type) {
        this.paramName = paramName;
        this.type = type;
    }

    public String getParamName() { return paramName; }
    public TypeMirror getType() { return type; }

    @Override
    public String toString() { return "Source(" + paramName + ":" + type + ")"; }
}
```

**Step 3: Create PropertyAccessNode**

```java
package io.github.joke.percolate.graph.node;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

public final class PropertyAccessNode implements MappingNode {
    private final String propertyName;
    private final TypeMirror inType;
    private final TypeMirror outType;
    private final Element accessor; // ExecutableElement (getter) or VariableElement (field)

    public PropertyAccessNode(String propertyName, TypeMirror inType, TypeMirror outType, Element accessor) {
        this.propertyName = propertyName;
        this.inType = inType;
        this.outType = outType;
        this.accessor = accessor;
    }

    public String getPropertyName() { return propertyName; }
    public TypeMirror getInType() { return inType; }
    public TypeMirror getOutType() { return outType; }
    public Element getAccessor() { return accessor; }

    @Override
    public String toString() { return "Property(" + propertyName + ":" + inType + "->" + outType + ")"; }
}
```

**Step 4: Create TargetSlotPlaceholder**

```java
package io.github.joke.percolate.graph.node;

import javax.lang.model.type.TypeMirror;

/** Terminal node created by BindingStage. Replaced by WiringStage with a TargetAssignmentNode. */
public final class TargetSlotPlaceholder implements MappingNode {
    private final TypeMirror targetType;
    private final String slotName;

    public TargetSlotPlaceholder(TypeMirror targetType, String slotName) {
        this.targetType = targetType;
        this.slotName = slotName;
    }

    public TypeMirror getTargetType() { return targetType; }
    public String getSlotName() { return slotName; }

    @Override
    public String toString() { return "Slot(" + slotName + " on " + targetType + ")"; }
}
```

**Step 5: Create TargetAssignmentNode interface**

```java
package io.github.joke.percolate.graph.node;

import javax.lang.model.element.TypeElement;

/** Represents how the target object is created. Strategy determined by WiringStage. */
public interface TargetAssignmentNode extends MappingNode {
    TypeElement getTargetType();
}
```

**Step 6: Create ConstructorAssignmentNode**

```java
package io.github.joke.percolate.graph.node;

import io.github.joke.percolate.spi.CreationDescriptor;
import javax.lang.model.element.TypeElement;

public final class ConstructorAssignmentNode implements TargetAssignmentNode {
    private final TypeElement targetType;
    private final CreationDescriptor descriptor;

    public ConstructorAssignmentNode(TypeElement targetType, CreationDescriptor descriptor) {
        this.targetType = targetType;
        this.descriptor = descriptor;
    }

    @Override
    public TypeElement getTargetType() { return targetType; }
    public CreationDescriptor getDescriptor() { return descriptor; }

    @Override
    public String toString() { return "Constructor(" + targetType.getSimpleName() + ")"; }
}
```

**Step 7: Create conversion and wrapper nodes**

`MethodCallNode`:
```java
package io.github.joke.percolate.graph.node;

import io.github.joke.percolate.model.MethodDefinition;
import javax.lang.model.type.TypeMirror;

public final class MethodCallNode implements MappingNode {
    private final MethodDefinition method;
    private final TypeMirror inType;
    private final TypeMirror outType;

    public MethodCallNode(MethodDefinition method, TypeMirror inType, TypeMirror outType) {
        this.method = method;
        this.inType = inType;
        this.outType = outType;
    }

    public MethodDefinition getMethod() { return method; }
    public TypeMirror getInType() { return inType; }
    public TypeMirror getOutType() { return outType; }

    @Override
    public String toString() { return "MethodCall(" + method.getName() + ":" + inType + "->" + outType + ")"; }
}
```

`CollectionIterationNode`:
```java
package io.github.joke.percolate.graph.node;

import javax.lang.model.type.TypeMirror;

/** Iterates a collection type, producing individual elements. Inline — no method registered. */
public final class CollectionIterationNode implements MappingNode {
    private final TypeMirror collectionType;
    private final TypeMirror elementType;

    public CollectionIterationNode(TypeMirror collectionType, TypeMirror elementType) {
        this.collectionType = collectionType;
        this.elementType = elementType;
    }

    public TypeMirror getCollectionType() { return collectionType; }
    public TypeMirror getElementType() { return elementType; }
}
```

`CollectionCollectNode`:
```java
package io.github.joke.percolate.graph.node;

import javax.lang.model.type.TypeMirror;

/** Collects elements into a target collection type. Inline — no method registered. */
public final class CollectionCollectNode implements MappingNode {
    private final TypeMirror targetCollectionType;
    private final TypeMirror elementType;

    public CollectionCollectNode(TypeMirror targetCollectionType, TypeMirror elementType) {
        this.targetCollectionType = targetCollectionType;
        this.elementType = elementType;
    }

    public TypeMirror getTargetCollectionType() { return targetCollectionType; }
    public TypeMirror getElementType() { return elementType; }
}
```

`OptionalWrapNode`, `OptionalUnwrapNode`, `BoxingNode`, `UnboxingNode` follow the same pattern — two `TypeMirror` fields (`inType`, `outType`), all-args constructor, getters, `toString()`.

**Step 8: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/graph/node/
git commit -m "feat: add MappingNode hierarchy for data-flow graph"
```

---

## Task 2: FlowEdge

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/graph/edge/FlowEdge.java`

**Step 1: Create FlowEdge**

```java
package io.github.joke.percolate.graph.edge;

import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

/**
 * Directed edge in the data-flow graph. Represents "output of source node becomes input of target node".
 * slotName is non-null only for edges flowing into a TargetAssignmentNode,
 * identifying which constructor parameter / builder setter this value feeds.
 */
public final class FlowEdge {
    private final TypeMirror sourceType;
    private final TypeMirror targetType;
    private final @Nullable String slotName;

    public FlowEdge(TypeMirror sourceType, TypeMirror targetType, @Nullable String slotName) {
        this.sourceType = sourceType;
        this.targetType = targetType;
        this.slotName = slotName;
    }

    /** Convenience constructor for edges that carry no slot binding. */
    public static FlowEdge of(TypeMirror sourceType, TypeMirror targetType) {
        return new FlowEdge(sourceType, targetType, null);
    }

    /** Convenience constructor for edges into TargetAssignmentNode. */
    public static FlowEdge forSlot(TypeMirror sourceType, TypeMirror targetType, String slotName) {
        return new FlowEdge(sourceType, targetType, slotName);
    }

    public TypeMirror getSourceType() { return sourceType; }
    public TypeMirror getTargetType() { return targetType; }
    public @Nullable String getSlotName() { return slotName; }

    @Override
    public String toString() {
        return slotName != null
            ? sourceType + " -[" + slotName + "]-> " + targetType
            : sourceType + " -> " + targetType;
    }
}
```

**Step 2: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/graph/edge/FlowEdge.java
git commit -m "feat: add FlowEdge for data-flow graph"
```

---

## Task 3: MethodRegistry

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/stage/TypePair.java`
- Create: `processor/src/main/java/io/github/joke/percolate/stage/RegistryEntry.java`
- Create: `processor/src/main/java/io/github/joke/percolate/stage/MethodRegistry.java`
- Create: `processor/src/test/groovy/io/github/joke/percolate/stage/MethodRegistrySpec.groovy`

**Step 1: Write the failing test**

```groovy
package io.github.joke.percolate.stage

import spock.lang.Specification

class MethodRegistrySpec extends Specification {

    def "lookup returns empty for unknown type pair"() {
        given:
        def registry = new MethodRegistry()

        when:
        def result = registry.lookup("test.A", "test.B")

        then:
        !result.isPresent()
    }

    def "register and lookup by type pair key"() {
        given:
        def registry = new MethodRegistry()
        def entry = new RegistryEntry(null, null)  // stub

        when:
        registry.register("test.A", "test.B", entry)

        then:
        registry.lookup("test.A", "test.B").isPresent()
        registry.lookup("test.A", "test.B").get() == entry
    }

    def "lookup is exact — different pair returns empty"() {
        given:
        def registry = new MethodRegistry()
        registry.register("test.A", "test.B", new RegistryEntry(null, null))

        when:
        def result = registry.lookup("test.A", "test.C")

        then:
        !result.isPresent()
    }
}
```

**Step 2: Run to verify it fails**

```bash
./gradlew :processor:test --tests 'MethodRegistrySpec'
```
Expected: FAIL (classes not found)

**Step 3: Create TypePair**

`TypePair` is the map key. Use `TypeMirror.toString()` (canonical qualified name) as the string key to avoid `TypeMirror.equals()` unreliability.

```java
package io.github.joke.percolate.stage;

import java.util.Objects;

/** Key for MethodRegistry lookups. Uses canonical type name strings to avoid TypeMirror equality issues. */
public final class TypePair {
    private final String inTypeName;
    private final String outTypeName;

    public TypePair(String inTypeName, String outTypeName) {
        this.inTypeName = inTypeName;
        this.outTypeName = outTypeName;
    }

    public String getInTypeName() { return inTypeName; }
    public String getOutTypeName() { return outTypeName; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TypePair)) return false;
        TypePair other = (TypePair) o;
        return inTypeName.equals(other.inTypeName) && outTypeName.equals(other.outTypeName);
    }

    @Override
    public int hashCode() { return Objects.hash(inTypeName, outTypeName); }

    @Override
    public String toString() { return inTypeName + " -> " + outTypeName; }
}
```

**Step 4: Create RegistryEntry**

```java
package io.github.joke.percolate.stage;

import io.github.joke.percolate.graph.edge.FlowEdge;
import io.github.joke.percolate.graph.node.MappingNode;
import io.github.joke.percolate.model.MethodDefinition;
import org.jgrapht.graph.DirectedWeightedMultigraph;
import org.jspecify.annotations.Nullable;

public final class RegistryEntry {
    private final @Nullable MethodDefinition signature;
    private final @Nullable DirectedWeightedMultigraph<MappingNode, FlowEdge> graph;

    public RegistryEntry(
            @Nullable MethodDefinition signature,
            @Nullable DirectedWeightedMultigraph<MappingNode, FlowEdge> graph) {
        this.signature = signature;
        this.graph = graph;
    }

    /** null for user-provided default methods (opaque). */
    public @Nullable MethodDefinition getSignature() { return signature; }

    /** null for opaque methods. Non-null for abstract and auto-generated methods. */
    public @Nullable DirectedWeightedMultigraph<MappingNode, FlowEdge> getGraph() { return graph; }

    public boolean isOpaque() { return graph == null; }
}
```

**Step 5: Create MethodRegistry**

```java
package io.github.joke.percolate.stage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

public final class MethodRegistry {
    private final Map<TypePair, RegistryEntry> entries = new LinkedHashMap<>();

    public Optional<RegistryEntry> lookup(TypeMirror in, TypeMirror out) {
        return lookup(in.toString(), out.toString());
    }

    public Optional<RegistryEntry> lookup(String inTypeName, String outTypeName) {
        return Optional.ofNullable(entries.get(new TypePair(inTypeName, outTypeName)));
    }

    public void register(TypeMirror in, TypeMirror out, RegistryEntry entry) {
        entries.put(new TypePair(in.toString(), out.toString()), entry);
    }

    public void register(String inTypeName, String outTypeName, RegistryEntry entry) {
        entries.put(new TypePair(inTypeName, outTypeName), entry);
    }

    public Map<TypePair, RegistryEntry> entries() {
        return java.util.Collections.unmodifiableMap(entries);
    }
}
```

**Step 6: Run test to verify it passes**

```bash
./gradlew :processor:test --tests 'MethodRegistrySpec'
```
Expected: PASS

**Step 7: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/stage/TypePair.java \
        processor/src/main/java/io/github/joke/percolate/stage/RegistryEntry.java \
        processor/src/main/java/io/github/joke/percolate/stage/MethodRegistry.java \
        processor/src/test/groovy/io/github/joke/percolate/stage/MethodRegistrySpec.groovy
git commit -m "feat: add MethodRegistry with TypePair key and RegistryEntry"
```

---

## Task 4: ParseStage — add MethodRegistry pre-population

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/ParseResult.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/ParseStage.java`

**Step 1: Read the existing ParseResult**

```bash
cat processor/src/main/java/io/github/joke/percolate/stage/ParseResult.java
```

**Step 2: Add MethodRegistry to ParseResult**

`ParseResult` currently holds `List<MapperDefinition>`. Add a `Map<TypeElement, MethodRegistry>` — one registry per mapper interface:

```java
// In ParseResult, add a second constructor parameter:
private final java.util.Map<javax.lang.model.element.TypeElement, MethodRegistry> registries;

public ParseResult(List<MapperDefinition> mappers,
                   java.util.Map<javax.lang.model.element.TypeElement, MethodRegistry> registries) {
    this.mappers = mappers;
    this.registries = registries;
}

public java.util.Map<javax.lang.model.element.TypeElement, MethodRegistry> getRegistries() {
    return registries;
}
```

**Step 3: Update ParseStage to build registries**

After building `MapperDefinition` list, iterate mappers and build one `MethodRegistry` per mapper:

```java
private MethodRegistry buildRegistry(MapperDefinition mapper) {
    MethodRegistry registry = new MethodRegistry();
    mapper.getMethods().forEach(method -> registerMethod(registry, method));
    return registry;
}

private void registerMethod(MethodRegistry registry, MethodDefinition method) {
    // Only register methods that have a single clear type pair (non-void, single return)
    if (method.getReturnType().getKind() == javax.lang.model.type.TypeKind.VOID) {
        return;
    }
    if (method.getParameters().size() != 1) {
        return; // multi-param methods are not direct type converters
    }
    javax.lang.model.type.TypeMirror inType = method.getParameters().get(0).getType();
    javax.lang.model.type.TypeMirror outType = method.getReturnType();
    registry.register(inType, outType, new RegistryEntry(method, null));
}
```

Update `execute()` to build the registries map and pass it to `ParseResult`:

```java
public ParseResult execute(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    List<MapperDefinition> mappers = roundEnv.getElementsAnnotatedWith(Mapper.class).stream()
            .filter(this::validateIsInterface)
            .map(element -> (TypeElement) element)
            .map(this::parseMapper)
            .collect(toList());

    Map<TypeElement, MethodRegistry> registries = mappers.stream()
            .collect(toMap(MapperDefinition::getTypeElement, this::buildRegistry));

    return new ParseResult(mappers, registries);
}
```

Note: `MapperDefinition.getTypeElement()` must exist. Check if it does; if not, add it.

**Step 4: Run existing parse tests to verify they still pass**

```bash
./gradlew :processor:test --tests 'ParseStageSpec'
```
Expected: PASS (or adjust tests to pass new constructor)

**Step 5: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/stage/ParseResult.java \
        processor/src/main/java/io/github/joke/percolate/stage/ParseStage.java
git commit -m "feat: pre-populate MethodRegistry in ParseStage output"
```

---

## Task 5: BindingStage — core

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/stage/BindingStage.java`
- Create: `processor/src/test/groovy/io/github/joke/percolate/stage/BindingStageSpec.groovy`

**Step 1: Write the failing test**

This test compiles a simple single-param mapper through the full processor. BindingStage doesn't generate files directly, so we verify that compilation succeeds (meaning BindingStage ran without error). The generated output test will come in Task 10.

```groovy
package io.github.joke.percolate.stage

import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class BindingStageSpec extends Specification {

    def "single-param method with explicit @Map directive compiles without error"() {
        given:
        def source = JavaFileObjects.forSourceLines('test.Source',
            'package test;',
            'public class Source { public String getName() { return ""; } }')
        def target = JavaFileObjects.forSourceLines('test.Target',
            'package test;',
            'public class Target { private final String name;',
            '    public Target(String name) { this.name = name; } }')
        def mapper = JavaFileObjects.forSourceLines('test.MyMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '@Mapper public interface MyMapper {',
            '    @Map(target = "name", source = "name")',
            '    Target map(Source source);',
            '}')

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(source, target, mapper)

        then:
        assertThat(compilation).succeeded()
    }

    def "multi-param method with param-prefixed source paths compiles without error"() {
        given:
        def a = JavaFileObjects.forSourceLines('test.A',
            'package test;',
            'public class A { public String getFoo() { return ""; } }')
        def b = JavaFileObjects.forSourceLines('test.B',
            'package test;',
            'public class B { public String getBar() { return ""; } }')
        def target = JavaFileObjects.forSourceLines('test.Out',
            'package test;',
            'public class Out { private final String foo; private final String bar;',
            '    public Out(String foo, String bar) { this.foo = foo; this.bar = bar; } }')
        def mapper = JavaFileObjects.forSourceLines('test.MultiMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '@Mapper public interface MultiMapper {',
            '    @Map(target = "foo", source = "a.foo")',
            '    @Map(target = "bar", source = "b.bar")',
            '    Out map(A a, B b);',
            '}')

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(a, b, target, mapper)

        then:
        assertThat(compilation).succeeded()
    }
}
```

**Step 2: Run to verify it fails**

```bash
./gradlew :processor:test --tests 'BindingStageSpec'
```
Expected: FAIL (BindingStage not yet wired into Pipeline)

**Step 3: Create BindingStage**

`BindingStage` receives `ParseResult`, uses `PropertyDiscoveryStrategy` to resolve paths, and builds partial graphs stored in the registry. It loads strategies via `ServiceLoader` (same pattern as existing `GraphBuildStage`).

```java
package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.toList;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.graph.edge.FlowEdge;
import io.github.joke.percolate.graph.node.MappingNode;
import io.github.joke.percolate.graph.node.PropertyAccessNode;
import io.github.joke.percolate.graph.node.SourceNode;
import io.github.joke.percolate.graph.node.TargetSlotPlaceholder;
import io.github.joke.percolate.model.MapDirective;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.model.ParameterDefinition;
import io.github.joke.percolate.model.Property;
import io.github.joke.percolate.spi.PropertyDiscoveryStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.jgrapht.graph.DirectedWeightedMultigraph;
import org.jspecify.annotations.Nullable;

@RoundScoped
public class BindingStage {

    private final ProcessingEnvironment processingEnv;
    private final List<PropertyDiscoveryStrategy> propertyStrategies;

    @Inject
    BindingStage(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.propertyStrategies = new ArrayList<>();
        ServiceLoader.load(PropertyDiscoveryStrategy.class, getClass().getClassLoader())
                .forEach(propertyStrategies::add);
    }

    public MethodRegistry execute(ParseResult parseResult) {
        parseResult.getMappers().forEach(mapper ->
                buildMapperGraphs(mapper, parseResult.getRegistries().get(mapper.getTypeElement())));
        return parseResult.getRegistries().values().stream()
                .reduce(new MethodRegistry(), this::mergeRegistries);
    }

    private void buildMapperGraphs(MapperDefinition mapper, MethodRegistry registry) {
        mapper.getMethods().stream()
                .filter(MethodDefinition::isAbstract)
                .forEach(method -> buildMethodGraph(method, registry));
    }

    private void buildMethodGraph(MethodDefinition method, MethodRegistry registry) {
        DirectedWeightedMultigraph<MappingNode, FlowEdge> graph =
                new DirectedWeightedMultigraph<>(FlowEdge.class);

        List<SourceNode> sourceNodes = method.getParameters().stream()
                .map(param -> {
                    SourceNode node = new SourceNode(param.getName(), param.getType());
                    graph.addVertex(node);
                    return node;
                })
                .collect(toList());

        method.getDirectives().forEach(directive ->
                processDirective(graph, directive, method, sourceNodes));

        // Store partial graph in registry under (void -> returnType) as a placeholder key
        // The real key is per-mapper method; use method name + return type for now
        TypeMirror returnType = method.getReturnType();
        // Update the existing registry entry to hold the graph
        Optional<RegistryEntry> existing = registry.lookup(
                getSingleParamType(method), returnType.toString());
        if (existing.isPresent()) {
            registry.register(getSingleParamType(method), returnType.toString(),
                    new RegistryEntry(existing.get().getSignature(), graph));
        }
    }

    private String getSingleParamType(MethodDefinition method) {
        if (method.getParameters().size() == 1) {
            return method.getParameters().get(0).getType().toString();
        }
        // Multi-param: use a synthetic key combining all param types
        return method.getParameters().stream()
                .map(p -> p.getType().toString())
                .reduce("(", (a, b) -> a + "," + b) + ")";
    }

    private void processDirective(
            DirectedWeightedMultigraph<MappingNode, FlowEdge> graph,
            MapDirective directive,
            MethodDefinition method,
            List<SourceNode> sourceNodes) {

        String sourcePath = directive.getSource();
        @SuppressWarnings("StringSplitter")
        String[] segments = sourcePath.split("\\.");

        // Resolve starting source node and segment offset
        SourceNode startNode;
        int startIndex;
        if (sourceNodes.size() > 1) {
            // Multi-param: first segment must be a parameter name
            String firstName = segments[0];
            Optional<SourceNode> match = sourceNodes.stream()
                    .filter(n -> n.getParamName().equals(firstName))
                    .findFirst();
            if (!match.isPresent()) return;
            startNode = match.get();
            startIndex = 1;
        } else {
            // Single-param: first segment may be param name (skip it) or property name (use it)
            SourceNode only = sourceNodes.get(0);
            if (segments.length > 1 && segments[0].equals(only.getParamName())) {
                startNode = only;
                startIndex = 1;
            } else {
                startNode = only;
                startIndex = 0;
            }
        }

        // Walk the property chain
        MappingNode current = startNode;
        TypeMirror currentType = startNode.getType();
        for (int i = startIndex; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.equals("*")) break; // wildcard — handled separately

            @Nullable TypeElement typeElement = asTypeElement(currentType);
            if (typeElement == null) return;

            Set<Property> properties = discoverProperties(typeElement);
            Optional<Property> prop = properties.stream()
                    .filter(p -> p.getName().equals(segment))
                    .findFirst();
            if (!prop.isPresent()) return;

            Property property = prop.get();
            PropertyAccessNode propNode = new PropertyAccessNode(
                    segment, currentType, property.getType(), property.getAccessor());
            graph.addVertex(propNode);
            graph.addEdge(current, propNode, FlowEdge.of(currentType, property.getType()));

            current = propNode;
            currentType = property.getType();
        }

        // Add TargetSlotPlaceholder at end of chain
        String target = directive.getTarget();
        if (target.equals(".")) return; // wildcard target — handled separately

        @Nullable TypeElement returnTypeElement = asTypeElement(method.getReturnType());
        if (returnTypeElement == null) return;

        TargetSlotPlaceholder slot = new TargetSlotPlaceholder(method.getReturnType(), target);
        graph.addVertex(slot);
        graph.addEdge(current, slot, FlowEdge.forSlot(currentType, method.getReturnType(), target));
    }

    private Set<Property> discoverProperties(TypeElement type) {
        return propertyStrategies.stream()
                .flatMap(s -> s.discoverProperties(type, processingEnv).stream())
                .collect(java.util.stream.Collectors.toSet());
    }

    private @Nullable TypeElement asTypeElement(TypeMirror type) {
        if (type instanceof DeclaredType) {
            return (TypeElement) ((DeclaredType) type).asElement();
        }
        return null;
    }

    private MethodRegistry mergeRegistries(MethodRegistry a, MethodRegistry b) {
        b.entries().forEach((pair, entry) -> a.register(pair.getInTypeName(), pair.getOutTypeName(), entry));
        return a;
    }
}
```

> **Note on wildcard expansion (`source="order.*"`):** The `"*"` wildcard creates multiple directives. Implement `expandWildcardDirectives()` in a follow-up step (Task 6). For now, skip `*` segments gracefully.

**Step 4: Wire BindingStage into Pipeline (temporary truncation)**

Update `Pipeline.java`. Keep `ValidateStage`, `OptimizeStage`, `CodeGenStage` injected so Dagger stays happy, but stop execution after WiringStage. Since WiringStage doesn't exist yet, just call BindingStage and return:

```java
// In Pipeline.java — replace the process() body temporarily:
public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    ParseResult parseResult = parseStage.execute(annotations, roundEnv);
    MethodRegistry registry = bindingStage.execute(parseResult);
    // WiringStage wired in Task 8
    // ValidateStage, OptimizeStage, CodeGenStage reconnected in future redesign
}
```

Add `bindingStage` field and constructor parameter. Keep other fields to avoid breaking Dagger for now.

**Step 5: Run test to verify it passes**

```bash
./gradlew :processor:test --tests 'BindingStageSpec'
```
Expected: PASS

**Step 6: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/stage/BindingStage.java \
        processor/src/main/java/io/github/joke/percolate/processor/Pipeline.java \
        processor/src/test/groovy/io/github/joke/percolate/stage/BindingStageSpec.groovy
git commit -m "feat: add BindingStage — builds typed source property chains"
```

---

## Task 6: BindingStage — wildcard expansion and same-name matching

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/BindingStage.java`
- Modify: `processor/src/test/groovy/io/github/joke/percolate/stage/BindingStageSpec.groovy`

**Step 1: Add failing tests**

```groovy
def "wildcard source expands all properties of the param type"() {
    given:
    def order = JavaFileObjects.forSourceLines('test.Order',
        'package test;',
        'public class Order {',
        '    public long getOrderId() { return 0L; }',
        '    public long getOrderNumber() { return 0L; }',
        '}')
    def out = JavaFileObjects.forSourceLines('test.Out',
        'package test;',
        'public class Out {',
        '    private final long orderId; private final long orderNumber;',
        '    public Out(long orderId, long orderNumber) {',
        '        this.orderId = orderId; this.orderNumber = orderNumber;',
        '    }',
        '}')
    def mapper = JavaFileObjects.forSourceLines('test.WildMapper',
        'package test;',
        'import io.github.joke.percolate.Mapper; import io.github.joke.percolate.Map;',
        '@Mapper public interface WildMapper {',
        '    @Map(target = ".", source = "order.*")',
        '    Out map(Order order);',
        '}')

    when:
    def compilation = Compiler.javac()
        .withProcessors(new PercolateProcessor())
        .compile(order, out, mapper)

    then:
    assertThat(compilation).succeeded()
}

def "same-name matching fills unmapped slots automatically"() {
    given:
    def src = JavaFileObjects.forSourceLines('test.Src',
        'package test;',
        'public class Src { public String getName() { return ""; } public int getAge() { return 0; } }')
    def tgt = JavaFileObjects.forSourceLines('test.Tgt',
        'package test;',
        'public class Tgt { private final String name; private final int age;',
        '    public Tgt(String name, int age) { this.name = name; this.age = age; } }')
    def mapper = JavaFileObjects.forSourceLines('test.AutoMapper',
        'package test;',
        'import io.github.joke.percolate.Mapper;',
        '@Mapper public interface AutoMapper { Tgt map(Src src); }')

    when:
    def compilation = Compiler.javac()
        .withProcessors(new PercolateProcessor())
        .compile(src, tgt, mapper)

    then:
    assertThat(compilation).succeeded()
}
```

**Step 2: Run to verify it fails**

```bash
./gradlew :processor:test --tests 'BindingStageSpec'
```
Expected: Tests for wildcard and same-name fail.

**Step 3: Implement wildcard expansion**

In `BindingStage`, add a pre-processing step that expands `@Map(target=".", source="order.*")` into individual directives before `processDirective` is called:

```java
private List<MapDirective> expandDirectives(
        List<MapDirective> directives, MethodDefinition method, List<SourceNode> sourceNodes) {
    List<MapDirective> expanded = new ArrayList<>();
    for (MapDirective directive : directives) {
        if (directive.getSource().endsWith(".*")) {
            expanded.addAll(expandWildcard(directive, method, sourceNodes));
        } else {
            expanded.add(directive);
        }
    }
    return expanded;
}

private List<MapDirective> expandWildcard(
        MapDirective directive, MethodDefinition method, List<SourceNode> sourceNodes) {
    // e.g., source="order.*" -> find SourceNode for "order", discover its properties
    String sourcePath = directive.getSource();
    String prefix = sourcePath.substring(0, sourcePath.length() - 2); // remove ".*"

    TypeMirror paramType = resolvePathType(prefix, sourceNodes);
    if (paramType == null) return java.util.Collections.emptyList();

    TypeElement typeElement = asTypeElement(paramType);
    if (typeElement == null) return java.util.Collections.emptyList();

    return discoverProperties(typeElement).stream()
            .map(prop -> new MapDirective(prop.getName(), prefix + "." + prop.getName()))
            .collect(toList());
}
```

**Step 4: Implement same-name matching**

After processing explicit directives, check which target constructor params remain uncovered. Discover source properties and create implicit directives for matching names:

```java
private List<MapDirective> generateSameNameDirectives(
        MethodDefinition method, List<SourceNode> sourceNodes, Set<String> alreadyMapped) {
    // Only applies to single-param methods
    if (sourceNodes.size() != 1) return java.util.Collections.emptyList();
    SourceNode sourceNode = sourceNodes.get(0);
    TypeElement sourceType = asTypeElement(sourceNode.getType());
    if (sourceType == null) return java.util.Collections.emptyList();

    return discoverProperties(sourceType).stream()
            .filter(prop -> !alreadyMapped.contains(prop.getName()))
            .map(prop -> new MapDirective(prop.getName(), prop.getName()))
            .collect(toList());
}
```

**Step 5: Run tests to verify they pass**

```bash
./gradlew :processor:test --tests 'BindingStageSpec'
```
Expected: All PASS

**Step 6: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/stage/BindingStage.java \
        processor/src/test/groovy/io/github/joke/percolate/stage/BindingStageSpec.groovy
git commit -m "feat: add wildcard expansion and same-name matching to BindingStage"
```

---

## Task 7: ConversionFragment and ConversionProvider redesign

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/spi/ConversionFragment.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/spi/ConversionProvider.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/spi/impl/ListProvider.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/spi/impl/OptionalProvider.java`

First read the existing `ConversionProvider.java` and provider implementations to understand what changes:

```bash
cat processor/src/main/java/io/github/joke/percolate/spi/ConversionProvider.java
cat processor/src/main/java/io/github/joke/percolate/spi/impl/ListProvider.java
cat processor/src/main/java/io/github/joke/percolate/spi/impl/OptionalProvider.java
```

**Step 1: Create ConversionFragment**

`ConversionFragment` is the ordered sequence of `MappingNode`s that WiringStage splices into the graph between two mismatched nodes:

```java
package io.github.joke.percolate.spi;

import io.github.joke.percolate.graph.node.MappingNode;
import java.util.List;

/**
 * An ordered list of MappingNodes to insert between two type-mismatched endpoints.
 * WiringStage splices these nodes into the graph, replacing the original FlowEdge.
 */
public final class ConversionFragment {
    private final List<MappingNode> nodes;

    public ConversionFragment(List<MappingNode> nodes) {
        this.nodes = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(nodes));
    }

    public static ConversionFragment of(MappingNode... nodes) {
        return new ConversionFragment(java.util.Arrays.asList(nodes));
    }

    public List<MappingNode> getNodes() { return nodes; }
    public boolean isEmpty() { return nodes.isEmpty(); }
}
```

**Step 2: Redesign ConversionProvider interface**

Replace the existing `ConversionProvider` with the new contract. The `MethodRegistry` parameter lets providers register auto-generated methods:

```java
package io.github.joke.percolate.spi;

import io.github.joke.percolate.stage.MethodRegistry;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;

public interface ConversionProvider {

    boolean canHandle(TypeMirror source, TypeMirror target, ProcessingEnvironment env);

    /**
     * Returns a ConversionFragment — the nodes to insert between source and target.
     * Providers may register auto-generated helper methods in the registry.
     * Inline providers (Optional, Boxing, Collection) do NOT register methods.
     */
    ConversionFragment provide(TypeMirror source, TypeMirror target,
                               MethodRegistry registry, ProcessingEnvironment env);
}
```

**Step 3: Update built-in providers**

Read each existing provider, understand its current logic, then rewrite to implement the new interface returning `ConversionFragment` with appropriate `MappingNode`s.

For `ListProvider`: `canHandle` returns true when source is `List<X>` and target is `List<Y>` (or other collection combinations). `provide` returns a fragment of `[CollectionIterationNode, MethodCallNode(X→Y), CollectionCollectNode]`. The `MethodCallNode` looks up the registry for an `X→Y` converter first.

For `OptionalProvider`: `canHandle` returns true when either source or target (or both) is `Optional<T>`. `provide` returns `[OptionalUnwrapNode]`, `[OptionalWrapNode]`, or both, depending on which side has Optional.

For `SubtypeProvider`, `PrimitiveWideningProvider`, `EnumProvider`: update to new interface signature.

**Step 4: Run full test suite to check for regressions**

```bash
./gradlew :processor:test
```
Expected: Tests that use old ConversionProvider (in later stages) may fail — that is acceptable per constraints.

**Step 5: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/spi/ConversionFragment.java \
        processor/src/main/java/io/github/joke/percolate/spi/ConversionProvider.java \
        processor/src/main/java/io/github/joke/percolate/spi/impl/
git commit -m "feat: redesign ConversionProvider SPI with ConversionFragment"
```

---

## Task 8: WiringStage — creation strategy resolution

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/stage/WiringStage.java`
- Create: `processor/src/test/groovy/io/github/joke/percolate/stage/WiringStageSpec.groovy`

**Step 1: Write the failing test**

```groovy
package io.github.joke.percolate.stage

import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class WiringStageSpec extends Specification {

    def "compatible types — no conversion needed — compiles and generates impl"() {
        given:
        def src = JavaFileObjects.forSourceLines('test.Src',
            'package test;',
            'public class Src { public String getName() { return ""; } }')
        def tgt = JavaFileObjects.forSourceLines('test.Tgt',
            'package test;',
            'public class Tgt { private final String name;',
            '    public Tgt(String name) { this.name = name; } }')
        def mapper = JavaFileObjects.forSourceLines('test.SimpleMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper public interface SimpleMapper { Tgt map(Src src); }')

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(src, tgt, mapper)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation).generatedSourceFile('test.SimpleMapperImpl')
    }
}
```

**Step 2: Run to verify it fails**

```bash
./gradlew :processor:test --tests 'WiringStageSpec'
```
Expected: FAIL (WiringStage not yet wired, no impl generated)

**Step 3: Create WiringStage skeleton**

```java
package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.toList;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.graph.edge.FlowEdge;
import io.github.joke.percolate.graph.node.ConstructorAssignmentNode;
import io.github.joke.percolate.graph.node.MappingNode;
import io.github.joke.percolate.graph.node.TargetSlotPlaceholder;
import io.github.joke.percolate.spi.CreationDescriptor;
import io.github.joke.percolate.spi.ObjectCreationStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.jgrapht.graph.DirectedWeightedMultigraph;
import org.jspecify.annotations.Nullable;

@RoundScoped
public class WiringStage {

    private final ProcessingEnvironment processingEnv;
    private final List<ObjectCreationStrategy> creationStrategies;

    @Inject
    WiringStage(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.creationStrategies = new ArrayList<>();
        ServiceLoader.load(ObjectCreationStrategy.class, getClass().getClassLoader())
                .forEach(creationStrategies::add);
    }

    public MethodRegistry execute(MethodRegistry registry) {
        registry.entries().forEach((pair, entry) -> {
            if (entry.getGraph() != null && !entry.isOpaque()) {
                wireGraph(entry.getGraph(), entry.getSignature(), registry);
            }
        });
        return registry;
    }

    private void wireGraph(
            DirectedWeightedMultigraph<MappingNode, FlowEdge> graph,
            @Nullable io.github.joke.percolate.model.MethodDefinition method,
            MethodRegistry registry) {
        if (method == null) return;

        resolveCreationStrategy(graph, method.getReturnType());
        // Conversion insertion follows in Task 9
    }

    private void resolveCreationStrategy(
            DirectedWeightedMultigraph<MappingNode, FlowEdge> graph,
            TypeMirror returnType) {
        @Nullable TypeElement returnTypeElement = asTypeElement(returnType);
        if (returnTypeElement == null) return;

        @Nullable CreationDescriptor descriptor = findCreationDescriptor(returnTypeElement);
        if (descriptor == null) return;

        ConstructorAssignmentNode assignmentNode = new ConstructorAssignmentNode(returnTypeElement, descriptor);
        graph.addVertex(assignmentNode);

        // Find all TargetSlotPlaceholders and rewire their incoming edges to ConstructorAssignmentNode
        Set<MappingNode> placeholders = graph.vertexSet().stream()
                .filter(TargetSlotPlaceholder.class::isInstance)
                .collect(java.util.stream.Collectors.toSet());

        for (MappingNode placeholder : placeholders) {
            Set<FlowEdge> incomingEdges = graph.incomingEdgesOf(placeholder);
            for (FlowEdge edge : new ArrayList<>(incomingEdges)) {
                MappingNode source = graph.getEdgeSource(edge);
                graph.addEdge(source, assignmentNode,
                        FlowEdge.forSlot(edge.getSourceType(), returnType,
                                ((TargetSlotPlaceholder) placeholder).getSlotName()));
            }
            graph.removeVertex(placeholder); // also removes its edges
        }
    }

    private @Nullable CreationDescriptor findCreationDescriptor(TypeElement type) {
        return creationStrategies.stream()
                .filter(s -> s.canCreate(type, processingEnv))
                .findFirst()
                .map(s -> s.describe(type, processingEnv))
                .orElse(null);
    }

    private @Nullable TypeElement asTypeElement(TypeMirror type) {
        if (type instanceof DeclaredType) {
            return (TypeElement) ((DeclaredType) type).asElement();
        }
        return null;
    }
}
```

**Step 4: Wire WiringStage into Pipeline and connect to CodeGen**

Update `Pipeline.java` to call WiringStage. Then wire the registry output into CodeGen. Since CodeGen currently expects `OptimizedGraphResult`, add a temporary adapter or simply call the existing CodeGen stage with a stub. For now, just call WiringStage and leave CodeGen disconnected:

```java
public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    ParseResult parseResult = parseStage.execute(annotations, roundEnv);
    MethodRegistry registry = bindingStage.execute(parseResult);
    MethodRegistry wiredRegistry = wiringStage.execute(registry);
    // CodeGen reconnected in a future task — see Task 10
}
```

**Step 5: Run test to verify it passes**

```bash
./gradlew :processor:test --tests 'WiringStageSpec'
```
Expected: First test (compatible types) PASS. More tests added in Task 9.

**Step 6: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/stage/WiringStage.java \
        processor/src/main/java/io/github/joke/percolate/processor/Pipeline.java \
        processor/src/test/groovy/io/github/joke/percolate/stage/WiringStageSpec.groovy
git commit -m "feat: add WiringStage — resolves creation strategy and replaces TargetSlotPlaceholders"
```

---

## Task 9: WiringStage — type conversion insertion

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/WiringStage.java`
- Modify: `processor/src/test/groovy/io/github/joke/percolate/stage/WiringStageSpec.groovy`

**Step 1: Add failing tests for type conversions**

```groovy
def "List<Actor> to List<TicketActor> inserts collection iteration nodes"() {
    given:
    def actor = JavaFileObjects.forSourceLines('test.Actor',
        'package test;',
        'public class Actor { public String getName() { return ""; } }')
    def ticketActor = JavaFileObjects.forSourceLines('test.TicketActor',
        'package test;',
        'public class TicketActor { private final String name;',
        '    public TicketActor(String name) { this.name = name; } }')
    def container = JavaFileObjects.forSourceLines('test.Container',
        'package test; import java.util.List;',
        'public class Container { public List<Actor> getActors() { return null; } }')
    def result = JavaFileObjects.forSourceLines('test.Result',
        'package test; import java.util.List;',
        'public class Result { private final List<TicketActor> actors;',
        '    public Result(List<TicketActor> actors) { this.actors = actors; } }')
    def mapper = JavaFileObjects.forSourceLines('test.ListMapper',
        'package test; import java.util.List;',
        'import io.github.joke.percolate.Mapper; import io.github.joke.percolate.Map;',
        '@Mapper public interface ListMapper {',
        '    @Map(target = "actors", source = "actors")',
        '    Result map(Container container);',
        '    TicketActor mapActor(Actor actor);',
        '}')

    when:
    def compilation = Compiler.javac()
        .withProcessors(new PercolateProcessor())
        .compile(actor, ticketActor, container, result, mapper)

    then:
    assertThat(compilation).succeeded()
    assertThat(compilation).generatedSourceFile('test.ResultImpl')
}
```

**Step 2: Run to verify it fails**

```bash
./gradlew :processor:test --tests 'WiringStageSpec'
```

**Step 3: Implement topological traversal and conversion insertion**

Add to `WiringStage.wireGraph()` after creation strategy resolution:

```java
private void insertConversions(
        DirectedWeightedMultigraph<MappingNode, FlowEdge> graph,
        MethodRegistry registry) {

    // Collect edges to process (copy to avoid ConcurrentModificationException)
    List<FlowEdge> edgesToCheck = new ArrayList<>(graph.edgeSet());

    for (FlowEdge edge : edgesToCheck) {
        TypeMirror sourceType = edge.getSourceType();
        TypeMirror targetType = edge.getTargetType();

        if (processingEnv.getTypeUtils().isSameType(
                processingEnv.getTypeUtils().erasure(sourceType),
                processingEnv.getTypeUtils().erasure(targetType))) {
            continue; // compatible — no conversion needed
        }

        // Ask each ConversionProvider in order
        Optional<ConversionFragment> fragment = findFragment(sourceType, targetType, registry);
        if (fragment.isEmpty() || fragment.get().isEmpty()) continue;

        // Splice fragment nodes between edge's source and target
        MappingNode edgeSource = graph.getEdgeSource(edge);
        MappingNode edgeTarget = graph.getEdgeTarget(edge);
        graph.removeEdge(edge);

        List<MappingNode> nodes = fragment.get().getNodes();
        MappingNode prev = edgeSource;
        TypeMirror prevType = sourceType;
        for (MappingNode node : nodes) {
            graph.addVertex(node);
            TypeMirror nodeOutType = outTypeOf(node);
            graph.addEdge(prev, node, FlowEdge.of(prevType, nodeOutType));
            prev = node;
            prevType = nodeOutType;
        }
        // Reconnect last fragment node to original target
        graph.addEdge(prev, edgeTarget,
                edge.getSlotName() != null
                        ? FlowEdge.forSlot(prevType, targetType, edge.getSlotName())
                        : FlowEdge.of(prevType, targetType));
    }
}
```

Load `ConversionProvider`s via `ServiceLoader` in the constructor (same pattern as other strategies).

**Step 4: Run tests to verify they pass**

```bash
./gradlew :processor:test --tests 'WiringStageSpec'
```
Expected: PASS

**Step 5: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/stage/WiringStage.java \
        processor/src/test/groovy/io/github/joke/percolate/stage/WiringStageSpec.groovy
git commit -m "feat: add type conversion insertion to WiringStage"
```

---

## Task 10: Pipeline update — wire CodeGen and remove old stages

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/processor/Pipeline.java`
- Delete: `processor/src/main/java/io/github/joke/percolate/stage/ResolveStage.java`
- Delete: `processor/src/main/java/io/github/joke/percolate/stage/GraphBuildStage.java`

> **Do NOT delete:** `ResolveResult.java`, `GraphResult.java` — later stages still reference these types.

**Step 1: Read current Pipeline and plan the changes**

The pipeline currently calls: `parse → resolve → graphBuild → validate → optimize → codeGen`

New: `parse → bind → wire → [validate/optimize/codeGen reconnected later]`

CodeGen currently expects `OptimizedGraphResult`. Since we're not touching CodeGen, we stop the pipeline after WiringStage for now. The full pipeline reconnection is a future task.

**Step 2: Update Pipeline**

```java
@RoundScoped
public class Pipeline {

    private final ParseStage parseStage;
    private final BindingStage bindingStage;
    private final WiringStage wiringStage;

    @Inject
    Pipeline(ParseStage parseStage, BindingStage bindingStage, WiringStage wiringStage) {
        this.parseStage = parseStage;
        this.bindingStage = bindingStage;
        this.wiringStage = wiringStage;
    }

    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        ParseResult parseResult = parseStage.execute(annotations, roundEnv);
        MethodRegistry registry = bindingStage.execute(parseResult);
        wiringStage.execute(registry);
        // ValidateStage, OptimizeStage, CodeGenStage reconnected in future redesign
    }
}
```

**Step 3: Delete old stages**

```bash
rm processor/src/main/java/io/github/joke/percolate/stage/ResolveStage.java
rm processor/src/main/java/io/github/joke/percolate/stage/GraphBuildStage.java
```

**Step 4: Run the full test suite**

```bash
./gradlew :processor:test
```
Expected: `BindingStageSpec`, `WiringStageSpec`, `MethodRegistrySpec` PASS. Tests for later stages (`ValidateStageSpec`, `CodeGenStageSpec`, integration tests) may fail — that is expected and acceptable at this stage.

**Step 5: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/processor/Pipeline.java
git rm processor/src/main/java/io/github/joke/percolate/stage/ResolveStage.java \
       processor/src/main/java/io/github/joke/percolate/stage/GraphBuildStage.java
git commit -m "refactor: replace ResolveStage+GraphBuildStage with BindingStage+WiringStage in Pipeline"
```

---

## Verification

After all tasks complete, run:

```bash
./gradlew :processor:test --tests 'BindingStageSpec'
./gradlew :processor:test --tests 'WiringStageSpec'
./gradlew :processor:test --tests 'MethodRegistrySpec'
```

All three specs must pass. Failures in `ValidateStageSpec`, `CodeGenStageSpec`, `GraphBuildStageSpec`, and `TicketMapperIntegrationSpec` are expected — they will be addressed in the next design phase.

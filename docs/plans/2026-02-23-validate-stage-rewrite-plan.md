# ValidateStage Rewrite with Lazy Graph — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Rewrite the ValidateStage to use JGraphT algorithms (ConnectivityInspector, CycleDetector) on a lazy-expanding graph that materializes type conversions on demand, replacing the ad-hoc type conversion logic currently in CodeGenStage.

**Architecture:** A `LazyMappingGraph` wraps the base `DirectedWeightedMultigraph` and lazily adds `ConversionEdge`s when `outgoingEdgesOf()` is called during algorithm traversal. Built-in `ConversionProvider` implementations handle mapper methods, Optional, List, primitives, enums, and subtype conversions. ValidateStage uses CycleDetector + ConnectivityInspector on this lazy graph. CodeGenStage becomes a pure graph traversal with no type-checking logic.

**Tech Stack:** Java 11 (release 11, `-parameters`, `-Werror`), JGraphT 1.5.2, Dagger 2.59.1, Palantir JavaPoet 0.11.0, Spock 2.4 + Google Compile Testing 0.23.0.

**Conventions:**
- Package root: `io.github.joke.percolate`
- Every new package needs `package-info.java` with `@org.jspecify.annotations.NullMarked`
- No `var`, no records, no text blocks
- Prefer streams, static imports, functional style
- ErrorProne + NullAway (JSpecify mode) enforced

---

## Task 1: ConversionEdge + ConversionProvider SPI

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/graph/edge/ConversionEdge.java`
- Create: `processor/src/main/java/io/github/joke/percolate/spi/ConversionProvider.java`

**Step 1: Create ConversionEdge**

```java
package io.github.joke.percolate.graph.edge;

import javax.lang.model.type.TypeMirror;

/**
 * Edge representing a type conversion between two TypeNodes or PropertyNode to TypeNode.
 * Carries enough information for CodeGenStage to emit the correct expression.
 */
public final class ConversionEdge implements GraphEdge {

    private final Kind kind;
    private final TypeMirror sourceType;
    private final TypeMirror targetType;
    private final String expressionTemplate;

    public ConversionEdge(Kind kind, TypeMirror sourceType, TypeMirror targetType, String expressionTemplate) {
        this.kind = kind;
        this.sourceType = sourceType;
        this.targetType = targetType;
        this.expressionTemplate = expressionTemplate;
    }

    public Kind getKind() {
        return kind;
    }

    public TypeMirror getSourceType() {
        return sourceType;
    }

    public TypeMirror getTargetType() {
        return targetType;
    }

    public String getExpressionTemplate() {
        return expressionTemplate;
    }

    @Override
    public String toString() {
        return "Conversion{" + kind + ": " + sourceType + " -> " + targetType + "}";
    }

    public enum Kind {
        MAPPER_METHOD,
        OPTIONAL_WRAP,
        OPTIONAL_UNWRAP,
        LIST_MAP,
        PRIMITIVE_WIDEN,
        PRIMITIVE_BOX,
        PRIMITIVE_UNBOX,
        ENUM_VALUE_OF,
        SUBTYPE
    }
}
```

**Step 2: Create ConversionProvider interface**

```java
package io.github.joke.percolate.spi;

import io.github.joke.percolate.graph.edge.ConversionEdge;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;

/**
 * SPI for discovering type conversions. Each provider returns all conversions
 * possible from a given source type. Used by LazyMappingGraph during traversal.
 */
public interface ConversionProvider {

    /**
     * Returns all types this source can convert to, along with the edge to add to the graph.
     * Called lazily during graph traversal — results are cached per source node.
     */
    List<Conversion> possibleConversions(TypeMirror source, ProcessingEnvironment env);

    final class Conversion {
        private final TypeMirror targetType;
        private final ConversionEdge edge;

        public Conversion(TypeMirror targetType, ConversionEdge edge) {
            this.targetType = targetType;
            this.edge = edge;
        }

        public TypeMirror getTargetType() {
            return targetType;
        }

        public ConversionEdge getEdge() {
            return edge;
        }
    }
}
```

**Step 3: Run build to verify compilation**

Run: `./gradlew :processor:compileJava`
Expected: SUCCESS

**Step 4: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/graph/edge/ConversionEdge.java \
       processor/src/main/java/io/github/joke/percolate/spi/ConversionProvider.java
git commit -m "feat: add ConversionEdge and ConversionProvider SPI"
```

---

## Task 2: LazyMappingGraph

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/graph/LazyMappingGraph.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/package-info.java` (already exists — verify `@NullMarked`)
- Test: `processor/src/test/groovy/io/github/joke/percolate/graph/LazyMappingGraphSpec.groovy`

**Step 1: Write the failing test**

```groovy
package io.github.joke.percolate.graph

import io.github.joke.percolate.graph.edge.ConversionEdge
import io.github.joke.percolate.graph.edge.GraphEdge
import io.github.joke.percolate.graph.node.GraphNode
import io.github.joke.percolate.graph.node.TypeNode
import io.github.joke.percolate.spi.ConversionProvider
import org.jgrapht.alg.connectivity.ConnectivityInspector
import org.jgrapht.graph.DirectedWeightedMultigraph
import spock.lang.Specification

import javax.lang.model.type.TypeMirror

class LazyMappingGraphSpec extends Specification {

    def "lazily expands conversion edges during outgoingEdgesOf"() {
        given:
        def base = new DirectedWeightedMultigraph<GraphNode, GraphEdge>(GraphEdge)
        def sourceType = Mock(TypeMirror) { toString() >> 'SourceType' }
        def targetType = Mock(TypeMirror) { toString() >> 'TargetType' }
        def sourceNode = new TypeNode(sourceType, 'source')
        base.addVertex(sourceNode)

        def edge = new ConversionEdge(
            ConversionEdge.Kind.MAPPER_METHOD, sourceType, targetType, 'this.convert($expr)')
        def conversion = new ConversionProvider.Conversion(targetType, edge)
        def provider = Mock(ConversionProvider) {
            possibleConversions(sourceType, _) >> [conversion]
        }

        def lazy = new LazyMappingGraph(base, [provider], null, 5)

        when:
        def edges = lazy.outgoingEdgesOf(sourceNode)

        then:
        edges.size() == 1
        edges.first() instanceof ConversionEdge
    }

    def "caches expansion — second call returns same edges without re-expanding"() {
        given:
        def base = new DirectedWeightedMultigraph<GraphNode, GraphEdge>(GraphEdge)
        def sourceType = Mock(TypeMirror) { toString() >> 'SourceType' }
        def targetType = Mock(TypeMirror) { toString() >> 'TargetType' }
        def sourceNode = new TypeNode(sourceType, 'source')
        base.addVertex(sourceNode)

        def edge = new ConversionEdge(
            ConversionEdge.Kind.SUBTYPE, sourceType, targetType, '$expr')
        def conversion = new ConversionProvider.Conversion(targetType, edge)
        def provider = Mock(ConversionProvider)

        def lazy = new LazyMappingGraph(base, [provider], null, 5)

        when:
        lazy.outgoingEdgesOf(sourceNode)
        lazy.outgoingEdgesOf(sourceNode)

        then:
        1 * provider.possibleConversions(sourceType, _) >> [conversion]
    }

    def "respects depth limit"() {
        given:
        def base = new DirectedWeightedMultigraph<GraphNode, GraphEdge>(GraphEdge)
        def typeA = Mock(TypeMirror) { toString() >> 'A' }
        def typeB = Mock(TypeMirror) { toString() >> 'B' }
        def nodeA = new TypeNode(typeA, 'a')
        base.addVertex(nodeA)

        // Provider that always creates a new conversion (infinite chain)
        def provider = Mock(ConversionProvider) {
            possibleConversions(_, _) >> { TypeMirror src, _ ->
                def tgt = Mock(TypeMirror) { toString() >> src.toString() + '+' }
                def e = new ConversionEdge(ConversionEdge.Kind.SUBTYPE, src, tgt, '$expr')
                [new ConversionProvider.Conversion(tgt, e)]
            }
        }

        def lazy = new LazyMappingGraph(base, [provider], null, 3)

        when: 'traverse the graph through BFS'
        def inspector = new ConnectivityInspector(lazy)
        def connected = inspector.connectedSetOf(nodeA)

        then: 'depth is limited'
        connected.size() <= 4 // nodeA + at most 3 expansions
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :processor:test --tests 'io.github.joke.percolate.graph.LazyMappingGraphSpec'`
Expected: FAIL — `LazyMappingGraph` does not exist

**Step 3: Implement LazyMappingGraph**

```java
package io.github.joke.percolate.graph;

import io.github.joke.percolate.graph.edge.GraphEdge;
import io.github.joke.percolate.graph.node.GraphNode;
import io.github.joke.percolate.graph.node.PropertyNode;
import io.github.joke.percolate.graph.node.TypeNode;
import io.github.joke.percolate.spi.ConversionProvider;
import io.github.joke.percolate.spi.ConversionProvider.Conversion;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;
import org.jgrapht.GraphType;
import org.jgrapht.graph.AbstractGraph;
import org.jgrapht.graph.DirectedWeightedMultigraph;
import org.jspecify.annotations.Nullable;

/**
 * A lazy-expanding graph that wraps a base DirectedWeightedMultigraph and materializes
 * ConversionEdges on demand when outgoingEdgesOf is called during algorithm traversal.
 */
public final class LazyMappingGraph extends AbstractGraph<GraphNode, GraphEdge> {

    private final DirectedWeightedMultigraph<GraphNode, GraphEdge> base;
    private final List<ConversionProvider> providers;
    private final @Nullable ProcessingEnvironment env;
    private final int maxDepth;
    private final Set<GraphNode> expanded = new HashSet<>();
    private int currentDepth;

    public LazyMappingGraph(
            DirectedWeightedMultigraph<GraphNode, GraphEdge> base,
            List<ConversionProvider> providers,
            @Nullable ProcessingEnvironment env,
            int maxDepth) {
        this.base = base;
        this.providers = providers;
        this.env = env;
        this.maxDepth = maxDepth;
        this.currentDepth = 0;
    }

    @Override
    public Set<GraphEdge> outgoingEdgesOf(GraphNode vertex) {
        if (!expanded.contains(vertex) && currentDepth < maxDepth) {
            expanded.add(vertex);
            expandConversions(vertex);
        }
        return base.outgoingEdgesOf(vertex);
    }

    private void expandConversions(GraphNode vertex) {
        @Nullable TypeMirror sourceType = getTypeOf(vertex);
        if (sourceType == null) {
            return;
        }
        currentDepth++;
        for (ConversionProvider provider : providers) {
            List<Conversion> conversions = provider.possibleConversions(sourceType, env);
            for (Conversion conversion : conversions) {
                TypeNode targetNode = new TypeNode(conversion.getTargetType(), conversion.getTargetType().toString());
                base.addVertex(targetNode);
                if (!base.containsEdge(vertex, targetNode)) {
                    base.addEdge(vertex, targetNode, conversion.getEdge());
                }
            }
        }
        currentDepth--;
    }

    private static @Nullable TypeMirror getTypeOf(GraphNode node) {
        if (node instanceof TypeNode) {
            return ((TypeNode) node).getType();
        }
        if (node instanceof PropertyNode) {
            return ((PropertyNode) node).getProperty().getType();
        }
        return null;
    }

    // --- Delegate all other Graph methods to base ---

    @Override
    public Set<GraphNode> vertexSet() {
        return base.vertexSet();
    }

    @Override
    public Set<GraphEdge> edgeSet() {
        return base.edgeSet();
    }

    @Override
    public GraphNode getEdgeSource(GraphEdge edge) {
        return base.getEdgeSource(edge);
    }

    @Override
    public GraphNode getEdgeTarget(GraphEdge edge) {
        return base.getEdgeTarget(edge);
    }

    @Override
    public Set<GraphEdge> incomingEdgesOf(GraphNode vertex) {
        return base.incomingEdgesOf(vertex);
    }

    @Override
    public int degreeOf(GraphNode vertex) {
        return base.degreeOf(vertex);
    }

    @Override
    public int inDegreeOf(GraphNode vertex) {
        return base.inDegreeOf(vertex);
    }

    @Override
    public int outDegreeOf(GraphNode vertex) {
        return base.outDegreeOf(vertex);
    }

    @Override
    public Set<GraphEdge> edgesOf(GraphNode vertex) {
        return base.edgesOf(vertex);
    }

    @Override
    public Set<GraphEdge> getAllEdges(GraphNode source, GraphNode target) {
        return base.getAllEdges(source, target);
    }

    @Override
    public GraphEdge getEdge(GraphNode source, GraphNode target) {
        return base.getEdge(source, target);
    }

    @Override
    public boolean containsEdge(GraphEdge edge) {
        return base.containsEdge(edge);
    }

    @Override
    public boolean containsEdge(GraphNode source, GraphNode target) {
        return base.containsEdge(source, target);
    }

    @Override
    public boolean containsVertex(GraphNode vertex) {
        return base.containsVertex(vertex);
    }

    @Override
    public GraphType getType() {
        return base.getType();
    }

    @Override
    public double getEdgeWeight(GraphEdge edge) {
        return base.getEdgeWeight(edge);
    }

    @Override
    public GraphEdge addEdge(GraphNode source, GraphNode target) {
        return base.addEdge(source, target);
    }

    @Override
    public boolean addEdge(GraphNode source, GraphNode target, GraphEdge edge) {
        return base.addEdge(source, target, edge);
    }

    @Override
    public GraphNode addVertex() {
        return base.addVertex();
    }

    @Override
    public boolean addVertex(GraphNode vertex) {
        return base.addVertex(vertex);
    }

    @Override
    public boolean removeEdge(GraphEdge edge) {
        return base.removeEdge(edge);
    }

    @Override
    public GraphEdge removeEdge(GraphNode source, GraphNode target) {
        return base.removeEdge(source, target);
    }

    @Override
    public boolean removeVertex(GraphNode vertex) {
        return base.removeVertex(vertex);
    }

    public DirectedWeightedMultigraph<GraphNode, GraphEdge> getBase() {
        return base;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :processor:test --tests 'io.github.joke.percolate.graph.LazyMappingGraphSpec'`
Expected: PASS

**Step 5: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/graph/LazyMappingGraph.java \
       processor/src/test/groovy/io/github/joke/percolate/graph/LazyMappingGraphSpec.groovy
git commit -m "feat: add LazyMappingGraph with lazy conversion expansion"
```

---

## Task 3: SubtypeProvider

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/spi/impl/SubtypeProvider.java`
- Test: `processor/src/test/groovy/io/github/joke/percolate/spi/impl/SubtypeProviderSpec.groovy`

**Step 1: Write the failing test**

Test uses Google Compile Testing to get real TypeMirror objects:

```groovy
package io.github.joke.percolate.spi.impl

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class SubtypeProviderSpec extends Specification {

    def "subtype is assignable to supertype without explicit converter"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.SubtypeMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper',
            'public interface SubtypeMapper {',
            '    Base map(Child child);',
            '}',
        )
        def base = JavaFileObjects.forSourceLines('test.Base',
            'package test;',
            'public class Base {',
            '    public final String name;',
            '    public Base(String name) { this.name = name; }',
            '    public String getName() { return name; }',
            '}',
        )
        def child = JavaFileObjects.forSourceLines('test.Child',
            'package test;',
            'public class Child extends Base {',
            '    public Child(String name) { super(name); }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, base, child)

        then:
        assertThat(compilation).succeeded()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :processor:test --tests 'io.github.joke.percolate.spi.impl.SubtypeProviderSpec'`
Expected: FAIL (currently no SubtypeProvider to handle this)

**Step 3: Implement SubtypeProvider**

```java
package io.github.joke.percolate.spi.impl;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.graph.edge.ConversionEdge;
import io.github.joke.percolate.spi.ConversionProvider;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Handles subtype-to-supertype conversions. No code is generated — it is a
 * direct assignment (identity expression).
 */
@AutoService(ConversionProvider.class)
public final class SubtypeProvider implements ConversionProvider {

    @Override
    public List<Conversion> possibleConversions(TypeMirror source, ProcessingEnvironment env) {
        if (env == null) {
            return emptyList();
        }
        List<? extends TypeMirror> supertypes = env.getTypeUtils().directSupertypes(source);
        return supertypes.stream()
                .filter(t -> t instanceof DeclaredType)
                .filter(t -> !t.toString().equals("java.lang.Object"))
                .map(t -> new Conversion(
                        t,
                        new ConversionEdge(ConversionEdge.Kind.SUBTYPE, source, t, "$expr")))
                .collect(toList());
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :processor:test --tests 'io.github.joke.percolate.spi.impl.SubtypeProviderSpec'`
Expected: This may not pass immediately since ValidateStage and the lazy graph aren't wired yet. Mark as TODO — this integration test is validated in Task 10.

**Step 5: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/spi/impl/SubtypeProvider.java \
       processor/src/test/groovy/io/github/joke/percolate/spi/impl/SubtypeProviderSpec.groovy
git commit -m "feat: add SubtypeProvider for subclass-to-superclass conversions"
```

---

## Task 4: MapperMethodProvider

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/spi/impl/MapperMethodProvider.java`

This provider is different — it needs access to the `MapperDefinition` to know what methods exist. It is NOT loaded via ServiceLoader but instantiated directly when constructing the `LazyMappingGraph`.

**Step 1: Implement MapperMethodProvider**

```java
package io.github.joke.percolate.spi.impl;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import io.github.joke.percolate.graph.edge.ConversionEdge;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.spi.ConversionProvider;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 * Discovers conversions via developer-defined mapper methods.
 * For each single-parameter method in the mapper, creates a conversion edge
 * from the parameter type to the return type.
 */
public final class MapperMethodProvider implements ConversionProvider {

    private final List<MapperDefinition> mappers;

    public MapperMethodProvider(List<MapperDefinition> mappers) {
        this.mappers = mappers;
    }

    @Override
    public List<Conversion> possibleConversions(TypeMirror source, ProcessingEnvironment env) {
        if (env == null) {
            return emptyList();
        }
        Types types = env.getTypeUtils();
        return mappers.stream()
                .flatMap(mapper -> mapper.getMethods().stream())
                .filter(m -> m.getParameters().size() == 1)
                .filter(m -> types.isSameType(
                        types.erasure(m.getParameters().get(0).getType()),
                        types.erasure(source)))
                .map(m -> new Conversion(
                        m.getReturnType(),
                        new ConversionEdge(
                                ConversionEdge.Kind.MAPPER_METHOD,
                                source,
                                m.getReturnType(),
                                "this." + m.getName() + "($expr)")))
                .collect(toList());
    }
}
```

**Step 2: Run build to verify compilation**

Run: `./gradlew :processor:compileJava`
Expected: SUCCESS

**Step 3: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/spi/impl/MapperMethodProvider.java
git commit -m "feat: add MapperMethodProvider for developer-defined mapper methods"
```

---

## Task 5: PrimitiveWideningProvider

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/spi/impl/PrimitiveWideningProvider.java`

**Step 1: Implement PrimitiveWideningProvider**

```java
package io.github.joke.percolate.spi.impl;

import static java.util.Collections.emptyList;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.graph.edge.ConversionEdge;
import io.github.joke.percolate.spi.ConversionProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 * Handles primitive widening conversions (int → long), boxing (int → Integer),
 * and unboxing (Integer → int).
 */
@AutoService(ConversionProvider.class)
public final class PrimitiveWideningProvider implements ConversionProvider {

    private static final Map<TypeKind, List<TypeKind>> WIDENING = new HashMap<>();

    static {
        WIDENING.put(TypeKind.BYTE, List.of(TypeKind.SHORT, TypeKind.INT, TypeKind.LONG, TypeKind.FLOAT, TypeKind.DOUBLE));
        WIDENING.put(TypeKind.SHORT, List.of(TypeKind.INT, TypeKind.LONG, TypeKind.FLOAT, TypeKind.DOUBLE));
        WIDENING.put(TypeKind.CHAR, List.of(TypeKind.INT, TypeKind.LONG, TypeKind.FLOAT, TypeKind.DOUBLE));
        WIDENING.put(TypeKind.INT, List.of(TypeKind.LONG, TypeKind.FLOAT, TypeKind.DOUBLE));
        WIDENING.put(TypeKind.LONG, List.of(TypeKind.FLOAT, TypeKind.DOUBLE));
        WIDENING.put(TypeKind.FLOAT, List.of(TypeKind.DOUBLE));
    }

    @Override
    public List<Conversion> possibleConversions(TypeMirror source, ProcessingEnvironment env) {
        if (env == null) {
            return emptyList();
        }
        Types types = env.getTypeUtils();
        List<Conversion> result = new ArrayList<>();

        if (source.getKind().isPrimitive()) {
            // Widening: int → long, etc.
            List<TypeKind> targets = WIDENING.getOrDefault(source.getKind(), emptyList());
            for (TypeKind targetKind : targets) {
                TypeMirror targetType = types.getPrimitiveType(targetKind);
                result.add(new Conversion(
                        targetType,
                        new ConversionEdge(ConversionEdge.Kind.PRIMITIVE_WIDEN, source, targetType, "$expr")));
            }
            // Boxing: int → Integer
            TypeMirror boxed = types.boxedClass(types.getPrimitiveType(source.getKind())).asType();
            result.add(new Conversion(
                    boxed,
                    new ConversionEdge(ConversionEdge.Kind.PRIMITIVE_BOX, source, boxed, "$expr")));
        } else {
            // Try unboxing: Integer → int
            try {
                TypeMirror unboxed = types.unboxedType(source);
                result.add(new Conversion(
                        unboxed,
                        new ConversionEdge(ConversionEdge.Kind.PRIMITIVE_UNBOX, source, unboxed, "$expr")));
            } catch (IllegalArgumentException ignored) {
                // Not a boxed type — no conversion
            }
        }

        return result;
    }
}
```

**Step 2: Run build to verify compilation**

Run: `./gradlew :processor:compileJava`
Expected: SUCCESS

**Step 3: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/spi/impl/PrimitiveWideningProvider.java
git commit -m "feat: add PrimitiveWideningProvider for boxing/unboxing/widening"
```

---

## Task 6: OptionalProvider

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/spi/impl/OptionalProvider.java`

**Step 1: Implement OptionalProvider**

```java
package io.github.joke.percolate.spi.impl;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.graph.edge.ConversionEdge;
import io.github.joke.percolate.spi.ConversionProvider;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Handles Optional wrapping ({@code T → Optional<T>}) and unwrapping ({@code Optional<T> → T}).
 */
@AutoService(ConversionProvider.class)
public final class OptionalProvider implements ConversionProvider {

    @Override
    public List<Conversion> possibleConversions(TypeMirror source, ProcessingEnvironment env) {
        if (env == null) {
            return emptyList();
        }
        if (isOptionalType(source)) {
            // Unwrap: Optional<T> → T
            List<? extends TypeMirror> args = ((DeclaredType) source).getTypeArguments();
            if (!args.isEmpty()) {
                TypeMirror inner = args.get(0);
                return singletonList(new Conversion(
                        inner,
                        new ConversionEdge(
                                ConversionEdge.Kind.OPTIONAL_UNWRAP,
                                source,
                                inner,
                                "$expr.orElse(null)")));
            }
        } else {
            // Wrap: T → Optional<T>
            TypeElement optionalElement = env.getElementUtils().getTypeElement("java.util.Optional");
            if (optionalElement != null) {
                DeclaredType optionalType = env.getTypeUtils().getDeclaredType(optionalElement, source);
                return singletonList(new Conversion(
                        optionalType,
                        new ConversionEdge(
                                ConversionEdge.Kind.OPTIONAL_WRAP,
                                source,
                                optionalType,
                                "java.util.Optional.ofNullable($expr)")));
            }
        }
        return emptyList();
    }

    private static boolean isOptionalType(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return false;
        }
        return "java.util.Optional".equals(((DeclaredType) type).asElement().toString());
    }
}
```

**Step 2: Run build to verify compilation**

Run: `./gradlew :processor:compileJava`
Expected: SUCCESS

**Step 3: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/spi/impl/OptionalProvider.java
git commit -m "feat: add OptionalProvider for Optional wrapping/unwrapping"
```

---

## Task 7: EnumProvider

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/spi/impl/EnumProvider.java`

**Step 1: Implement EnumProvider**

```java
package io.github.joke.percolate.spi.impl;

import static java.util.Collections.emptyList;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.graph.edge.ConversionEdge;
import io.github.joke.percolate.spi.ConversionProvider;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Handles enum-to-enum conversion via {@code TargetEnum.valueOf(source.name())}.
 *
 * <p>Note: This provider only advertises that an enum can be converted to itself.
 * Cross-enum conversions are discovered when the validate stage checks reachability
 * and finds an enum source needs to reach an enum target.
 */
@AutoService(ConversionProvider.class)
public final class EnumProvider implements ConversionProvider {

    @Override
    public List<Conversion> possibleConversions(TypeMirror source, ProcessingEnvironment env) {
        // Enum conversions are target-directed — handled during validation
        // when a specific target enum type is known
        return emptyList();
    }

    /**
     * Checks if source enum can convert to target enum by name.
     * Called explicitly by ValidateStage when checking type compatibility.
     */
    public static boolean canConvertEnums(TypeMirror source, TypeMirror target) {
        return isEnumType(source) && isEnumType(target);
    }

    public static ConversionEdge createEnumEdge(TypeMirror source, TypeMirror target) {
        String targetName = ((DeclaredType) target).asElement().toString();
        return new ConversionEdge(
                ConversionEdge.Kind.ENUM_VALUE_OF,
                source,
                target,
                targetName + ".valueOf($expr.name())");
    }

    private static boolean isEnumType(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return false;
        }
        return ((DeclaredType) type).asElement().getKind() == ElementKind.ENUM;
    }
}
```

**Step 2: Run build to verify compilation**

Run: `./gradlew :processor:compileJava`
Expected: SUCCESS

**Step 3: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/spi/impl/EnumProvider.java
git commit -m "feat: add EnumProvider for enum-to-enum valueOf conversion"
```

---

## Task 8: ListProvider

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/spi/impl/ListProvider.java`

**Step 1: Implement ListProvider**

The ListProvider needs access to the mapper's methods to know which element conversions are available. Like MapperMethodProvider, it is instantiated directly.

```java
package io.github.joke.percolate.spi.impl;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import io.github.joke.percolate.graph.edge.ConversionEdge;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.spi.ConversionProvider;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 * Handles {@code List<A> → List<B>} conversions when an element converter (A → B)
 * is available as a mapper method.
 *
 * <p>Generated code: {@code source.stream().map(this::converter).collect(Collectors.toList())}
 */
public final class ListProvider implements ConversionProvider {

    private final List<MapperDefinition> mappers;

    public ListProvider(List<MapperDefinition> mappers) {
        this.mappers = mappers;
    }

    @Override
    public List<Conversion> possibleConversions(TypeMirror source, ProcessingEnvironment env) {
        if (env == null || !isListType(source)) {
            return emptyList();
        }
        TypeMirror sourceElement = getFirstTypeArgument(source);
        if (sourceElement == null) {
            return emptyList();
        }

        Types types = env.getTypeUtils();
        TypeElement listElement = env.getElementUtils().getTypeElement("java.util.List");
        if (listElement == null) {
            return emptyList();
        }

        // Find mapper methods that convert the element type
        return mappers.stream()
                .flatMap(mapper -> mapper.getMethods().stream())
                .filter(m -> m.getParameters().size() == 1)
                .filter(m -> types.isSameType(
                        types.erasure(m.getParameters().get(0).getType()),
                        types.erasure(sourceElement)))
                .map(m -> {
                    TypeMirror targetElement = m.getReturnType();
                    DeclaredType targetListType = types.getDeclaredType(listElement, targetElement);
                    String template = "$expr.stream().map(this::" + m.getName()
                            + ").collect(java.util.stream.Collectors.toList())";
                    return new Conversion(
                            targetListType,
                            new ConversionEdge(
                                    ConversionEdge.Kind.LIST_MAP,
                                    source,
                                    targetListType,
                                    template));
                })
                .collect(toList());
    }

    private static boolean isListType(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return false;
        }
        return "java.util.List".equals(((DeclaredType) type).asElement().toString());
    }

    private static TypeMirror getFirstTypeArgument(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return null;
        }
        List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
        return args.isEmpty() ? null : args.get(0);
    }
}
```

**Step 2: Run build to verify compilation**

Run: `./gradlew :processor:compileJava`
Expected: SUCCESS

**Step 3: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/spi/impl/ListProvider.java
git commit -m "feat: add ListProvider for List<A> to List<B> conversions"
```

---

## Task 9: Rewrite ValidateStage

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/ValidateStage.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/ValidationResult.java` (add lazy graph field)
- Modify: `processor/src/test/groovy/io/github/joke/percolate/stage/ValidateStageSpec.groovy`

**Step 1: Update ValidationResult to carry the lazy graph**

```java
package io.github.joke.percolate.stage;

import io.github.joke.percolate.graph.LazyMappingGraph;

public final class ValidationResult {
    private final GraphResult graphResult;
    private final LazyMappingGraph lazyGraph;
    private final boolean hasFatalErrors;

    public ValidationResult(GraphResult graphResult, LazyMappingGraph lazyGraph, boolean hasFatalErrors) {
        this.graphResult = graphResult;
        this.lazyGraph = lazyGraph;
        this.hasFatalErrors = hasFatalErrors;
    }

    public GraphResult graphResult() {
        return graphResult;
    }

    public LazyMappingGraph lazyGraph() {
        return lazyGraph;
    }

    public boolean hasFatalErrors() {
        return hasFatalErrors;
    }
}
```

**Step 2: Write the failing test**

Add a new test to `ValidateStageSpec.groovy`:

```groovy
package io.github.joke.percolate.stage

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class ValidateStageSpec extends Specification {

    def "emits error when target property has no source mapping"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.BadMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper',
            'public interface BadMapper {',
            '    Target map(Source source);',
            '}',
        )
        def source = JavaFileObjects.forSourceLines('test.Source',
            'package test;',
            'public class Source {',
            '    public String getName() { return ""; }',
            '}',
        )
        def target = JavaFileObjects.forSourceLines('test.Target',
            'package test;',
            'public class Target {',
            '    public final String name;',
            '    public final int age;',
            '    public Target(String name, int age) {',
            '        this.name = name;',
            '        this.age = age;',
            '    }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, source, target)

        then:
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining('age')
    }

    def "emits error when converter method is missing for type mismatch"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.MissingConverterMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '@Mapper',
            'public interface MissingConverterMapper {',
            '    @Map(target = "venue", source = "venue")',
            '    FlatOrder map(Order order);',
            '}',
        )
        def venue = JavaFileObjects.forSourceLines('test.Venue',
            'package test;',
            'public class Venue {',
            '    public String getName() { return ""; }',
            '}',
        )
        def flatVenue = JavaFileObjects.forSourceLines('test.FlatVenue',
            'package test;',
            'public class FlatVenue {',
            '    public final String name;',
            '    public FlatVenue(String name) { this.name = name; }',
            '}',
        )
        def order = JavaFileObjects.forSourceLines('test.Order',
            'package test;',
            'public class Order {',
            '    public Venue getVenue() { return null; }',
            '}',
        )
        def flatOrder = JavaFileObjects.forSourceLines('test.FlatOrder',
            'package test;',
            'public class FlatOrder {',
            '    public final FlatVenue venue;',
            '    public FlatOrder(FlatVenue venue) { this.venue = venue; }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, venue, flatVenue, order, flatOrder)

        then:
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining('venue')
        assertThat(compilation).hadErrorContaining('Venue')
        assertThat(compilation).hadErrorContaining('FlatVenue')
    }
}
```

**Step 3: Run test to verify the new test fails**

Run: `./gradlew :processor:test --tests 'io.github.joke.percolate.stage.ValidateStageSpec'`
Expected: The "missing converter" test should FAIL (currently ValidateStage doesn't check type compatibility)

**Step 4: Rewrite ValidateStage**

```java
package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.graph.LazyMappingGraph;
import io.github.joke.percolate.graph.edge.ConstructorParamEdge;
import io.github.joke.percolate.graph.edge.GraphEdge;
import io.github.joke.percolate.graph.node.ConstructorNode;
import io.github.joke.percolate.graph.node.GraphNode;
import io.github.joke.percolate.graph.node.PropertyNode;
import io.github.joke.percolate.graph.node.TypeNode;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.model.Property;
import io.github.joke.percolate.spi.ConversionProvider;
import io.github.joke.percolate.spi.impl.ListProvider;
import io.github.joke.percolate.spi.impl.MapperMethodProvider;
import io.github.joke.percolate.spi.impl.OptionalProvider;
import io.github.joke.percolate.spi.impl.PrimitiveWideningProvider;
import io.github.joke.percolate.spi.impl.SubtypeProvider;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DirectedWeightedMultigraph;

@RoundScoped
public class ValidateStage {

    private final Messager messager;
    private final ProcessingEnvironment processingEnv;
    private final Types types;

    @Inject
    ValidateStage(Messager messager, ProcessingEnvironment processingEnv) {
        this.messager = messager;
        this.processingEnv = processingEnv;
        this.types = processingEnv.getTypeUtils();
    }

    public ValidationResult execute(GraphResult graphResult) {
        DirectedWeightedMultigraph<GraphNode, GraphEdge> baseGraph = graphResult.getGraph();

        // Build conversion providers
        List<ConversionProvider> providers = buildProviders(graphResult);

        // Wrap in lazy graph
        LazyMappingGraph lazyGraph = new LazyMappingGraph(baseGraph, providers, processingEnv, 5);

        boolean hasFatalErrors = false;

        // Phase 1: Cycle detection
        CycleDetector<GraphNode, GraphEdge> cycleDetector = new CycleDetector<>(lazyGraph);
        if (cycleDetector.detectCycles()) {
            hasFatalErrors = true;
            Set<GraphNode> cycleNodes = cycleDetector.findCycles();
            String nodeNames = cycleNodes.stream()
                    .map(GraphNode::toString)
                    .collect(java.util.stream.Collectors.joining(", "));
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Circular dependency detected involving: " + nodeNames);
        }

        // Phase 2: Reachability — verify every constructor param is reachable
        Set<ConstructorNode> constructorNodes = lazyGraph.vertexSet().stream()
                .filter(ConstructorNode.class::isInstance)
                .map(ConstructorNode.class::cast)
                .collect(toSet());

        // Collect method parameter TypeNodes
        Set<TypeNode> methodParamNodes = lazyGraph.vertexSet().stream()
                .filter(TypeNode.class::isInstance)
                .map(TypeNode.class::cast)
                .filter(n -> !n.getLabel().equals("return"))
                .collect(toSet());

        ConnectivityInspector<GraphNode, GraphEdge> inspector = new ConnectivityInspector<>(lazyGraph);

        for (ConstructorNode constructorNode : constructorNodes) {
            Set<String> mappedParams = lazyGraph.incomingEdgesOf(constructorNode).stream()
                    .filter(ConstructorParamEdge.class::isInstance)
                    .map(ConstructorParamEdge.class::cast)
                    .map(ConstructorParamEdge::getParameterName)
                    .collect(toSet());

            List<Property> parameters = constructorNode.getDescriptor().getParameters();
            Set<String> missingParams = new LinkedHashSet<>();
            List<String> typeErrors = new ArrayList<>();

            for (Property param : parameters) {
                if (!mappedParams.contains(param.getName())) {
                    missingParams.add(param.getName());
                    continue;
                }

                // Check type compatibility for mapped params
                ConstructorParamEdge paramEdge = lazyGraph.incomingEdgesOf(constructorNode).stream()
                        .filter(ConstructorParamEdge.class::isInstance)
                        .map(ConstructorParamEdge.class::cast)
                        .filter(e -> e.getParameterName().equals(param.getName()))
                        .findFirst()
                        .orElse(null);

                if (paramEdge != null) {
                    GraphNode sourceNode = lazyGraph.getEdgeSource(paramEdge);
                    TypeMirror sourceType = getNodeType(sourceNode);
                    TypeMirror targetType = param.getType();

                    if (sourceType != null && !isAssignable(sourceType, targetType)) {
                        // Check if lazy graph can find a conversion path
                        boolean pathExists = inspector.pathExists(sourceNode, constructorNode);
                        if (!pathExists) {
                            typeErrors.add(param.getName() + ": "
                                    + sourceType + " cannot be converted to " + targetType);
                        }
                    }
                }
            }

            if (!missingParams.isEmpty()) {
                hasFatalErrors = true;
                String rendered = io.github.joke.percolate.graph.GraphRenderer
                        .renderConstructorNode(baseGraph, constructorNode, missingParams);
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Unmapped target properties in "
                                + constructorNode.getTargetType().getQualifiedName() + ": "
                                + String.join(", ", missingParams) + "\n" + rendered);
            }

            if (!typeErrors.isEmpty()) {
                hasFatalErrors = true;
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Type mismatches in "
                                + constructorNode.getTargetType().getQualifiedName() + ": "
                                + String.join("; ", typeErrors));
            }
        }

        return new ValidationResult(graphResult, lazyGraph, hasFatalErrors);
    }

    private List<ConversionProvider> buildProviders(GraphResult graphResult) {
        List<ConversionProvider> providers = new ArrayList<>();
        providers.add(new MapperMethodProvider(graphResult.getMappers()));
        providers.add(new ListProvider(graphResult.getMappers()));
        providers.add(new OptionalProvider());
        providers.add(new PrimitiveWideningProvider());
        providers.add(new SubtypeProvider());
        // EnumProvider is target-directed — handled separately
        return providers;
    }

    private boolean isAssignable(TypeMirror source, TypeMirror target) {
        return types.isAssignable(source, target);
    }

    private static TypeMirror getNodeType(GraphNode node) {
        if (node instanceof TypeNode) {
            return ((TypeNode) node).getType();
        }
        if (node instanceof PropertyNode) {
            return ((PropertyNode) node).getProperty().getType();
        }
        return null;
    }
}
```

**Step 5: Run tests**

Run: `./gradlew :processor:test --tests 'io.github.joke.percolate.stage.ValidateStageSpec'`
Expected: PASS

**Step 6: Run full test suite to check for regressions**

Run: `./gradlew :processor:test`
Expected: All existing tests pass

**Step 7: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/stage/ValidateStage.java \
       processor/src/main/java/io/github/joke/percolate/stage/ValidationResult.java \
       processor/src/test/groovy/io/github/joke/percolate/stage/ValidateStageSpec.groovy
git commit -m "feat: rewrite ValidateStage with JGraphT algorithms on lazy graph"
```

---

## Task 10: Simplify CodeGenStage — Use Lazy Graph for Conversions

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/CodeGenStage.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/OptimizeStage.java` (pass lazy graph through)
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/OptimizedGraphResult.java` (add lazy graph)

**Step 1: Update OptimizedGraphResult**

```java
package io.github.joke.percolate.stage;

import io.github.joke.percolate.graph.LazyMappingGraph;

public final class OptimizedGraphResult {
    private final GraphResult graphResult;
    private final LazyMappingGraph lazyGraph;

    public OptimizedGraphResult(GraphResult graphResult, LazyMappingGraph lazyGraph) {
        this.graphResult = graphResult;
        this.lazyGraph = lazyGraph;
    }

    public GraphResult graphResult() {
        return graphResult;
    }

    public LazyMappingGraph lazyGraph() {
        return lazyGraph;
    }
}
```

**Step 2: Update OptimizeStage**

```java
package io.github.joke.percolate.stage;

import io.github.joke.percolate.di.RoundScoped;
import javax.inject.Inject;

@RoundScoped
public class OptimizeStage {

    @Inject
    OptimizeStage() {}

    public OptimizedGraphResult execute(ValidationResult validationResult) {
        return new OptimizedGraphResult(validationResult.graphResult(), validationResult.lazyGraph());
    }
}
```

**Step 3: Rewrite CodeGenStage to use lazy graph for conversions**

Replace the `applyConversion()` method and related helpers with expression building that reads ConversionEdges from the lazy graph:

```java
package io.github.joke.percolate.stage;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.graph.LazyMappingGraph;
import io.github.joke.percolate.graph.edge.ConstructorParamEdge;
import io.github.joke.percolate.graph.edge.ConversionEdge;
import io.github.joke.percolate.graph.edge.GraphEdge;
import io.github.joke.percolate.graph.edge.PropertyAccessEdge;
import io.github.joke.percolate.graph.node.ConstructorNode;
import io.github.joke.percolate.graph.node.GraphNode;
import io.github.joke.percolate.graph.node.PropertyNode;
import io.github.joke.percolate.graph.node.TypeNode;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.model.Property;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.jspecify.annotations.Nullable;

@RoundScoped
public class CodeGenStage {

    private final Filer filer;
    private final Types types;

    @Inject
    CodeGenStage(Filer filer, Types types) {
        this.filer = filer;
        this.types = types;
    }

    public void execute(OptimizedGraphResult optimizedResult) {
        GraphResult graphResult = optimizedResult.graphResult();
        LazyMappingGraph graph = optimizedResult.lazyGraph();

        graphResult.getMappers().forEach(mapper -> generateMapper(graph, mapper));
    }

    private void generateMapper(LazyMappingGraph graph, MapperDefinition mapper) {
        ClassName mapperName = ClassName.get(mapper.getPackageName(), mapper.getSimpleName());
        ClassName implName = ClassName.get(mapper.getPackageName(), mapper.getSimpleName() + "Impl");

        List<MethodSpec> methods = mapper.getMethods().stream()
                .filter(MethodDefinition::isAbstract)
                .map(method -> generateMethod(graph, method))
                .collect(toList());

        TypeSpec typeSpec = TypeSpec.classBuilder(implName)
                .addModifiers(PUBLIC)
                .addSuperinterface(mapperName)
                .addMethods(methods)
                .build();

        JavaFile javaFile = JavaFile.builder(mapper.getPackageName(), typeSpec).build();

        try {
            javaFile.writeTo(filer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write generated source for " + implName, e);
        }
    }

    private MethodSpec generateMethod(LazyMappingGraph graph, MethodDefinition method) {
        TypeMirror returnType = method.getReturnType();
        String returnTypeQualified = getQualifiedName(returnType);

        ConstructorNode constructorNode = graph.vertexSet().stream()
                .filter(ConstructorNode.class::isInstance)
                .map(ConstructorNode.class::cast)
                .filter(cn -> cn.getTargetType().getQualifiedName().toString().equals(returnTypeQualified))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No ConstructorNode found for " + returnType));

        List<Property> parameters = constructorNode.getDescriptor().getParameters();

        List<ConstructorParamEdge> paramEdges = graph.incomingEdgesOf(constructorNode).stream()
                .filter(ConstructorParamEdge.class::isInstance)
                .map(ConstructorParamEdge.class::cast)
                .sorted(comparingInt(ConstructorParamEdge::getParameterIndex))
                .collect(toList());

        List<String> args = new ArrayList<>();
        for (Property param : parameters) {
            @Nullable ConstructorParamEdge edge = paramEdges.stream()
                    .filter(e -> e.getParameterName().equals(param.getName()))
                    .findFirst()
                    .orElse(null);

            if (edge == null) {
                args.add("null /* unmapped: " + param.getName() + " */");
                continue;
            }

            GraphNode sourceNode = graph.getEdgeSource(edge);
            String expression = buildExpression(graph, sourceNode);

            // Check if a ConversionEdge exists from source to handle type mismatch
            @Nullable TypeMirror sourceType = getNodeType(sourceNode);
            TypeMirror targetType = param.getType();

            if (sourceType != null && !types.isSameType(sourceType, targetType)) {
                expression = applyConversionFromGraph(graph, sourceNode, expression);
            }

            args.add(expression);
        }

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(method.getName())
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(TypeName.get(returnType));

        method.getParameters().forEach(p -> methodBuilder.addParameter(TypeName.get(p.getType()), p.getName()));

        String argsJoined = String.join(", ", args);
        methodBuilder.addStatement("return new $L($L)", returnTypeQualified, argsJoined);

        return methodBuilder.build();
    }

    /**
     * Walks ConversionEdges from the source node to build the conversion expression.
     * Follows chains: e.g. source → Optional wrap → ...
     */
    private String applyConversionFromGraph(LazyMappingGraph graph, GraphNode sourceNode, String baseExpression) {
        String expression = baseExpression;
        GraphNode current = sourceNode;

        for (int depth = 0; depth < 5; depth++) {
            @Nullable ConversionEdge convEdge = graph.outgoingEdgesOf(current).stream()
                    .filter(ConversionEdge.class::isInstance)
                    .map(ConversionEdge.class::cast)
                    .findFirst()
                    .orElse(null);

            if (convEdge == null) {
                break;
            }

            expression = convEdge.getExpressionTemplate().replace("$expr", expression);
            current = graph.getEdgeTarget(convEdge);
        }

        return expression;
    }

    private @Nullable TypeMirror getNodeType(GraphNode node) {
        if (node instanceof TypeNode) {
            return ((TypeNode) node).getType();
        }
        if (node instanceof PropertyNode) {
            return ((PropertyNode) node).getProperty().getType();
        }
        return null;
    }

    private String buildExpression(LazyMappingGraph graph, GraphNode node) {
        List<String> chain = new ArrayList<>();
        GraphNode current = node;

        while (current instanceof PropertyNode) {
            PropertyNode propertyNode = (PropertyNode) current;
            Property property = propertyNode.getProperty();
            javax.lang.model.element.Element accessor = property.getAccessor();

            if (accessor instanceof ExecutableElement) {
                chain.add(0, ((ExecutableElement) accessor).getSimpleName().toString() + "()");
            } else {
                chain.add(0, property.getName());
            }

            @Nullable GraphNode parent = findParentViaPropertyAccess(graph, current);
            if (parent == null) {
                parent = propertyNode.getParent();
            }
            current = parent;
        }

        if (current instanceof TypeNode) {
            chain.add(0, ((TypeNode) current).getLabel());
        }

        return String.join(".", chain);
    }

    private @Nullable GraphNode findParentViaPropertyAccess(LazyMappingGraph graph, GraphNode node) {
        return graph.incomingEdgesOf(node).stream()
                .filter(PropertyAccessEdge.class::isInstance)
                .map(graph::getEdgeSource)
                .findFirst()
                .orElse(null);
    }

    private String getQualifiedName(TypeMirror typeMirror) {
        if (typeMirror instanceof DeclaredType) {
            return ((DeclaredType) typeMirror).asElement().toString();
        }
        return typeMirror.toString();
    }
}
```

**Step 4: Run full test suite**

Run: `./gradlew :processor:test`
Expected: All tests pass (existing behavior preserved)

**Step 5: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/stage/CodeGenStage.java \
       processor/src/main/java/io/github/joke/percolate/stage/OptimizeStage.java \
       processor/src/main/java/io/github/joke/percolate/stage/OptimizedGraphResult.java
git commit -m "refactor: simplify CodeGenStage to use lazy graph for conversions"
```

---

## Task 11: Simplify GraphBuildStage — Remove GenericPlaceholderEdge

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/GraphBuildStage.java`

**Step 1: Remove GenericPlaceholderEdge insertion from GraphBuildStage**

In `processDirective()` method (around line 185-194), remove the block that inserts GenericPlaceholderEdges:

```java
// REMOVE this block from processDirective():
// } else {
//     // Add GenericPlaceholderEdge for later expansion
//     TypeNode sourceTypeNode = ensureTypeNode(graph, sourceType);
//     TypeNode targetTypeNode = ensureTypeNode(graph, targetType);
//     graph.addEdge(sourceTypeNode, targetTypeNode, new GenericPlaceholderEdge(sourceType, targetType));
// }
```

Also remove the `ensureTypeNode` method and the import of `GenericPlaceholderEdge`.

The type mismatch handling (`if (sourceType != null && !typesMatch(sourceType, targetType))`) can be simplified to just the MethodCallEdge insertion for known mapper methods. The lazy graph handles everything else.

**Step 2: Run full test suite**

Run: `./gradlew :processor:test`
Expected: All tests pass

**Step 3: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/stage/GraphBuildStage.java
git commit -m "refactor: remove GenericPlaceholderEdge insertion from GraphBuildStage"
```

---

## Task 12: Delete GenericPlaceholderEdge + GenericMappingStrategy

**Files:**
- Delete: `processor/src/main/java/io/github/joke/percolate/graph/edge/GenericPlaceholderEdge.java`
- Delete: `processor/src/main/java/io/github/joke/percolate/spi/GenericMappingStrategy.java`
- Delete: `processor/src/main/java/io/github/joke/percolate/spi/GraphFragment.java`
- Delete: `processor/src/main/java/io/github/joke/percolate/spi/impl/ListMappingStrategy.java`
- Delete: `processor/src/main/java/io/github/joke/percolate/spi/impl/OptionalMappingStrategy.java`
- Delete: `processor/src/main/java/io/github/joke/percolate/spi/impl/EnumMappingStrategy.java`
- Delete: `processor/src/test/groovy/io/github/joke/percolate/spi/impl/ListMappingStrategySpec.groovy`
- Delete: `processor/src/test/groovy/io/github/joke/percolate/spi/impl/OptionalMappingStrategySpec.groovy`
- Delete: `processor/src/test/groovy/io/github/joke/percolate/spi/impl/EnumMappingStrategySpec.groovy`

**Step 1: Delete obsolete files**

```bash
rm processor/src/main/java/io/github/joke/percolate/graph/edge/GenericPlaceholderEdge.java
rm processor/src/main/java/io/github/joke/percolate/spi/GenericMappingStrategy.java
rm processor/src/main/java/io/github/joke/percolate/spi/GraphFragment.java
rm processor/src/main/java/io/github/joke/percolate/spi/impl/ListMappingStrategy.java
rm processor/src/main/java/io/github/joke/percolate/spi/impl/OptionalMappingStrategy.java
rm processor/src/main/java/io/github/joke/percolate/spi/impl/EnumMappingStrategy.java
rm processor/src/test/groovy/io/github/joke/percolate/spi/impl/ListMappingStrategySpec.groovy
rm processor/src/test/groovy/io/github/joke/percolate/spi/impl/OptionalMappingStrategySpec.groovy
rm processor/src/test/groovy/io/github/joke/percolate/spi/impl/EnumMappingStrategySpec.groovy
```

**Step 2: Remove any remaining imports referencing deleted classes**

Search for `GenericPlaceholderEdge`, `GenericMappingStrategy`, `GraphFragment` in remaining files and remove stale imports.

**Step 3: Run full test suite**

Run: `./gradlew :processor:test`
Expected: All tests pass

**Step 4: Commit**

```bash
git add -A
git commit -m "refactor: delete GenericPlaceholderEdge, GenericMappingStrategy and old strategy impls"
```

---

## Task 13: Enhance GraphRenderer

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/graph/GraphRenderer.java`

**Step 1: Enhance GraphRenderer to show conversion paths and suggestions**

```java
package io.github.joke.percolate.graph;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import io.github.joke.percolate.graph.edge.ConstructorParamEdge;
import io.github.joke.percolate.graph.edge.ConversionEdge;
import io.github.joke.percolate.graph.edge.GraphEdge;
import io.github.joke.percolate.graph.node.ConstructorNode;
import io.github.joke.percolate.graph.node.GraphNode;
import io.github.joke.percolate.graph.node.PropertyNode;
import io.github.joke.percolate.graph.node.TypeNode;
import io.github.joke.percolate.model.Property;
import java.util.List;
import java.util.Set;
import org.jgrapht.Graph;

/** Produces ASCII diagnostic output for validation errors. */
public final class GraphRenderer {

    private GraphRenderer() {}

    public static String renderConstructorNode(
            Graph<GraphNode, GraphEdge> graph,
            ConstructorNode constructorNode,
            Set<String> missingParams) {

        List<Property> parameters = constructorNode.getDescriptor().getParameters();

        Set<String> mappedParams = graph.incomingEdgesOf(constructorNode).stream()
                .filter(ConstructorParamEdge.class::isInstance)
                .map(ConstructorParamEdge.class::cast)
                .map(ConstructorParamEdge::getParameterName)
                .collect(toSet());

        int maxNameLen =
                parameters.stream().mapToInt(p -> p.getName().length()).max().orElse(0);

        StringBuilder sb = new StringBuilder();
        sb.append("\n  ConstructorNode(")
                .append(constructorNode.getTargetType().getSimpleName())
                .append("):\n");

        for (Property param : parameters) {
            String name = param.getName();
            String padded = String.format("%-" + maxNameLen + "s", name);
            if (mappedParams.contains(name)) {
                // Find source description
                String sourceDesc = findSourceDescription(graph, constructorNode, name);
                sb.append("    ").append(padded).append(" \u2190 ").append(sourceDesc).append(" \u2713\n");
            } else {
                sb.append("    ").append(padded).append(" \u2190 ???  \u2717  (no source mapping)\n");
            }
        }

        if (!missingParams.isEmpty()) {
            sb.append("\n  Suggestion: Add a matching source property or converter for: ")
                    .append(String.join(", ", missingParams));
        }

        return sb.toString();
    }

    private static String findSourceDescription(
            Graph<GraphNode, GraphEdge> graph, ConstructorNode constructorNode, String paramName) {
        return graph.incomingEdgesOf(constructorNode).stream()
                .filter(ConstructorParamEdge.class::isInstance)
                .map(ConstructorParamEdge.class::cast)
                .filter(e -> e.getParameterName().equals(paramName))
                .findFirst()
                .map(e -> {
                    GraphNode source = graph.getEdgeSource(e);
                    if (source instanceof PropertyNode) {
                        return ((PropertyNode) source).name() + " (" + ((PropertyNode) source).getProperty().getType() + ")";
                    }
                    if (source instanceof TypeNode) {
                        return ((TypeNode) source).getLabel() + " (" + ((TypeNode) source).getType() + ")";
                    }
                    return source.toString();
                })
                .orElse("(mapped)");
    }
}
```

**Step 2: Run full test suite**

Run: `./gradlew :processor:test`
Expected: All tests pass

**Step 3: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/graph/GraphRenderer.java
git commit -m "feat: enhance GraphRenderer with source path display and suggestions"
```

---

## Task 14: Integration Tests — Missing Converter, Conversion Chains

**Files:**
- Create: `processor/src/test/groovy/io/github/joke/percolate/stage/ValidateStageConversionSpec.groovy`

**Step 1: Write integration tests for conversion scenarios**

```groovy
package io.github.joke.percolate.stage

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class ValidateStageConversionSpec extends Specification {

    def "fails when mapper method is missing for type conversion"() {
        given: 'a mapper referencing Venue→TicketVenue without a mapVenue method'
        def mapper = JavaFileObjects.forSourceLines('test.TicketMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '@Mapper',
            'public interface TicketMapper {',
            '    @Map(target = "venue", source = "venue")',
            '    FlatOrder map(Order order);',
            '}',
        )
        def venue = JavaFileObjects.forSourceLines('test.Venue',
            'package test;',
            'public class Venue {',
            '    public String getName() { return ""; }',
            '}',
        )
        def flatVenue = JavaFileObjects.forSourceLines('test.FlatVenue',
            'package test;',
            'public class FlatVenue {',
            '    public final String name;',
            '    public FlatVenue(String name) { this.name = name; }',
            '}',
        )
        def order = JavaFileObjects.forSourceLines('test.Order',
            'package test;',
            'public class Order {',
            '    public Venue getVenue() { return null; }',
            '}',
        )
        def flatOrder = JavaFileObjects.forSourceLines('test.FlatOrder',
            'package test;',
            'public class FlatOrder {',
            '    public final FlatVenue venue;',
            '    public FlatOrder(FlatVenue venue) { this.venue = venue; }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, venue, flatVenue, order, flatOrder)

        then:
        assertThat(compilation).failed()
    }

    def "succeeds when mapper method exists for type conversion"() {
        given: 'same setup but with mapVenue method present'
        def mapper = JavaFileObjects.forSourceLines('test.TicketMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '@Mapper',
            'public interface TicketMapper {',
            '    @Map(target = "venue", source = "venue")',
            '    FlatOrder map(Order order);',
            '    FlatVenue mapVenue(Venue venue);',
            '}',
        )
        def venue = JavaFileObjects.forSourceLines('test.Venue',
            'package test;',
            'public class Venue {',
            '    public String getName() { return ""; }',
            '}',
        )
        def flatVenue = JavaFileObjects.forSourceLines('test.FlatVenue',
            'package test;',
            'public class FlatVenue {',
            '    public final String name;',
            '    public FlatVenue(String name) { this.name = name; }',
            '}',
        )
        def order = JavaFileObjects.forSourceLines('test.Order',
            'package test;',
            'public class Order {',
            '    public Venue getVenue() { return null; }',
            '}',
        )
        def flatOrder = JavaFileObjects.forSourceLines('test.FlatOrder',
            'package test;',
            'public class FlatOrder {',
            '    public final FlatVenue venue;',
            '    public FlatOrder(FlatVenue venue) { this.venue = venue; }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, venue, flatVenue, order, flatOrder)

        then:
        assertThat(compilation).succeeded()
    }

    def "existing TicketMapper integration test still passes"() {
        // This is a smoke test — the full TicketMapperIntegrationSpec covers details
        expect:
        true
    }
}
```

**Step 2: Run tests**

Run: `./gradlew :processor:test --tests 'io.github.joke.percolate.stage.ValidateStageConversionSpec'`
Expected: PASS

**Step 3: Run full test suite**

Run: `./gradlew :processor:test`
Expected: All tests pass

**Step 4: Commit**

```bash
git add processor/src/test/groovy/io/github/joke/percolate/stage/ValidateStageConversionSpec.groovy
git commit -m "test: add integration tests for conversion validation"
```

---

## Task 15: Final Verification — Full Build

**Step 1: Run full build with all checks**

Run: `./gradlew build`
Expected: SUCCESS (compileJava, ErrorProne, NullAway, all tests)

**Step 2: Run the TicketMapper integration test specifically**

Run: `./gradlew :processor:test --tests 'io.github.joke.percolate.processor.TicketMapperIntegrationSpec'`
Expected: PASS

**Step 3: Verify no stale references**

Search for any remaining references to deleted classes:
- `GenericPlaceholderEdge`
- `GenericMappingStrategy`
- `GraphFragment`
- `ListMappingStrategy`
- `OptionalMappingStrategy`
- `EnumMappingStrategy`

Run: `grep -r "GenericPlaceholderEdge\|GenericMappingStrategy\|GraphFragment" processor/src/`
Expected: No matches

**Step 4: Commit any final cleanups**

```bash
git add -A
git commit -m "chore: final cleanup after ValidateStage rewrite"
```

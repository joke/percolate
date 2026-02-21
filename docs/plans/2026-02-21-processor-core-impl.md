# Percolate Processor Core Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement the Percolate annotation processor — a Dagger-powered 4-stage pipeline (Analysis → Validation → Graph → CodeGen) that reads `@Mapper` interfaces and generates mapper implementation classes.

**Architecture:** Each stage produces an immutable output consumed by the next. Property discovery and type mapping strategies are loaded via `ServiceLoader` (SPI) and injected by Dagger. JGraphT drives all graph operations. Built-in strategies self-register via `@AutoService`.

**Tech Stack:** Java 11, Dagger 2.59, JavaPoet, JGraphT 1.5.2, Spock 2.4/Groovy 5, Google Compile Testing 0.23, AutoService 1.1.1, JSpecify/NullAway.

---

## Phase 1: Foundation

### Task 1: Add JGraphT to BOM and processor

**Files:**
- Modify: `dependencies/build.gradle`
- Modify: `processor/build.gradle`
- Modify: `test-mapper/build.gradle`

**Step 1: Update BOM**

In `dependencies/build.gradle`, add inside the `constraints` block:
```groovy
api 'org.jgrapht:jgrapht-core:1.5.2'
```

**Step 2: Add to processor**

In `processor/build.gradle`, add to `dependencies`:
```groovy
implementation 'org.jgrapht:jgrapht-core'
```

**Step 3: Remove hardcoded version from test-mapper**

In `test-mapper/build.gradle`, replace:
```groovy
implementation 'org.jgrapht:jgrapht-core:1.5.2'
```
with:
```groovy
implementation 'org.jgrapht:jgrapht-core'
```
And add at the top of `dependencies`:
```groovy
implementation platform(project(':dependencies'))
```
(The `implementation platform(project(':dependencies'))` line already exists — verify it appears before the jgrapht line.)

**Step 4: Verify build**
```bash
./gradlew :processor:compileJava :test-mapper:compileJava
```
Expected: BUILD SUCCESSFUL

**Step 5: Commit**
```bash
git add dependencies/build.gradle processor/build.gradle test-mapper/build.gradle
git commit -m "chore: add jgrapht to BOM and processor dependencies"
```

---

### Task 2: Model and strategy interfaces

Create all data-carrier classes and strategy interfaces. No logic yet — these are the shared vocabulary of the pipeline.

**Files to create:**
- `processor/src/main/java/io/github/joke/caffeinate/analysis/property/Property.java`
- `processor/src/main/java/io/github/joke/caffeinate/analysis/property/PropertyDiscoveryStrategy.java`
- `processor/src/main/java/io/github/joke/caffeinate/analysis/MapAnnotation.java`
- `processor/src/main/java/io/github/joke/caffeinate/analysis/MappingMethod.java`
- `processor/src/main/java/io/github/joke/caffeinate/analysis/MapperDescriptor.java`
- `processor/src/main/java/io/github/joke/caffeinate/analysis/AnalysisResult.java`
- `processor/src/main/java/io/github/joke/caffeinate/validation/ValidationResult.java`
- `processor/src/main/java/io/github/joke/caffeinate/graph/MethodEdge.java`
- `processor/src/main/java/io/github/joke/caffeinate/graph/GraphResult.java`
- `processor/src/main/java/io/github/joke/caffeinate/codegen/strategy/TypeMappingStrategy.java`

**Step 1: Write the classes**

`Property.java`:
```java
package io.github.joke.caffeinate.analysis.property;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

public final class Property {
    private final String name;
    private final TypeMirror type;
    private final Element accessor;

    public Property(String name, TypeMirror type, Element accessor) {
        this.name = name;
        this.type = type;
        this.accessor = accessor;
    }

    public String getName() { return name; }
    public TypeMirror getType() { return type; }
    public Element getAccessor() { return accessor; }
}
```

`PropertyDiscoveryStrategy.java`:
```java
package io.github.joke.caffeinate.analysis.property;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import java.util.List;

public interface PropertyDiscoveryStrategy {
    List<Property> discover(TypeElement type, ProcessingEnvironment env);
}
```

`MapAnnotation.java`:
```java
package io.github.joke.caffeinate.analysis;

public final class MapAnnotation {
    private final String target;
    private final String source;

    public MapAnnotation(String target, String source) {
        this.target = target;
        this.source = source;
    }

    public String getTarget() { return target; }
    public String getSource() { return source; }
}
```

`MappingMethod.java`:
```java
package io.github.joke.caffeinate.analysis;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.List;

public final class MappingMethod {
    private final ExecutableElement method;
    private final TypeElement targetType;
    private final List<? extends VariableElement> parameters;
    private final List<MapAnnotation> mapAnnotations;
    private final List<ExecutableElement> converterCandidates;

    public MappingMethod(
            ExecutableElement method,
            TypeElement targetType,
            List<? extends VariableElement> parameters,
            List<MapAnnotation> mapAnnotations,
            List<ExecutableElement> converterCandidates) {
        this.method = method;
        this.targetType = targetType;
        this.parameters = parameters;
        this.mapAnnotations = mapAnnotations;
        this.converterCandidates = converterCandidates;
    }

    public ExecutableElement getMethod() { return method; }
    public TypeElement getTargetType() { return targetType; }
    public List<? extends VariableElement> getParameters() { return parameters; }
    public List<MapAnnotation> getMapAnnotations() { return mapAnnotations; }
    public List<ExecutableElement> getConverterCandidates() { return converterCandidates; }
}
```

`MapperDescriptor.java`:
```java
package io.github.joke.caffeinate.analysis;

import javax.lang.model.element.TypeElement;
import java.util.List;

public final class MapperDescriptor {
    private final TypeElement mapperInterface;
    private final List<MappingMethod> methods;

    public MapperDescriptor(TypeElement mapperInterface, List<MappingMethod> methods) {
        this.mapperInterface = mapperInterface;
        this.methods = methods;
    }

    public TypeElement getMapperInterface() { return mapperInterface; }
    public List<MappingMethod> getMethods() { return methods; }
}
```

`AnalysisResult.java`:
```java
package io.github.joke.caffeinate.analysis;

import java.util.List;

public final class AnalysisResult {
    private final List<MapperDescriptor> mappers;

    public AnalysisResult(List<MapperDescriptor> mappers) {
        this.mappers = List.copyOf(mappers);
    }

    public List<MapperDescriptor> getMappers() { return mappers; }
}
```

`ValidationResult.java` — wraps `AnalysisResult` as a marker that validation passed:
```java
package io.github.joke.caffeinate.validation;

import io.github.joke.caffeinate.analysis.AnalysisResult;
import io.github.joke.caffeinate.analysis.MapperDescriptor;
import java.util.List;

public final class ValidationResult {
    private final AnalysisResult analysisResult;
    private final boolean hasFatalErrors;

    public ValidationResult(AnalysisResult analysisResult, boolean hasFatalErrors) {
        this.analysisResult = analysisResult;
        this.hasFatalErrors = hasFatalErrors;
    }

    public List<MapperDescriptor> getMappers() { return analysisResult.getMappers(); }
    public boolean hasFatalErrors() { return hasFatalErrors; }
}
```

`MethodEdge.java` (JGraphT edge label):
```java
package io.github.joke.caffeinate.graph;

import io.github.joke.caffeinate.analysis.MappingMethod;

public final class MethodEdge {
    private final MappingMethod method;

    public MethodEdge(MappingMethod method) {
        this.method = method;
    }

    public MappingMethod getMethod() { return method; }
}
```

`GraphResult.java`:
```java
package io.github.joke.caffeinate.graph;

import io.github.joke.caffeinate.analysis.MappingMethod;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.graph.DefaultEdge;

import javax.lang.model.type.TypeMirror;
import java.util.Optional;

public final class GraphResult {
    // type-resolution graph: which method converts source → target type
    private final DefaultDirectedGraph<TypeMirror, MethodEdge> typeGraph;
    // method-ordering graph: call dependencies between mapper methods
    private final DirectedAcyclicGraph<MappingMethod, DefaultEdge> methodGraph;

    public GraphResult(
            DefaultDirectedGraph<TypeMirror, MethodEdge> typeGraph,
            DirectedAcyclicGraph<MappingMethod, DefaultEdge> methodGraph) {
        this.typeGraph = typeGraph;
        this.methodGraph = methodGraph;
    }

    public Optional<MappingMethod> resolverFor(TypeMirror source, TypeMirror target) {
        return typeGraph.getAllEdges(source, target).stream()
                .map(MethodEdge::getMethod)
                .findFirst();
    }

    public DefaultDirectedGraph<TypeMirror, MethodEdge> getTypeGraph() { return typeGraph; }
    public DirectedAcyclicGraph<MappingMethod, DefaultEdge> getMethodGraph() { return methodGraph; }
}
```

`TypeMappingStrategy.java`:
```java
package io.github.joke.caffeinate.codegen.strategy;

import com.palantir.javapoet.CodeBlock;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;

public interface TypeMappingStrategy {
    boolean supports(TypeMirror source, TypeMirror target, ProcessingEnvironment env);
    CodeBlock generate(String sourceExpr, TypeMirror source, TypeMirror target, ProcessingEnvironment env);
}
```

**Step 2: Verify compilation**
```bash
./gradlew :processor:compileJava
```
Expected: BUILD SUCCESSFUL

**Step 3: Commit**
```bash
git add processor/src/main/java/
git commit -m "feat: add pipeline model classes and strategy interfaces"
```

---

### Task 3: GetterPropertyStrategy

Discovers properties from `getX()` / `isX()` methods on a type.

**Files:**
- Create: `processor/src/main/java/io/github/joke/caffeinate/analysis/property/GetterPropertyStrategy.java`
- Create: `processor/src/main/java/io/github/joke/caffeinate/analysis/property/PropertyMerger.java`
- Create: `processor/src/main/java/META-INF/services/io.github.joke.caffeinate.analysis.property.PropertyDiscoveryStrategy`
- Create: `processor/src/test/groovy/io/github/joke/caffeinate/analysis/property/GetterPropertyStrategySpec.groovy`

**Step 1: Write the failing test**

`GetterPropertyStrategySpec.groovy`:
```groovy
package io.github.joke.caffeinate.analysis.property

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.caffeinate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class GetterPropertyStrategySpec extends Specification {

    // A simple mapper that maps a source with getters to an identical target.
    // If property discovery fails, the compilation will produce an error.
    def "compiles cleanly when source has only getter-based properties"() {
        given:
        def personSrc = JavaFileObjects.forSourceLines("io.example.Person",
            "package io.example;",
            "public class Person {",
            "    private final String name;",
            "    public Person(String name) { this.name = name; }",
            "    public String getName() { return name; }",
            "}")
        def mapperSrc = JavaFileObjects.forSourceLines("io.example.PersonMapper",
            "package io.example;",
            "import io.github.joke.caffeinate.Mapper;",
            "@Mapper",
            "public interface PersonMapper {",
            "    Person map(Person source);",
            "}")

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(personSrc, mapperSrc)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation).generatedSourceFile("io.example.PersonMapperImpl")
    }
}
```

**Step 2: Run to verify failure**
```bash
./gradlew :processor:test --tests 'GetterPropertyStrategySpec'
```
Expected: FAIL — `PercolateProcessor` class not found (not yet implemented).

**Step 3: Implement GetterPropertyStrategy**

`GetterPropertyStrategy.java`:
```java
package io.github.joke.caffeinate.analysis.property;

import com.google.auto.service.AutoService;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import java.util.ArrayList;
import java.util.List;

@AutoService(PropertyDiscoveryStrategy.class)
public class GetterPropertyStrategy implements PropertyDiscoveryStrategy {

    @Override
    public List<Property> discover(TypeElement type, ProcessingEnvironment env) {
        List<Property> properties = new ArrayList<>();
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.METHOD) continue;
            ExecutableElement method = (ExecutableElement) enclosed;
            if (!method.getParameters().isEmpty()) continue;
            if (method.getReturnType().getKind() == TypeKind.VOID) continue;
            String methodName = method.getSimpleName().toString();
            String propertyName = extractPropertyName(methodName, method);
            if (propertyName == null) continue;
            properties.add(new Property(propertyName, method.getReturnType(), method));
        }
        return properties;
    }

    private String extractPropertyName(String methodName, ExecutableElement method) {
        if (methodName.startsWith("get") && methodName.length() > 3
                && !methodName.equals("getClass")) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        }
        if (methodName.startsWith("is") && methodName.length() > 2
                && method.getReturnType().getKind() == TypeKind.BOOLEAN) {
            return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        }
        return null;
    }
}
```

`PropertyMerger.java` — merges results from all strategies, getter wins over field:
```java
package io.github.joke.caffeinate.analysis.property;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PropertyMerger {

    private PropertyMerger() {}

    public static List<Property> merge(
            Set<PropertyDiscoveryStrategy> strategies,
            TypeElement type,
            ProcessingEnvironment env) {
        Map<String, Property> byName = new LinkedHashMap<>();
        // Fields first (lower priority)
        for (PropertyDiscoveryStrategy strategy : strategies) {
            for (Property p : strategy.discover(type, env)) {
                if (p.getAccessor().getKind() == ElementKind.FIELD) {
                    byName.putIfAbsent(p.getName(), p);
                }
            }
        }
        // Getters second — overwrite fields of same name
        for (PropertyDiscoveryStrategy strategy : strategies) {
            for (Property p : strategy.discover(type, env)) {
                if (p.getAccessor().getKind() == ElementKind.METHOD) {
                    byName.put(p.getName(), p);
                }
            }
        }
        return new ArrayList<>(byName.values());
    }
}
```

**Step 4: Run (will still fail — PercolateProcessor not yet wired)**

The test cannot pass yet. Continue to Tasks 4–9 to build the full pipeline before running this test.

**Step 5: Commit**
```bash
git add processor/src/main/java/io/github/joke/caffeinate/analysis/property/ \
        processor/src/test/groovy/io/github/joke/caffeinate/analysis/property/
git commit -m "feat: add GetterPropertyStrategy and PropertyMerger"
```

---

### Task 4: FieldPropertyStrategy

**Files:**
- Create: `processor/src/main/java/io/github/joke/caffeinate/analysis/property/FieldPropertyStrategy.java`

**Step 1: Implement**

`FieldPropertyStrategy.java`:
```java
package io.github.joke.caffeinate.analysis.property;

import com.google.auto.service.AutoService;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.List;

@AutoService(PropertyDiscoveryStrategy.class)
public class FieldPropertyStrategy implements PropertyDiscoveryStrategy {

    @Override
    public List<Property> discover(TypeElement type, ProcessingEnvironment env) {
        List<Property> properties = new ArrayList<>();
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) continue;
            VariableElement field = (VariableElement) enclosed;
            if (field.getModifiers().contains(Modifier.STATIC)) continue;
            properties.add(new Property(field.getSimpleName().toString(), field.asType(), field));
        }
        return properties;
    }
}
```

**Step 2: Commit**
```bash
git add processor/src/main/java/io/github/joke/caffeinate/analysis/property/FieldPropertyStrategy.java
git commit -m "feat: add FieldPropertyStrategy"
```

---

## Phase 2: Pipeline Stages

### Task 5: AnalysisStage

Reads all `@Mapper` interfaces from the round and builds an `AnalysisResult`.

**Files:**
- Create: `processor/src/main/java/io/github/joke/caffeinate/analysis/AnalysisStage.java`

**Step 1: Implement**

`AnalysisStage.java`:
```java
package io.github.joke.caffeinate.analysis;

import io.github.joke.caffeinate.analysis.property.Property;
import io.github.joke.caffeinate.analysis.property.PropertyDiscoveryStrategy;
import io.github.joke.caffeinate.analysis.property.PropertyMerger;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AnalysisStage {

    private final ProcessingEnvironment env;
    private final Set<PropertyDiscoveryStrategy> strategies;

    @Inject
    public AnalysisStage(ProcessingEnvironment env, Set<PropertyDiscoveryStrategy> strategies) {
        this.env = env;
        this.strategies = strategies;
    }

    public AnalysisResult analyze(RoundEnvironment roundEnv, Set<? extends Element> mapperElements) {
        List<MapperDescriptor> descriptors = new ArrayList<>();
        for (Element element : mapperElements) {
            if (element.getKind() != ElementKind.INTERFACE) continue;
            TypeElement mapperInterface = (TypeElement) element;
            descriptors.add(analyzeMapper(mapperInterface));
        }
        return new AnalysisResult(descriptors);
    }

    private MapperDescriptor analyzeMapper(TypeElement mapperInterface) {
        List<ExecutableElement> allMethods = allMethods(mapperInterface);
        List<ExecutableElement> converterCandidates = collectConverterCandidates(allMethods);
        List<MappingMethod> mappingMethods = new ArrayList<>();

        for (ExecutableElement method : allMethods) {
            if (!isAbstract(method)) continue; // skip default methods
            TypeElement targetType = resolveTypeElement(method.getReturnType());
            if (targetType == null) continue;
            List<MapAnnotation> mapAnnotations = extractMapAnnotations(method);
            mappingMethods.add(new MappingMethod(
                    method, targetType, method.getParameters(), mapAnnotations, converterCandidates));
        }
        return new MapperDescriptor(mapperInterface, mappingMethods);
    }

    /** All executable elements (abstract + default) on the interface. */
    private List<ExecutableElement> allMethods(TypeElement iface) {
        List<ExecutableElement> methods = new ArrayList<>();
        for (Element enclosed : iface.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD) {
                methods.add((ExecutableElement) enclosed);
            }
        }
        return methods;
    }

    /**
     * Converter candidates are all methods on the interface that can be used
     * by the code generator to convert between types: both abstract (to be
     * generated) and default (user-supplied).
     */
    private List<ExecutableElement> collectConverterCandidates(List<ExecutableElement> methods) {
        return new ArrayList<>(methods); // all methods are potential converters
    }

    private boolean isAbstract(ExecutableElement method) {
        return method.getModifiers().contains(Modifier.ABSTRACT);
    }

    private TypeElement resolveTypeElement(TypeMirror type) {
        if (!(type instanceof DeclaredType)) return null;
        Element element = ((DeclaredType) type).asElement();
        return element instanceof TypeElement ? (TypeElement) element : null;
    }

    private List<MapAnnotation> extractMapAnnotations(ExecutableElement method) {
        List<MapAnnotation> annotations = new ArrayList<>();
        for (AnnotationMirror mirror : method.getAnnotationMirrors()) {
            String annotationName = ((TypeElement) mirror.getAnnotationType().asElement())
                    .getQualifiedName().toString();
            if (annotationName.equals("io.github.joke.caffeinate.Map")) {
                annotations.add(parseMapAnnotation(mirror));
            } else if (annotationName.equals("io.github.joke.caffeinate.MapList")) {
                annotations.addAll(parseMapListAnnotation(mirror));
            }
        }
        return annotations;
    }

    private MapAnnotation parseMapAnnotation(AnnotationMirror mirror) {
        String target = "";
        String source = "";
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                : mirror.getElementValues().entrySet()) {
            String name = entry.getKey().getSimpleName().toString();
            if (name.equals("target")) target = (String) entry.getValue().getValue();
            else if (name.equals("source")) source = (String) entry.getValue().getValue();
        }
        return new MapAnnotation(target, source);
    }

    private List<MapAnnotation> parseMapListAnnotation(AnnotationMirror mirror) {
        List<MapAnnotation> result = new ArrayList<>();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                : mirror.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().toString().equals("value")) {
                @SuppressWarnings("unchecked")
                List<? extends AnnotationValue> values =
                        (List<? extends AnnotationValue>) entry.getValue().getValue();
                for (AnnotationValue av : values) {
                    result.add(parseMapAnnotation((AnnotationMirror) av.getValue()));
                }
            }
        }
        return result;
    }
}
```

**Step 2: Commit**
```bash
git add processor/src/main/java/io/github/joke/caffeinate/analysis/AnalysisStage.java
git commit -m "feat: add AnalysisStage"
```

---

### Task 6: ValidationStage

Checks every target property is covered. Emits Messager errors for missing mappings. On success, wraps `AnalysisResult` in `ValidationResult`.

**Files:**
- Create: `processor/src/main/java/io/github/joke/caffeinate/validation/ValidationStage.java`

**Step 1: Implement**

`ValidationStage.java`:
```java
package io.github.joke.caffeinate.validation;

import io.github.joke.caffeinate.analysis.AnalysisResult;
import io.github.joke.caffeinate.analysis.MapAnnotation;
import io.github.joke.caffeinate.analysis.MapperDescriptor;
import io.github.joke.caffeinate.analysis.MappingMethod;
import io.github.joke.caffeinate.analysis.property.Property;
import io.github.joke.caffeinate.analysis.property.PropertyDiscoveryStrategy;
import io.github.joke.caffeinate.analysis.property.PropertyMerger;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ValidationStage {

    private final ProcessingEnvironment env;
    private final Set<PropertyDiscoveryStrategy> strategies;

    @Inject
    public ValidationStage(ProcessingEnvironment env, Set<PropertyDiscoveryStrategy> strategies) {
        this.env = env;
        this.strategies = strategies;
    }

    public ValidationResult validate(AnalysisResult analysis) {
        boolean hasFatalErrors = false;
        for (MapperDescriptor descriptor : analysis.getMappers()) {
            for (MappingMethod method : descriptor.getMethods()) {
                if (!validateMethod(method, descriptor)) {
                    hasFatalErrors = true;
                }
            }
        }
        return new ValidationResult(analysis, hasFatalErrors);
    }

    private boolean validateMethod(MappingMethod method, MapperDescriptor descriptor) {
        List<Property> targetProperties = PropertyMerger.merge(
                strategies, method.getTargetType(), env);
        boolean valid = true;

        for (Property targetProp : targetProperties) {
            if (!isCovered(targetProp, method)) {
                env.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        String.format(
                            "[Percolate] %s.%s: no mapping found for target property '%s' (%s).\n"
                            + "  Consider adding a method: %s map%s(%s source)",
                            descriptor.getMapperInterface().getSimpleName(),
                            method.getMethod().getSimpleName(),
                            targetProp.getName(),
                            targetProp.getType(),
                            targetProp.getType(),
                            capitalize(targetProp.getName()),
                            targetProp.getType()),
                        method.getMethod());
                valid = false;
            }
        }
        return valid;
    }

    /** A target property is covered if any of the following is true:
     *  1. An explicit @Map(target = "propName", ...) annotation exists
     *  2. A source parameter has a property of the same name and compatible type
     *  3. A converter method on the mapper handles the target property's type
     */
    private boolean isCovered(Property targetProp, MappingMethod method) {
        // 1. Explicit @Map annotation
        for (MapAnnotation ann : method.getMapAnnotations()) {
            if (ann.getTarget().equals(targetProp.getName())) return true;
        }
        // 2. Name-match from any source parameter
        for (VariableElement param : method.getParameters()) {
            List<Property> sourceProps = PropertyMerger.merge(
                    strategies,
                    (javax.lang.model.element.TypeElement) env.getTypeUtils()
                            .asElement(param.asType()),
                    env);
            for (Property srcProp : sourceProps) {
                if (srcProp.getName().equals(targetProp.getName())) return true;
            }
        }
        // 3. A converter method whose return type matches the target property type
        for (ExecutableElement converter : method.getConverterCandidates()) {
            if (env.getTypeUtils().isSameType(
                    converter.getReturnType(), targetProp.getType())) return true;
        }
        return false;
    }

    private String capitalize(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
```

**Step 2: Commit**
```bash
git add processor/src/main/java/io/github/joke/caffeinate/validation/ValidationStage.java
git commit -m "feat: add ValidationStage with property coverage checking"
```

---

### Task 7: GraphStage

Builds the type-resolution graph and method-ordering graph using JGraphT.

**Files:**
- Create: `processor/src/main/java/io/github/joke/caffeinate/graph/GraphStage.java`

**Step 1: Implement**

`GraphStage.java`:
```java
package io.github.joke.caffeinate.graph;

import io.github.joke.caffeinate.analysis.MappingMethod;
import io.github.joke.caffeinate.analysis.property.Property;
import io.github.joke.caffeinate.analysis.property.PropertyDiscoveryStrategy;
import io.github.joke.caffeinate.analysis.property.PropertyMerger;
import io.github.joke.caffeinate.validation.ValidationResult;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;

import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Set;

public class GraphStage {

    private final ProcessingEnvironment env;
    private final Set<PropertyDiscoveryStrategy> strategies;

    @Inject
    public GraphStage(ProcessingEnvironment env, Set<PropertyDiscoveryStrategy> strategies) {
        this.env = env;
        this.strategies = strategies;
    }

    public GraphResult build(ValidationResult validation) {
        DefaultDirectedGraph<TypeMirror, MethodEdge> typeGraph =
                new DefaultDirectedGraph<>(MethodEdge.class);
        DirectedAcyclicGraph<MappingMethod, DefaultEdge> methodGraph =
                new DirectedAcyclicGraph<>(DefaultEdge.class);

        for (var descriptor : validation.getMappers()) {
            for (MappingMethod method : descriptor.getMethods()) {
                methodGraph.addVertex(method);

                // Edge in type graph: each source param type → target type
                for (VariableElement param : method.getParameters()) {
                    TypeMirror sourceType = param.asType();
                    TypeMirror targetType = method.getTargetType().asType();
                    if (!typeGraph.containsVertex(sourceType)) typeGraph.addVertex(sourceType);
                    if (!typeGraph.containsVertex(targetType)) typeGraph.addVertex(targetType);
                    typeGraph.addEdge(sourceType, targetType, new MethodEdge(method));
                }

                // Find converter dependencies: target properties that need delegate calls
                List<Property> targetProps = PropertyMerger.merge(
                        strategies, method.getTargetType(), env);
                for (Property targetProp : targetProps) {
                    findConverterFor(targetProp.getType(), method)
                            .ifPresent(converter -> {
                                if (methodGraph.containsVertex(converter)) {
                                    try {
                                        methodGraph.addEdge(method, converter);
                                    } catch (IllegalArgumentException ignored) {
                                        // edge already exists
                                    }
                                }
                            });
                }
            }
        }

        return new GraphResult(typeGraph, methodGraph);
    }

    private java.util.Optional<MappingMethod> findConverterFor(
            TypeMirror targetPropType,
            MappingMethod currentMethod) {
        for (var descriptor : List.of()) { // converters from all descriptors resolved in CodeGen
            // This is resolved at codegen time from GraphResult.resolverFor()
        }
        return java.util.Optional.empty();
    }
}
```

**Step 2: Commit**
```bash
git add processor/src/main/java/io/github/joke/caffeinate/graph/GraphStage.java
git commit -m "feat: add GraphStage using JGraphT"
```

---

### Task 8: CodeGenStage — name-match mapping

Generates `{Mapper}Impl` using JavaPoet. Initial implementation handles only name-matched properties via a constructor call.

**Files:**
- Create: `processor/src/main/java/io/github/joke/caffeinate/codegen/CodeGenStage.java`

**Step 1: Implement**

`CodeGenStage.java`:
```java
package io.github.joke.caffeinate.codegen;

import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import io.github.joke.caffeinate.analysis.MapAnnotation;
import io.github.joke.caffeinate.analysis.MapperDescriptor;
import io.github.joke.caffeinate.analysis.MappingMethod;
import io.github.joke.caffeinate.analysis.property.Property;
import io.github.joke.caffeinate.analysis.property.PropertyDiscoveryStrategy;
import io.github.joke.caffeinate.analysis.property.PropertyMerger;
import io.github.joke.caffeinate.graph.GraphResult;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CodeGenStage {

    private final ProcessingEnvironment env;
    private final Filer filer;
    private final Set<PropertyDiscoveryStrategy> strategies;

    @Inject
    public CodeGenStage(ProcessingEnvironment env, Filer filer,
                         Set<PropertyDiscoveryStrategy> strategies) {
        this.env = env;
        this.filer = filer;
        this.strategies = strategies;
    }

    public void generate(GraphResult graph, List<MapperDescriptor> mappers) {
        for (MapperDescriptor descriptor : mappers) {
            generateMapper(descriptor, graph);
        }
    }

    private void generateMapper(MapperDescriptor descriptor, GraphResult graph) {
        TypeElement iface = descriptor.getMapperInterface();
        String implName = iface.getSimpleName() + "Impl";
        String packageName = env.getElementUtils()
                .getPackageOf(iface).getQualifiedName().toString();

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(implName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(TypeName.get(iface.asType()));

        for (MappingMethod method : descriptor.getMethods()) {
            classBuilder.addMethod(generateMethod(method, descriptor, graph));
        }

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build()).build();
        try {
            javaFile.writeTo(filer);
        } catch (IOException e) {
            env.getMessager().printMessage(
                    javax.tools.Diagnostic.Kind.ERROR,
                    "[Percolate] Failed to write " + implName + ": " + e.getMessage());
        }
    }

    private MethodSpec generateMethod(
            MappingMethod method, MapperDescriptor descriptor, GraphResult graph) {
        ExecutableElement elem = method.getMethod();
        MethodSpec.Builder builder = MethodSpec.overriding(elem);

        List<Property> targetProps = PropertyMerger.merge(
                strategies, method.getTargetType(), env);

        // Build constructor argument expressions for each target property
        List<String> args = new ArrayList<>();
        for (Property targetProp : targetProps) {
            String expr = resolveExpression(targetProp, method, descriptor, graph);
            args.add(expr);
        }

        String targetFqn = method.getTargetType().getQualifiedName().toString();
        String argsJoined = String.join(", ", args);
        builder.addStatement("return new $L($L)", targetFqn, argsJoined);

        return builder.build();
    }

    /**
     * Returns a Java expression that produces the value for a target property.
     * Priority: explicit @Map > name-match > converter method delegate.
     */
    private String resolveExpression(
            Property targetProp,
            MappingMethod method,
            MapperDescriptor descriptor,
            GraphResult graph) {
        // 1. Explicit @Map annotation
        for (MapAnnotation ann : method.getMapAnnotations()) {
            if (ann.getTarget().equals(targetProp.getName())) {
                return resolveSourcePath(ann.getSource(), method);
            }
        }

        // 2. Name-match from any source parameter
        for (VariableElement param : method.getParameters()) {
            TypeElement paramType = (TypeElement) env.getTypeUtils().asElement(param.asType());
            List<Property> sourceProps = PropertyMerger.merge(strategies, paramType, env);
            for (Property srcProp : sourceProps) {
                if (srcProp.getName().equals(targetProp.getName())) {
                    return accessorExpr(param.getSimpleName().toString(), srcProp);
                }
            }
        }

        // 3. Converter delegate (another mapper method handles this type)
        for (ExecutableElement candidate : method.getConverterCandidates()) {
            if (env.getTypeUtils().isSameType(candidate.getReturnType(), targetProp.getType())) {
                // Find the source expression for the converter's parameter type
                if (!candidate.getParameters().isEmpty()) {
                    TypeElement converterSourceType = (TypeElement) env.getTypeUtils()
                            .asElement(candidate.getParameters().get(0).asType());
                    for (VariableElement param : method.getParameters()) {
                        TypeElement paramType = (TypeElement) env.getTypeUtils().asElement(param.asType());
                        List<Property> sourceProps = PropertyMerger.merge(strategies, paramType, env);
                        for (Property srcProp : sourceProps) {
                            if (env.getTypeUtils().isSameType(srcProp.getType(), converterSourceType.asType())) {
                                return "this." + candidate.getSimpleName() + "("
                                        + accessorExpr(param.getSimpleName().toString(), srcProp) + ")";
                            }
                        }
                    }
                }
            }
        }

        // Fallback: null (validation should have prevented this)
        return "null /* unresolved: " + targetProp.getName() + " */";
    }

    /**
     * Resolves a dot-notation source path like "ticket.ticketId" to a Java expression.
     * Simple case: "param.property" → "param.getProperty()".
     * Wildcard "param.*" is not yet supported.
     */
    private String resolveSourcePath(String sourcePath, MappingMethod method) {
        String[] parts = sourcePath.split("\\.");
        if (parts.length == 2) {
            String paramName = parts[0];
            String propName = parts[1];
            // Find the parameter
            for (VariableElement param : method.getParameters()) {
                if (param.getSimpleName().toString().equals(paramName)) {
                    TypeElement paramType = (TypeElement) env.getTypeUtils().asElement(param.asType());
                    List<Property> sourceProps = PropertyMerger.merge(strategies, paramType, env);
                    for (Property srcProp : sourceProps) {
                        if (srcProp.getName().equals(propName)) {
                            return accessorExpr(paramName, srcProp);
                        }
                    }
                }
            }
        }
        // If path can't be resolved, fall through (validation should have caught this)
        return "null /* unresolved path: " + sourcePath + " */";
    }

    private String accessorExpr(String paramName, Property property) {
        if (property.getAccessor().getKind() == javax.lang.model.element.ElementKind.METHOD) {
            // getter: param.getX()
            return paramName + "." + property.getAccessor().getSimpleName() + "()";
        } else {
            // field: param.x (direct access for package-private or if needed)
            return paramName + "." + property.getName();
        }
    }
}
```

**Step 2: Commit**
```bash
git add processor/src/main/java/io/github/joke/caffeinate/codegen/CodeGenStage.java
git commit -m "feat: add CodeGenStage with name-match and @Map support"
```

---

## Phase 3: Dagger Wiring

### Task 9: Dagger scopes and modules

**Files:**
- Create: `processor/src/main/java/io/github/joke/caffeinate/processor/ProcessorScoped.java`
- Create: `processor/src/main/java/io/github/joke/caffeinate/processor/RoundScoped.java`
- Create: `processor/src/main/java/io/github/joke/caffeinate/processor/ProcessorModule.java`
- Create: `processor/src/main/java/io/github/joke/caffeinate/processor/StrategyModule.java`
- Create: `processor/src/main/java/io/github/joke/caffeinate/processor/ProcessorComponent.java`
- Create: `processor/src/main/java/io/github/joke/caffeinate/processor/RoundComponent.java`
- Create: `processor/src/main/java/io/github/joke/caffeinate/processor/Pipeline.java`

**Step 1: Implement**

`ProcessorScoped.java`:
```java
package io.github.joke.caffeinate.processor;

import javax.inject.Scope;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Scope
@Retention(RetentionPolicy.RUNTIME)
public @interface ProcessorScoped {}
```

`RoundScoped.java`:
```java
package io.github.joke.caffeinate.processor;

import javax.inject.Scope;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Scope
@Retention(RetentionPolicy.RUNTIME)
public @interface RoundScoped {}
```

`ProcessorModule.java` — provides `ProcessingEnvironment` and its derived types:
```java
package io.github.joke.caffeinate.processor;

import dagger.Module;
import dagger.Provides;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

@Module
public class ProcessorModule {

    private final ProcessingEnvironment processingEnv;

    public ProcessorModule(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    @Provides @ProcessorScoped
    ProcessingEnvironment processingEnvironment() { return processingEnv; }

    @Provides @ProcessorScoped
    Messager messager() { return processingEnv.getMessager(); }

    @Provides @ProcessorScoped
    Types types() { return processingEnv.getTypeUtils(); }

    @Provides @ProcessorScoped
    Elements elements() { return processingEnv.getElementUtils(); }

    @Provides @ProcessorScoped
    Filer filer() { return processingEnv.getFiler(); }
}
```

`StrategyModule.java` — loads strategies from SPI:
```java
package io.github.joke.caffeinate.processor;

import dagger.Module;
import dagger.Provides;
import io.github.joke.caffeinate.analysis.property.PropertyDiscoveryStrategy;
import io.github.joke.caffeinate.codegen.strategy.TypeMappingStrategy;

import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Module
public class StrategyModule {

    @Provides @ProcessorScoped
    Set<PropertyDiscoveryStrategy> propertyDiscoveryStrategies() {
        return StreamSupport.stream(
                ServiceLoader.load(PropertyDiscoveryStrategy.class,
                        StrategyModule.class.getClassLoader()).spliterator(), false)
                .collect(Collectors.toSet());
    }

    @Provides @ProcessorScoped
    Set<TypeMappingStrategy> typeMappingStrategies() {
        return StreamSupport.stream(
                ServiceLoader.load(TypeMappingStrategy.class,
                        StrategyModule.class.getClassLoader()).spliterator(), false)
                .collect(Collectors.toSet());
    }
}
```

`ProcessorComponent.java`:
```java
package io.github.joke.caffeinate.processor;

import dagger.Component;

@ProcessorScoped
@Component(modules = {ProcessorModule.class, StrategyModule.class})
public interface ProcessorComponent {
    RoundComponent.Factory roundComponentFactory();
}
```

`RoundComponent.java`:
```java
package io.github.joke.caffeinate.processor;

import dagger.BindsInstance;
import dagger.Subcomponent;
import io.github.joke.caffeinate.analysis.AnalysisStage;
import io.github.joke.caffeinate.codegen.CodeGenStage;
import io.github.joke.caffeinate.graph.GraphStage;
import io.github.joke.caffeinate.validation.ValidationStage;

import javax.annotation.processing.RoundEnvironment;

@RoundScoped
@Subcomponent
public interface RoundComponent {

    AnalysisStage analysisStage();
    ValidationStage validationStage();
    GraphStage graphStage();
    CodeGenStage codeGenStage();
    Pipeline pipeline();

    @Subcomponent.Factory
    interface Factory {
        RoundComponent create(@BindsInstance RoundEnvironment roundEnv);
    }
}
```

`Pipeline.java`:
```java
package io.github.joke.caffeinate.processor;

import io.github.joke.caffeinate.analysis.AnalysisResult;
import io.github.joke.caffeinate.analysis.AnalysisStage;
import io.github.joke.caffeinate.codegen.CodeGenStage;
import io.github.joke.caffeinate.graph.GraphResult;
import io.github.joke.caffeinate.graph.GraphStage;
import io.github.joke.caffeinate.validation.ValidationResult;
import io.github.joke.caffeinate.validation.ValidationStage;

import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import java.util.Set;

public class Pipeline {

    private final AnalysisStage analysisStage;
    private final ValidationStage validationStage;
    private final GraphStage graphStage;
    private final CodeGenStage codeGenStage;
    private final RoundEnvironment roundEnv;

    @Inject
    public Pipeline(AnalysisStage analysisStage, ValidationStage validationStage,
                    GraphStage graphStage, CodeGenStage codeGenStage,
                    RoundEnvironment roundEnv) {
        this.analysisStage = analysisStage;
        this.validationStage = validationStage;
        this.graphStage = graphStage;
        this.codeGenStage = codeGenStage;
        this.roundEnv = roundEnv;
    }

    public void run(Set<? extends Element> mapperElements) {
        AnalysisResult analysis = analysisStage.analyze(roundEnv, mapperElements);
        ValidationResult validation = validationStage.validate(analysis);
        if (validation.hasFatalErrors()) return;
        GraphResult graph = graphStage.build(validation);
        codeGenStage.generate(graph, validation.getMappers());
    }
}
```

**Step 2: Compile to trigger Dagger code generation**
```bash
./gradlew :processor:compileJava
```
Expected: BUILD SUCCESSFUL (Dagger generates `DaggerProcessorComponent` and related classes).

**Step 3: Commit**
```bash
git add processor/src/main/java/io/github/joke/caffeinate/processor/
git commit -m "feat: add Dagger component hierarchy and Pipeline orchestrator"
```

---

### Task 10: PercolateProcessor entry point + integration test

Wires everything together and runs the first end-to-end integration test.

**Files:**
- Create: `processor/src/main/java/io/github/joke/caffeinate/processor/PercolateProcessor.java`

**Step 1: Write the failing integration test**

Add to `GetterPropertyStrategySpec.groovy` (or create `PercolateProcessorSpec.groovy`):
```groovy
package io.github.joke.caffeinate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class PercolateProcessorSpec extends Specification {

    def "generates impl for mapper with getter-based name-matched properties"() {
        given:
        def personSrc = JavaFileObjects.forSourceLines("io.example.Person",
            "package io.example;",
            "public final class Person {",
            "    private final String name;",
            "    public Person(String name) { this.name = name; }",
            "    public String getName() { return name; }",
            "}")
        def mapperSrc = JavaFileObjects.forSourceLines("io.example.PersonMapper",
            "package io.example;",
            "import io.github.joke.caffeinate.Mapper;",
            "@Mapper",
            "public interface PersonMapper {",
            "    Person map(Person source);",
            "}")

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(personSrc, mapperSrc)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation).generatedSourceFile("io.example.PersonMapperImpl")
    }
}
```

**Step 2: Run to verify failure**
```bash
./gradlew :processor:test --tests 'PercolateProcessorSpec'
```
Expected: FAIL — `PercolateProcessor` not found.

**Step 3: Implement PercolateProcessor**

`PercolateProcessor.java`:
```java
package io.github.joke.caffeinate.processor;

import com.google.auto.service.AutoService;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Set;

@AutoService(Processor.class)
@SupportedAnnotationTypes("io.github.joke.caffeinate.Mapper")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class PercolateProcessor extends AbstractProcessor {

    private ProcessorComponent processorComponent;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        processorComponent = DaggerProcessorComponent.builder()
                .processorModule(new ProcessorModule(processingEnv))
                .build();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;
        Set<? extends Element> mapperElements = roundEnv.getElementsAnnotatedWith(
                annotations.iterator().next());
        if (mapperElements.isEmpty()) return false;
        processorComponent.roundComponentFactory()
                .create(roundEnv)
                .pipeline()
                .run(mapperElements);
        return true;
    }
}
```

**Step 4: Run to verify passage**
```bash
./gradlew :processor:test --tests 'PercolateProcessorSpec'
```
Expected: PASS

**Step 5: Run full test suite**
```bash
./gradlew :processor:test
```
Expected: BUILD SUCCESSFUL, all tests pass.

**Step 6: Commit**
```bash
git add processor/src/main/java/io/github/joke/caffeinate/processor/PercolateProcessor.java \
        processor/src/test/groovy/io/github/joke/caffeinate/processor/PercolateProcessorSpec.groovy
git commit -m "feat: add PercolateProcessor entry point and first integration test"
```

---

## Phase 4: Type Mapping Strategies

### Task 11: CollectionMappingStrategy

Maps `List<A>` → `List<B>` by streaming and calling the appropriate converter.

**Files:**
- Create: `processor/src/main/java/io/github/joke/caffeinate/codegen/strategy/CollectionMappingStrategy.java`
- Create: `processor/src/main/resources/META-INF/services/io.github.joke.caffeinate.codegen.strategy.TypeMappingStrategy`
- Modify: `processor/src/test/groovy/io/github/joke/caffeinate/processor/PercolateProcessorSpec.groovy`

**Step 1: Write failing test**

Add to `PercolateProcessorSpec.groovy`:
```groovy
def "generates impl that maps List<A> to List<B> via converter method"() {
    given:
    def actorSrc = JavaFileObjects.forSourceLines("io.example.Actor",
        "package io.example;",
        "public final class Actor {",
        "    private final String name;",
        "    public Actor(String name) { this.name = name; }",
        "    public String getName() { return name; }",
        "}")
    def actorDtoSrc = JavaFileObjects.forSourceLines("io.example.ActorDto",
        "package io.example;",
        "public final class ActorDto {",
        "    private final String name;",
        "    public ActorDto(String name) { this.name = name; }",
        "    public String getName() { return name; }",
        "}")
    def showSrc = JavaFileObjects.forSourceLines("io.example.Show",
        "package io.example;",
        "import java.util.List;",
        "public final class Show {",
        "    private final List<Actor> actors;",
        "    public Show(List<Actor> actors) { this.actors = actors; }",
        "    public List<Actor> getActors() { return actors; }",
        "}")
    def showDtoSrc = JavaFileObjects.forSourceLines("io.example.ShowDto",
        "package io.example;",
        "import java.util.List;",
        "public final class ShowDto {",
        "    private final List<ActorDto> actors;",
        "    public ShowDto(List<ActorDto> actors) { this.actors = actors; }",
        "    public List<ActorDto> getActors() { return actors; }",
        "}")
    def mapperSrc = JavaFileObjects.forSourceLines("io.example.ShowMapper",
        "package io.example;",
        "import io.github.joke.caffeinate.Mapper;",
        "@Mapper",
        "public interface ShowMapper {",
        "    ShowDto map(Show show);",
        "    ActorDto mapActor(Actor actor);",
        "}")

    when:
    Compilation compilation = Compiler.javac()
        .withProcessors(new PercolateProcessor())
        .compile(actorSrc, actorDtoSrc, showSrc, showDtoSrc, mapperSrc)

    then:
    assertThat(compilation).succeeded()
    assertThat(compilation).generatedSourceFile("io.example.ShowMapperImpl")
}
```

**Step 2: Run to verify failure**
```bash
./gradlew :processor:test --tests 'PercolateProcessorSpec.generates impl that maps List'
```
Expected: FAIL

**Step 3: Implement CollectionMappingStrategy**

`CollectionMappingStrategy.java`:
```java
package io.github.joke.caffeinate.codegen.strategy;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

@AutoService(TypeMappingStrategy.class)
public class CollectionMappingStrategy implements TypeMappingStrategy {

    @Override
    public boolean supports(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        return isList(source, env) && isList(target, env);
    }

    @Override
    public CodeBlock generate(String sourceExpr, TypeMirror source, TypeMirror target,
                               ProcessingEnvironment env) {
        // Generates: sourceExpr.stream().map(this::mapX).collect(java.util.stream.Collectors.toList())
        // The converter method name is resolved by the CodeGenStage using the graph.
        // The strategy emits a template; CodeGenStage fills in the method reference.
        return CodeBlock.of("$L.stream().map(this::$L).collect($T.toList())",
                sourceExpr, "{{converterRef}}", java.util.stream.Collectors.class);
    }

    private boolean isList(TypeMirror type, ProcessingEnvironment env) {
        if (!(type instanceof DeclaredType)) return false;
        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        return element.getQualifiedName().toString().equals("java.util.List");
    }
}
```

**Note:** The `{{converterRef}}` placeholder pattern works during CodeGenStage because the stage resolves the converter method name from the graph before handing off to the strategy. Alternatively, the strategy's `generate` method receives a `converterMethodName` parameter (preferred — update the `TypeMappingStrategy` interface if you prefer cleaner coupling). A clean design:

Update `TypeMappingStrategy.java` to:
```java
public interface TypeMappingStrategy {
    boolean supports(TypeMirror source, TypeMirror target, ProcessingEnvironment env);
    CodeBlock generate(String sourceExpr, TypeMirror source, TypeMirror target,
                       String converterMethodRef, ProcessingEnvironment env);
}
```

Then `CollectionMappingStrategy.generate` becomes:
```java
return CodeBlock.of("$L.stream().map(this::$L).collect($T.toList())",
        sourceExpr, converterMethodRef, java.util.stream.Collectors.class);
```

Update `CodeGenStage.resolveExpression` to pass the converter method name to the strategy.

**Step 4: Register via META-INF/services**

Create `processor/src/main/resources/META-INF/services/io.github.joke.caffeinate.codegen.strategy.TypeMappingStrategy`:
```
io.github.joke.caffeinate.codegen.strategy.CollectionMappingStrategy
```

(The `@AutoService` annotation handles this automatically if annotation processing is configured — verify `annotationProcessor 'com.google.auto.service:auto-service'` is in the processor's build.gradle. It is. The `META-INF/services` file is generated automatically.)

**Step 5: Run test**
```bash
./gradlew :processor:test --tests 'PercolateProcessorSpec'
```
Expected: PASS

**Step 6: Commit**
```bash
git add processor/src/main/java/io/github/joke/caffeinate/codegen/strategy/CollectionMappingStrategy.java \
        processor/src/test/groovy/io/github/joke/caffeinate/processor/PercolateProcessorSpec.groovy
git commit -m "feat: add CollectionMappingStrategy for List<A> -> List<B>"
```

---

### Task 12: OptionalMappingStrategy

Maps `Optional<A>` → `Optional<B>` by calling `source.map(this::converterMethod)`.

**Files:**
- Create: `processor/src/main/java/io/github/joke/caffeinate/codegen/strategy/OptionalMappingStrategy.java`

**Step 1: Write failing test**

Add to `PercolateProcessorSpec.groovy`:
```groovy
def "generates impl that maps Optional<A> to Optional<B>"() {
    given:
    def addressSrc = JavaFileObjects.forSourceLines("io.example.Address",
        "package io.example;",
        "public final class Address { private final String city;",
        "    public Address(String city) { this.city = city; }",
        "    public String getCity() { return city; } }")
    def addressDtoSrc = JavaFileObjects.forSourceLines("io.example.AddressDto",
        "package io.example;",
        "public final class AddressDto { private final String city;",
        "    public AddressDto(String city) { this.city = city; }",
        "    public String getCity() { return city; } }")
    def personSrc = JavaFileObjects.forSourceLines("io.example.Person2",
        "package io.example;",
        "import java.util.Optional;",
        "public final class Person2 { private final Optional<Address> address;",
        "    public Person2(Optional<Address> address) { this.address = address; }",
        "    public Optional<Address> getAddress() { return address; } }")
    def personDtoSrc = JavaFileObjects.forSourceLines("io.example.Person2Dto",
        "package io.example;",
        "import java.util.Optional;",
        "public final class Person2Dto { private final Optional<AddressDto> address;",
        "    public Person2Dto(Optional<AddressDto> address) { this.address = address; }",
        "    public Optional<AddressDto> getAddress() { return address; } }")
    def mapperSrc = JavaFileObjects.forSourceLines("io.example.PersonMapper2",
        "package io.example;",
        "import io.github.joke.caffeinate.Mapper;",
        "@Mapper",
        "public interface PersonMapper2 {",
        "    Person2Dto map(Person2 person);",
        "    AddressDto mapAddress(Address address);",
        "}")

    when:
    Compilation compilation = Compiler.javac()
        .withProcessors(new PercolateProcessor())
        .compile(addressSrc, addressDtoSrc, personSrc, personDtoSrc, mapperSrc)

    then:
    assertThat(compilation).succeeded()
}
```

**Step 2: Implement**

`OptionalMappingStrategy.java`:
```java
package io.github.joke.caffeinate.codegen.strategy;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

@AutoService(TypeMappingStrategy.class)
public class OptionalMappingStrategy implements TypeMappingStrategy {

    @Override
    public boolean supports(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        return isOptional(source, env) && isOptional(target, env);
    }

    @Override
    public CodeBlock generate(String sourceExpr, TypeMirror source, TypeMirror target,
                               String converterMethodRef, ProcessingEnvironment env) {
        return CodeBlock.of("$L.map(this::$L)", sourceExpr, converterMethodRef);
    }

    private boolean isOptional(TypeMirror type, ProcessingEnvironment env) {
        if (!(type instanceof DeclaredType)) return false;
        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        return element.getQualifiedName().toString().equals("java.util.Optional");
    }
}
```

**Step 3: Run and commit**
```bash
./gradlew :processor:test --tests 'PercolateProcessorSpec'
git add processor/src/main/java/io/github/joke/caffeinate/codegen/strategy/OptionalMappingStrategy.java \
        processor/src/test/groovy/io/github/joke/caffeinate/processor/PercolateProcessorSpec.groovy
git commit -m "feat: add OptionalMappingStrategy"
```

---

### Task 13: EnumMappingStrategy

Maps enum constants by name. Errors on unmatched constants.

**Files:**
- Create: `processor/src/main/java/io/github/joke/caffeinate/codegen/strategy/EnumMappingStrategy.java`

**Step 1: Write failing test**

Add to `PercolateProcessorSpec.groovy`:
```groovy
def "generates impl that maps same-named enum constants"() {
    given:
    def statusSrcSrc = JavaFileObjects.forSourceLines("io.example.Status",
        "package io.example;",
        "public enum Status { ACTIVE, INACTIVE }")
    def statusDtoSrc = JavaFileObjects.forSourceLines("io.example.StatusDto",
        "package io.example;",
        "public enum StatusDto { ACTIVE, INACTIVE }")
    def mapperSrc = JavaFileObjects.forSourceLines("io.example.StatusMapper",
        "package io.example;",
        "import io.github.joke.caffeinate.Mapper;",
        "@Mapper",
        "public interface StatusMapper {",
        "    StatusDto map(Status status);",
        "}")

    when:
    Compilation compilation = Compiler.javac()
        .withProcessors(new PercolateProcessor())
        .compile(statusSrcSrc, statusDtoSrc, mapperSrc)

    then:
    assertThat(compilation).succeeded()
    assertThat(compilation).generatedSourceFile("io.example.StatusMapperImpl")
}
```

**Step 2: Implement**

`EnumMappingStrategy.java`:
```java
package io.github.joke.caffeinate.codegen.strategy;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@AutoService(TypeMappingStrategy.class)
public class EnumMappingStrategy implements TypeMappingStrategy {

    @Override
    public boolean supports(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        return isEnum(source) && isEnum(target);
    }

    @Override
    public CodeBlock generate(String sourceExpr, TypeMirror source, TypeMirror target,
                               String converterMethodRef, ProcessingEnvironment env) {
        TypeElement targetEnum = (TypeElement) ((DeclaredType) target).asElement();
        TypeElement sourceEnum = (TypeElement) ((DeclaredType) source).asElement();

        Set<String> targetConstants = enumConstants(targetEnum);
        Set<String> sourceConstants = enumConstants(sourceEnum);

        // Validate all source constants exist in target
        for (String constant : sourceConstants) {
            if (!targetConstants.contains(constant)) {
                env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        String.format("[Percolate] Enum constant '%s' from %s has no match in %s",
                                constant, sourceEnum.getSimpleName(), targetEnum.getSimpleName()));
            }
        }

        // Generate switch expression: switch (source) { case ACTIVE -> TargetEnum.ACTIVE; ... }
        String targetFqn = targetEnum.getQualifiedName().toString();
        StringBuilder switchBlock = new StringBuilder("switch ($L) {\n");
        for (String constant : sourceConstants) {
            switchBlock.append("    case ").append(constant)
                    .append(" -> ").append(targetFqn).append(".").append(constant).append(";\n");
        }
        switchBlock.append("    default -> throw new IllegalArgumentException(\"Unknown: \" + $L);\n}");

        return CodeBlock.of(switchBlock.toString(), sourceExpr, sourceExpr);
    }

    private boolean isEnum(TypeMirror type) {
        if (!(type instanceof DeclaredType)) return false;
        return ((DeclaredType) type).asElement().getKind() == ElementKind.ENUM;
    }

    private Set<String> enumConstants(TypeElement enumType) {
        return enumType.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.ENUM_CONSTANT)
                .map(e -> e.getSimpleName().toString())
                .collect(Collectors.toSet());
    }
}
```

**Step 3: Run and commit**
```bash
./gradlew :processor:test --tests 'PercolateProcessorSpec'
git add processor/src/main/java/io/github/joke/caffeinate/codegen/strategy/EnumMappingStrategy.java \
        processor/src/test/groovy/io/github/joke/caffeinate/processor/PercolateProcessorSpec.groovy
git commit -m "feat: add EnumMappingStrategy for same-named enum constants"
```

---

## Phase 5: Validation Error Diagnostics

### Task 14: Partial resolution graph rendering

When validation fails, render the partial type graph from the target root using JGraphT traversal.

**Files:**
- Create: `processor/src/main/java/io/github/joke/caffeinate/validation/PartialGraphRenderer.java`
- Modify: `processor/src/main/java/io/github/joke/caffeinate/validation/ValidationStage.java`
- Modify: `processor/src/test/groovy/io/github/joke/caffeinate/processor/PercolateProcessorSpec.groovy`

**Step 1: Write failing test**

Add to `PercolateProcessorSpec.groovy`:
```groovy
def "emits partial resolution graph when mapping is incomplete"() {
    given:
    def actorSrc = JavaFileObjects.forSourceLines("io.example.Actor2",
        "package io.example;",
        "public final class Actor2 {",
        "    private final String firstName;",
        "    private final String lastName;",
        "    public Actor2(String firstName, String lastName) { this.firstName = firstName; this.lastName = lastName; }",
        "    public String getFirstName() { return firstName; }",
        "    public String getLastName() { return lastName; }",
        "}")
    def showSrc = JavaFileObjects.forSourceLines("io.example.Show2",
        "package io.example;",
        "import java.util.List;",
        "public final class Show2 {",
        "    private final String title;",
        "    private final List<Actor2> actors;",
        "    public Show2(String title, List<Actor2> actors) { this.title = title; this.actors = actors; }",
        "    public String getTitle() { return title; }",
        "    public List<Actor2> getActors() { return actors; }",
        "}")
    def showDtoSrc = JavaFileObjects.forSourceLines("io.example.Show2Dto",
        "package io.example;",
        "import java.util.List;",
        "public final class Show2Dto {",
        "    private final String title;",
        "    private final List<String> actorNames;",
        "    public Show2Dto(String title, List<String> actorNames) { this.title = title; this.actorNames = actorNames; }",
        "    public String getTitle() { return title; }",
        "    public List<String> getActorNames() { return actorNames; }",
        "}")
    // Intentionally missing a converter for actors
    def mapperSrc = JavaFileObjects.forSourceLines("io.example.Show2Mapper",
        "package io.example;",
        "import io.github.joke.caffeinate.Mapper;",
        "@Mapper",
        "public interface Show2Mapper {",
        "    Show2Dto map(Show2 show);",  // missing converter for actorNames
        "}")

    when:
    Compilation compilation = Compiler.javac()
        .withProcessors(new PercolateProcessor())
        .compile(actorSrc, showSrc, showDtoSrc, mapperSrc)

    then:
    assertThat(compilation).failed()
    assertThat(compilation).hadErrorContaining("Partial resolution graph")
    assertThat(compilation).hadErrorContaining("actorNames")
    assertThat(compilation).hadErrorContaining("✗")
    assertThat(compilation).hadErrorContaining("✓")
}
```

**Step 2: Implement PartialGraphRenderer**

`PartialGraphRenderer.java`:
```java
package io.github.joke.caffeinate.validation;

import io.github.joke.caffeinate.analysis.MappingMethod;
import io.github.joke.caffeinate.analysis.property.Property;
import io.github.joke.caffeinate.analysis.property.PropertyDiscoveryStrategy;
import io.github.joke.caffeinate.analysis.property.PropertyMerger;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.List;
import java.util.Set;

/**
 * Renders a tree showing which target properties are resolved and which are not.
 * Uses depth-first traversal from the target type root.
 */
public final class PartialGraphRenderer {

    private PartialGraphRenderer() {}

    public static String render(MappingMethod method, Set<PropertyDiscoveryStrategy> strategies,
                                 ProcessingEnvironment env) {
        StringBuilder sb = new StringBuilder();
        sb.append("\nPartial resolution graph (from target ")
          .append(method.getTargetType().getSimpleName()).append("):\n");
        sb.append("  ").append(method.getTargetType().getSimpleName()).append("\n");

        List<Property> targetProps = PropertyMerger.merge(strategies, method.getTargetType(), env);
        int lastIdx = targetProps.size() - 1;
        for (int i = 0; i <= lastIdx; i++) {
            Property targetProp = targetProps.get(i);
            boolean isLast = i == lastIdx;
            boolean resolved = isCovered(targetProp, method, strategies, env);
            String branch = isLast ? "└── " : "├── ";
            String mark = resolved ? "✓" : "✗";
            sb.append("  ").append(branch).append(targetProp.getName())
              .append("  ").append(mark);
            if (resolved) {
                sb.append("  ← ").append(resolvedDescription(targetProp, method, strategies, env));
            } else {
                sb.append("  ← unresolved (").append(targetProp.getType()).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static boolean isCovered(Property targetProp, MappingMethod method,
                                      Set<PropertyDiscoveryStrategy> strategies,
                                      ProcessingEnvironment env) {
        for (var ann : method.getMapAnnotations()) {
            if (ann.getTarget().equals(targetProp.getName())) return true;
        }
        for (VariableElement param : method.getParameters()) {
            TypeElement paramType = (TypeElement) env.getTypeUtils().asElement(param.asType());
            for (Property srcProp : PropertyMerger.merge(strategies, paramType, env)) {
                if (srcProp.getName().equals(targetProp.getName())) return true;
            }
        }
        for (var converter : method.getConverterCandidates()) {
            if (env.getTypeUtils().isSameType(converter.getReturnType(), targetProp.getType())) return true;
        }
        return false;
    }

    private static String resolvedDescription(Property targetProp, MappingMethod method,
                                               Set<PropertyDiscoveryStrategy> strategies,
                                               ProcessingEnvironment env) {
        for (var ann : method.getMapAnnotations()) {
            if (ann.getTarget().equals(targetProp.getName())) return ann.getSource();
        }
        for (VariableElement param : method.getParameters()) {
            TypeElement paramType = (TypeElement) env.getTypeUtils().asElement(param.asType());
            for (Property srcProp : PropertyMerger.merge(strategies, paramType, env)) {
                if (srcProp.getName().equals(targetProp.getName())) {
                    return param.getSimpleName() + "." + srcProp.getName();
                }
            }
        }
        for (var converter : method.getConverterCandidates()) {
            if (env.getTypeUtils().isSameType(converter.getReturnType(), targetProp.getType())) {
                return "this." + converter.getSimpleName() + "(...)";
            }
        }
        return "?";
    }
}
```

**Step 3: Update ValidationStage** to include the rendered graph in error messages:

In `ValidationStage.validateMethod`, append the rendered graph to the error message:
```java
String graphDiagram = PartialGraphRenderer.render(method, strategies, env);
env.getMessager().printMessage(
    Diagnostic.Kind.ERROR,
    String.format("[Percolate] %s.%s: no mapping for target property '%s'.\n%s\n  Consider adding: %s map%s(%s source)",
        descriptor.getMapperInterface().getSimpleName(),
        method.getMethod().getSimpleName(),
        targetProp.getName(),
        graphDiagram,
        targetProp.getType(),
        capitalize(targetProp.getName()),
        targetProp.getType()),
    method.getMethod());
```

**Step 4: Run tests**
```bash
./gradlew :processor:test
```
Expected: all tests pass.

**Step 5: Commit**
```bash
git add processor/src/main/java/io/github/joke/caffeinate/validation/PartialGraphRenderer.java \
        processor/src/main/java/io/github/joke/caffeinate/validation/ValidationStage.java \
        processor/src/test/groovy/io/github/joke/caffeinate/processor/PercolateProcessorSpec.groovy
git commit -m "feat: render partial resolution graph in validation error messages"
```

---

## Phase 6: Wire test-mapper

### Task 15: Register processor in test-mapper and verify TicketMapper compiles

**Files:**
- Modify: `test-mapper/build.gradle`

**Step 1: Add processor dependency**

In `test-mapper/build.gradle`, add:
```groovy
annotationProcessor project(':processor')
```

**Step 2: Build**
```bash
./gradlew :test-mapper:compileJava
```
Expected: BUILD SUCCESSFUL, and `TicketMapperImpl.java` is generated in `test-mapper/build/generated/sources/annotationProcessor/`.

**Step 3: Inspect generated file**
```bash
find test-mapper/build/generated -name "TicketMapperImpl.java" | xargs cat
```
Verify the generated file contains correct method implementations.

**Step 4: Run full build**
```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL

**Step 5: Commit**
```bash
git add test-mapper/build.gradle
git commit -m "feat: wire processor into test-mapper and verify TicketMapper generation"
```

---

## Reference: Key Design Decisions

- **SPI loading:** `ServiceLoader.load(Strategy.class, StrategyModule.class.getClassLoader())` — use the processor's classloader, not the context classloader, to find strategies on the annotation processor classpath.
- **Getter wins over field:** `PropertyMerger` runs two passes — fields first (`putIfAbsent`), then getters (overwrite).
- **JGraphT for everything:** No manual graph traversal. Use `DefaultDirectedGraph` for type-resolution, `DirectedAcyclicGraph` for method ordering. Use `DepthFirstIterator` or `BreadthFirstIterator` for traversal.
- **NullAway:** Add `@NullMarked` `package-info.java` to each new package if you want NullAway enforcement there. Sub-packages without it are not checked (`onlyNullMarked = true`).
- **Java 11:** No records, no `var` in lambdas, no switch expressions. Use `List.copyOf()`, `Set.of()`, streams. Switch statements only.
- **ErrorProne + `-Werror`:** All warnings are errors. If you get an unexpected error, check the exact ErrorProne rule and either fix the code or apply a targeted suppression (`@SuppressWarnings`).

# Percolate Annotation Processor Rewrite — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Rewrite the annotation processor from scratch with a graph-first architecture where a JGraphT dependency graph is the single source of truth for validation and code generation.

**Architecture:** 6-stage pipeline (Parse → Resolve → Graph Build → Validate → Optimize → CodeGen) orchestrated by Dagger 2, with SPI extension points for property discovery, object creation, and generic type handling. The dependency graph's nodes represent types, properties, constructors, and methods; edges represent access paths and conversions.

**Tech Stack:** Java 8, Dagger 2.59.1, Palantir JavaPoet 0.11.0, JGraphT 1.5.2, Google AutoService 1.1.1, Spock 2.4 + Google Compile Testing 0.23.0 for tests.

**Conventions:**
- Package root: `io.github.joke.percolate`
- Every new package needs a `package-info.java` with `@org.jspecify.annotations.NullMarked`
- Java 8 target — no `var`, no records, no text blocks
- Prefer streams, static imports, functional style
- `-Werror` — all warnings are errors
- ErrorProne + NullAway (JSpecify mode) enforced
- Palantir JavaFormat code style

---

## Task 1: Processor Entry Point + Dagger Skeleton

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/processor/PercolateProcessor.java`
- Create: `processor/src/main/java/io/github/joke/percolate/processor/Pipeline.java`
- Create: `processor/src/main/java/io/github/joke/percolate/processor/package-info.java`
- Create: `processor/src/main/java/io/github/joke/percolate/di/ProcessorComponent.java`
- Create: `processor/src/main/java/io/github/joke/percolate/di/ProcessorModule.java`
- Create: `processor/src/main/java/io/github/joke/percolate/di/ProcessorScoped.java`
- Create: `processor/src/main/java/io/github/joke/percolate/di/RoundComponent.java`
- Create: `processor/src/main/java/io/github/joke/percolate/di/RoundModule.java`
- Create: `processor/src/main/java/io/github/joke/percolate/di/RoundScoped.java`
- Create: `processor/src/main/java/io/github/joke/percolate/di/package-info.java`
- Test: `processor/src/test/groovy/io/github/joke/percolate/processor/PercolateProcessorSpec.groovy`

**Step 1: Write the failing test**

```groovy
package io.github.joke.percolate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class PercolateProcessorSpec extends Specification {

    def "processor compiles @Mapper interface without errors"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.SimpleMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '',
            '@Mapper',
            'public interface SimpleMapper {',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper)

        then:
        assertThat(compilation).succeeded()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :processor:test --tests 'io.github.joke.percolate.processor.PercolateProcessorSpec'`
Expected: FAIL — `PercolateProcessor` class does not exist.

**Step 3: Implement the Dagger skeleton and processor**

Create scope annotations:

```java
// ProcessorScoped.java
package io.github.joke.percolate.di;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import javax.inject.Scope;

@Scope
@Retention(RUNTIME)
public @interface ProcessorScoped {}
```

```java
// RoundScoped.java
package io.github.joke.percolate.di;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import javax.inject.Scope;

@Scope
@Retention(RUNTIME)
public @interface RoundScoped {}
```

Create the Dagger module for processor-scoped bindings:

```java
// ProcessorModule.java
package io.github.joke.percolate.di;

import dagger.Module;
import dagger.Provides;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

@Module
public final class ProcessorModule {

    private final ProcessingEnvironment processingEnv;

    public ProcessorModule(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    @Provides
    @ProcessorScoped
    ProcessingEnvironment processingEnvironment() {
        return processingEnv;
    }

    @Provides
    @ProcessorScoped
    Elements elements() {
        return processingEnv.getElementUtils();
    }

    @Provides
    @ProcessorScoped
    Types types() {
        return processingEnv.getTypeUtils();
    }

    @Provides
    @ProcessorScoped
    Filer filer() {
        return processingEnv.getFiler();
    }

    @Provides
    @ProcessorScoped
    Messager messager() {
        return processingEnv.getMessager();
    }
}
```

Create the round module (empty for now):

```java
// RoundModule.java
package io.github.joke.percolate.di;

import dagger.Module;

@Module
public final class RoundModule {}
```

Create the round subcomponent:

```java
// RoundComponent.java
package io.github.joke.percolate.di;

import dagger.Subcomponent;
import io.github.joke.percolate.processor.Pipeline;

@RoundScoped
@Subcomponent(modules = RoundModule.class)
public interface RoundComponent {

    Pipeline pipeline();

    @Subcomponent.Factory
    interface Factory {
        RoundComponent create();
    }
}
```

Create the processor component:

```java
// ProcessorComponent.java
package io.github.joke.percolate.di;

import dagger.Component;

@ProcessorScoped
@Component(modules = ProcessorModule.class)
public interface ProcessorComponent {

    RoundComponent.Factory roundComponentFactory();

    @Component.Factory
    interface Factory {
        ProcessorComponent create(ProcessorModule processorModule);
    }
}
```

Create the pipeline (skeleton):

```java
// Pipeline.java
package io.github.joke.percolate.processor;

import io.github.joke.percolate.di.RoundScoped;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;
import java.util.Set;

@RoundScoped
public class Pipeline {

    @Inject
    Pipeline() {}

    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // stages will be wired in here
    }
}
```

Create the processor entry point:

```java
// PercolateProcessor.java
package io.github.joke.percolate.processor;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.Mapper;
import io.github.joke.percolate.di.DaggerProcessorComponent;
import io.github.joke.percolate.di.ProcessorComponent;
import io.github.joke.percolate.di.ProcessorModule;
import java.util.Collections;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

@AutoService(Processor.class)
public class PercolateProcessor extends AbstractProcessor {

    private ProcessorComponent component;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        component = DaggerProcessorComponent.factory()
            .create(new ProcessorModule(processingEnv));
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(Mapper.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            return false;
        }
        component.roundComponentFactory()
            .create()
            .pipeline()
            .process(annotations, roundEnv);
        return true;
    }
}
```

Create `package-info.java` files for each new package with `@org.jspecify.annotations.NullMarked`.

**Step 4: Run test to verify it passes**

Run: `./gradlew :processor:test --tests 'io.github.joke.percolate.processor.PercolateProcessorSpec'`
Expected: PASS

**Step 5: Commit**

```bash
git add processor/src/
git commit -m "feat: add processor entry point and Dagger DI skeleton"
```

---

## Task 2: Model Classes

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/model/MapperDefinition.java`
- Create: `processor/src/main/java/io/github/joke/percolate/model/MethodDefinition.java`
- Create: `processor/src/main/java/io/github/joke/percolate/model/MapDirective.java`
- Create: `processor/src/main/java/io/github/joke/percolate/model/Property.java`
- Create: `processor/src/main/java/io/github/joke/percolate/model/package-info.java`

**Step 1: Create the model classes**

These are simple value objects that carry data between pipeline stages. No tests needed — they'll be tested transitively through stage tests.

```java
// MapDirective.java — represents a single @Map annotation
package io.github.joke.percolate.model;

public final class MapDirective {
    private final String target;
    private final String source;

    public MapDirective(String target, String source) {
        this.target = target;
        this.source = source;
    }

    public String target() { return target; }
    public String source() { return source; }
}
```

```java
// MethodDefinition.java — represents a mapper method
package io.github.joke.percolate.model;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

public final class MethodDefinition {
    private final ExecutableElement element;
    private final String name;
    private final TypeMirror returnType;
    private final List<ParameterDefinition> parameters;
    private final boolean isAbstract;
    private final List<MapDirective> directives;

    // constructor, getters...
    // ParameterDefinition is a simple inner record-like class with name + type
}
```

```java
// ParameterDefinition.java — a method parameter
package io.github.joke.percolate.model;

import javax.lang.model.type.TypeMirror;

public final class ParameterDefinition {
    private final String name;
    private final TypeMirror type;

    public ParameterDefinition(String name, TypeMirror type) {
        this.name = name;
        this.type = type;
    }

    public String name() { return name; }
    public TypeMirror type() { return type; }
}
```

```java
// MapperDefinition.java — represents a @Mapper interface
package io.github.joke.percolate.model;

import javax.lang.model.element.TypeElement;
import java.util.List;

public final class MapperDefinition {
    private final TypeElement element;
    private final String packageName;
    private final String simpleName;
    private final List<MethodDefinition> methods;

    // constructor, getters...
}
```

```java
// Property.java — a discovered property on a type
package io.github.joke.percolate.model;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

public final class Property {
    private final String name;
    private final TypeMirror type;
    private final Element accessor; // the getter method or field

    // constructor, getters...
}
```

**Step 2: Verify build compiles**

Run: `./gradlew :processor:compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/model/
git commit -m "feat: add pipeline model classes"
```

---

## Task 3: SPI Interfaces

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/spi/PropertyDiscoveryStrategy.java`
- Create: `processor/src/main/java/io/github/joke/percolate/spi/ObjectCreationStrategy.java`
- Create: `processor/src/main/java/io/github/joke/percolate/spi/GenericMappingStrategy.java`
- Create: `processor/src/main/java/io/github/joke/percolate/spi/CreationDescriptor.java`
- Create: `processor/src/main/java/io/github/joke/percolate/spi/GraphFragment.java`
- Create: `processor/src/main/java/io/github/joke/percolate/spi/package-info.java`

**Step 1: Create the SPI interfaces**

```java
// PropertyDiscoveryStrategy.java
package io.github.joke.percolate.spi;

import io.github.joke.percolate.model.Property;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import java.util.Set;

public interface PropertyDiscoveryStrategy {
    Set<Property> discoverProperties(TypeElement type, ProcessingEnvironment env);
}
```

```java
// ObjectCreationStrategy.java
package io.github.joke.percolate.spi;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

public interface ObjectCreationStrategy {
    boolean canCreate(TypeElement type, ProcessingEnvironment env);
    CreationDescriptor describe(TypeElement type, ProcessingEnvironment env);
}
```

```java
// CreationDescriptor.java — describes how to create an object
package io.github.joke.percolate.spi;

import io.github.joke.percolate.model.Property;
import javax.lang.model.element.ExecutableElement;
import java.util.List;

public final class CreationDescriptor {
    private final ExecutableElement constructor;
    private final List<Property> parameters; // ordered constructor params as properties

    // constructor, getters...
}
```

```java
// GenericMappingStrategy.java
package io.github.joke.percolate.spi;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;

public interface GenericMappingStrategy {
    boolean canHandle(TypeMirror source, TypeMirror target, ProcessingEnvironment env);
    GraphFragment expand(TypeMirror source, TypeMirror target, ProcessingEnvironment env);
}
```

```java
// GraphFragment.java — a piece of graph to splice in during lazy expansion
package io.github.joke.percolate.spi;

// This will hold nodes and edges to splice into the main graph.
// Exact structure defined in Task 8 when graph model exists.
// For now: marker class.

public final class GraphFragment {
    // Will be populated in Task 8
}
```

**Step 2: Verify build compiles**

Run: `./gradlew :processor:compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/spi/
git commit -m "feat: add SPI interfaces for property discovery, object creation, generic mapping"
```

---

## Task 4: Parse Stage

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/stage/ParseStage.java`
- Create: `processor/src/main/java/io/github/joke/percolate/stage/ParseResult.java`
- Create: `processor/src/main/java/io/github/joke/percolate/stage/package-info.java`
- Test: `processor/src/test/groovy/io/github/joke/percolate/stage/ParseStageSpec.groovy`

**Step 1: Write the failing test**

```groovy
package io.github.joke.percolate.stage

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class ParseStageSpec extends Specification {

    def "parses @Mapper interface with abstract and default methods"() {
        given:
        def source = JavaFileObjects.forSourceLines('test.TestMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '',
            '@Mapper',
            'public interface TestMapper {',
            '    @Map(target = "name", source = "firstName")',
            '    Target map(Source source);',
            '',
            '    default String identity(String s) { return s; }',
            '}',
        )
        def targetClass = JavaFileObjects.forSourceLines('test.Target',
            'package test;',
            'public class Target {',
            '    public final String name;',
            '    public Target(String name) { this.name = name; }',
            '}',
        )
        def sourceClass = JavaFileObjects.forSourceLines('test.Source',
            'package test;',
            'public class Source {',
            '    public String getFirstName() { return ""; }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(source, targetClass, sourceClass)

        then:
        assertThat(compilation).succeeded()
        // The processor should not fail during parse.
        // Detailed parse verification done via generated output in later tasks.
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :processor:test --tests 'io.github.joke.percolate.stage.ParseStageSpec'`
Expected: FAIL — `ParseStage` does not exist.

**Step 3: Implement ParseStage**

```java
// ParseResult.java
package io.github.joke.percolate.stage;

import io.github.joke.percolate.model.MapperDefinition;
import java.util.List;

public final class ParseResult {
    private final List<MapperDefinition> mappers;

    public ParseResult(List<MapperDefinition> mappers) {
        this.mappers = mappers;
    }

    public List<MapperDefinition> mappers() { return mappers; }
}
```

```java
// ParseStage.java
package io.github.joke.percolate.stage;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;
import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.model.MapDirective;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.model.ParameterDefinition;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static javax.tools.Diagnostic.Kind.ERROR;

@RoundScoped
public class ParseStage {

    private final Elements elements;
    private final Messager messager;

    @Inject
    ParseStage(Elements elements, Messager messager) {
        this.elements = elements;
        this.messager = messager;
    }

    public ParseResult execute(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        List<MapperDefinition> mappers = roundEnv.getElementsAnnotatedWith(Mapper.class).stream()
            .filter(this::validateIsInterface)
            .map(e -> (TypeElement) e)
            .map(this::parseMapper)
            .collect(toList());
        return new ParseResult(mappers);
    }

    private boolean validateIsInterface(Element element) {
        if (element.getKind() != ElementKind.INTERFACE) {
            messager.printMessage(ERROR, "@Mapper can only be applied to interfaces", element);
            return false;
        }
        return true;
    }

    private MapperDefinition parseMapper(TypeElement typeElement) {
        String packageName = elements.getPackageOf(typeElement)
            .getQualifiedName().toString();
        String simpleName = typeElement.getSimpleName().toString();

        List<MethodDefinition> methods = typeElement.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.METHOD)
            .map(e -> (ExecutableElement) e)
            .map(this::parseMethod)
            .collect(toList());

        return new MapperDefinition(typeElement, packageName, simpleName, methods);
    }

    private MethodDefinition parseMethod(ExecutableElement method) {
        boolean isAbstract = method.getModifiers().contains(Modifier.ABSTRACT);

        List<ParameterDefinition> parameters = method.getParameters().stream()
            .map(p -> new ParameterDefinition(p.getSimpleName().toString(), p.asType()))
            .collect(toList());

        List<MapDirective> directives = method.getAnnotationMirrors().stream()
            .filter(am -> am.getAnnotationType().toString().equals(Map.class.getCanonicalName()))
            .map(am -> {
                String target = am.getElementValues().entrySet().stream()
                    .filter(e -> e.getKey().getSimpleName().toString().equals("target"))
                    .map(e -> (String) e.getValue().getValue())
                    .findFirst().orElse("");
                String source = am.getElementValues().entrySet().stream()
                    .filter(e -> e.getKey().getSimpleName().toString().equals("source"))
                    .map(e -> (String) e.getValue().getValue())
                    .findFirst().orElse("");
                return new MapDirective(target, source);
            })
            .collect(toList());

        return new MethodDefinition(method, method.getSimpleName().toString(),
            method.getReturnType(), parameters, isAbstract, directives);
    }
}
```

Wire `ParseStage` into the Pipeline and RoundComponent via Dagger.

**Step 4: Run test to verify it passes**

Run: `./gradlew :processor:test --tests 'io.github.joke.percolate.stage.ParseStageSpec'`
Expected: PASS

**Step 5: Commit**

```bash
git add processor/src/
git commit -m "feat: implement parse stage — extract @Mapper definitions"
```

---

## Task 5: Built-in Property Discovery Strategies

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/spi/impl/GetterPropertyStrategy.java`
- Create: `processor/src/main/java/io/github/joke/percolate/spi/impl/FieldPropertyStrategy.java`
- Create: `processor/src/main/java/io/github/joke/percolate/spi/impl/package-info.java`
- Create: `processor/src/main/resources/META-INF/services/io.github.joke.percolate.spi.PropertyDiscoveryStrategy`
- Test: `processor/src/test/groovy/io/github/joke/percolate/spi/impl/GetterPropertyStrategySpec.groovy`
- Test: `processor/src/test/groovy/io/github/joke/percolate/spi/impl/FieldPropertyStrategySpec.groovy`

**Step 1: Write the failing test for getter discovery**

```groovy
package io.github.joke.percolate.spi.impl

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class GetterPropertyStrategySpec extends Specification {

    def "discovers getter-based properties on a class"() {
        given:
        def source = JavaFileObjects.forSourceLines('test.SimpleClass',
            'package test;',
            'public class SimpleClass {',
            '    private String name;',
            '    private int age;',
            '    public String getName() { return name; }',
            '    public int getAge() { return age; }',
            '    public boolean isActive() { return true; }',
            '}',
        )

        // Test via a mapper that uses this class
        def mapper = JavaFileObjects.forSourceLines('test.SimpleMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '',
            '@Mapper',
            'public interface SimpleMapper {',
            '    SimpleTarget map(SimpleClass source);',
            '}',
        )
        def target = JavaFileObjects.forSourceLines('test.SimpleTarget',
            'package test;',
            'public class SimpleTarget {',
            '    public final String name;',
            '    public final int age;',
            '    public SimpleTarget(String name, int age) {',
            '        this.name = name;',
            '        this.age = age;',
            '    }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(source, mapper, target)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation)
            .generatedSourceFile('test.SimpleMapperImpl')
            .contentsAsUtf8String()
            .contains('source.getName()')
        assertThat(compilation)
            .generatedSourceFile('test.SimpleMapperImpl')
            .contentsAsUtf8String()
            .contains('source.getAge()')
    }
}
```

Note: This test will not pass until CodeGen is implemented (Task 11). For now, write the strategy implementations and test them transitively. Mark this test as `@spock.lang.Ignore` until Task 11, or write a simpler unit test using compile-testing that just verifies no errors.

**Step 2: Implement GetterPropertyStrategy**

```java
// GetterPropertyStrategy.java
package io.github.joke.percolate.spi.impl;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.model.Property;
import io.github.joke.percolate.spi.PropertyDiscoveryStrategy;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.Locale;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

@AutoService(PropertyDiscoveryStrategy.class)
public class GetterPropertyStrategy implements PropertyDiscoveryStrategy {

    @Override
    public Set<Property> discoverProperties(TypeElement type, ProcessingEnvironment env) {
        return type.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.METHOD)
            .map(e -> (ExecutableElement) e)
            .filter(m -> m.getModifiers().contains(Modifier.PUBLIC))
            .filter(m -> m.getParameters().isEmpty())
            .filter(m -> isGetter(m))
            .map(m -> new Property(
                extractPropertyName(m),
                m.getReturnType(),
                m))
            .collect(toSet());
    }

    private boolean isGetter(ExecutableElement method) {
        String name = method.getSimpleName().toString();
        return (name.startsWith("get") && name.length() > 3)
            || (name.startsWith("is") && name.length() > 2);
    }

    private String extractPropertyName(ExecutableElement method) {
        String name = method.getSimpleName().toString();
        if (name.startsWith("get")) {
            return decapitalize(name.substring(3));
        }
        return decapitalize(name.substring(2));
    }

    private String decapitalize(String s) {
        if (s.isEmpty()) return s;
        return s.substring(0, 1).toLowerCase(Locale.ROOT) + s.substring(1);
    }
}
```

**Step 3: Implement FieldPropertyStrategy**

```java
// FieldPropertyStrategy.java
package io.github.joke.percolate.spi.impl;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.model.Property;
import io.github.joke.percolate.spi.PropertyDiscoveryStrategy;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

@AutoService(PropertyDiscoveryStrategy.class)
public class FieldPropertyStrategy implements PropertyDiscoveryStrategy {

    @Override
    public Set<Property> discoverProperties(TypeElement type, ProcessingEnvironment env) {
        return type.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.FIELD)
            .map(e -> (VariableElement) e)
            .filter(f -> f.getModifiers().contains(Modifier.PUBLIC)
                || !f.getModifiers().contains(Modifier.PRIVATE))
            .map(f -> new Property(
                f.getSimpleName().toString(),
                f.asType(),
                f))
            .collect(toSet());
    }
}
```

Register both via `@AutoService` which generates `META-INF/services` automatically.

**Step 4: Verify build compiles**

Run: `./gradlew :processor:compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add processor/src/
git commit -m "feat: add getter and field property discovery strategies (SPI)"
```

---

## Task 6: Resolve Stage — Same-Name Matching

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/stage/ResolveStage.java`
- Create: `processor/src/main/java/io/github/joke/percolate/stage/ResolveResult.java`
- Test: `processor/src/test/groovy/io/github/joke/percolate/stage/ResolveStageSpec.groovy`

**Step 1: Write the failing test**

Test that a single-param method with no `@Map` annotations gets same-name properties auto-matched. This is verified indirectly — a mapper with matching property names should compile without errors once the full pipeline is wired.

For now, write a compile-test that verifies the processor does not emit errors for a single-param mapper with name-matched properties.

```groovy
package io.github.joke.percolate.stage

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class ResolveStageSpec extends Specification {

    def "auto-matches same-name properties for single-param method"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.AutoMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '',
            '@Mapper',
            'public interface AutoMapper {',
            '    Target map(Source source);',
            '}',
        )
        def source = JavaFileObjects.forSourceLines('test.Source',
            'package test;',
            'public class Source {',
            '    public String getName() { return ""; }',
            '    public int getAge() { return 0; }',
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
        assertThat(compilation).succeeded()
    }
}
```

**Step 2: Implement ResolveStage**

The resolve stage:
1. Loads all `PropertyDiscoveryStrategy` implementations via `ServiceLoader`
2. For each single-param abstract method without full `@Map` coverage, discovers properties on both source and target types, and adds `MapDirective`s for matching names
3. Passes through multi-param methods and methods with explicit directives unchanged

```java
// ResolveStage.java
package io.github.joke.percolate.stage;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.model.MapDirective;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.model.Property;
import io.github.joke.percolate.spi.PropertyDiscoveryStrategy;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@RoundScoped
public class ResolveStage {

    private final ProcessingEnvironment env;
    private final List<PropertyDiscoveryStrategy> strategies;

    @Inject
    ResolveStage(ProcessingEnvironment env) {
        this.env = env;
        this.strategies = new ArrayList<>();
        ServiceLoader.load(PropertyDiscoveryStrategy.class, getClass().getClassLoader())
            .forEach(strategies::add);
    }

    public ResolveResult execute(ParseResult parseResult) {
        List<MapperDefinition> resolved = parseResult.mappers().stream()
            .map(this::resolveMapper)
            .collect(toList());
        return new ResolveResult(resolved);
    }

    private MapperDefinition resolveMapper(MapperDefinition mapper) {
        List<MethodDefinition> resolvedMethods = mapper.methods().stream()
            .map(this::resolveMethod)
            .collect(toList());
        return mapper.withMethods(resolvedMethods);
    }

    private MethodDefinition resolveMethod(MethodDefinition method) {
        if (!method.isAbstract()) return method;
        if (method.parameters().size() != 1) return method;

        // Single-param method: auto-match same-name properties
        Set<String> explicitTargets = method.directives().stream()
            .map(MapDirective::target)
            .collect(toSet());

        TypeElement sourceType = asTypeElement(method.parameters().get(0).type());
        TypeElement targetType = asTypeElement(method.returnType());
        if (sourceType == null || targetType == null) return method;

        Set<String> sourceProps = discoverPropertyNames(sourceType);
        Set<String> targetProps = discoverPropertyNames(targetType);

        List<MapDirective> augmented = new ArrayList<>(method.directives());
        targetProps.stream()
            .filter(sourceProps::contains)
            .filter(name -> !explicitTargets.contains(name))
            .forEach(name -> augmented.add(new MapDirective(name, name)));

        return method.withDirectives(augmented);
    }

    private Set<String> discoverPropertyNames(TypeElement type) {
        return strategies.stream()
            .flatMap(s -> s.discoverProperties(type, env).stream())
            .map(Property::name)
            .collect(toSet());
    }

    private TypeElement asTypeElement(javax.lang.model.type.TypeMirror type) {
        if (type instanceof DeclaredType) {
            return (TypeElement) ((DeclaredType) type).asElement();
        }
        return null;
    }
}
```

Note: `MapperDefinition.withMethods()` and `MethodDefinition.withDirectives()` are copy-with methods to be added to the model classes. They return new instances with the specified field replaced.

Wire `ResolveStage` into Pipeline.

**Step 3: Run test to verify it passes**

Run: `./gradlew :processor:test --tests 'io.github.joke.percolate.stage.ResolveStageSpec'`
Expected: PASS (compilation succeeds, no generated code yet)

**Step 4: Commit**

```bash
git add processor/src/
git commit -m "feat: implement resolve stage — same-name property matching"
```

---

## Task 7: Constructor Creation Strategy

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/spi/impl/ConstructorCreationStrategy.java`
- Create: `processor/src/main/resources/META-INF/services/io.github.joke.percolate.spi.ObjectCreationStrategy`
- Test: (verified transitively through graph build tests)

**Step 1: Implement ConstructorCreationStrategy**

```java
// ConstructorCreationStrategy.java
package io.github.joke.percolate.spi.impl;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.model.Property;
import io.github.joke.percolate.spi.CreationDescriptor;
import io.github.joke.percolate.spi.ObjectCreationStrategy;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.List;

import static java.util.stream.Collectors.toList;

@AutoService(ObjectCreationStrategy.class)
public class ConstructorCreationStrategy implements ObjectCreationStrategy {

    @Override
    public boolean canCreate(TypeElement type, ProcessingEnvironment env) {
        return type.getEnclosedElements().stream()
            .anyMatch(e -> e.getKind() == ElementKind.CONSTRUCTOR);
    }

    @Override
    public CreationDescriptor describe(TypeElement type, ProcessingEnvironment env) {
        // Find the constructor with the most parameters (or the only constructor)
        ExecutableElement constructor = type.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
            .map(e -> (ExecutableElement) e)
            .reduce((a, b) -> a.getParameters().size() >= b.getParameters().size() ? a : b)
            .orElseThrow(() -> new IllegalStateException(
                "No constructor found for " + type.getQualifiedName()));

        List<Property> params = constructor.getParameters().stream()
            .map(p -> new Property(p.getSimpleName().toString(), p.asType(), p))
            .collect(toList());

        return new CreationDescriptor(constructor, params);
    }
}
```

**Step 2: Verify build compiles**

Run: `./gradlew :processor:compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add processor/src/
git commit -m "feat: add constructor creation strategy (SPI)"
```

---

## Task 8: Graph Model — Nodes and Edges

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/graph/node/GraphNode.java` (sealed interface or marker)
- Create: `processor/src/main/java/io/github/joke/percolate/graph/node/TypeNode.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/node/PropertyNode.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/node/ConstructorNode.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/node/MethodNode.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/node/package-info.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/edge/GraphEdge.java` (marker interface)
- Create: `processor/src/main/java/io/github/joke/percolate/graph/edge/PropertyAccessEdge.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/edge/MethodCallEdge.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/edge/ConstructorParamEdge.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/edge/ConstructorResultEdge.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/edge/GenericPlaceholderEdge.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/edge/package-info.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/package-info.java`

**Step 1: Create node types**

```java
// GraphNode.java — marker interface for all graph nodes
package io.github.joke.percolate.graph.node;

public interface GraphNode {}
```

```java
// TypeNode.java
package io.github.joke.percolate.graph.node;

import javax.lang.model.type.TypeMirror;

public final class TypeNode implements GraphNode {
    private final TypeMirror type;
    private final String label; // human-readable label for graph rendering

    public TypeNode(TypeMirror type, String label) {
        this.type = type;
        this.label = label;
    }

    public TypeMirror type() { return type; }
    public String label() { return label; }

    // equals/hashCode based on type string representation
    // toString for debugging
}
```

```java
// PropertyNode.java
package io.github.joke.percolate.graph.node;

import io.github.joke.percolate.model.Property;

public final class PropertyNode implements GraphNode {
    private final GraphNode parent; // the TypeNode this property belongs to
    private final Property property;

    public PropertyNode(GraphNode parent, Property property) {
        this.parent = parent;
        this.property = property;
    }

    public GraphNode parent() { return parent; }
    public Property property() { return property; }
    public String name() { return property.name(); }

    // equals/hashCode, toString
}
```

```java
// ConstructorNode.java
package io.github.joke.percolate.graph.node;

import io.github.joke.percolate.spi.CreationDescriptor;
import javax.lang.model.element.TypeElement;

public final class ConstructorNode implements GraphNode {
    private final TypeElement targetType;
    private final CreationDescriptor descriptor;

    public ConstructorNode(TypeElement targetType, CreationDescriptor descriptor) {
        this.targetType = targetType;
        this.descriptor = descriptor;
    }

    public TypeElement targetType() { return targetType; }
    public CreationDescriptor descriptor() { return descriptor; }

    // equals/hashCode, toString
}
```

```java
// MethodNode.java
package io.github.joke.percolate.graph.node;

import io.github.joke.percolate.model.MethodDefinition;

public final class MethodNode implements GraphNode {
    private final MethodDefinition method;

    public MethodNode(MethodDefinition method) {
        this.method = method;
    }

    public MethodDefinition method() { return method; }

    // equals/hashCode, toString
}
```

**Step 2: Create edge types**

```java
// GraphEdge.java — marker interface
package io.github.joke.percolate.graph.edge;

public interface GraphEdge {}
```

```java
// PropertyAccessEdge.java — TypeNode → PropertyNode
package io.github.joke.percolate.graph.edge;

import io.github.joke.percolate.model.Property;

public final class PropertyAccessEdge implements GraphEdge {
    private final Property property;

    public PropertyAccessEdge(Property property) {
        this.property = property;
    }

    public Property property() { return property; }
}
```

```java
// MethodCallEdge.java — TypeNode → TypeNode (via mapper method)
package io.github.joke.percolate.graph.edge;

import io.github.joke.percolate.model.MethodDefinition;

public final class MethodCallEdge implements GraphEdge {
    private final MethodDefinition method;

    public MethodCallEdge(MethodDefinition method) {
        this.method = method;
    }

    public MethodDefinition method() { return method; }
}
```

```java
// ConstructorParamEdge.java — PropertyNode/TypeNode → ConstructorNode
package io.github.joke.percolate.graph.edge;

public final class ConstructorParamEdge implements GraphEdge {
    private final String parameterName;
    private final int parameterIndex;

    public ConstructorParamEdge(String parameterName, int parameterIndex) {
        this.parameterName = parameterName;
        this.parameterIndex = parameterIndex;
    }

    public String parameterName() { return parameterName; }
    public int parameterIndex() { return parameterIndex; }
}
```

```java
// ConstructorResultEdge.java — ConstructorNode → TypeNode
package io.github.joke.percolate.graph.edge;

public final class ConstructorResultEdge implements GraphEdge {}
```

```java
// GenericPlaceholderEdge.java — TypeNode → TypeNode (lazy, expanded during validation)
package io.github.joke.percolate.graph.edge;

import javax.lang.model.type.TypeMirror;

public final class GenericPlaceholderEdge implements GraphEdge {
    private final TypeMirror sourceType;
    private final TypeMirror targetType;

    public GenericPlaceholderEdge(TypeMirror sourceType, TypeMirror targetType) {
        this.sourceType = sourceType;
        this.targetType = targetType;
    }

    public TypeMirror sourceType() { return sourceType; }
    public TypeMirror targetType() { return targetType; }
}
```

**Step 3: Verify build compiles**

Run: `./gradlew :processor:compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/graph/
git commit -m "feat: add graph model — nodes and edges for dependency graph"
```

---

## Task 9: Graph Build Stage

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/stage/GraphBuildStage.java`
- Create: `processor/src/main/java/io/github/joke/percolate/stage/GraphResult.java`
- Test: `processor/src/test/groovy/io/github/joke/percolate/stage/GraphBuildStageSpec.groovy`

**Step 1: Write the failing test**

```groovy
package io.github.joke.percolate.stage

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class GraphBuildStageSpec extends Specification {

    def "builds graph for single-param mapper without errors"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.PersonMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '',
            '@Mapper',
            'public interface PersonMapper {',
            '    PersonDto map(Person person);',
            '}',
        )
        def person = JavaFileObjects.forSourceLines('test.Person',
            'package test;',
            'public class Person {',
            '    public String getName() { return ""; }',
            '}',
        )
        def personDto = JavaFileObjects.forSourceLines('test.PersonDto',
            'package test;',
            'public class PersonDto {',
            '    public final String name;',
            '    public PersonDto(String name) { this.name = name; }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, person, personDto)

        then:
        assertThat(compilation).succeeded()
    }
}
```

**Step 2: Implement GraphBuildStage**

```java
// GraphResult.java
package io.github.joke.percolate.stage;

import io.github.joke.percolate.graph.edge.GraphEdge;
import io.github.joke.percolate.graph.node.GraphNode;
import io.github.joke.percolate.model.MapperDefinition;
import org.jgrapht.graph.DirectedWeightedMultigraph;
import java.util.List;

public final class GraphResult {
    private final DirectedWeightedMultigraph<GraphNode, GraphEdge> graph;
    private final List<MapperDefinition> mappers;

    public GraphResult(
            DirectedWeightedMultigraph<GraphNode, GraphEdge> graph,
            List<MapperDefinition> mappers) {
        this.graph = graph;
        this.mappers = mappers;
    }

    public DirectedWeightedMultigraph<GraphNode, GraphEdge> graph() { return graph; }
    public List<MapperDefinition> mappers() { return mappers; }
}
```

```java
// GraphBuildStage.java
package io.github.joke.percolate.stage;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.graph.edge.*;
import io.github.joke.percolate.graph.node.*;
import io.github.joke.percolate.model.*;
import io.github.joke.percolate.spi.CreationDescriptor;
import io.github.joke.percolate.spi.ObjectCreationStrategy;
import io.github.joke.percolate.spi.PropertyDiscoveryStrategy;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.jgrapht.graph.DirectedWeightedMultigraph;
import java.util.*;

import static java.util.stream.Collectors.toList;

@RoundScoped
public class GraphBuildStage {

    private final ProcessingEnvironment env;
    private final List<PropertyDiscoveryStrategy> propertyStrategies;
    private final List<ObjectCreationStrategy> creationStrategies;

    @Inject
    GraphBuildStage(ProcessingEnvironment env) {
        this.env = env;
        this.propertyStrategies = new ArrayList<>();
        ServiceLoader.load(PropertyDiscoveryStrategy.class, getClass().getClassLoader())
            .forEach(propertyStrategies::add);
        this.creationStrategies = new ArrayList<>();
        ServiceLoader.load(ObjectCreationStrategy.class, getClass().getClassLoader())
            .forEach(creationStrategies::add);
    }

    public GraphResult execute(ResolveResult resolveResult) {
        DirectedWeightedMultigraph<GraphNode, GraphEdge> graph =
            new DirectedWeightedMultigraph<>(GraphEdge.class);

        for (MapperDefinition mapper : resolveResult.mappers()) {
            for (MethodDefinition method : mapper.methods()) {
                if (!method.isAbstract()) continue;
                buildMethodGraph(graph, method);
            }
        }

        return new GraphResult(graph, resolveResult.mappers());
    }

    private void buildMethodGraph(
            DirectedWeightedMultigraph<GraphNode, GraphEdge> graph,
            MethodDefinition method) {

        MethodNode methodNode = new MethodNode(method);
        graph.addVertex(methodNode);

        // Create TypeNodes for each parameter
        Map<String, TypeNode> paramNodes = new HashMap<>();
        for (ParameterDefinition param : method.parameters()) {
            TypeNode paramTypeNode = new TypeNode(param.type(), param.name());
            graph.addVertex(paramTypeNode);
            paramNodes.put(param.name(), paramTypeNode);
        }

        // Create return type node and constructor node
        TypeElement returnTypeElement = asTypeElement(method.returnType());
        TypeNode returnTypeNode = new TypeNode(method.returnType(),
            returnTypeElement.getSimpleName().toString());
        graph.addVertex(returnTypeNode);

        CreationDescriptor creation = findCreationStrategy(returnTypeElement);
        ConstructorNode ctorNode = new ConstructorNode(returnTypeElement, creation);
        graph.addVertex(ctorNode);
        graph.addEdge(ctorNode, returnTypeNode, new ConstructorResultEdge());

        // Process each @Map directive: build edges from source path to constructor param
        for (MapDirective directive : method.directives()) {
            buildDirectiveEdges(graph, directive, paramNodes, ctorNode, creation, method);
        }
    }

    private void buildDirectiveEdges(
            DirectedWeightedMultigraph<GraphNode, GraphEdge> graph,
            MapDirective directive,
            Map<String, TypeNode> paramNodes,
            ConstructorNode ctorNode,
            CreationDescriptor creation,
            MethodDefinition method) {

        // Resolve source path
        String sourcePath = directive.source();
        String[] sourceParts = sourcePath.split("\\.");

        // First part is the parameter name (for multi-param) or the property name (for single-param)
        GraphNode currentNode;
        int startIdx;
        if (method.parameters().size() == 1) {
            // Single param: source path is property chain on the single parameter
            currentNode = paramNodes.values().iterator().next();
            startIdx = 0;
        } else {
            // Multi param: first segment is parameter name
            currentNode = paramNodes.get(sourceParts[0]);
            startIdx = 1;
        }

        // Walk the source property chain
        for (int i = startIdx; i < sourceParts.length; i++) {
            String propName = sourceParts[i];
            TypeElement typeElement = asTypeElement(getNodeType(currentNode));
            Set<Property> props = discoverProperties(typeElement);
            Property prop = props.stream()
                .filter(p -> p.name().equals(propName))
                .findFirst()
                .orElse(null);
            if (prop == null) break; // error handling in validate stage

            PropertyNode propNode = new PropertyNode(currentNode, prop);
            graph.addVertex(propNode);
            graph.addEdge(currentNode, propNode, new PropertyAccessEdge(prop));
            currentNode = propNode;
        }

        // Connect to constructor parameter
        String targetPath = directive.target();
        // Find matching constructor parameter index
        List<Property> ctorParams = creation.parameters();
        for (int i = 0; i < ctorParams.size(); i++) {
            if (ctorParams.get(i).name().equals(targetPath)) {
                graph.addEdge(currentNode, ctorNode,
                    new ConstructorParamEdge(targetPath, i));
                break;
            }
        }
    }

    // Helper methods: discoverProperties, asTypeElement, getNodeType, findCreationStrategy
    // ...
}
```

Wire `GraphBuildStage` into Pipeline.

**Step 3: Run test to verify it passes**

Run: `./gradlew :processor:test --tests 'io.github.joke.percolate.stage.GraphBuildStageSpec'`
Expected: PASS

**Step 4: Commit**

```bash
git add processor/src/
git commit -m "feat: implement graph build stage — construct dependency graph"
```

---

## Task 10: Validate Stage + GraphRenderer

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/stage/ValidateStage.java`
- Create: `processor/src/main/java/io/github/joke/percolate/stage/ValidationResult.java`
- Create: `processor/src/main/java/io/github/joke/percolate/graph/GraphRenderer.java`
- Test: `processor/src/test/groovy/io/github/joke/percolate/stage/ValidateStageSpec.groovy`

**Step 1: Write the failing test**

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
            '',
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
            '    public final int age;',  // no matching source property
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
}
```

**Step 2: Implement ValidateStage and GraphRenderer**

```java
// ValidationResult.java
package io.github.joke.percolate.stage;

public final class ValidationResult {
    private final GraphResult graphResult;
    private final boolean hasFatalErrors;

    public ValidationResult(GraphResult graphResult, boolean hasFatalErrors) {
        this.graphResult = graphResult;
        this.hasFatalErrors = hasFatalErrors;
    }

    public GraphResult graphResult() { return graphResult; }
    public boolean hasFatalErrors() { return hasFatalErrors; }
}
```

The `ValidateStage` verifies that every constructor parameter in the graph has an incoming edge (i.e., every target property is reachable from some source). If not, it emits an error using `Messager` with an ASCII graph rendering from `GraphRenderer`.

The `GraphRenderer` produces ASCII output showing the constructor node with checkmarks/crosses for each parameter.

**Step 3: Run test to verify it passes**

Run: `./gradlew :processor:test --tests 'io.github.joke.percolate.stage.ValidateStageSpec'`
Expected: PASS

**Step 4: Commit**

```bash
git add processor/src/
git commit -m "feat: implement validate stage with ASCII graph error output"
```

---

## Task 11: CodeGen Stage — Simple Single-Param Mapper

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/stage/OptimizeStage.java` (pass-through for now)
- Create: `processor/src/main/java/io/github/joke/percolate/stage/OptimizedGraphResult.java`
- Create: `processor/src/main/java/io/github/joke/percolate/stage/CodeGenStage.java`
- Test: `processor/src/test/groovy/io/github/joke/percolate/stage/CodeGenStageSpec.groovy`

**Step 1: Write the failing test**

```groovy
package io.github.joke.percolate.stage

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class CodeGenStageSpec extends Specification {

    def "generates impl for single-param mapper with name-matched properties"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.PersonMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '',
            '@Mapper',
            'public interface PersonMapper {',
            '    PersonDto map(Person person);',
            '}',
        )
        def person = JavaFileObjects.forSourceLines('test.Person',
            'package test;',
            'public class Person {',
            '    public String getName() { return ""; }',
            '    public int getAge() { return 0; }',
            '}',
        )
        def personDto = JavaFileObjects.forSourceLines('test.PersonDto',
            'package test;',
            'public class PersonDto {',
            '    public final String name;',
            '    public final int age;',
            '    public PersonDto(String name, int age) {',
            '        this.name = name;',
            '        this.age = age;',
            '    }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, person, personDto)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation)
            .generatedSourceFile('test.PersonMapperImpl')
            .contentsAsUtf8String()
            .contains('implements PersonMapper')
        assertThat(compilation)
            .generatedSourceFile('test.PersonMapperImpl')
            .contentsAsUtf8String()
            .contains('person.getName()')
        assertThat(compilation)
            .generatedSourceFile('test.PersonMapperImpl')
            .contentsAsUtf8String()
            .contains('person.getAge()')
        assertThat(compilation)
            .generatedSourceFile('test.PersonMapperImpl')
            .contentsAsUtf8String()
            .contains('new test.PersonDto(')
    }
}
```

**Step 2: Implement OptimizeStage (pass-through)**

```java
// OptimizeStage.java — pass-through for now, real optimization in Task 16
package io.github.joke.percolate.stage;

import io.github.joke.percolate.di.RoundScoped;
import javax.inject.Inject;

@RoundScoped
public class OptimizeStage {

    @Inject
    OptimizeStage() {}

    public OptimizedGraphResult execute(ValidationResult validationResult) {
        return new OptimizedGraphResult(validationResult.graphResult());
    }
}
```

**Step 3: Implement CodeGenStage**

```java
// CodeGenStage.java
package io.github.joke.percolate.stage;

import com.palantir.javapoet.*;
import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.graph.edge.*;
import io.github.joke.percolate.graph.node.*;
import io.github.joke.percolate.model.*;
import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.lang.model.element.Modifier;
import org.jgrapht.graph.DirectedWeightedMultigraph;
import java.io.IOException;

@RoundScoped
public class CodeGenStage {

    private final Filer filer;

    @Inject
    CodeGenStage(Filer filer) {
        this.filer = filer;
    }

    public void execute(OptimizedGraphResult optimizedResult) {
        GraphResult graphResult = optimizedResult.graphResult();
        DirectedWeightedMultigraph<GraphNode, GraphEdge> graph = graphResult.graph();

        for (MapperDefinition mapper : graphResult.mappers()) {
            generateMapperImpl(mapper, graph);
        }
    }

    private void generateMapperImpl(
            MapperDefinition mapper,
            DirectedWeightedMultigraph<GraphNode, GraphEdge> graph) {

        ClassName mapperClassName = ClassName.get(mapper.packageName(), mapper.simpleName());
        String implName = mapper.simpleName() + "Impl";

        TypeSpec.Builder implBuilder = TypeSpec.classBuilder(implName)
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(mapperClassName);

        for (MethodDefinition method : mapper.methods()) {
            if (!method.isAbstract()) continue;
            implBuilder.addMethod(generateMethod(method, graph));
        }

        TypeSpec implSpec = implBuilder.build();
        JavaFile javaFile = JavaFile.builder(mapper.packageName(), implSpec).build();

        try {
            javaFile.writeTo(filer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + implName, e);
        }
    }

    private MethodSpec generateMethod(
            MethodDefinition method,
            DirectedWeightedMultigraph<GraphNode, GraphEdge> graph) {

        // Find the MethodNode, ConstructorNode, and traverse the graph
        // to build the constructor call with the right arguments.
        // Each ConstructorParamEdge tells us what value feeds into which constructor param.
        // Walk backwards from each param edge to find the source expression.

        // Build the method using JavaPoet, traversing the graph to produce:
        //   return new TargetType(param.getX(), param.getY(), ...);

        // Implementation details: traverse graph from ConstructorNode,
        // find all incoming ConstructorParamEdge sources,
        // for each source walk back through PropertyAccessEdges to build the expression chain.

        // ... (full implementation)
    }
}
```

Wire all stages into Pipeline:

```java
public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    ParseResult parsed = parseStage.execute(annotations, roundEnv);
    ResolveResult resolved = resolveStage.execute(parsed);
    GraphResult graph = graphBuildStage.execute(resolved);
    ValidationResult validated = validateStage.execute(graph);
    if (validated.hasFatalErrors()) return;
    OptimizedGraphResult optimized = optimizeStage.execute(validated);
    codeGenStage.execute(optimized);
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :processor:test --tests 'io.github.joke.percolate.stage.CodeGenStageSpec'`
Expected: PASS

**Step 5: Commit**

```bash
git add processor/src/
git commit -m "feat: implement code generation for single-param name-matched mappers"
```

---

## Task 12: Resolve Stage — Wildcard Expansion

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/ResolveStage.java`
- Test: `processor/src/test/groovy/io/github/joke/percolate/stage/WildcardExpansionSpec.groovy`

**Step 1: Write the failing test**

```groovy
package io.github.joke.percolate.stage

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class WildcardExpansionSpec extends Specification {

    def "expands wildcard source and maps matching named properties"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.WildcardMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '',
            '@Mapper',
            'public interface WildcardMapper {',
            '    @Map(target = ".", source = "order.*")',
            '    FlatOrder map(Order order);',
            '}',
        )
        def order = JavaFileObjects.forSourceLines('test.Order',
            'package test;',
            'public class Order {',
            '    public long getOrderId() { return 0; }',
            '    public String getStatus() { return ""; }',
            '}',
        )
        def flatOrder = JavaFileObjects.forSourceLines('test.FlatOrder',
            'package test;',
            'public class FlatOrder {',
            '    public final long orderId;',
            '    public final String status;',
            '    public FlatOrder(long orderId, String status) {',
            '        this.orderId = orderId;',
            '        this.status = status;',
            '    }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, order, flatOrder)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation)
            .generatedSourceFile('test.WildcardMapperImpl')
            .contentsAsUtf8String()
            .contains('order.getOrderId()')
        assertThat(compilation)
            .generatedSourceFile('test.WildcardMapperImpl')
            .contentsAsUtf8String()
            .contains('order.getStatus()')
    }
}
```

**Step 2: Add wildcard expansion to ResolveStage**

In `ResolveStage.resolveMethod()`, after same-name matching, handle directives whose source ends with `.*`:
- Parse the source path up to `*` to find the source type
- Discover properties on that type
- For each property, create a new `MapDirective` mapping it to the corresponding target path
- The target `"."` means "root level of the target type"

**Step 3: Run test to verify it passes**

Run: `./gradlew :processor:test --tests 'io.github.joke.percolate.stage.WildcardExpansionSpec'`
Expected: PASS

**Step 4: Commit**

```bash
git add processor/src/
git commit -m "feat: add wildcard expansion to resolve stage"
```

---

## Task 13: CodeGen — Multi-Param Mapper with @Map Directives

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/CodeGenStage.java`
- Test: `processor/src/test/groovy/io/github/joke/percolate/stage/MultiParamCodeGenSpec.groovy`

**Step 1: Write the failing test**

```groovy
package io.github.joke.percolate.stage

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class MultiParamCodeGenSpec extends Specification {

    def "generates impl for multi-param mapper with explicit @Map directives"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.MergeMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '',
            '@Mapper',
            'public interface MergeMapper {',
            '    @Map(target = "id", source = "a.id")',
            '    @Map(target = "label", source = "b.name")',
            '    Merged merge(TypeA a, TypeB b);',
            '}',
        )
        def typeA = JavaFileObjects.forSourceLines('test.TypeA',
            'package test;',
            'public class TypeA {',
            '    public long getId() { return 0; }',
            '}',
        )
        def typeB = JavaFileObjects.forSourceLines('test.TypeB',
            'package test;',
            'public class TypeB {',
            '    public String getName() { return ""; }',
            '}',
        )
        def merged = JavaFileObjects.forSourceLines('test.Merged',
            'package test;',
            'public class Merged {',
            '    public final long id;',
            '    public final String label;',
            '    public Merged(long id, String label) {',
            '        this.id = id;',
            '        this.label = label;',
            '    }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, typeA, typeB, merged)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation)
            .generatedSourceFile('test.MergeMapperImpl')
            .contentsAsUtf8String()
            .contains('a.getId()')
        assertThat(compilation)
            .generatedSourceFile('test.MergeMapperImpl')
            .contentsAsUtf8String()
            .contains('b.getName()')
    }
}
```

**Step 2: Extend CodeGenStage for multi-param methods**

The graph already contains edges from parameter TypeNodes through property chains to the constructor. The code generator walks the graph from each ConstructorParamEdge source back to a parameter TypeNode, building the expression chain (e.g., `a.getId()`, `b.getName()`).

**Step 3: Run test to verify it passes**

Run: `./gradlew :processor:test --tests 'io.github.joke.percolate.stage.MultiParamCodeGenSpec'`
Expected: PASS

**Step 4: Commit**

```bash
git add processor/src/
git commit -m "feat: support multi-param mapper code generation with @Map directives"
```

---

## Task 14: ListMappingStrategy

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/spi/impl/ListMappingStrategy.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/ValidateStage.java` (add generic expansion)
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/CodeGenStage.java` (handle list conversion)
- Test: `processor/src/test/groovy/io/github/joke/percolate/spi/impl/ListMappingStrategySpec.groovy`

**Step 1: Write the failing test**

```groovy
package io.github.joke.percolate.spi.impl

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class ListMappingStrategySpec extends Specification {

    def "generates List<A> to List<B> conversion via converter method"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.ListMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import java.util.List;',
            '',
            '@Mapper',
            'public interface ListMapper {',
            '    Result map(Input input);',
            '    ItemDto mapItem(Item item);',
            '}',
        )
        def input = JavaFileObjects.forSourceLines('test.Input',
            'package test;',
            'import java.util.List;',
            'public class Input {',
            '    public List<Item> getItems() { return null; }',
            '}',
        )
        def item = JavaFileObjects.forSourceLines('test.Item',
            'package test;',
            'public class Item {',
            '    public String getName() { return ""; }',
            '}',
        )
        def itemDto = JavaFileObjects.forSourceLines('test.ItemDto',
            'package test;',
            'public class ItemDto {',
            '    public final String name;',
            '    public ItemDto(String name) { this.name = name; }',
            '}',
        )
        def result = JavaFileObjects.forSourceLines('test.Result',
            'package test;',
            'import java.util.List;',
            'public class Result {',
            '    public final List<ItemDto> items;',
            '    public Result(List<ItemDto> items) { this.items = items; }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, input, item, itemDto, result)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation)
            .generatedSourceFile('test.ListMapperImpl')
            .contentsAsUtf8String()
            .contains('.stream()')
        assertThat(compilation)
            .generatedSourceFile('test.ListMapperImpl')
            .contentsAsUtf8String()
            .contains('this::mapItem')
        assertThat(compilation)
            .generatedSourceFile('test.ListMapperImpl')
            .contentsAsUtf8String()
            .contains('collect(')
    }
}
```

**Step 2: Implement ListMappingStrategy**

```java
// ListMappingStrategy.java
package io.github.joke.percolate.spi.impl;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.spi.GenericMappingStrategy;
import io.github.joke.percolate.spi.GraphFragment;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

@AutoService(GenericMappingStrategy.class)
public class ListMappingStrategy implements GenericMappingStrategy {

    @Override
    public boolean canHandle(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        return isList(source, env) && isList(target, env);
    }

    @Override
    public GraphFragment expand(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        // Extract element types from List<A> and List<B>
        // Return a GraphFragment that says "need converter from A to B"
        // The validate stage will check if such a converter exists in the graph
        // ...
    }

    private boolean isList(TypeMirror type, ProcessingEnvironment env) {
        if (!(type instanceof DeclaredType)) return false;
        DeclaredType declared = (DeclaredType) type;
        String erasedName = env.getTypeUtils().erasure(type).toString();
        return erasedName.equals("java.util.List");
    }
}
```

**Step 3: Integrate lazy expansion into ValidateStage**

During validation, for each GenericPlaceholderEdge:
1. Ask each `GenericMappingStrategy` if it can handle the source/target types
2. If yes, expand the placeholder into concrete graph edges
3. Check that the inner type conversion exists (e.g., `Item` → `ItemDto` has a mapper method)

**Step 4: Extend CodeGenStage for list conversions**

When the graph path includes a list conversion, generate:
```java
input.getItems().stream().map(this::mapItem).collect(java.util.stream.Collectors.toList())
```

**Step 5: Run test to verify it passes**

Run: `./gradlew :processor:test --tests 'io.github.joke.percolate.spi.impl.ListMappingStrategySpec'`
Expected: PASS

**Step 6: Commit**

```bash
git add processor/src/
git commit -m "feat: add List mapping strategy with lazy graph expansion"
```

---

## Task 15: OptionalMappingStrategy

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/spi/impl/OptionalMappingStrategy.java`
- Test: `processor/src/test/groovy/io/github/joke/percolate/spi/impl/OptionalMappingStrategySpec.groovy`

**Step 1: Write the failing test**

```groovy
package io.github.joke.percolate.spi.impl

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class OptionalMappingStrategySpec extends Specification {

    def "wraps non-Optional source into Optional target via Optional.ofNullable"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.OptMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '',
            '@Mapper',
            'public interface OptMapper {',
            '    Result map(Source source);',
            '    TargetItem mapItem(SourceItem item);',
            '}',
        )
        def source = JavaFileObjects.forSourceLines('test.Source',
            'package test;',
            'public class Source {',
            '    public SourceItem getItem() { return null; }',
            '}',
        )
        def sourceItem = JavaFileObjects.forSourceLines('test.SourceItem',
            'package test;',
            'public class SourceItem {',
            '    public String getName() { return ""; }',
            '}',
        )
        def targetItem = JavaFileObjects.forSourceLines('test.TargetItem',
            'package test;',
            'public class TargetItem {',
            '    public final String name;',
            '    public TargetItem(String name) { this.name = name; }',
            '}',
        )
        def result = JavaFileObjects.forSourceLines('test.Result',
            'package test;',
            'import java.util.Optional;',
            'public class Result {',
            '    public final Optional<TargetItem> item;',
            '    public Result(Optional<TargetItem> item) { this.item = item; }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, source, sourceItem, targetItem, result)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation)
            .generatedSourceFile('test.OptMapperImpl')
            .contentsAsUtf8String()
            .contains('Optional.ofNullable')
    }
}
```

**Step 2: Implement OptionalMappingStrategy**

Handles three cases:
- `Optional<A>` → `Optional<B>`: `.map(this::converter)`
- `A` → `Optional<B>`: `Optional.ofNullable(this.converter(a))`
- `Optional<A>` → `B`: extract + convert

**Step 3: Run test and commit**

```bash
git commit -m "feat: add Optional mapping strategy"
```

---

## Task 16: EnumMappingStrategy

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/spi/impl/EnumMappingStrategy.java`
- Test: `processor/src/test/groovy/io/github/joke/percolate/spi/impl/EnumMappingStrategySpec.groovy`

**Step 1: Write the failing test**

```groovy
package io.github.joke.percolate.spi.impl

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class EnumMappingStrategySpec extends Specification {

    def "generates enum-to-enum mapping via valueOf(name())"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.EnumMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '',
            '@Mapper',
            'public interface EnumMapper {',
            '    TargetObj map(SourceObj source);',
            '}',
        )
        def sourceObj = JavaFileObjects.forSourceLines('test.SourceObj',
            'package test;',
            'public class SourceObj {',
            '    public SourceStatus getStatus() { return null; }',
            '}',
        )
        def sourceEnum = JavaFileObjects.forSourceLines('test.SourceStatus',
            'package test;',
            'public enum SourceStatus { ACTIVE, INACTIVE }',
        )
        def targetObj = JavaFileObjects.forSourceLines('test.TargetObj',
            'package test;',
            'public class TargetObj {',
            '    public final TargetStatus status;',
            '    public TargetObj(TargetStatus status) { this.status = status; }',
            '}',
        )
        def targetEnum = JavaFileObjects.forSourceLines('test.TargetStatus',
            'package test;',
            'public enum TargetStatus { ACTIVE, INACTIVE }',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, sourceObj, sourceEnum, targetObj, targetEnum)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation)
            .generatedSourceFile('test.EnumMapperImpl')
            .contentsAsUtf8String()
            .contains('TargetStatus.valueOf(')
        assertThat(compilation)
            .generatedSourceFile('test.EnumMapperImpl')
            .contentsAsUtf8String()
            .contains('.name()')
    }
}
```

**Step 2: Implement EnumMappingStrategy**

Generates: `TargetEnum.valueOf(sourceExpr.name())`

**Step 3: Run test and commit**

```bash
git commit -m "feat: add enum-to-enum mapping strategy"
```

---

## Task 17: Optimize Stage

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/OptimizeStage.java`
- Test: `processor/src/test/groovy/io/github/joke/percolate/stage/OptimizeStageSpec.groovy`

**Step 1: Write the failing test**

Test that when a default method exists and an abstract method has the same signature, the optimizer doesn't generate a redundant implementation. Instead, it prunes methods that are already provided as default.

```groovy
package io.github.joke.percolate.stage

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class OptimizeStageSpec extends Specification {

    def "does not generate methods that delegate trivially to default methods"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.OptMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '',
            '@Mapper',
            'public interface OptMapper {',
            '    Result map(Source source);',
            '    default NameDto mapName(Name name) {',
            '        return new NameDto(name.getFirst() + " " + name.getLast());',
            '    }',
            '}',
        )
        // ... supporting types

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper /* , ... */)

        then:
        assertThat(compilation).succeeded()
        // The generated impl should call this::mapName, not generate a new method for Name→NameDto
    }
}
```

**Step 2: Implement graph optimization**

- Remove unreachable nodes (nodes with no path from any parameter)
- Collapse trivial chains where a conversion is directly handled by a default method
- Ensure default methods are used as MethodCallEdges, not regenerated

**Step 3: Run test and commit**

```bash
git commit -m "feat: implement optimize stage — prune redundant nodes and methods"
```

---

## Task 18: TicketMapper Integration Test

**Files:**
- Test: `processor/src/test/groovy/io/github/joke/percolate/processor/TicketMapperIntegrationSpec.groovy`

**Step 1: Write the integration test**

This is the capstone test using the full TicketMapper example from the test-mapper module.

```groovy
package io.github.joke.percolate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class TicketMapperIntegrationSpec extends Specification {

    def "generates TicketMapperImpl with full mapping pipeline"() {
        given:
        def actor = JavaFileObjects.forSourceLines('io.github.joke.Actor',
            'package io.github.joke;',
            'public class Actor {',
            '    private final String firstName;',
            '    private final String lastName;',
            '    public Actor(String firstName, String lastName) {',
            '        this.firstName = firstName;',
            '        this.lastName = lastName;',
            '    }',
            '    public String getFirstName() { return firstName; }',
            '    public String getLastName() { return lastName; }',
            '}',
        )
        def venue = JavaFileObjects.forSourceLines('io.github.joke.Venue',
            'package io.github.joke;',
            'public class Venue {',
            '    private final String name;',
            '    private final String street;',
            '    private final String zipCode;',
            '    private final String city;',
            '    public Venue(String name, String street, String zipCode, String city) {',
            '        this.name = name; this.street = street;',
            '        this.zipCode = zipCode; this.city = city;',
            '    }',
            '    public String getName() { return name; }',
            '    public String getStreet() { return street; }',
            '    public String getZipCode() { return zipCode; }',
            '    public String getCity() { return city; }',
            '}',
        )
        def order = JavaFileObjects.forSourceLines('io.github.joke.Order',
            'package io.github.joke;',
            'public class Order {',
            '    private final long orderId;',
            '    private final long orderNumber;',
            '    private final Venue venue;',
            '    public Order(long orderId, long orderNumber, Venue venue) {',
            '        this.orderId = orderId; this.orderNumber = orderNumber;',
            '        this.venue = venue;',
            '    }',
            '    public long getOrderId() { return orderId; }',
            '    public long getOrderNumber() { return orderNumber; }',
            '    public Venue getVenue() { return venue; }',
            '}',
        )
        def ticket = JavaFileObjects.forSourceLines('io.github.joke.Ticket',
            'package io.github.joke;',
            'import java.util.List;',
            'public class Ticket {',
            '    private final long ticketId;',
            '    private final String ticketNumber;',
            '    private final List<Actor> actors;',
            '    public Ticket(long ticketId, String ticketNumber, List<Actor> actors) {',
            '        this.ticketId = ticketId; this.ticketNumber = ticketNumber;',
            '        this.actors = actors;',
            '    }',
            '    public long getTicketId() { return ticketId; }',
            '    public String getTicketNumber() { return ticketNumber; }',
            '    public List<Actor> getActors() { return actors; }',
            '}',
        )
        def ticketActor = JavaFileObjects.forSourceLines('io.github.joke.FlatTicket.TicketActor',
            'package io.github.joke;',
            'public class FlatTicket {',
            '    private final long ticketId;',
            '    private final String ticketNumber;',
            '    private final long orderId;',
            '    private final long orderNumber;',
            '    private final java.util.Optional<FlatTicket.TicketVenue> venue;',
            '    private final java.util.List<FlatTicket.TicketActor> actors;',
            '    public FlatTicket(long ticketId, String ticketNumber, long orderId,',
            '            long orderNumber, java.util.Optional<FlatTicket.TicketVenue> venue,',
            '            java.util.List<FlatTicket.TicketActor> actors) {',
            '        this.ticketId = ticketId; this.ticketNumber = ticketNumber;',
            '        this.orderId = orderId; this.orderNumber = orderNumber;',
            '        this.venue = venue; this.actors = actors;',
            '    }',
            '    public static class TicketActor {',
            '        private final String name;',
            '        public TicketActor(String name) { this.name = name; }',
            '        public String getName() { return name; }',
            '    }',
            '    public static class TicketVenue {',
            '        private final String name;',
            '        private final String street;',
            '        private final String zip;',
            '        public TicketVenue(String name, String street, String zip) {',
            '            this.name = name; this.street = street; this.zip = zip;',
            '        }',
            '    }',
            '}',
        )
        def ticketMapper = JavaFileObjects.forSourceLines('io.github.joke.TicketMapper',
            'package io.github.joke;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '',
            '@Mapper',
            'public interface TicketMapper {',
            '    @Map(target = "ticketId", source = "ticket.ticketId")',
            '    @Map(target = "ticketNumber", source = "ticket.ticketNumber")',
            '    @Map(target = "actors", source = "ticket.actors")',
            '    @Map(target = ".", source = "order.*")',
            '    FlatTicket mapPerson(Ticket ticket, Order order);',
            '',
            '    @Map(target = "zip", source = "zipCode")',
            '    FlatTicket.TicketVenue mapVenue(Venue venue);',
            '',
            '    default FlatTicket.TicketActor mapActor(Actor actor) {',
            '        String name = actor.getFirstName() + " " + actor.getLastName();',
            '        return new FlatTicket.TicketActor(name);',
            '    }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(actor, venue, order, ticket, ticketActor, ticketMapper)

        then:
        assertThat(compilation).succeeded()

        and: 'generates TicketMapperImpl'
        assertThat(compilation)
            .generatedSourceFile('io.github.joke.TicketMapperImpl')

        and: 'mapPerson uses ticket parameter for ticketId and ticketNumber'
        def impl = compilation.generatedSourceFile('io.github.joke.TicketMapperImpl')
            .get().getCharContent(false).toString()
        impl.contains('ticket.getTicketId()')
        impl.contains('ticket.getTicketNumber()')

        and: 'mapPerson uses order wildcard expansion for orderId, orderNumber, venue'
        impl.contains('order.getOrderId()')
        impl.contains('order.getOrderNumber()')

        and: 'actors mapped via stream with default method'
        impl.contains('.stream()')
        impl.contains('this::mapActor')
        impl.contains('collect(')

        and: 'venue wrapped in Optional.ofNullable with mapVenue call'
        impl.contains('Optional.ofNullable')
        impl.contains('this.mapVenue(')

        and: 'mapVenue maps zipCode to zip'
        impl.contains('venue.getZipCode()')

        and: 'mapActor is NOT generated (it is a default method)'
        !impl.contains('public FlatTicket.TicketActor mapActor')
    }
}
```

**Step 2: Run the test and fix any issues**

Run: `./gradlew :processor:test --tests 'io.github.joke.percolate.processor.TicketMapperIntegrationSpec'`
Expected: PASS (if all previous tasks are implemented correctly)

This test exercises:
- Multi-param method with explicit `@Map` directives
- Wildcard expansion (`order.*`)
- List mapping via converter (`List<Actor>` → `List<TicketActor>`)
- Optional wrapping (`Venue` → `Optional<TicketVenue>`)
- Default method recognition (mapActor not regenerated)
- Property renaming (`zipCode` → `zip`)
- Same-name auto-matching on mapVenue (`name`, `street`)

**Step 3: Commit**

```bash
git add processor/src/test/
git commit -m "feat: add TicketMapper end-to-end integration test"
```

---

## Task 19: Verify Full Build

**Step 1: Run the complete build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

This verifies:
- All compilation passes (Java 8 target, -Werror)
- ErrorProne + NullAway pass
- Spotless formatting passes
- All tests pass

**Step 2: Fix any issues**

If spotless fails: `./gradlew spotlessApply`
If NullAway fails: add `@Nullable` annotations or fix null handling.
If ErrorProne flags issues: address the specific warnings.

**Step 3: Commit any fixes**

```bash
git add -u
git commit -m "fix: address build warnings and formatting"
```

---

## Summary

| Task | Description | Key Test |
|------|------------|----------|
| 1 | Processor + Dagger skeleton | Empty @Mapper compiles |
| 2 | Model classes | (build-only) |
| 3 | SPI interfaces | (build-only) |
| 4 | Parse stage | @Mapper with methods parsed |
| 5 | Property discovery (Getter/Field) | (transitively via Task 11) |
| 6 | Resolve — same-name matching | Auto-matched properties compile |
| 7 | Constructor creation strategy | (transitively via Task 9) |
| 8 | Graph nodes and edges | (build-only) |
| 9 | Graph Build stage | Simple mapper builds graph |
| 10 | Validate stage + GraphRenderer | Missing property → error with graph |
| 11 | CodeGen — simple mapper | Generated impl with getters + constructor |
| 12 | Resolve — wildcard expansion | `source.*` expanded correctly |
| 13 | CodeGen — multi-param | Multi-param with @Map generates correctly |
| 14 | ListMappingStrategy | `List<A>` → `List<B>` via stream |
| 15 | OptionalMappingStrategy | `A` → `Optional<B>` via ofNullable |
| 16 | EnumMappingStrategy | Enum → Enum via valueOf(name()) |
| 17 | Optimize stage | Prune redundant methods |
| 18 | TicketMapper integration | Full end-to-end |
| 19 | Full build verification | `./gradlew build` passes |

# Pre-Pipeline Separation of Concerns — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix separation of concerns so `ParseStage` only discovers mapper interfaces, a new `ParseMapperStage` handles per-mapper parsing inside the pipeline, and `PercolateProcessor` delegates to a single round-level `RoundProcessor`.

**Architecture:** Two new classes — `ParseMapperStage` (unscoped, TypeElement → MapperDefinition, first stage in Pipeline) and `RoundProcessor` (@RoundScoped, owns the round loop). `ParseStage` is renamed `MapperDiscoveryStage` and stripped to discovery only (returns `List<TypeElement>`). `RoundComponent` exposes only `processor()`. `PercolateProcessor.process` becomes a one-liner delegation.

**Tech Stack:** Java 11, Dagger 2 (DI), Spock + Google Compile Testing (tests), Gradle, ErrorProne + NullAway (`-Werror`).

---

## Background

Current flow:
```
PercolateProcessor
  └─ round.parseStage().execute(annotations, roundEnv)   →  List<MapperDefinition>
       .forEach(round.pipeline()::process)
```

After this plan:
```
PercolateProcessor
  └─ round.processor().process(annotations, roundEnv)

RoundProcessor
  └─ discoveryStage.execute(annotations, roundEnv)       →  List<TypeElement>
       .forEach(pipeline::process)

Pipeline.process(TypeElement)
  ├─ ParseMapperStage.execute(typeElement)               →  MapperDefinition
  ├─ RegistrationStage.execute(mapper)                   →  MethodRegistry
  ├─ BindingStage.execute(registry)                      →  MethodRegistry
  └─ WiringStage.execute(registry)                       →  MethodRegistry
```

Key rules:
- Run tests after every task: `./gradlew :processor:test`
- Build must stay clean (ErrorProne + NullAway, `-Werror`)
- The same 7 downstream tests remain expected failures throughout: `CodeGenStageSpec`, `MultiParamCodeGenSpec`, `WildcardExpansionSpec`, `ValidateStageSpec` (×2), `ValidateStageConversionSpec`, `TicketMapperIntegrationSpec`
- Commit after every task

---

## Task 1: Create `ParseMapperStage`

New unscoped stage that takes a `TypeElement` and returns a `MapperDefinition`. Contains `parseMapper`, `parseMethod`, and `extractMapDirectives` — the mapper-specific parsing logic extracted from `ParseStage`.

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/stage/ParseMapperStage.java`
- Create: `processor/src/test/groovy/io/github/joke/percolate/stage/ParseMapperStageSpec.groovy`

**Step 1: Write the failing test**

Create `ParseMapperStageSpec.groovy`:

```groovy
package io.github.joke.percolate.stage

import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class ParseMapperStageSpec extends Specification {

    def "abstract method with @Map directive is parsed and compiles"() {
        given:
        def src = JavaFileObjects.forSourceLines('test.Src',
            'package test;',
            'public class Src { public String getFirstName() { return ""; } }')
        def tgt = JavaFileObjects.forSourceLines('test.Tgt',
            'package test;',
            'public class Tgt { public final String name;',
            '    public Tgt(String name) { this.name = name; } }')
        def mapper = JavaFileObjects.forSourceLines('test.MyMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '@Mapper public interface MyMapper {',
            '    @Map(target = "name", source = "firstName")',
            '    Tgt map(Src src);',
            '}')

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(src, tgt, mapper)

        then:
        assertThat(compilation).succeeded()
    }

    def "default method is parsed as non-abstract and compiles"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.HelpMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper public interface HelpMapper {',
            '    default String helper(String s) { return s.trim(); }',
            '}')

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper)

        then:
        assertThat(compilation).succeeded()
    }

    def "multiple @Map annotations on a method are all parsed"() {
        given:
        def src = JavaFileObjects.forSourceLines('test.Src',
            'package test;',
            'public class Src {',
            '    public String getFirstName() { return ""; }',
            '    public int getYears() { return 0; }',
            '}')
        def tgt = JavaFileObjects.forSourceLines('test.Tgt',
            'package test;',
            'public class Tgt { public final String name; public final int age;',
            '    public Tgt(String name, int age) { this.name = name; this.age = age; } }')
        def mapper = JavaFileObjects.forSourceLines('test.MultiMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '@Mapper public interface MultiMapper {',
            '    @Map(target = "name", source = "firstName")',
            '    @Map(target = "age", source = "years")',
            '    Tgt map(Src src);',
            '}')

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(src, tgt, mapper)

        then:
        assertThat(compilation).succeeded()
    }
}
```

**Step 2: Run test to confirm it currently passes (coverage via existing pipeline)**

```bash
./gradlew :processor:test --tests 'ParseMapperStageSpec'
```

Expected: FAIL — class does not exist yet.

**Step 3: Implement `ParseMapperStage.java`**

```java
package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.Modifier.ABSTRACT;

import io.github.joke.percolate.MapList;
import io.github.joke.percolate.model.MapDirective;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.model.ParameterDefinition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

public class ParseMapperStage {

    private final Elements elements;

    @Inject
    ParseMapperStage(Elements elements) {
        this.elements = elements;
    }

    public MapperDefinition execute(TypeElement typeElement) {
        String packageName = elements.getPackageOf(typeElement).getQualifiedName().toString();
        String simpleName = typeElement.getSimpleName().toString();
        List<MethodDefinition> methods = typeElement.getEnclosedElements().stream()
                .filter(e -> e instanceof ExecutableElement)
                .map(e -> (ExecutableElement) e)
                .map(this::parseMethod)
                .collect(toList());
        return new MapperDefinition(typeElement, packageName, simpleName, methods);
    }

    private MethodDefinition parseMethod(ExecutableElement method) {
        String name = method.getSimpleName().toString();
        boolean isAbstract = method.getModifiers().contains(ABSTRACT);
        List<ParameterDefinition> parameters = method.getParameters().stream()
                .map(param -> new ParameterDefinition(param.getSimpleName().toString(), param.asType()))
                .collect(toList());
        List<MapDirective> directives = extractMapDirectives(method);
        return new MethodDefinition(method, name, method.getReturnType(), parameters, isAbstract, directives);
    }

    private List<MapDirective> extractMapDirectives(ExecutableElement method) {
        List<MapDirective> directives = new ArrayList<>();
        MapList mapList = method.getAnnotation(MapList.class);
        if (mapList != null) {
            Arrays.stream(mapList.value())
                    .map(m -> new MapDirective(m.target(), m.source()))
                    .forEach(directives::add);
            return directives;
        }
        io.github.joke.percolate.Map map = method.getAnnotation(io.github.joke.percolate.Map.class);
        if (map != null) {
            directives.add(new MapDirective(map.target(), map.source()));
        }
        return directives;
    }
}
```

Note: no `@RoundScoped` — stateless pure function. No fields beyond injected infrastructure.

**Step 4: Run the tests**

```bash
./gradlew :processor:test --tests 'ParseMapperStageSpec'
```

Expected: all 3 PASS.

**Step 5: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/stage/ParseMapperStage.java \
        processor/src/test/groovy/io/github/joke/percolate/stage/ParseMapperStageSpec.groovy
git commit -m "feat: add ParseMapperStage — mapper-specific parsing extracted from ParseStage"
```

---

## Task 2: Big restructure — rename, rewire, and simplify

This task changes several tightly coupled classes and must be done in one pass — they cannot be changed independently without breaking compilation.

Changes:
- `ParseStage` → deleted, replaced by `MapperDiscoveryStage` (returns `List<TypeElement>`)
- `Pipeline.process(MapperDefinition)` → `process(TypeElement)` (calls `ParseMapperStage` first)
- New `RoundProcessor` (@RoundScoped, owns the round loop)
- `RoundComponent` → exposes only `RoundProcessor processor()`
- `PercolateProcessor.process` → single delegation call
- `ParseStageSpec` → renamed `MapperDiscoveryStageSpec` (keep only the validation test)

**Files:**
- Delete: `processor/src/main/java/io/github/joke/percolate/stage/ParseStage.java`
- Create: `processor/src/main/java/io/github/joke/percolate/stage/MapperDiscoveryStage.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/processor/Pipeline.java`
- Create: `processor/src/main/java/io/github/joke/percolate/processor/RoundProcessor.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/di/RoundComponent.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/processor/PercolateProcessor.java`
- Delete: `processor/src/test/groovy/io/github/joke/percolate/stage/ParseStageSpec.groovy`
- Create: `processor/src/test/groovy/io/github/joke/percolate/stage/MapperDiscoveryStageSpec.groovy`

**Step 1: Delete `ParseStage.java`**

```bash
rm processor/src/main/java/io/github/joke/percolate/stage/ParseStage.java
```

**Step 2: Create `MapperDiscoveryStage.java`**

```java
package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.ElementKind.INTERFACE;
import static javax.tools.Diagnostic.Kind.ERROR;

import io.github.joke.percolate.Mapper;
import io.github.joke.percolate.di.RoundScoped;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

@RoundScoped
public class MapperDiscoveryStage {

    private final Messager messager;

    @Inject
    MapperDiscoveryStage(Messager messager) {
        this.messager = messager;
    }

    public List<TypeElement> execute(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return roundEnv.getElementsAnnotatedWith(Mapper.class).stream()
                .filter(this::validateIsInterface)
                .map(element -> (TypeElement) element)
                .collect(toList());
    }

    private boolean validateIsInterface(Element element) {
        if (element.getKind() != INTERFACE) {
            messager.printMessage(ERROR, "@Mapper can only be applied to interfaces", element);
            return false;
        }
        return true;
    }
}
```

**Step 3: Update `Pipeline.java`**

Replace the entire file:

```java
package io.github.joke.percolate.processor;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.stage.BindingStage;
import io.github.joke.percolate.stage.MethodRegistry;
import io.github.joke.percolate.stage.ParseMapperStage;
import io.github.joke.percolate.stage.RegistrationStage;
import io.github.joke.percolate.stage.WiringStage;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;

@RoundScoped
public class Pipeline {

    private final ParseMapperStage parseMapperStage;
    private final RegistrationStage registrationStage;
    private final BindingStage bindingStage;
    private final WiringStage wiringStage;

    @Inject
    Pipeline(ParseMapperStage parseMapperStage, RegistrationStage registrationStage,
             BindingStage bindingStage, WiringStage wiringStage) {
        this.parseMapperStage = parseMapperStage;
        this.registrationStage = registrationStage;
        this.bindingStage = bindingStage;
        this.wiringStage = wiringStage;
    }

    public void process(TypeElement typeElement) {
        MethodRegistry registry = registrationStage.execute(parseMapperStage.execute(typeElement));
        bindingStage.execute(registry);
        wiringStage.execute(registry);
        // ValidateStage, OptimizeStage, CodeGenStage reconnected in future redesign
    }
}
```

**Step 4: Create `RoundProcessor.java`**

```java
package io.github.joke.percolate.processor;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.stage.MapperDiscoveryStage;
import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;

@RoundScoped
public class RoundProcessor {

    private final MapperDiscoveryStage discoveryStage;
    private final Pipeline pipeline;

    @Inject
    RoundProcessor(MapperDiscoveryStage discoveryStage, Pipeline pipeline) {
        this.discoveryStage = discoveryStage;
        this.pipeline = pipeline;
    }

    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        discoveryStage.execute(annotations, roundEnv).forEach(pipeline::process);
    }
}
```

**Step 5: Update `RoundComponent.java`**

Replace the entire file:

```java
package io.github.joke.percolate.di;

import dagger.Subcomponent;
import io.github.joke.percolate.processor.RoundProcessor;

@RoundScoped
@Subcomponent(modules = RoundModule.class)
public interface RoundComponent {

    RoundProcessor processor();

    @Subcomponent.Factory
    interface Factory {
        RoundComponent create();
    }
}
```

**Step 6: Update `PercolateProcessor.java`**

Replace only the `process` method and remove the now-unused `RoundComponent` import:

```java
@Override
public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (annotations.isEmpty()) {
        return false;
    }
    component.roundComponentFactory().create().processor().process(annotations, roundEnv);
    return false;
}
```

Remove the import `import io.github.joke.percolate.di.RoundComponent;` — it is no longer referenced by name.

**Step 7: Delete `ParseStageSpec.groovy` and create `MapperDiscoveryStageSpec.groovy`**

```bash
rm processor/src/test/groovy/io/github/joke/percolate/stage/ParseStageSpec.groovy
```

Create `MapperDiscoveryStageSpec.groovy` — keeps only the validation test from `ParseStageSpec`. The parsing tests (abstract methods, @Map annotations) have moved to `ParseMapperStageSpec` in Task 1.

```groovy
package io.github.joke.percolate.stage

import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class MapperDiscoveryStageSpec extends Specification {

    def "emits error when @Mapper applied to a class"() {
        given:
        def badMapper = JavaFileObjects.forSourceLines('test.BadMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper',
            'public class BadMapper {}')

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(badMapper)

        then:
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining('@Mapper can only be applied to interfaces')
    }
}
```

**Step 8: Build to verify compilation**

```bash
./gradlew :processor:compileJava
```

Fix any compilation errors before running tests. Common issues:
- Any remaining `import ...ParseStage` anywhere — search with `grep -r "ParseStage" processor/src/main/`
- Unused imports in `PercolateProcessor` after removing `RoundComponent`

**Step 9: Run the full test suite**

```bash
./gradlew :processor:test
```

Expected: `ParseMapperStageSpec` ✓, `MapperDiscoveryStageSpec` ✓, `BindingStageSpec` ✓, `WiringStageSpec` ✓, `RegistrationStageSpec` ✓. Same 7 downstream tests remain failing.

**Step 10: Commit**

```bash
git add -u
git add processor/src/main/java/io/github/joke/percolate/stage/MapperDiscoveryStage.java \
        processor/src/main/java/io/github/joke/percolate/processor/RoundProcessor.java \
        processor/src/test/groovy/io/github/joke/percolate/stage/MapperDiscoveryStageSpec.groovy
git commit -m "refactor: MapperDiscoveryStage, ParseMapperStage in pipeline, RoundProcessor as single entry point"
```

---

## Final check

```bash
./gradlew build
```

Expected: same passing tests as before, same 7 known failures, no new failures.

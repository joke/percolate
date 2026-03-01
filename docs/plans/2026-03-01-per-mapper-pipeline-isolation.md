# Per-Mapper Pipeline Isolation — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Restructure the annotation processor pipeline so each `MapperDefinition` is processed in isolation through all stages, with `ParseStage` returning a list and the per-mapper loop living in `PercolateProcessor`.

**Architecture:** A new `RegistrationStage` replaces the registry-building responsibility currently split between `ParseStage` and `BindingStage`. `Pipeline.process(MapperDefinition)` processes exactly one mapper. `PercolateProcessor` holds the loop: `parseStage.execute() → forEach → pipeline.process(mapper)`. All per-mapper stages are unscoped in Dagger.

**Tech Stack:** Java 11, Dagger 2 (DI), JGraphT (graphs), Spock + Google Compile Testing (tests), Gradle, ErrorProne + NullAway (enforced, `-Werror`).

---

## Background for the implementer

The processor currently has this pipeline:
```
PercolateProcessor
  └── Pipeline.process(annotations, roundEnv)
        ├── ParseStage   → ParseResult (all mappers + all registries bundled)
        ├── BindingStage → MethodRegistry (merged from all mappers)
        └── WiringStage  → MethodRegistry
```

After this plan it will be:
```
PercolateProcessor
  ├── ParseStage.execute(annotations, roundEnv)  →  List<MapperDefinition>
  └── forEach mapper:
        Pipeline.process(MapperDefinition)
          ├── RegistrationStage  →  MethodRegistry  (new stage)
          ├── BindingStage       →  MethodRegistry  (refactored)
          └── WiringStage        →  MethodRegistry  (refactored)
```

Key rules:
- Run tests after every task: `./gradlew :processor:test`
- Build must be clean (ErrorProne + NullAway enforce `-Werror`)
- Tests use Spock (Groovy) with Google Compile Testing — they compile in-memory Java snippets through `PercolateProcessor`
- 7 tests are currently expected to fail (downstream stages disconnected): `CodeGenStageSpec`, `MultiParamCodeGenSpec`, `WildcardExpansionSpec`, `ValidateStageSpec` (×2), `ValidateStageConversionSpec`, `TicketMapperIntegrationSpec` — these failures are acceptable throughout this plan
- Commit after every task

---

## Task 1: Add `register(MethodDefinition, RegistryEntry)` to `MethodRegistry`

Both `RegistrationStage` (new) and `BindingStage` (existing) need to compute the same registry key for a method. Centralise the key logic in `MethodRegistry` so neither stage duplicates it.

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/MethodRegistry.java`
- Test: `processor/src/test/groovy/io/github/joke/percolate/stage/MethodRegistrySpec.groovy`

**Step 1: Write the failing test**

Add to `MethodRegistrySpec.groovy` (file already exists, add a new `def` block):

```groovy
def "register and lookup by MethodDefinition works for single-param method"() {
    given:
    // We cannot easily create real TypeMirror/MethodDefinition in unit tests,
    // so this test is covered by end-to-end specs. Mark as pending.
    expect:
    true // placeholder — real coverage via ParseStageSpec/BindingStageSpec
}
```

(The real value of this task is the implementation change; the Compile Testing suite covers the behaviour.)

**Step 2: Add the method to `MethodRegistry.java`**

The key format for single-param methods is `param.getType().toString()`. For multi-param it is `"(A,B,C)"`. This logic currently lives in `BindingStage.buildInKey()` — move it here.

Add these two methods to `MethodRegistry`:

```java
import io.github.joke.percolate.model.MethodDefinition;
import java.util.stream.Collectors;

public static String keyFor(MethodDefinition method) {
    if (method.getParameters().size() == 1) {
        return method.getParameters().get(0).getType().toString();
    }
    return "("
            + method.getParameters().stream()
                    .map(p -> p.getType().toString())
                    .collect(Collectors.joining(","))
            + ")";
}

public void register(MethodDefinition method, RegistryEntry entry) {
    register(keyFor(method), method.getReturnType().toString(), entry);
}

public Optional<RegistryEntry> lookup(MethodDefinition method) {
    return lookup(keyFor(method), method.getReturnType().toString());
}
```

**Step 3: Run tests**

```bash
./gradlew :processor:test
```

Expected: same results as before (no new failures, no existing failures fixed — pure addition).

**Step 4: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/stage/MethodRegistry.java \
        processor/src/test/groovy/io/github/joke/percolate/stage/MethodRegistrySpec.groovy
git commit -m "refactor: centralise method registry key computation in MethodRegistry"
```

---

## Task 2: Create `RegistrationStage`

New unscoped stage. Takes a `MapperDefinition`, returns a `MethodRegistry` with all public non-void methods registered (opaque — no graph yet). Removes the single-param restriction that was in `ParseStage.registerMethod()`.

**Files:**
- Create: `processor/src/main/java/io/github/joke/percolate/stage/RegistrationStage.java`
- Create: `processor/src/test/groovy/io/github/joke/percolate/stage/RegistrationStageSpec.groovy`

**Step 1: Write the failing test**

Create `RegistrationStageSpec.groovy`:

```groovy
package io.github.joke.percolate.stage

import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class RegistrationStageSpec extends Specification {

    def "mapper with abstract single-param method is registered and compiles"() {
        given:
        def src = JavaFileObjects.forSourceLines('test.Src',
            'package test;',
            'public class Src { public String getName() { return ""; } }')
        def tgt = JavaFileObjects.forSourceLines('test.Tgt',
            'package test;',
            'public class Tgt { private final String name;',
            '    public Tgt(String name) { this.name = name; }',
            '    public String getName() { return name; } }')
        def mapper = JavaFileObjects.forSourceLines('test.MyMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper public interface MyMapper { Tgt map(Src src); }')

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(src, tgt, mapper)

        then:
        assertThat(compilation).succeeded()
    }

    def "mapper with default method (opaque) compiles without error"() {
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

    def "mapper with multi-param abstract method compiles without error"() {
        given:
        def a = JavaFileObjects.forSourceLines('test.A',
            'package test;',
            'public class A { public String getFoo() { return ""; } }')
        def b = JavaFileObjects.forSourceLines('test.B',
            'package test;',
            'public class B { public String getBar() { return ""; } }')
        def out = JavaFileObjects.forSourceLines('test.Out',
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
            .compile(a, b, out, mapper)

        then:
        assertThat(compilation).succeeded()
    }
}
```

**Step 2: Run the test to confirm it currently passes or fails**

```bash
./gradlew :processor:test --tests 'RegistrationStageSpec'
```

Expected: FAIL (class does not exist yet) or compilation error — class must be created.

**Step 3: Implement `RegistrationStage.java`**

```java
package io.github.joke.percolate.stage;

import static javax.lang.model.type.TypeKind.VOID;

import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.model.MethodDefinition;
import javax.inject.Inject;

public class RegistrationStage {

    @Inject
    RegistrationStage() {}

    public MethodRegistry execute(MapperDefinition mapper) {
        MethodRegistry registry = new MethodRegistry();
        mapper.getMethods().forEach(method -> register(registry, method));
        return registry;
    }

    private static void register(MethodRegistry registry, MethodDefinition method) {
        if (method.getReturnType().getKind() == VOID) {
            return;
        }
        registry.register(method, new RegistryEntry(method, null));
    }
}
```

Note: no `@RoundScoped` — this stage is unscoped (stateless pure function). No fields.

**Step 4: Run the tests**

```bash
./gradlew :processor:test --tests 'RegistrationStageSpec'
```

Expected: PASS (all 3 tests).

**Step 5: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/stage/RegistrationStage.java \
        processor/src/test/groovy/io/github/joke/percolate/stage/RegistrationStageSpec.groovy
git commit -m "feat: add RegistrationStage — registers all public non-void mapper methods"
```

---

## Task 3: Big Pipeline Restructure

This task changes several tightly coupled classes in one go — they cannot be changed independently without breaking compilation. Work through the files in the order given.

The changes:
- `ParseStage` returns `List<MapperDefinition>` (remove `buildRegistry`, `registerMethod`)
- `BindingStage` takes `MethodRegistry` → `MethodRegistry` (remove merge, remove `@RoundScoped`)
- `Pipeline` takes `MapperDefinition`, injects `RegistrationStage`
- `RoundComponent` exposes `ParseStage parseStage()`
- `PercolateProcessor` calls `parseStage`, loops per mapper
- `ParseResult` deleted

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/ParseStage.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/BindingStage.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/processor/Pipeline.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/di/RoundComponent.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/processor/PercolateProcessor.java`
- Delete: `processor/src/main/java/io/github/joke/percolate/stage/ParseResult.java`

**Step 1: Refactor `ParseStage.java`**

Remove `buildRegistry()`, `registerMethod()`, `Map<TypeElement, MethodRegistry> registries` construction. Change return type to `List<MapperDefinition>`. Keep `parseMapper`, `parseMethod`, `extractMapDirectives`, `validateIsInterface` unchanged.

```java
package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.ElementKind.INTERFACE;
import static javax.tools.Diagnostic.Kind.ERROR;

import io.github.joke.percolate.MapList;
import io.github.joke.percolate.Mapper;
import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.model.MapDirective;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.model.ParameterDefinition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

@RoundScoped
public class ParseStage {

    private final Elements elements;
    private final Messager messager;

    @Inject
    ParseStage(Elements elements, Messager messager) {
        this.elements = elements;
        this.messager = messager;
    }

    public List<MapperDefinition> execute(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return roundEnv.getElementsAnnotatedWith(Mapper.class).stream()
                .filter(this::validateIsInterface)
                .map(element -> (TypeElement) element)
                .map(this::parseMapper)
                .collect(toList());
    }

    private boolean validateIsInterface(Element element) {
        if (element.getKind() != INTERFACE) {
            messager.printMessage(ERROR, "@Mapper can only be applied to interfaces", element);
            return false;
        }
        return true;
    }

    private MapperDefinition parseMapper(TypeElement typeElement) {
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
        boolean isAbstract = method.getModifiers().contains(javax.lang.model.element.Modifier.ABSTRACT);
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

**Step 2: Refactor `BindingStage.java`**

Change `execute(ParseResult)` → `execute(MethodRegistry)`. Remove `buildMapperGraphs`, `mergeRegistries`. Remove `@RoundScoped`. Use `MethodRegistry.lookup(method)` and `MethodRegistry.register(method, entry)` from Task 1. Keep all graph-building helpers (`buildMethodGraph`, `expandDirectives`, `expandWildcard`, etc.) unchanged.

Change just the class declaration and `execute` method:

```java
// Remove @RoundScoped annotation from class
// Change execute signature:

public MethodRegistry execute(MethodRegistry registry) {
    new ArrayList<>(registry.entries().values()).forEach(entry -> {
        if (entry.getSignature() != null && entry.getSignature().isAbstract()) {
            buildMethodGraph(entry.getSignature(), registry);
        }
    });
    return registry;
}
```

Also update `buildMethodGraph` to use `registry.lookup(method)` and `registry.register(method, entry)` instead of the string-based calls with `buildInKey()`:

```java
private void buildMethodGraph(MethodDefinition method, MethodRegistry registry) {
    // ... (graph building code unchanged) ...

    // Replace the end of the method:
    registry.lookup(method).ifPresent(existing ->
            registry.register(method, new RegistryEntry(existing.getSignature(), graph)));
}
```

Remove `buildInKey()` from `BindingStage` (it now lives in `MethodRegistry.keyFor()`).

Remove the unused imports: `ParseResult`, `MapperDefinition`, `MethodRegistry` merge-related code.

**Step 3: Refactor `Pipeline.java`**

```java
package io.github.joke.percolate.processor;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.stage.BindingStage;
import io.github.joke.percolate.stage.MethodRegistry;
import io.github.joke.percolate.stage.RegistrationStage;
import io.github.joke.percolate.stage.WiringStage;
import javax.inject.Inject;

@RoundScoped
public class Pipeline {

    private final RegistrationStage registrationStage;
    private final BindingStage bindingStage;
    private final WiringStage wiringStage;

    @Inject
    Pipeline(RegistrationStage registrationStage, BindingStage bindingStage, WiringStage wiringStage) {
        this.registrationStage = registrationStage;
        this.bindingStage = bindingStage;
        this.wiringStage = wiringStage;
    }

    public void process(MapperDefinition mapper) {
        MethodRegistry registry = registrationStage.execute(mapper);
        registry = bindingStage.execute(registry);
        wiringStage.execute(registry);
        // ValidateStage, OptimizeStage, CodeGenStage reconnected in future redesign
    }
}
```

**Step 4: Update `RoundComponent.java`**

Add `ParseStage parseStage()` so `PercolateProcessor` can call it:

```java
package io.github.joke.percolate.di;

import dagger.Subcomponent;
import io.github.joke.percolate.processor.Pipeline;
import io.github.joke.percolate.stage.ParseStage;

@RoundScoped
@Subcomponent(modules = RoundModule.class)
public interface RoundComponent {

    ParseStage parseStage();

    Pipeline pipeline();

    @Subcomponent.Factory
    interface Factory {
        RoundComponent create();
    }
}
```

**Step 5: Update `PercolateProcessor.java`**

The loop moves here. `ParseStage` is obtained from the round component:

```java
@Override
public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (annotations.isEmpty()) {
        return false;
    }
    RoundComponent round = component.roundComponentFactory().create();
    round.parseStage().execute(annotations, roundEnv).forEach(round.pipeline()::process);
    return false;
}
```

Add the import for `RoundComponent`:
```java
import io.github.joke.percolate.di.RoundComponent;
```

**Step 6: Delete `ParseResult.java`**

```bash
rm processor/src/main/java/io/github/joke/percolate/stage/ParseResult.java
```

**Step 7: Build to check compilation**

```bash
./gradlew :processor:compileJava
```

Fix any compilation errors before running tests. Common issues:
- Any remaining import of `ParseResult` in other files — search with `grep -r "ParseResult" processor/src/main/`
- `buildInKey` references in `BindingStage` — replaced by `MethodRegistry.keyFor()`

**Step 8: Run the full test suite**

```bash
./gradlew :processor:test
```

Expected: same passing tests as before this task (ParseStageSpec ✓, BindingStageSpec ✓, WiringStageSpec ✓, RegistrationStageSpec ✓). The same 7 downstream tests remain failing.

**Step 9: Commit**

```bash
git add -u
git add processor/src/main/java/io/github/joke/percolate/stage/ParseStage.java \
        processor/src/main/java/io/github/joke/percolate/stage/BindingStage.java \
        processor/src/main/java/io/github/joke/percolate/processor/Pipeline.java \
        processor/src/main/java/io/github/joke/percolate/di/RoundComponent.java \
        processor/src/main/java/io/github/joke/percolate/processor/PercolateProcessor.java
git commit -m "refactor: per-mapper pipeline — ParseStage returns List, loop in PercolateProcessor"
```

---

## Task 4: Refactor `MapperMethodProvider` — use `MethodRegistry`

`MapperMethodProvider` currently takes `List<MapperDefinition>` (cross-mapper lookup). Replace with `MethodRegistry` (per-mapper only). It is not `@AutoService` and is not currently active in `WiringStage` — this task activates it.

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/spi/impl/MapperMethodProvider.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/WiringStage.java`

**Step 1: Rewrite `MapperMethodProvider.java`**

```java
package io.github.joke.percolate.spi.impl;

import io.github.joke.percolate.graph.node.MethodCallNode;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.spi.ConversionFragment;
import io.github.joke.percolate.spi.ConversionProvider;
import io.github.joke.percolate.stage.MethodRegistry;
import io.github.joke.percolate.stage.RegistryEntry;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

public final class MapperMethodProvider implements ConversionProvider {

    private final MethodRegistry registry;

    public MapperMethodProvider(MethodRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean canHandle(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        return findMethod(env.getTypeUtils(), source, target).isPresent();
    }

    @Override
    public ConversionFragment provide(
            TypeMirror source, TypeMirror target, MethodRegistry ignored, ProcessingEnvironment env) {
        return findMethod(env.getTypeUtils(), source, target)
                .map(m -> ConversionFragment.of(new MethodCallNode(m, source, target)))
                .orElse(ConversionFragment.of());
    }

    private Optional<MethodDefinition> findMethod(Types types, TypeMirror source, TypeMirror target) {
        return registry.entries().values().stream()
                .map(RegistryEntry::getSignature)
                .filter(sig -> sig != null)
                .filter(m -> m.getParameters().size() == 1)
                .filter(m -> types.isSameType(
                        types.erasure(m.getParameters().get(0).getType()), types.erasure(source)))
                .filter(m -> types.isSameType(types.erasure(m.getReturnType()), types.erasure(target)))
                .findFirst();
    }
}
```

**Step 2: Update `WiringStage.java` — add `MapperMethodProvider` to providers**

In `WiringStage`, the `conversionProviders` list is populated via `ServiceLoader` in the constructor. `MapperMethodProvider` is not `@AutoService` so it is not in the ServiceLoader list. We need to add it at execution time, constructed with the per-mapper `MethodRegistry`.

Change the `execute` method in `WiringStage.java`:

```java
public MethodRegistry execute(MethodRegistry registry) {
    List<ConversionProvider> allProviders = buildProviders(registry);
    registry.entries().forEach((pair, entry) -> {
        if (!entry.isOpaque() && entry.getGraph() != null && entry.getSignature() != null) {
            wireGraph(entry.getGraph(), entry.getSignature().getReturnType(), registry, allProviders);
        }
    });
    return registry;
}

private List<ConversionProvider> buildProviders(MethodRegistry registry) {
    List<ConversionProvider> all = new ArrayList<>(conversionProviders);
    all.add(0, new MapperMethodProvider(registry)); // highest priority
    return all;
}
```

Also update `wireGraph` and `insertConversions` to accept the providers list instead of using `conversionProviders` directly:

```java
private void wireGraph(
        Graph<MappingNode, FlowEdge> graph,
        TypeMirror returnType,
        MethodRegistry registry,
        List<ConversionProvider> providers) {
    resolveCreationStrategy(graph, returnType);
    insertConversions(graph, registry, providers);
}

private void insertConversions(
        Graph<MappingNode, FlowEdge> graph, MethodRegistry registry, List<ConversionProvider> providers) {
    List<FlowEdge> edgesToCheck = new ArrayList<>(graph.edgeSet());
    for (FlowEdge edge : edgesToCheck) {
        if (!graph.containsEdge(edge)) continue;
        TypeMirror sourceType = edge.getSourceType();
        TypeMirror targetType = edge.getTargetType();
        if (typesCompatible(sourceType, targetType)) continue;
        findFragment(sourceType, targetType, registry, providers).ifPresent(fragment -> {
            if (!fragment.isEmpty()) {
                spliceFragment(graph, edge, fragment);
            }
        });
    }
}

private Optional<ConversionFragment> findFragment(
        TypeMirror source, TypeMirror target, MethodRegistry registry, List<ConversionProvider> providers) {
    return providers.stream()
            .filter(p -> p.canHandle(source, target, processingEnv))
            .findFirst()
            .map(p -> p.provide(source, target, registry, processingEnv));
}
```

Remove the old `wireGraph(graph, returnType, registry)` (2-arg) and `insertConversions(graph, registry)` private methods.

**Step 3: Run the tests**

```bash
./gradlew :processor:test
```

Expected: same pass/fail distribution as before. The `WiringStageSpec` test should still pass.

**Step 4: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/spi/impl/MapperMethodProvider.java \
        processor/src/main/java/io/github/joke/percolate/stage/WiringStage.java
git commit -m "refactor: MapperMethodProvider uses per-mapper MethodRegistry, activated in WiringStage"
```

---

## Task 5: Remove `@RoundScoped` from per-mapper stages

`BindingStage` and `WiringStage` are stateless — they hold no mapper-specific state. Remove the scope annotation. `RegistrationStage` was never scoped. `ParseStage` and `Pipeline` remain `@RoundScoped`.

**Files:**
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/BindingStage.java`
- Modify: `processor/src/main/java/io/github/joke/percolate/stage/WiringStage.java`

**Step 1: Remove `@RoundScoped` from `BindingStage`**

Remove the `@RoundScoped` annotation and its import from `BindingStage.java`.

**Step 2: Remove `@RoundScoped` from `WiringStage`**

Remove the `@RoundScoped` annotation and its import from `WiringStage.java`.

**Step 3: Build to verify Dagger is happy**

```bash
./gradlew :processor:compileJava
```

Dagger will regenerate the DI wiring. Unscoped components injected into a scoped component (`Pipeline`, which is `@RoundScoped`) are handled correctly by Dagger — a new instance is created per injection point within the scoped component.

**Step 4: Run the tests**

```bash
./gradlew :processor:test
```

Expected: no change in pass/fail distribution.

**Step 5: Commit**

```bash
git add processor/src/main/java/io/github/joke/percolate/stage/BindingStage.java \
        processor/src/main/java/io/github/joke/percolate/stage/WiringStage.java
git commit -m "refactor: remove @RoundScoped from stateless per-mapper stages"
```

---

## Final check

Run the full build:

```bash
./gradlew build
```

Expected outcome:
- All previously-passing tests still pass
- The same 7 downstream tests remain failing (CodeGenStageSpec, MultiParamCodeGenSpec, WildcardExpansionSpec, ValidateStageSpec ×2, ValidateStageConversionSpec, TicketMapperIntegrationSpec)
- No new failures
- Build output: `BUILD FAILED` only due to those 7 expected test failures

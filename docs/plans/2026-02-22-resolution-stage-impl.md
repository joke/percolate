# Resolution Stage Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add `ResolutionStage` between Analysis and Validation that validates `@Map` source/target paths, expands `param.*` wildcards, enforces multi-parameter no-auto-match, and builds a `ConverterRegistry` that lets `TypeMappingStrategy` implementations participate as graph nodes.

**Architecture:** New `resolution` package owns `ResolutionStage`, `ConverterRegistry`, `Converter` types, and `Resolved*` data classes. `TypeMappingStrategy` interface gains a `canContribute(registry)` method replacing `supports()`. `CodeGenStage` and `ValidationStage` are simplified to consume pre-resolved mappings and the registry. Pipeline order: Analysis → Resolution → Validation → Graph → CodeGen.

**Tech Stack:** Java 11, Dagger 2, JSpecify/NullAway, Spock + Google Compile Testing.

---

## Phase 1: Converter infrastructure

### Task 1: `Converter` types and `ConverterRegistry`

**Files:**
- Create: `processor/src/main/java/io/github/joke/caffeinate/resolution/package-info.java`
- Create: `processor/src/main/java/io/github/joke/caffeinate/resolution/Converter.java`
- Create: `processor/src/main/java/io/github/joke/caffeinate/resolution/MethodConverter.java`
- Create: `processor/src/main/java/io/github/joke/caffeinate/resolution/StrategyConverter.java`
- Create: `processor/src/main/java/io/github/joke/caffeinate/resolution/ConverterRegistry.java`

**Step 1: Write the files**

`package-info.java`:
```java
@NullMarked
package io.github.joke.caffeinate.resolution;

import org.jspecify.annotations.NullMarked;
```

`Converter.java`:
```java
package io.github.joke.caffeinate.resolution;

/** Marker interface for a type-conversion edge in the converter graph. */
public interface Converter {}
```

`MethodConverter.java`:
```java
package io.github.joke.caffeinate.resolution;

import javax.lang.model.element.ExecutableElement;

/** A converter backed by an explicit mapper method (abstract or default). */
public final class MethodConverter implements Converter {
    private final ExecutableElement method;

    public MethodConverter(ExecutableElement method) {
        this.method = method;
    }

    public ExecutableElement getMethod() {
        return method;
    }
}
```

`StrategyConverter.java`:
```java
package io.github.joke.caffeinate.resolution;

import io.github.joke.caffeinate.codegen.strategy.TypeMappingStrategy;
import javax.lang.model.type.TypeMirror;

/** A converter backed by a built-in TypeMappingStrategy (e.g. Optional-wrapping). */
public final class StrategyConverter implements Converter {
    private final TypeMappingStrategy strategy;
    private final TypeMirror sourceType;
    private final TypeMirror targetType;

    public StrategyConverter(TypeMappingStrategy strategy, TypeMirror sourceType, TypeMirror targetType) {
        this.strategy = strategy;
        this.sourceType = sourceType;
        this.targetType = targetType;
    }

    public TypeMappingStrategy getStrategy() { return strategy; }
    public TypeMirror getSourceType() { return sourceType; }
    public TypeMirror getTargetType() { return targetType; }
}
```

`ConverterRegistry.java`:
```java
package io.github.joke.caffeinate.resolution;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

/**
 * Lookup table: (sourceType, targetType) -> Converter.
 * User-defined MethodConverters are registered first and are never overwritten by strategies.
 */
public final class ConverterRegistry {

    private final Map<String, Converter> converters = new LinkedHashMap<>();

    public ConverterRegistry() {
    }

    /**
     * Registers a converter for (source -> target). No-op if the pair is already registered.
     * Returns true if a new entry was added, false if already present.
     */
    public boolean register(TypeMirror source, TypeMirror target, Converter converter) {
        return converters.putIfAbsent(key(source, target), converter) == null;
    }

    public Optional<Converter> converterFor(TypeMirror source, TypeMirror target) {
        return Optional.ofNullable(converters.get(key(source, target)));
    }

    public boolean hasConverter(TypeMirror source, TypeMirror target) {
        return converters.containsKey(key(source, target));
    }

    /**
     * Key uses toString() for hashing (TypeMirror has no stable hashCode across implementations).
     */
    private String key(TypeMirror source, TypeMirror target) {
        return source.toString() + " -> " + target.toString();
    }
}
```

**Step 2: Verify compilation**
```bash
./gradlew :processor:compileJava
```
Expected: BUILD SUCCESSFUL

**Step 3: Commit**
```bash
git add processor/src/main/java/io/github/joke/caffeinate/resolution/
git commit -m "feat: add Converter types and ConverterRegistry"
```

---

### Task 2: Update `TypeMappingStrategy` interface and all built-in strategies

Replace `supports()`/`supportsIdentity()` with `canContribute(registry)`. Replace the `@Nullable converterMethodRef` parameter with `registry` in `generate()`. Update `CodeGenStage` to build a temporary `ConverterRegistry` from `converterCandidates` so the build stays green until `ResolutionStage` is wired in.

**Files:**
- Modify: `processor/src/main/java/io/github/joke/caffeinate/codegen/strategy/TypeMappingStrategy.java`
- Modify: `processor/src/main/java/io/github/joke/caffeinate/codegen/strategy/OptionalMappingStrategy.java`
- Modify: `processor/src/main/java/io/github/joke/caffeinate/codegen/strategy/CollectionMappingStrategy.java`
- Modify: `processor/src/main/java/io/github/joke/caffeinate/codegen/strategy/EnumMappingStrategy.java`
- Modify: `processor/src/main/java/io/github/joke/caffeinate/codegen/CodeGenStage.java`

**Step 1: Replace `TypeMappingStrategy.java`**

```java
package io.github.joke.caffeinate.codegen.strategy;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.caffeinate.resolution.ConverterRegistry;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;

public interface TypeMappingStrategy {

    /**
     * Returns true if this strategy can produce a converter for (source -> target) given the
     * converters already registered in the registry. Called during the fixpoint in ResolutionStage.
     */
    boolean canContribute(TypeMirror source, TypeMirror target,
                          ConverterRegistry registry, ProcessingEnvironment env);

    /**
     * Generates a CodeBlock that converts {@code sourceExpr} (of type {@code source})
     * to {@code target}. Only called after {@code canContribute} returned true.
     */
    CodeBlock generate(String sourceExpr, TypeMirror source, TypeMirror target,
                       ConverterRegistry registry, ProcessingEnvironment env);
}
```

**Step 2: Replace `OptionalMappingStrategy.java`**

```java
package io.github.joke.caffeinate.codegen.strategy;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.caffeinate.resolution.Converter;
import io.github.joke.caffeinate.resolution.ConverterRegistry;
import io.github.joke.caffeinate.resolution.MethodConverter;
import java.util.List;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

@AutoService(TypeMappingStrategy.class)
public class OptionalMappingStrategy implements TypeMappingStrategy {

    @Override
    public boolean canContribute(TypeMirror source, TypeMirror target,
                                 ConverterRegistry registry, ProcessingEnvironment env) {
        if (!isOptional(target)) return false;
        TypeMirror tgtElem = elementType(target);
        if (isOptional(source)) {
            TypeMirror srcElem = elementType(source);
            return env.getTypeUtils().isSameType(srcElem, tgtElem)
                    || registry.hasConverter(srcElem, tgtElem);
        }
        // non-Optional source -> Optional<T> target
        return env.getTypeUtils().isSameType(source, tgtElem)
                || registry.hasConverter(source, tgtElem);
    }

    @Override
    public CodeBlock generate(String sourceExpr, TypeMirror source, TypeMirror target,
                              ConverterRegistry registry, ProcessingEnvironment env) {
        TypeMirror tgtElem = elementType(target);
        if (isOptional(source)) {
            TypeMirror srcElem = elementType(source);
            if (env.getTypeUtils().isSameType(srcElem, tgtElem)) {
                return CodeBlock.of("$L", sourceExpr);
            }
            String methodName = methodName(registry.converterFor(srcElem, tgtElem));
            return CodeBlock.of("$L.map(this::$L)", sourceExpr, methodName);
        }
        // non-Optional source -> Optional<T> target
        if (env.getTypeUtils().isSameType(source, tgtElem)) {
            return CodeBlock.of("$T.ofNullable($L)", Optional.class, sourceExpr);
        }
        String methodName = methodName(registry.converterFor(source, tgtElem));
        return CodeBlock.of("$T.ofNullable(this.$L($L))", Optional.class, methodName, sourceExpr);
    }

    private String methodName(Optional<Converter> converter) {
        if (converter.isPresent() && converter.get() instanceof MethodConverter) {
            return ((MethodConverter) converter.get()).getMethod().getSimpleName().toString();
        }
        throw new IllegalStateException("OptionalMappingStrategy: expected a MethodConverter");
    }

    private boolean isOptional(TypeMirror type) {
        if (!(type instanceof DeclaredType)) return false;
        DeclaredType declared = (DeclaredType) type;
        TypeElement element = (TypeElement) declared.asElement();
        return element.getQualifiedName().toString().equals("java.util.Optional")
                && !declared.getTypeArguments().isEmpty();
    }

    private TypeMirror elementType(TypeMirror type) {
        List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
        return args.get(0);
    }
}
```

**Step 3: Replace `CollectionMappingStrategy.java`**

```java
package io.github.joke.caffeinate.codegen.strategy;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.caffeinate.resolution.Converter;
import io.github.joke.caffeinate.resolution.ConverterRegistry;
import io.github.joke.caffeinate.resolution.MethodConverter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

@AutoService(TypeMappingStrategy.class)
public class CollectionMappingStrategy implements TypeMappingStrategy {

    @Override
    public boolean canContribute(TypeMirror source, TypeMirror target,
                                 ConverterRegistry registry, ProcessingEnvironment env) {
        if (!isList(source) || !isList(target)) return false;
        TypeMirror srcElem = elementType(source);
        TypeMirror tgtElem = elementType(target);
        return env.getTypeUtils().isSameType(srcElem, tgtElem)
                || registry.hasConverter(srcElem, tgtElem);
    }

    @Override
    public CodeBlock generate(String sourceExpr, TypeMirror source, TypeMirror target,
                              ConverterRegistry registry, ProcessingEnvironment env) {
        TypeMirror srcElem = elementType(source);
        TypeMirror tgtElem = elementType(target);
        if (env.getTypeUtils().isSameType(srcElem, tgtElem)) {
            return CodeBlock.of("$L", sourceExpr);
        }
        Optional<Converter> converter = registry.converterFor(srcElem, tgtElem);
        if (converter.isPresent() && converter.get() instanceof MethodConverter) {
            String methodName = ((MethodConverter) converter.get()).getMethod().getSimpleName().toString();
            return CodeBlock.of("$L.stream().map(this::$L).collect($T.toList())",
                    sourceExpr, methodName, Collectors.class);
        }
        return CodeBlock.of("$L /* unresolved collection mapping */", sourceExpr);
    }

    private boolean isList(TypeMirror type) {
        if (!(type instanceof DeclaredType)) return false;
        DeclaredType declared = (DeclaredType) type;
        TypeElement element = (TypeElement) declared.asElement();
        return element.getQualifiedName().toString().equals("java.util.List")
                && !declared.getTypeArguments().isEmpty();
    }

    private TypeMirror elementType(TypeMirror type) {
        List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
        return args.get(0);
    }
}
```

**Step 4: Replace `EnumMappingStrategy.java`**

```java
package io.github.joke.caffeinate.codegen.strategy;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.caffeinate.resolution.ConverterRegistry;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

@AutoService(TypeMappingStrategy.class)
public class EnumMappingStrategy implements TypeMappingStrategy {

    @Override
    public boolean canContribute(TypeMirror source, TypeMirror target,
                                 ConverterRegistry registry, ProcessingEnvironment env) {
        if (!isEnum(source) || !isEnum(target)) return false;
        TypeElement srcEnum = (TypeElement) ((DeclaredType) source).asElement();
        TypeElement tgtEnum = (TypeElement) ((DeclaredType) target).asElement();
        Set<String> tgtConstants = enumConstants(tgtEnum);
        for (String c : enumConstants(srcEnum)) {
            if (!tgtConstants.contains(c)) {
                env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        String.format("[Percolate] Enum constant '%s' from %s has no match in %s",
                                c, srcEnum.getSimpleName(), tgtEnum.getSimpleName()));
                return false;
            }
        }
        return true;
    }

    @Override
    public CodeBlock generate(String sourceExpr, TypeMirror source, TypeMirror target,
                              ConverterRegistry registry, ProcessingEnvironment env) {
        TypeElement targetEnum = (TypeElement) ((DeclaredType) target).asElement();
        return CodeBlock.of("$L.valueOf($L.name())", targetEnum.getQualifiedName(), sourceExpr);
    }

    private boolean isEnum(TypeMirror type) {
        if (!(type instanceof DeclaredType)) return false;
        return ((DeclaredType) type).asElement().getKind() == ElementKind.ENUM;
    }

    private Set<String> enumConstants(TypeElement enumType) {
        Set<String> result = new LinkedHashSet<>();
        for (Element e : enumType.getEnclosedElements()) {
            if (e.getKind() == ElementKind.ENUM_CONSTANT) result.add(e.getSimpleName().toString());
        }
        return result;
    }
}
```

**Step 5: Update `CodeGenStage.java` to use new interface**

Replace the `findConverterRef`, `supportsIdentity`, and `supports` call sites with `canContribute`/`generate` using a temporary `ConverterRegistry` built from `converterCandidates`. Replace the body of `generateMethod` and `resolveExpression` to use the new strategy interface. Keep `resolveSourcePath` and property-discovery logic intact for now (those get removed in Task 9).

Replace the class body — keep the same fields and constructor, but update the method-level logic:

```java
    private MethodSpec generateMethod(MappingMethod method) {
        ExecutableElement elem = method.getMethod();
        MethodSpec.Builder builder = MethodSpec.overriding(elem);

        ConverterRegistry registry = buildTemporaryRegistry(method);

        // For single-parameter methods: check if a TypeMappingStrategy handles the
        // whole conversion at the top level (e.g., enum-to-enum, list-to-list).
        if (method.getParameters().size() == 1) {
            VariableElement param = method.getParameters().get(0);
            TypeMirror sourceType = param.asType();
            TypeMirror targetType = elem.getReturnType();
            for (TypeMappingStrategy strategy : typeMappingStrategies) {
                if (strategy.canContribute(sourceType, targetType, registry, env)) {
                    String expr = strategy
                            .generate(param.getSimpleName().toString(), sourceType, targetType, registry, env)
                            .toString();
                    builder.addStatement("return $L", expr);
                    return builder.build();
                }
            }
        }

        List<Property> targetProps = PropertyMerger.merge(strategies, method.getTargetType(), env);
        List<String> args = new ArrayList<>();
        for (Property targetProp : targetProps) {
            args.add(resolveExpression(targetProp, method, registry));
        }

        String targetFqn = method.getTargetType().getQualifiedName().toString();
        builder.addStatement("return new $L($L)", targetFqn, String.join(", ", args));
        return builder.build();
    }

    /** Builds a registry from the method's explicit converter candidates. No strategy fixpoint. */
    private ConverterRegistry buildTemporaryRegistry(MappingMethod method) {
        ConverterRegistry registry = new ConverterRegistry();
        for (ExecutableElement candidate : method.getConverterCandidates()) {
            if (candidate.getParameters().isEmpty()) continue;
            TypeMirror paramType = candidate.getParameters().get(0).asType();
            TypeMirror returnType = candidate.getReturnType();
            registry.register(paramType, returnType, new MethodConverter(candidate));
        }
        return registry;
    }

    private String resolveExpression(Property targetProp, MappingMethod method, ConverterRegistry registry) {
        // 1. Explicit @Map annotation
        for (MapAnnotation ann : method.getMapAnnotations()) {
            if (ann.getTarget().equals(targetProp.getName())) {
                return resolveSourcePath(ann.getSource(), method);
            }
        }

        // 2. Name-match from any source parameter
        for (VariableElement param : method.getParameters()) {
            Element paramElement = env.getTypeUtils().asElement(param.asType());
            if (!(paramElement instanceof TypeElement)) continue;
            TypeElement paramType = (TypeElement) paramElement;
            for (Property srcProp : PropertyMerger.merge(strategies, paramType, env)) {
                if (!srcProp.getName().equals(targetProp.getName())) continue;
                if (env.getTypeUtils().isSameType(srcProp.getType(), targetProp.getType())) {
                    return accessorExpr(param.getSimpleName().toString(), srcProp);
                }
                String srcExpr = accessorExpr(param.getSimpleName().toString(), srcProp);
                for (TypeMappingStrategy strategy : typeMappingStrategies) {
                    if (strategy.canContribute(srcProp.getType(), targetProp.getType(), registry, env)) {
                        return strategy.generate(srcExpr, srcProp.getType(), targetProp.getType(), registry, env)
                                .toString();
                    }
                }
            }
        }

        // 3. Converter delegate
        for (ExecutableElement candidate : method.getConverterCandidates()) {
            if (!env.getTypeUtils().isSameType(candidate.getReturnType(), targetProp.getType())) continue;
            if (candidate.getParameters().isEmpty()) continue;
            TypeMirror converterParamType = candidate.getParameters().get(0).asType();
            for (VariableElement param : method.getParameters()) {
                Element paramElement = env.getTypeUtils().asElement(param.asType());
                if (!(paramElement instanceof TypeElement)) continue;
                for (Property srcProp : PropertyMerger.merge(strategies, (TypeElement) paramElement, env)) {
                    if (env.getTypeUtils().isSameType(srcProp.getType(), converterParamType)) {
                        return "this." + candidate.getSimpleName()
                                + "(" + accessorExpr(param.getSimpleName().toString(), srcProp) + ")";
                    }
                }
            }
        }

        return "null /* unresolved: " + targetProp.getName() + " */";
    }
```

Also add the import at the top of `CodeGenStage.java`:
```java
import io.github.joke.caffeinate.resolution.ConverterRegistry;
import io.github.joke.caffeinate.resolution.MethodConverter;
```

**Step 6: Compile and run existing tests**
```bash
./gradlew :processor:test
```
Expected: BUILD SUCCESSFUL, all existing tests pass.

**Step 7: Commit**
```bash
git add processor/src/main/java/io/github/joke/caffeinate/codegen/
git commit -m "feat: replace TypeMappingStrategy supports() with canContribute(ConverterRegistry)"
```

---

## Phase 2: Resolution data types

### Task 3: `Resolved*` data classes and `ResolutionResult`

Pure data carriers — no logic. Everything lives in `io.github.joke.caffeinate.resolution`.

**Files:**
- Create: `processor/src/main/java/io/github/joke/caffeinate/resolution/ResolvedMapAnnotation.java`
- Create: `processor/src/main/java/io/github/joke/caffeinate/resolution/ResolvedMappingMethod.java`
- Create: `processor/src/main/java/io/github/joke/caffeinate/resolution/ResolvedMapperDescriptor.java`
- Create: `processor/src/main/java/io/github/joke/caffeinate/resolution/ResolutionResult.java`

**Step 1: Write the files**

`ResolvedMapAnnotation.java`:
```java
package io.github.joke.caffeinate.resolution;

import io.github.joke.caffeinate.analysis.property.Property;
import javax.lang.model.type.TypeMirror;

/** A single fully-resolved property mapping: a source expression -> a target property. */
public final class ResolvedMapAnnotation {
    private final Property targetProperty;
    private final String sourceExpression;   // e.g. "order.getVenue()"
    private final TypeMirror sourceType;     // resolved type of sourceExpression

    public ResolvedMapAnnotation(Property targetProperty, String sourceExpression, TypeMirror sourceType) {
        this.targetProperty = targetProperty;
        this.sourceExpression = sourceExpression;
        this.sourceType = sourceType;
    }

    public Property getTargetProperty() { return targetProperty; }
    public String getSourceExpression() { return sourceExpression; }
    public TypeMirror getSourceType() { return sourceType; }
}
```

`ResolvedMappingMethod.java`:
```java
package io.github.joke.caffeinate.resolution;

import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

public final class ResolvedMappingMethod {
    private final ExecutableElement method;
    private final TypeElement targetType;
    private final List<? extends VariableElement> parameters;
    /** True when the method has >1 parameter — auto name-matching is disabled. */
    private final boolean requiresExplicitMappings;
    private final List<ResolvedMapAnnotation> resolvedMappings;

    public ResolvedMappingMethod(
            ExecutableElement method,
            TypeElement targetType,
            List<? extends VariableElement> parameters,
            boolean requiresExplicitMappings,
            List<ResolvedMapAnnotation> resolvedMappings) {
        this.method = method;
        this.targetType = targetType;
        this.parameters = parameters;
        this.requiresExplicitMappings = requiresExplicitMappings;
        this.resolvedMappings = List.copyOf(resolvedMappings);
    }

    public ExecutableElement getMethod() { return method; }
    public TypeElement getTargetType() { return targetType; }
    public List<? extends VariableElement> getParameters() { return parameters; }
    public boolean isRequiresExplicitMappings() { return requiresExplicitMappings; }
    public List<ResolvedMapAnnotation> getResolvedMappings() { return resolvedMappings; }
}
```

`ResolvedMapperDescriptor.java`:
```java
package io.github.joke.caffeinate.resolution;

import java.util.List;
import javax.lang.model.element.TypeElement;

public final class ResolvedMapperDescriptor {
    private final TypeElement mapperInterface;
    private final List<ResolvedMappingMethod> methods;

    public ResolvedMapperDescriptor(TypeElement mapperInterface, List<ResolvedMappingMethod> methods) {
        this.mapperInterface = mapperInterface;
        this.methods = List.copyOf(methods);
    }

    public TypeElement getMapperInterface() { return mapperInterface; }
    public List<ResolvedMappingMethod> getMethods() { return methods; }
}
```

`ResolutionResult.java`:
```java
package io.github.joke.caffeinate.resolution;

import java.util.List;

public final class ResolutionResult {
    private final List<ResolvedMapperDescriptor> mappers;
    private final ConverterRegistry converterRegistry;

    public ResolutionResult(List<ResolvedMapperDescriptor> mappers, ConverterRegistry converterRegistry) {
        this.mappers = List.copyOf(mappers);
        this.converterRegistry = converterRegistry;
    }

    public List<ResolvedMapperDescriptor> getMappers() { return mappers; }
    public ConverterRegistry getConverterRegistry() { return converterRegistry; }
}
```

**Step 2: Compile**
```bash
./gradlew :processor:compileJava
```
Expected: BUILD SUCCESSFUL

**Step 3: Commit**
```bash
git add processor/src/main/java/io/github/joke/caffeinate/resolution/
git commit -m "feat: add Resolved* data types and ResolutionResult"
```

---

## Phase 3: ResolutionStage

### Task 4: `ResolutionStage` — path validation and wildcard expansion

**Files:**
- Create: `processor/src/main/java/io/github/joke/caffeinate/resolution/ResolutionStage.java`
- Modify: `processor/src/test/groovy/io/github/joke/caffeinate/processor/PercolateProcessorSpec.groovy`

**Step 1: Write failing tests**

Add to `PercolateProcessorSpec.groovy`:

```groovy
def "emits error when @Map source path segment does not exist"() {
    given:
    def src = JavaFileObjects.forSourceLines("io.example.Src",
        "package io.example;",
        "public final class Src {",
        "    public String getName() { return null; }",
        "}")
    def tgt = JavaFileObjects.forSourceLines("io.example.Tgt",
        "package io.example;",
        "public final class Tgt {",
        "    private final String title;",
        "    public Tgt(String title) { this.title = title; }",
        "    public String getTitle() { return title; }",
        "}")
    def mapper = JavaFileObjects.forSourceLines("io.example.BadSourceMapper",
        "package io.example;",
        "import io.github.joke.caffeinate.Mapper;",
        "import io.github.joke.caffeinate.Map;",
        "@Mapper",
        "public interface BadSourceMapper {",
        "    @Map(target = \"title\", source = \"src.noSuchProp\")",
        "    Tgt map(Src src);",
        "}")

    when:
    def compilation = Compiler.javac()
        .withProcessors(new PercolateProcessor())
        .compile(src, tgt, mapper)

    then:
    assertThat(compilation).failed()
    assertThat(compilation).hadErrorContaining("noSuchProp")
}

def "emits error when @Map target path segment does not exist"() {
    given:
    def src = JavaFileObjects.forSourceLines("io.example.Src2",
        "package io.example;",
        "public final class Src2 {",
        "    public String getName() { return null; }",
        "}")
    def tgt = JavaFileObjects.forSourceLines("io.example.Tgt2",
        "package io.example;",
        "public final class Tgt2 {",
        "    private final String name;",
        "    public Tgt2(String name) { this.name = name; }",
        "    public String getName() { return name; }",
        "}")
    def mapper = JavaFileObjects.forSourceLines("io.example.BadTargetMapper",
        "package io.example;",
        "import io.github.joke.caffeinate.Mapper;",
        "import io.github.joke.caffeinate.Map;",
        "@Mapper",
        "public interface BadTargetMapper {",
        "    @Map(target = \"noSuchProp\", source = \"src2.name\")",
        "    Tgt2 map(Src2 src2);",
        "}")

    when:
    def compilation = Compiler.javac()
        .withProcessors(new PercolateProcessor())
        .compile(src, tgt, mapper)

    then:
    assertThat(compilation).failed()
    assertThat(compilation).hadErrorContaining("noSuchProp")
}

def "expands wildcard source and maps matching named properties"() {
    given:
    def order = JavaFileObjects.forSourceLines("io.example.SimpleOrder",
        "package io.example;",
        "public final class SimpleOrder {",
        "    private final long orderId;",
        "    private final String orderName;",
        "    public SimpleOrder(long orderId, String orderName) { this.orderId = orderId; this.orderName = orderName; }",
        "    public long getOrderId() { return orderId; }",
        "    public String getOrderName() { return orderName; }",
        "}")
    def flat = JavaFileObjects.forSourceLines("io.example.FlatOrder",
        "package io.example;",
        "public final class FlatOrder {",
        "    private final long orderId;",
        "    private final String orderName;",
        "    public FlatOrder(long orderId, String orderName) { this.orderId = orderId; this.orderName = orderName; }",
        "    public long getOrderId() { return orderId; }",
        "    public String getOrderName() { return orderName; }",
        "}")
    def mapper = JavaFileObjects.forSourceLines("io.example.WildcardMapper",
        "package io.example;",
        "import io.github.joke.caffeinate.Mapper;",
        "import io.github.joke.caffeinate.Map;",
        "@Mapper",
        "public interface WildcardMapper {",
        "    @Map(target = \".\", source = \"order.*\")",
        "    FlatOrder map(SimpleOrder order);",
        "}")

    when:
    def compilation = Compiler.javac()
        .withProcessors(new PercolateProcessor())
        .compile(order, flat, mapper)

    then:
    assertThat(compilation).succeeded()
    def impl = assertThat(compilation).generatedSourceFile("io.example.WildcardMapperImpl")
    impl.contentsAsUtf8String().contains("getOrderId()")
    impl.contentsAsUtf8String().contains("getOrderName()")
}

def "emits error when multi-param method has uncovered target property"() {
    given:
    def a = JavaFileObjects.forSourceLines("io.example.PartA",
        "package io.example;",
        "public final class PartA { public String getFoo() { return null; } }")
    def b = JavaFileObjects.forSourceLines("io.example.PartB",
        "package io.example;",
        "public final class PartB { public String getBar() { return null; } }")
    def result = JavaFileObjects.forSourceLines("io.example.Combined",
        "package io.example;",
        "public final class Combined {",
        "    private final String foo;",
        "    private final String bar;",
        "    public Combined(String foo, String bar) { this.foo = foo; this.bar = bar; }",
        "    public String getFoo() { return foo; }",
        "    public String getBar() { return bar; }",
        "}")
    // Multi-param, no @Map — both foo and bar are uncovered with the new rule
    def mapper = JavaFileObjects.forSourceLines("io.example.MultiParamMapper",
        "package io.example;",
        "import io.github.joke.caffeinate.Mapper;",
        "@Mapper",
        "public interface MultiParamMapper {",
        "    Combined map(PartA a, PartB b);",
        "}")

    when:
    def compilation = Compiler.javac()
        .withProcessors(new PercolateProcessor())
        .compile(a, b, result, mapper)

    then:
    assertThat(compilation).failed()
}
```

**Step 2: Run to verify tests fail**
```bash
./gradlew :processor:test --tests 'PercolateProcessorSpec.emits error when @Map source path*'
./gradlew :processor:test --tests 'PercolateProcessorSpec.expands wildcard*'
./gradlew :processor:test --tests 'PercolateProcessorSpec.emits error when multi-param*'
```
Expected: FAIL (ResolutionStage not yet created)

**Step 3: Implement `ResolutionStage.java`**

```java
package io.github.joke.caffeinate.resolution;

import io.github.joke.caffeinate.analysis.AnalysisResult;
import io.github.joke.caffeinate.analysis.MapAnnotation;
import io.github.joke.caffeinate.analysis.MapperDescriptor;
import io.github.joke.caffeinate.analysis.MappingMethod;
import io.github.joke.caffeinate.analysis.property.Property;
import io.github.joke.caffeinate.analysis.property.PropertyDiscoveryStrategy;
import io.github.joke.caffeinate.analysis.property.PropertyMerger;
import io.github.joke.caffeinate.codegen.strategy.TypeMappingStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

public class ResolutionStage {

    private final ProcessingEnvironment env;
    private final Set<PropertyDiscoveryStrategy> propertyStrategies;
    private final Set<TypeMappingStrategy> typeMappingStrategies;

    @Inject
    public ResolutionStage(
            ProcessingEnvironment env,
            Set<PropertyDiscoveryStrategy> propertyStrategies,
            Set<TypeMappingStrategy> typeMappingStrategies) {
        this.env = env;
        this.propertyStrategies = propertyStrategies;
        this.typeMappingStrategies = typeMappingStrategies;
    }

    public ResolutionResult resolve(AnalysisResult analysis) {
        ConverterRegistry registry = new ConverterRegistry();

        // Pass 1: register all single-param mapper methods as explicit MethodConverters.
        for (MapperDescriptor descriptor : analysis.getMappers()) {
            for (MappingMethod method : descriptor.getMethods()) {
                if (method.getParameters().size() == 1) {
                    TypeMirror paramType = method.getParameters().get(0).asType();
                    TypeMirror returnType = method.getMethod().getReturnType();
                    registry.register(paramType, returnType, new MethodConverter(method.getMethod()));
                }
            }
        }

        // Resolve all mapping methods.
        List<ResolvedMapperDescriptor> resolved = new ArrayList<>();
        List<TypePairForFixpoint> pairsToCheck = new ArrayList<>();
        boolean hasErrors = false;

        for (MapperDescriptor descriptor : analysis.getMappers()) {
            List<ResolvedMappingMethod> resolvedMethods = new ArrayList<>();
            for (MappingMethod method : descriptor.getMethods()) {
                if (!isAbstract(method)) continue;
                ResolvedMappingMethod rmm = resolveMethod(method, descriptor, pairsToCheck);
                if (rmm == null) {
                    hasErrors = true;
                } else {
                    resolvedMethods.add(rmm);
                }
            }
            resolved.add(new ResolvedMapperDescriptor(descriptor.getMapperInterface(), resolvedMethods));
        }

        // Pass 2: strategy fixpoint — add virtual edges for unresolved type pairs.
        if (!hasErrors) {
            runStrategyFixpoint(pairsToCheck, registry);
        }

        return new ResolutionResult(resolved, registry);
    }

    private boolean isAbstract(MappingMethod method) {
        return method.getMethod().getModifiers().contains(Modifier.ABSTRACT);
    }

    /**
     * Resolves all @Map annotations for a single mapping method.
     * Returns null if any path validation fails (errors emitted via Messager).
     */
    private ResolvedMappingMethod resolveMethod(
            MappingMethod method,
            MapperDescriptor descriptor,
            List<TypePairForFixpoint> pairsToCheck) {

        boolean multiParam = method.getParameters().size() > 1;
        List<ResolvedMapAnnotation> resolvedMappings = new ArrayList<>();
        boolean hasErrors = false;

        TypeElement targetType = method.getTargetType();
        List<Property> targetProperties = PropertyMerger.merge(propertyStrategies, targetType, env);

        for (MapAnnotation ann : method.getMapAnnotations()) {
            String sourceStr = ann.getSource();
            String targetStr = ann.getTarget();

            if (isWildcard(sourceStr)) {
                // Expand param.* into one ResolvedMapAnnotation per first-level property.
                String paramName = sourceStr.substring(0, sourceStr.length() - 2); // strip ".*"
                VariableElement param = findParam(paramName, method);
                if (param == null) {
                    env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            String.format("[Percolate] %s.%s: wildcard source '%s' — no parameter named '%s'",
                                    descriptor.getMapperInterface().getSimpleName(),
                                    method.getMethod().getSimpleName(),
                                    sourceStr, paramName),
                            method.getMethod());
                    hasErrors = true;
                    continue;
                }
                TypeElement paramType = asTypeElement(param.asType());
                if (paramType == null) continue;
                List<Property> sourceProps = PropertyMerger.merge(propertyStrategies, paramType, env);

                for (Property srcProp : sourceProps) {
                    // Find the matching target property by name.
                    Property tgtProp = findTargetProperty(srcProp.getName(), targetProperties);
                    if (tgtProp == null) continue; // no match on target — skip silently
                    String srcExpr = accessorExpr(paramName, srcProp);
                    resolvedMappings.add(new ResolvedMapAnnotation(tgtProp, srcExpr, srcProp.getType()));
                    if (!env.getTypeUtils().isSameType(srcProp.getType(), tgtProp.getType())) {
                        pairsToCheck.add(new TypePairForFixpoint(srcProp.getType(), tgtProp.getType()));
                    }
                }
                continue;
            }

            // Validate target path.
            Property tgtProp = resolveTargetPath(targetStr, targetType, descriptor, method);
            if (tgtProp == null) { hasErrors = true; continue; }

            // Validate and resolve source path.
            SourceResolution src = resolveSourcePath(sourceStr, method, descriptor);
            if (src == null) { hasErrors = true; continue; }

            resolvedMappings.add(new ResolvedMapAnnotation(tgtProp, src.expression, src.type));
            if (!env.getTypeUtils().isSameType(src.type, tgtProp.getType())) {
                pairsToCheck.add(new TypePairForFixpoint(src.type, tgtProp.getType()));
            }
        }

        // For single-param methods, add name-matched properties not already covered.
        if (!multiParam && method.getParameters().size() == 1) {
            VariableElement param = method.getParameters().get(0);
            TypeElement paramType = asTypeElement(param.asType());
            if (paramType != null) {
                List<Property> sourceProps = PropertyMerger.merge(propertyStrategies, paramType, env);
                for (Property tgtProp : targetProperties) {
                    if (isAlreadyCovered(tgtProp, resolvedMappings)) continue;
                    for (Property srcProp : sourceProps) {
                        if (!srcProp.getName().equals(tgtProp.getName())) continue;
                        String srcExpr = accessorExpr(param.getSimpleName().toString(), srcProp);
                        resolvedMappings.add(new ResolvedMapAnnotation(tgtProp, srcExpr, srcProp.getType()));
                        if (!env.getTypeUtils().isSameType(srcProp.getType(), tgtProp.getType())) {
                            pairsToCheck.add(new TypePairForFixpoint(srcProp.getType(), tgtProp.getType()));
                        }
                        break;
                    }
                }
            }
        }

        if (hasErrors) return null;

        return new ResolvedMappingMethod(
                method.getMethod(), targetType, method.getParameters(), multiParam, resolvedMappings);
    }

    /**
     * Validates a target path (e.g. "zip" or "address.city") against the target type.
     * Returns the terminal Property if valid, null and emits an error if not.
     */
    private Property resolveTargetPath(
            String targetPath,
            TypeElement targetType,
            MapperDescriptor descriptor,
            MappingMethod method) {
        if (targetPath.equals(".")) return null; // handled by wildcard caller — should not reach here
        String[] segments = targetPath.split("\\.");
        TypeElement currentType = targetType;
        Property result = null;
        for (String segment : segments) {
            List<Property> props = PropertyMerger.merge(propertyStrategies, currentType, env);
            Property found = findTargetProperty(segment, props);
            if (found == null) {
                env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        String.format("[Percolate] %s.%s: @Map target path '%s' — no property '%s' on %s",
                                descriptor.getMapperInterface().getSimpleName(),
                                method.getMethod().getSimpleName(),
                                targetPath, segment, currentType.getSimpleName()),
                        method.getMethod());
                return null;
            }
            result = found;
            TypeElement next = asTypeElement(found.getType());
            if (next == null) break; // terminal type (primitive, String, etc.)
            currentType = next;
        }
        return result;
    }

    /**
     * Validates and resolves a source path (e.g. "ticket.ticketId" or bare "zipCode").
     * Returns a SourceResolution with the Java expression and resolved type, or null on error.
     *
     * Rules:
     * - Multi-param method: first segment must be a parameter name.
     * - Single-param method: bare property (no dot) is relative to the single parameter.
     */
    private SourceResolution resolveSourcePath(
            String sourcePath,
            MappingMethod method,
            MapperDescriptor descriptor) {

        boolean multiParam = method.getParameters().size() > 1;
        String[] segments = sourcePath.split("\\.");

        VariableElement startParam;
        int propStart;

        if (segments.length == 1 && !multiParam) {
            // Bare property on single param.
            startParam = method.getParameters().get(0);
            propStart = 0;
        } else if (segments.length >= 2) {
            String paramName = segments[0];
            startParam = findParam(paramName, method);
            if (startParam == null) {
                env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        String.format("[Percolate] %s.%s: source path '%s' — no parameter named '%s'",
                                descriptor.getMapperInterface().getSimpleName(),
                                method.getMethod().getSimpleName(),
                                sourcePath, paramName),
                        method.getMethod());
                return null;
            }
            propStart = 1;
        } else {
            env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    String.format("[Percolate] %s.%s: source path '%s' — must include parameter name for multi-param method",
                            descriptor.getMapperInterface().getSimpleName(),
                            method.getMethod().getSimpleName(),
                            sourcePath),
                    method.getMethod());
            return null;
        }

        // Navigate remaining segments.
        StringBuilder expr = new StringBuilder(startParam.getSimpleName());
        TypeElement currentType = asTypeElement(startParam.asType());
        TypeMirror currentTypeMirror = startParam.asType();
        if (currentType == null) {
            env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    String.format("[Percolate] %s.%s: parameter '%s' is not a class type",
                            descriptor.getMapperInterface().getSimpleName(),
                            method.getMethod().getSimpleName(),
                            startParam.getSimpleName()),
                    method.getMethod());
            return null;
        }

        for (int i = propStart; i < segments.length; i++) {
            String seg = segments[i];
            List<Property> props = PropertyMerger.merge(propertyStrategies, currentType, env);
            Property found = null;
            for (Property p : props) {
                if (p.getName().equals(seg)) { found = p; break; }
            }
            if (found == null) {
                env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        String.format("[Percolate] %s.%s: source path '%s' — no property '%s' on %s",
                                descriptor.getMapperInterface().getSimpleName(),
                                method.getMethod().getSimpleName(),
                                sourcePath, seg, currentType.getSimpleName()),
                        method.getMethod());
                return null;
            }
            expr.append(".").append(accessorSuffix(found));
            currentTypeMirror = found.getType();
            currentType = asTypeElement(found.getType()); // may be null for terminal types
        }

        return new SourceResolution(expr.toString(), currentTypeMirror);
    }

    /** Runs strategy fixpoint: adds StrategyConverter entries for all unresolved type pairs. */
    private void runStrategyFixpoint(List<TypePairForFixpoint> pairs, ConverterRegistry registry) {
        boolean added = true;
        while (added) {
            added = false;
            for (TypePairForFixpoint pair : pairs) {
                if (registry.hasConverter(pair.source, pair.target)) continue;
                for (TypeMappingStrategy strategy : typeMappingStrategies) {
                    if (strategy.canContribute(pair.source, pair.target, registry, env)) {
                        registry.register(pair.source, pair.target,
                                new StrategyConverter(strategy, pair.source, pair.target));
                        added = true;
                        break;
                    }
                }
            }
        }
    }

    private boolean isWildcard(String source) {
        return source.endsWith(".*");
    }

    private VariableElement findParam(String name, MappingMethod method) {
        for (VariableElement param : method.getParameters()) {
            if (param.getSimpleName().toString().equals(name)) return param;
        }
        return null;
    }

    private Property findTargetProperty(String name, List<Property> properties) {
        for (Property p : properties) {
            if (p.getName().equals(name)) return p;
        }
        return null;
    }

    private boolean isAlreadyCovered(Property tgtProp, List<ResolvedMapAnnotation> existing) {
        for (ResolvedMapAnnotation rma : existing) {
            if (rma.getTargetProperty().getName().equals(tgtProp.getName())) return true;
        }
        return false;
    }

    private TypeElement asTypeElement(TypeMirror type) {
        if (!(type instanceof DeclaredType)) return null;
        Element el = ((DeclaredType) type).asElement();
        return (el instanceof TypeElement) ? (TypeElement) el : null;
    }

    private String accessorExpr(String prefix, Property property) {
        return prefix + "." + accessorSuffix(property);
    }

    private String accessorSuffix(Property property) {
        if (property.getAccessor().getKind() == ElementKind.METHOD) {
            return property.getAccessor().getSimpleName() + "()";
        }
        return property.getName();
    }

    /** Holds a (source, target) type pair for the strategy fixpoint loop. */
    private static final class TypePairForFixpoint {
        final TypeMirror source;
        final TypeMirror target;
        TypePairForFixpoint(TypeMirror source, TypeMirror target) {
            this.source = source; this.target = target;
        }
    }

    /** Result of resolving a source path: the Java expression and its type. */
    private static final class SourceResolution {
        final String expression;
        final TypeMirror type;
        SourceResolution(String expression, TypeMirror type) {
            this.expression = expression; this.type = type;
        }
    }
}
```

**Step 4: Compile**
```bash
./gradlew :processor:compileJava
```
Expected: BUILD SUCCESSFUL

**Step 5: Run new tests (still fail — ResolutionStage not wired yet)**
```bash
./gradlew :processor:test --tests 'PercolateProcessorSpec.expands wildcard*'
```
Expected: FAIL — ResolutionStage not in pipeline

**Step 6: Commit**
```bash
git add processor/src/main/java/io/github/joke/caffeinate/resolution/ResolutionStage.java \
        processor/src/test/groovy/io/github/joke/caffeinate/processor/PercolateProcessorSpec.groovy
git commit -m "feat: add ResolutionStage with path validation, wildcard expansion, and strategy fixpoint"
```

---

## Phase 4: Update downstream stages and wire

### Task 5: Update `ValidationStage` to consume `ResolutionResult`

`ValidationStage` no longer does path validation or property discovery — it just checks that every target property of every `ResolvedMappingMethod` has a `ResolvedMapAnnotation` covering it, and that the registry has a converter for any type-mismatched pair.

**Files:**
- Modify: `processor/src/main/java/io/github/joke/caffeinate/validation/ValidationStage.java`
- Modify: `processor/src/main/java/io/github/joke/caffeinate/validation/ValidationResult.java`
- Modify: `processor/src/main/java/io/github/joke/caffeinate/validation/PartialGraphRenderer.java`

**Step 1: Replace `ValidationResult.java`**

```java
package io.github.joke.caffeinate.validation;

import io.github.joke.caffeinate.resolution.ResolutionResult;
import io.github.joke.caffeinate.resolution.ResolvedMapperDescriptor;
import java.util.List;

public final class ValidationResult {
    private final ResolutionResult resolutionResult;
    private final boolean hasFatalErrors;

    public ValidationResult(ResolutionResult resolutionResult, boolean hasFatalErrors) {
        this.resolutionResult = resolutionResult;
        this.hasFatalErrors = hasFatalErrors;
    }

    public ResolutionResult getResolutionResult() { return resolutionResult; }
    public List<ResolvedMapperDescriptor> getMappers() { return resolutionResult.getMappers(); }
    public boolean hasFatalErrors() { return hasFatalErrors; }
}
```

**Step 2: Replace `ValidationStage.java`**

```java
package io.github.joke.caffeinate.validation;

import io.github.joke.caffeinate.analysis.property.Property;
import io.github.joke.caffeinate.analysis.property.PropertyDiscoveryStrategy;
import io.github.joke.caffeinate.analysis.property.PropertyMerger;
import io.github.joke.caffeinate.resolution.ConverterRegistry;
import io.github.joke.caffeinate.resolution.ResolvedMapAnnotation;
import io.github.joke.caffeinate.resolution.ResolvedMapperDescriptor;
import io.github.joke.caffeinate.resolution.ResolvedMappingMethod;
import io.github.joke.caffeinate.resolution.ResolutionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.tools.Diagnostic;

public class ValidationStage {

    private final ProcessingEnvironment env;
    private final Set<PropertyDiscoveryStrategy> propertyStrategies;

    @Inject
    public ValidationStage(ProcessingEnvironment env, Set<PropertyDiscoveryStrategy> propertyStrategies) {
        this.env = env;
        this.propertyStrategies = propertyStrategies;
    }

    public ValidationResult validate(ResolutionResult resolution) {
        boolean hasFatalErrors = false;
        ConverterRegistry registry = resolution.getConverterRegistry();

        for (ResolvedMapperDescriptor descriptor : resolution.getMappers()) {
            for (ResolvedMappingMethod method : descriptor.getMethods()) {
                if (!validateMethod(method, descriptor, registry)) {
                    hasFatalErrors = true;
                }
            }
        }
        return new ValidationResult(resolution, hasFatalErrors);
    }

    private boolean validateMethod(
            ResolvedMappingMethod method,
            ResolvedMapperDescriptor descriptor,
            ConverterRegistry registry) {

        List<Property> targetProperties = PropertyMerger.merge(propertyStrategies, method.getTargetType(), env);
        List<Property> uncovered = new ArrayList<>();

        for (Property tgtProp : targetProperties) {
            ResolvedMapAnnotation rma = findMapping(tgtProp, method);
            if (rma == null) {
                uncovered.add(tgtProp);
                continue;
            }
            // Check type compatibility: same type or a converter exists.
            if (!env.getTypeUtils().isSameType(rma.getSourceType(), tgtProp.getType())
                    && !registry.hasConverter(rma.getSourceType(), tgtProp.getType())) {
                env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        String.format(
                                "[Percolate] %s.%s: cannot convert '%s' to '%s' for target property '%s'"
                                + " — no converter or strategy found.\n"
                                + "  Consider adding: %s map%s(%s source)",
                                descriptor.getMapperInterface().getSimpleName(),
                                method.getMethod().getSimpleName(),
                                rma.getSourceType(), tgtProp.getType(), tgtProp.getName(),
                                tgtProp.getType(), capitalize(tgtProp.getName()), rma.getSourceType()),
                        method.getMethod());
                return false;
            }
        }

        if (uncovered.isEmpty()) return true;

        String graph = PartialGraphRenderer.render(method, propertyStrategies, env);
        Property first = uncovered.get(0);
        env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                String.format(
                        "[Percolate] %s.%s: validation failed.\n%s\nConsider adding: %s map%s(%s source)",
                        descriptor.getMapperInterface().getSimpleName(),
                        method.getMethod().getSimpleName(),
                        graph,
                        first.getType(), capitalize(first.getName()), first.getType()),
                method.getMethod());
        return false;
    }

    private ResolvedMapAnnotation findMapping(Property tgtProp, ResolvedMappingMethod method) {
        for (ResolvedMapAnnotation rma : method.getResolvedMappings()) {
            if (rma.getTargetProperty().getName().equals(tgtProp.getName())) return rma;
        }
        return null;
    }

    private String capitalize(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
```

**Step 3: Update `PartialGraphRenderer.java`** to accept `ResolvedMappingMethod` instead of `MappingMethod`.

Replace the signature and implementation:

```java
package io.github.joke.caffeinate.validation;

import io.github.joke.caffeinate.analysis.property.Property;
import io.github.joke.caffeinate.analysis.property.PropertyDiscoveryStrategy;
import io.github.joke.caffeinate.analysis.property.PropertyMerger;
import io.github.joke.caffeinate.resolution.ResolvedMapAnnotation;
import io.github.joke.caffeinate.resolution.ResolvedMappingMethod;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;

public final class PartialGraphRenderer {

    private PartialGraphRenderer() {}

    public static String render(ResolvedMappingMethod method,
                                Set<PropertyDiscoveryStrategy> strategies,
                                ProcessingEnvironment env) {
        StringBuilder sb = new StringBuilder();
        sb.append("\nPartial resolution graph (from target ")
          .append(method.getTargetType().getSimpleName()).append("):\n");
        sb.append("  ").append(method.getTargetType().getSimpleName()).append("\n");

        List<Property> targetProps = PropertyMerger.merge(strategies, method.getTargetType(), env);
        int last = targetProps.size() - 1;
        for (int i = 0; i <= last; i++) {
            Property tgtProp = targetProps.get(i);
            ResolvedMapAnnotation rma = findMapping(tgtProp, method);
            boolean resolved = rma != null;
            String branch = (i == last) ? "└── " : "├── ";
            String mark = resolved ? "\u2713" : "\u2717";
            sb.append("  ").append(branch).append(tgtProp.getName()).append("  ").append(mark);
            if (resolved) {
                sb.append("  \u2190 ").append(rma.getSourceExpression());
            } else {
                sb.append("  \u2190 unresolved (").append(tgtProp.getType()).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static ResolvedMapAnnotation findMapping(Property tgtProp, ResolvedMappingMethod method) {
        for (ResolvedMapAnnotation rma : method.getResolvedMappings()) {
            if (rma.getTargetProperty().getName().equals(tgtProp.getName())) return rma;
        }
        return null;
    }
}
```

**Step 4: Compile**
```bash
./gradlew :processor:compileJava
```
Expected: BUILD SUCCESSFUL (GraphStage and Pipeline still use old ValidationResult API — that's fine since getMappers() still works)

**Step 5: Commit**
```bash
git add processor/src/main/java/io/github/joke/caffeinate/validation/
git commit -m "refactor: ValidationStage consumes ResolutionResult, PartialGraphRenderer uses ResolvedMappingMethod"
```

---

### Task 6: Update `CodeGenStage` to use `ResolvedMapAnnotation` and `ConverterRegistry`

Remove all property discovery, source path resolution, and converter candidate scanning from `CodeGenStage`. It now iterates `ResolvedMapAnnotation` entries and looks up conversions in the registry.

**Files:**
- Modify: `processor/src/main/java/io/github/joke/caffeinate/codegen/CodeGenStage.java`

**Step 1: Replace `CodeGenStage.java`**

```java
package io.github.joke.caffeinate.codegen;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import io.github.joke.caffeinate.codegen.strategy.TypeMappingStrategy;
import io.github.joke.caffeinate.resolution.Converter;
import io.github.joke.caffeinate.resolution.ConverterRegistry;
import io.github.joke.caffeinate.resolution.MethodConverter;
import io.github.joke.caffeinate.resolution.ResolvedMapAnnotation;
import io.github.joke.caffeinate.resolution.ResolvedMapperDescriptor;
import io.github.joke.caffeinate.resolution.ResolvedMappingMethod;
import io.github.joke.caffeinate.resolution.StrategyConverter;
import io.github.joke.caffeinate.validation.ValidationResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

public class CodeGenStage {

    private final ProcessingEnvironment env;
    private final Filer filer;
    private final Set<TypeMappingStrategy> typeMappingStrategies;

    @Inject
    public CodeGenStage(
            ProcessingEnvironment env,
            Filer filer,
            Set<TypeMappingStrategy> typeMappingStrategies) {
        this.env = env;
        this.filer = filer;
        this.typeMappingStrategies = typeMappingStrategies;
    }

    public void generate(ValidationResult validation) {
        ConverterRegistry registry = validation.getResolutionResult().getConverterRegistry();
        for (ResolvedMapperDescriptor descriptor : validation.getMappers()) {
            generateMapper(descriptor, registry);
        }
    }

    private void generateMapper(ResolvedMapperDescriptor descriptor, ConverterRegistry registry) {
        TypeElement iface = descriptor.getMapperInterface();
        String implName = iface.getSimpleName() + "Impl";
        String packageName = env.getElementUtils().getPackageOf(iface).getQualifiedName().toString();

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(implName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(TypeName.get(iface.asType()));

        for (ResolvedMappingMethod method : descriptor.getMethods()) {
            classBuilder.addMethod(generateMethod(method, registry));
        }

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build()).build();
        try {
            javaFile.writeTo(filer);
        } catch (IOException e) {
            String msg = e.getMessage();
            env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "[Percolate] Failed to write " + implName + ": " + (msg != null ? msg : e.toString()));
        }
    }

    private MethodSpec generateMethod(ResolvedMappingMethod method, ConverterRegistry registry) {
        ExecutableElement elem = method.getMethod();
        MethodSpec.Builder builder = MethodSpec.overriding(elem);

        // Single-param method: check if the whole conversion is handled by a strategy at top level
        // (e.g., enum-to-enum, List<A>->List<A>). This covers methods like mapVenue(Venue)->TicketVenue
        // that have a strategy handling the full return type.
        if (method.getParameters().size() == 1) {
            TypeMirror sourceType = method.getParameters().get(0).asType();
            TypeMirror targetType = elem.getReturnType();
            if (!env.getTypeUtils().isSameType(sourceType, targetType)) {
                Converter converter = registry.converterFor(sourceType, targetType).orElse(null);
                if (converter instanceof StrategyConverter) {
                    StrategyConverter sc = (StrategyConverter) converter;
                    String paramName = method.getParameters().get(0).getSimpleName().toString();
                    CodeBlock code = sc.getStrategy().generate(paramName, sourceType, targetType, registry, env);
                    builder.addStatement("return $L", code);
                    return builder.build();
                }
            }
        }

        // Property-by-property mapping via ResolvedMapAnnotation.
        List<String> args = new ArrayList<>();
        for (ResolvedMapAnnotation rma : method.getResolvedMappings()) {
            args.add(convertExpression(rma, registry));
        }

        String targetFqn = method.getTargetType().getQualifiedName().toString();
        builder.addStatement("return new $L($L)", targetFqn, String.join(", ", args));
        return builder.build();
    }

    /**
     * Produces the Java expression for a single target property assignment.
     * - Same type: emit sourceExpression directly.
     * - MethodConverter: emit this.mapX(sourceExpression).
     * - StrategyConverter: delegate to strategy.generate().
     */
    private String convertExpression(ResolvedMapAnnotation rma, ConverterRegistry registry) {
        TypeMirror srcType = rma.getSourceType();
        TypeMirror tgtType = rma.getTargetProperty().getType();
        String srcExpr = rma.getSourceExpression();

        if (env.getTypeUtils().isSameType(srcType, tgtType)) {
            return srcExpr;
        }

        Converter converter = registry.converterFor(srcType, tgtType).orElse(null);
        if (converter instanceof MethodConverter) {
            String methodName = ((MethodConverter) converter).getMethod().getSimpleName().toString();
            return "this." + methodName + "(" + srcExpr + ")";
        }
        if (converter instanceof StrategyConverter) {
            StrategyConverter sc = (StrategyConverter) converter;
            return sc.getStrategy().generate(srcExpr, srcType, tgtType, registry, env).toString();
        }

        return "null /* unresolved: " + rma.getTargetProperty().getName() + " */";
    }
}
```

**Step 2: Compile**
```bash
./gradlew :processor:compileJava
```
Expected: BUILD SUCCESSFUL

**Step 3: Commit**
```bash
git add processor/src/main/java/io/github/joke/caffeinate/codegen/CodeGenStage.java
git commit -m "refactor: CodeGenStage uses ResolvedMapAnnotation and ConverterRegistry — remove ad-hoc resolution"
```

---

### Task 7: Wire `ResolutionStage` into `Pipeline` and `RoundComponent`, run all tests

**Files:**
- Modify: `processor/src/main/java/io/github/joke/caffeinate/processor/RoundComponent.java`
- Modify: `processor/src/main/java/io/github/joke/caffeinate/processor/Pipeline.java`

**Step 1: Add `ResolutionStage` to `RoundComponent.java`**

```java
package io.github.joke.caffeinate.processor;

import dagger.Subcomponent;
import io.github.joke.caffeinate.analysis.AnalysisStage;
import io.github.joke.caffeinate.codegen.CodeGenStage;
import io.github.joke.caffeinate.graph.GraphStage;
import io.github.joke.caffeinate.resolution.ResolutionStage;
import io.github.joke.caffeinate.validation.ValidationStage;

@RoundScoped
@Subcomponent
public interface RoundComponent {

    AnalysisStage analysisStage();
    ResolutionStage resolutionStage();
    ValidationStage validationStage();
    GraphStage graphStage();
    CodeGenStage codeGenStage();
    Pipeline pipeline();

    @Subcomponent.Factory
    interface Factory {
        RoundComponent create();
    }
}
```

**Step 2: Update `Pipeline.java`**

```java
package io.github.joke.caffeinate.processor;

import io.github.joke.caffeinate.analysis.AnalysisResult;
import io.github.joke.caffeinate.analysis.AnalysisStage;
import io.github.joke.caffeinate.codegen.CodeGenStage;
import io.github.joke.caffeinate.graph.GraphStage;
import io.github.joke.caffeinate.resolution.ResolutionResult;
import io.github.joke.caffeinate.resolution.ResolutionStage;
import io.github.joke.caffeinate.validation.ValidationResult;
import io.github.joke.caffeinate.validation.ValidationStage;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.Element;

public class Pipeline {

    private final AnalysisStage analysisStage;
    private final ResolutionStage resolutionStage;
    private final ValidationStage validationStage;
    private final GraphStage graphStage;
    private final CodeGenStage codeGenStage;

    @Inject
    public Pipeline(
            AnalysisStage analysisStage,
            ResolutionStage resolutionStage,
            ValidationStage validationStage,
            GraphStage graphStage,
            CodeGenStage codeGenStage) {
        this.analysisStage = analysisStage;
        this.resolutionStage = resolutionStage;
        this.validationStage = validationStage;
        this.graphStage = graphStage;
        this.codeGenStage = codeGenStage;
    }

    public void run(Set<? extends Element> mapperElements) {
        AnalysisResult analysis = analysisStage.analyze(mapperElements);
        ResolutionResult resolution = resolutionStage.resolve(analysis);
        ValidationResult validation = validationStage.validate(resolution);
        if (validation.hasFatalErrors()) return;
        graphStage.build(validation);
        codeGenStage.generate(validation);
    }
}
```

**Step 3: Compile**
```bash
./gradlew :processor:compileJava
```
Expected: BUILD SUCCESSFUL (Dagger regenerates `DaggerProcessorComponent`)

**Step 4: Run all tests**
```bash
./gradlew :processor:test
```
Expected: all existing tests pass plus new path-validation and wildcard tests pass.

**Step 5: Commit**
```bash
git add processor/src/main/java/io/github/joke/caffeinate/processor/
git commit -m "feat: wire ResolutionStage into pipeline"
```

---

### Task 8: End-to-end test — `Venue → Optional<TicketVenue>` via strategy composition

Verify that the strategy fixpoint correctly generates `Optional.ofNullable(this.mapVenue(...))` for the `A → Optional<B>` case, and that the existing test-mapper compiles end-to-end.

**Files:**
- Modify: `processor/src/test/groovy/io/github/joke/caffeinate/processor/PercolateProcessorSpec.groovy`

**Step 1: Add test**

```groovy
def "generates Optional.ofNullable wrapping when non-Optional source maps to Optional target via converter"() {
    given:
    def venue = JavaFileObjects.forSourceLines("io.example.VenueE2e",
        "package io.example;",
        "public final class VenueE2e {",
        "    private final String name;",
        "    public VenueE2e(String name) { this.name = name; }",
        "    public String getName() { return name; }",
        "}")
    def ticketVenue = JavaFileObjects.forSourceLines("io.example.TicketVenueE2e",
        "package io.example;",
        "public final class TicketVenueE2e {",
        "    private final String name;",
        "    public TicketVenueE2e(String name) { this.name = name; }",
        "    public String getName() { return name; }",
        "}")
    def order = JavaFileObjects.forSourceLines("io.example.OrderE2e",
        "package io.example;",
        "public final class OrderE2e {",
        "    private final VenueE2e venue;",
        "    public OrderE2e(VenueE2e venue) { this.venue = venue; }",
        "    public VenueE2e getVenue() { return venue; }",
        "}")
    def flatOrder = JavaFileObjects.forSourceLines("io.example.FlatOrderE2e",
        "package io.example;",
        "import java.util.Optional;",
        "public final class FlatOrderE2e {",
        "    private final Optional<TicketVenueE2e> venue;",
        "    public FlatOrderE2e(Optional<TicketVenueE2e> venue) { this.venue = venue; }",
        "    public Optional<TicketVenueE2e> getVenue() { return venue; }",
        "}")
    def mapper = JavaFileObjects.forSourceLines("io.example.VenueWrapMapper",
        "package io.example;",
        "import io.github.joke.caffeinate.Mapper;",
        "import io.github.joke.caffeinate.Map;",
        "@Mapper",
        "public interface VenueWrapMapper {",
        "    @Map(target = \".\", source = \"order.*\")",
        "    FlatOrderE2e map(OrderE2e order);",
        "    TicketVenueE2e mapVenue(VenueE2e venue);",
        "}")

    when:
    def compilation = Compiler.javac()
        .withProcessors(new PercolateProcessor())
        .compile(venue, ticketVenue, order, flatOrder, mapper)

    then:
    assertThat(compilation).succeeded()
    assertThat(compilation).generatedSourceFile("io.example.VenueWrapMapperImpl")
        .contentsAsUtf8String()
        .contains("ofNullable")
}
```

**Step 2: Run**
```bash
./gradlew :processor:test --tests 'PercolateProcessorSpec.generates Optional.ofNullable wrapping*'
```
Expected: PASS

**Step 3: Run full test suite including test-mapper**
```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL

**Step 4: Commit**
```bash
git add processor/src/test/groovy/io/github/joke/caffeinate/processor/PercolateProcessorSpec.groovy
git commit -m "test: verify Optional.ofNullable wrapping via strategy composition and end-to-end build"
```

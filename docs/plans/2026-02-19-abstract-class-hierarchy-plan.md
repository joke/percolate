# Abstract Class and Inheritance Chain Support — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Support `@Immutable` and `@Mutable` on abstract classes, and collect abstract methods from the full type hierarchy rather than only the directly annotated type.

**Architecture:** A new `TypeHierarchyResolver` service (Dagger-injected) walks the full `javax.lang.model` type graph and returns a flat, deduplicated list of every abstract method the generated class must implement. Both discovery strategies consume this list unchanged. `ClassStructureStrategy` detects `INTERFACE` vs `CLASS` and emits `implements` or `extends` accordingly. `CaffeinateProcessor` is updated to accept abstract classes and validate a no-args constructor is present.

**Tech Stack:** Java 11 annotation processing (`javax.lang.model`), Dagger 2, Palantir JavaPoet, Spock + Google Compile Testing (Groovy).

---

### Task 1: Write failing tests — interface hierarchy traversal

**Files:**
- Modify: `processor/src/test/groovy/io/github/joke/caffeinate/ImmutableProcessorSpec.groovy`
- Modify: `processor/src/test/groovy/io/github/joke/caffeinate/MutableProcessorSpec.groovy`

**Step 1: Add failing test to `ImmutableProcessorSpec` for single-level inherited getter**

Append to `ImmutableProcessorSpec.groovy`:

```groovy
def 'generates field and getter for getter inherited from parent interface'() {
    given:
    def named = JavaFileObjects.forSourceString('test.Named', '''\
        package test;
        interface Named { String getName(); }
    ''')
    def source = JavaFileObjects.forSourceString('test.Person', '''\
        package test;
        import io.github.joke.caffeinate.Immutable;
        @Immutable
        public interface Person extends Named {
            int getAge();
        }
    ''')

    when:
    def compilation = javac()
        .withProcessors(new CaffeinateProcessor())
        .compile(named, source)

    then:
    compilation.status() == Compilation.Status.SUCCESS

    and:
    def generated = compilation.generatedSourceFile('test.PersonImpl')
        .get().getCharContent(true).toString()
    generated.contains('private final String name')
    generated.contains('private final int age')
    generated.contains('public String getName()')
    generated.contains('public int getAge()')
    generated.contains('PersonImpl(String name, int age)')
}

def 'collects abstract methods from multi-level interface hierarchy'() {
    given:
    def identifiable = JavaFileObjects.forSourceString('test.Identifiable', '''\
        package test;
        interface Identifiable { String getId(); }
    ''')
    def named = JavaFileObjects.forSourceString('test.Named', '''\
        package test;
        interface Named extends Identifiable { String getName(); }
    ''')
    def source = JavaFileObjects.forSourceString('test.Person', '''\
        package test;
        import io.github.joke.caffeinate.Immutable;
        @Immutable
        public interface Person extends Named {
            int getAge();
        }
    ''')

    when:
    def compilation = javac()
        .withProcessors(new CaffeinateProcessor())
        .compile(identifiable, named, source)

    then:
    compilation.status() == Compilation.Status.SUCCESS

    and:
    def generated = compilation.generatedSourceFile('test.PersonImpl')
        .get().getCharContent(true).toString()
    generated.contains('private final String id')
    generated.contains('private final String name')
    generated.contains('private final int age')
    generated.contains('PersonImpl(String id, String name, int age)')
}
```

**Step 2: Add failing test to `MutableProcessorSpec` for inherited getter**

Append to `MutableProcessorSpec.groovy`:

```groovy
def 'generates field and setter for getter inherited from parent interface'() {
    given:
    def named = JavaFileObjects.forSourceString('test.Named', '''\
        package test;
        interface Named { String getName(); }
    ''')
    def source = JavaFileObjects.forSourceString('test.Person', '''\
        package test;
        import io.github.joke.caffeinate.Mutable;
        @Mutable
        public interface Person extends Named {
            int getAge();
        }
    ''')

    when:
    def compilation = javac()
        .withProcessors(new CaffeinateProcessor())
        .compile(named, source)

    then:
    compilation.status() == Compilation.Status.SUCCESS

    and:
    def generated = compilation.generatedSourceFile('test.PersonImpl')
        .get().getCharContent(true).toString()
    generated.contains('private String name')
    generated.contains('private int age')
    generated.contains('public String getName()')
    generated.contains('public void setName(String name)')
    generated.contains('public int getAge()')
    generated.contains('public void setAge(int age)')
}
```

**Step 3: Run the new tests and verify they fail**

```bash
./gradlew :processor:test --tests 'ImmutableProcessorSpec' --tests 'MutableProcessorSpec'
```

Expected: the three new tests FAIL (currently only directly declared methods are collected).

**Step 4: Commit the failing tests**

```bash
git add processor/src/test/groovy/io/github/joke/caffeinate/ImmutableProcessorSpec.groovy \
        processor/src/test/groovy/io/github/joke/caffeinate/MutableProcessorSpec.groovy
git commit -m "test: add failing tests for interface hierarchy traversal"
```

---

### Task 2: Implement `TypeHierarchyResolver` and wire into discovery strategies

**Files:**
- Create: `processor/src/main/java/io/github/joke/caffeinate/strategy/TypeHierarchyResolver.java`
- Modify: `processor/src/main/java/io/github/joke/caffeinate/component/ProcessorModule.java`
- Modify: `processor/src/main/java/io/github/joke/caffeinate/strategy/PropertyDiscoveryStrategy.java`
- Modify: `processor/src/main/java/io/github/joke/caffeinate/mutable/MutablePropertyDiscoveryStrategy.java`

**Step 1: Add `@Provides Types` to `ProcessorModule`**

In `ProcessorModule.java`, add one import and one provider method:

```java
import javax.lang.model.util.Types;

// inside ProcessorModule class, alongside existing @Provides methods:
@Provides
Types types() {
    return processingEnvironment.getTypeUtils();
}
```

**Step 2: Create `TypeHierarchyResolver`**

Create `processor/src/main/java/io/github/joke/caffeinate/strategy/TypeHierarchyResolver.java`:

```java
package io.github.joke.caffeinate.strategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;

public class TypeHierarchyResolver {

    private final Types types;

    @Inject
    TypeHierarchyResolver(Types types) {
        this.types = types;
    }

    public List<ExecutableElement> getAllAbstractMethods(TypeElement element) {
        List<ExecutableElement> result = new ArrayList<>();
        collectAbstractMethods(element, result, new HashSet<>());
        return result;
    }

    private void collectAbstractMethods(
            TypeElement element, List<ExecutableElement> result, Set<String> seen) {
        for (ExecutableElement method : ElementFilter.methodsIn(element.getEnclosedElements())) {
            if (method.getModifiers().contains(Modifier.ABSTRACT)) {
                if (seen.add(methodKey(method))) {
                    result.add(method);
                }
            }
        }

        for (TypeMirror iface : element.getInterfaces()) {
            collectAbstractMethods((TypeElement) types.asElement(iface), result, seen);
        }

        TypeMirror superclass = element.getSuperclass();
        if (superclass.getKind() != TypeKind.NONE && superclass.getKind() != TypeKind.ERROR) {
            TypeElement superElement = (TypeElement) types.asElement(superclass);
            if (superElement != null
                    && !superElement.getQualifiedName().contentEquals("java.lang.Object")
                    && superElement.getModifiers().contains(Modifier.ABSTRACT)) {
                collectAbstractMethods(superElement, result, seen);
            }
        }
    }

    private String methodKey(ExecutableElement method) {
        StringBuilder key = new StringBuilder(method.getSimpleName().toString()).append("::");
        method.getParameters().forEach(p -> key.append(types.erasure(p.asType())).append(","));
        return key.toString();
    }
}
```

**Step 3: Inject `TypeHierarchyResolver` into `PropertyDiscoveryStrategy`**

Replace the body of `PropertyDiscoveryStrategy.java`:

```java
package io.github.joke.caffeinate.strategy;

import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

public class PropertyDiscoveryStrategy implements GenerationStrategy {

    private final Messager messager;
    private final TypeHierarchyResolver resolver;

    @Inject
    PropertyDiscoveryStrategy(Messager messager, TypeHierarchyResolver resolver) {
        this.messager = messager;
        this.resolver = resolver;
    }

    @Override
    public void generate(TypeElement source, ClassModel model) {
        for (ExecutableElement method : resolver.getAllAbstractMethods(source)) {
            if (PropertyUtils.isGetterMethod(method)) {
                model.getProperties().add(PropertyUtils.extractProperty(method));
            } else {
                reportError(method, model);
            }
        }
    }

    private void reportError(ExecutableElement method, ClassModel model) {
        if (!method.getParameters().isEmpty()) {
            messager.printMessage(
                    Diagnostic.Kind.ERROR, "Methods in @Immutable interfaces must have no parameters", method);
        } else if (method.getReturnType().getKind() == javax.lang.model.type.TypeKind.VOID) {
            messager.printMessage(
                    Diagnostic.Kind.ERROR, "Methods in @Immutable interfaces must not return void", method);
        } else {
            messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Methods in @Immutable interfaces must follow get*/is* naming convention",
                    method);
        }
        model.setHasErrors(true);
    }
}
```

**Step 4: Inject `TypeHierarchyResolver` into `MutablePropertyDiscoveryStrategy`**

Replace the body of `MutablePropertyDiscoveryStrategy.java`:

```java
package io.github.joke.caffeinate.mutable;

import io.github.joke.caffeinate.strategy.ClassModel;
import io.github.joke.caffeinate.strategy.GenerationStrategy;
import io.github.joke.caffeinate.strategy.PropertyUtils;
import io.github.joke.caffeinate.strategy.TypeHierarchyResolver;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;

public class MutablePropertyDiscoveryStrategy implements GenerationStrategy {

    private final Messager messager;
    private final TypeHierarchyResolver resolver;

    @Inject
    MutablePropertyDiscoveryStrategy(Messager messager, TypeHierarchyResolver resolver) {
        this.messager = messager;
        this.resolver = resolver;
    }

    @Override
    public void generate(TypeElement source, ClassModel model) {
        for (ExecutableElement method : resolver.getAllAbstractMethods(source)) {
            if (PropertyUtils.isGetterMethod(method)) {
                model.getProperties().add(PropertyUtils.extractProperty(method));
            } else if (PropertyUtils.isSetterMethod(method)) {
                model.getDeclaredSetters().add(method);
            } else {
                reportError(method, model);
            }
        }
    }

    private void reportError(ExecutableElement method, ClassModel model) {
        if (!method.getParameters().isEmpty()) {
            messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Methods in @Mutable interfaces must have no parameters (except setters)",
                    method);
        } else if (method.getReturnType().getKind() == TypeKind.VOID) {
            messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Void methods in @Mutable interfaces must follow set* naming convention",
                    method);
        } else {
            messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Methods in @Mutable interfaces must follow get*/is*/set* naming convention",
                    method);
        }
        model.setHasErrors(true);
    }
}
```

**Step 5: Run the hierarchy tests and verify they pass**

```bash
./gradlew :processor:test --tests 'ImmutableProcessorSpec' --tests 'MutableProcessorSpec'
```

Expected: all three new tests PASS; all pre-existing tests still PASS.

**Step 6: Commit**

```bash
git add processor/src/main/java/io/github/joke/caffeinate/strategy/TypeHierarchyResolver.java \
        processor/src/main/java/io/github/joke/caffeinate/component/ProcessorModule.java \
        processor/src/main/java/io/github/joke/caffeinate/strategy/PropertyDiscoveryStrategy.java \
        processor/src/main/java/io/github/joke/caffeinate/mutable/MutablePropertyDiscoveryStrategy.java
git commit -m "feat: add TypeHierarchyResolver and wire into discovery strategies"
```

---

### Task 3: Write failing tests — abstract class support

**Files:**
- Modify: `processor/src/test/groovy/io/github/joke/caffeinate/ImmutableProcessorSpec.groovy`
- Modify: `processor/src/test/groovy/io/github/joke/caffeinate/MutableProcessorSpec.groovy`

**Step 1: Add abstract class tests to `ImmutableProcessorSpec`**

Append to `ImmutableProcessorSpec.groovy`:

```groovy
def 'generates implementation extending abstract class'() {
    given:
    def source = JavaFileObjects.forSourceString('test.AbstractEntity', '''\
        package test;
        import io.github.joke.caffeinate.Immutable;
        @Immutable
        public abstract class AbstractEntity {
            public abstract String getId();
        }
    ''')

    when:
    def compilation = javac()
        .withProcessors(new CaffeinateProcessor())
        .compile(source)

    then:
    compilation.status() == Compilation.Status.SUCCESS

    and:
    def generated = compilation.generatedSourceFile('test.AbstractEntityImpl')
        .get().getCharContent(true).toString()
    generated.contains('public class AbstractEntityImpl extends AbstractEntity')
    generated.contains('private final String id')
    generated.contains('public String getId()')
    generated.contains('AbstractEntityImpl(String id)')
    generated.contains('super()')
}

def 'collects abstract methods from abstract class hierarchy'() {
    given:
    def base = JavaFileObjects.forSourceString('test.Base', '''\
        package test;
        public abstract class Base {
            public abstract String getId();
        }
    ''')
    def source = JavaFileObjects.forSourceString('test.Entity', '''\
        package test;
        import io.github.joke.caffeinate.Immutable;
        @Immutable
        public abstract class Entity extends Base {
            public abstract String getName();
        }
    ''')

    when:
    def compilation = javac()
        .withProcessors(new CaffeinateProcessor())
        .compile(base, source)

    then:
    compilation.status() == Compilation.Status.SUCCESS

    and:
    def generated = compilation.generatedSourceFile('test.EntityImpl')
        .get().getCharContent(true).toString()
    generated.contains('public class EntityImpl extends Entity')
    generated.contains('private final String id')
    generated.contains('private final String name')
    generated.contains('EntityImpl(String id, String name)')
    generated.contains('super()')
}

def 'fails when @Immutable applied to abstract class with no no-args constructor'() {
    given:
    def source = JavaFileObjects.forSourceString('test.AbstractEntity', '''\
        package test;
        import io.github.joke.caffeinate.Immutable;
        @Immutable
        public abstract class AbstractEntity {
            AbstractEntity(String id) {}
            public abstract String getId();
        }
    ''')

    when:
    def compilation = javac()
        .withProcessors(new CaffeinateProcessor())
        .compile(source)

    then:
    compilation.status() == Compilation.Status.FAILURE
    compilation.errors().any {
        it.getMessage(null).contains('requires a no-args constructor')
    }
}
```

**Step 2: Add abstract class tests to `MutableProcessorSpec`**

Append to `MutableProcessorSpec.groovy`:

```groovy
def 'generates mutable implementation extending abstract class'() {
    given:
    def source = JavaFileObjects.forSourceString('test.AbstractEntity', '''\
        package test;
        import io.github.joke.caffeinate.Mutable;
        @Mutable
        public abstract class AbstractEntity {
            public abstract String getName();
        }
    ''')

    when:
    def compilation = javac()
        .withProcessors(new CaffeinateProcessor())
        .compile(source)

    then:
    compilation.status() == Compilation.Status.SUCCESS

    and:
    def generated = compilation.generatedSourceFile('test.AbstractEntityImpl')
        .get().getCharContent(true).toString()
    generated.contains('public class AbstractEntityImpl extends AbstractEntity')
    generated.contains('private String name')
    !generated.contains('private final String name')
    generated.contains('public AbstractEntityImpl()')
    generated.contains('super()')
    generated.contains('public AbstractEntityImpl(String name)')
    generated.contains('public void setName(String name)')
}

def 'fails when @Mutable applied to abstract class with no no-args constructor'() {
    given:
    def source = JavaFileObjects.forSourceString('test.AbstractEntity', '''\
        package test;
        import io.github.joke.caffeinate.Mutable;
        @Mutable
        public abstract class AbstractEntity {
            AbstractEntity(String id) {}
            public abstract String getName();
        }
    ''')

    when:
    def compilation = javac()
        .withProcessors(new CaffeinateProcessor())
        .compile(source)

    then:
    compilation.status() == Compilation.Status.FAILURE
    compilation.errors().any {
        it.getMessage(null).contains('requires a no-args constructor')
    }
}
```

**Step 3: Run new tests and verify they fail**

```bash
./gradlew :processor:test --tests 'ImmutableProcessorSpec' --tests 'MutableProcessorSpec'
```

Expected: the five new abstract class tests FAIL (currently rejected as non-interface).

**Step 4: Commit failing tests**

```bash
git add processor/src/test/groovy/io/github/joke/caffeinate/ImmutableProcessorSpec.groovy \
        processor/src/test/groovy/io/github/joke/caffeinate/MutableProcessorSpec.groovy
git commit -m "test: add failing tests for abstract class support"
```

---

### Task 4: Update `CaffeinateProcessor` to accept abstract classes

**Files:**
- Modify: `processor/src/main/java/io/github/joke/caffeinate/CaffeinateProcessor.java`

**Step 1: Replace the kind guard and add `hasNoArgsConstructor`**

Replace the body of `CaffeinateProcessor.java` with:

```java
package io.github.joke.caffeinate;

import com.google.auto.service.AutoService;
import io.github.joke.caffeinate.component.DaggerProcessorComponent;
import io.github.joke.caffeinate.component.ProcessorModule;
import io.github.joke.caffeinate.immutable.ImmutableSubcomponent;
import io.github.joke.caffeinate.mutable.MutableSubcomponent;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;

@AutoService(Processor.class)
public class CaffeinateProcessor extends AbstractProcessor {

    private ImmutableSubcomponent immutableSubcomponent;
    private MutableSubcomponent mutableSubcomponent;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        var component = DaggerProcessorComponent.builder()
                .processorModule(new ProcessorModule(processingEnv))
                .build();
        immutableSubcomponent = component.immutable().create();
        mutableSubcomponent = component.mutable().create();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(Immutable.class.getCanonicalName(), Mutable.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            String annotationName = annotation.getQualifiedName().toString();

            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                boolean isInterface = element.getKind() == ElementKind.INTERFACE;
                boolean isAbstractClass = element.getKind() == ElementKind.CLASS
                        && element.getModifiers().contains(Modifier.ABSTRACT);

                if (!isInterface && !isAbstractClass) {
                    processingEnv
                            .getMessager()
                            .printMessage(
                                    Diagnostic.Kind.ERROR,
                                    "@" + annotation.getSimpleName()
                                            + " can only be applied to interfaces or abstract classes",
                                    element);
                    continue;
                }

                if (isAbstractClass && !hasNoArgsConstructor((TypeElement) element)) {
                    processingEnv
                            .getMessager()
                            .printMessage(
                                    Diagnostic.Kind.ERROR,
                                    "@" + annotation.getSimpleName()
                                            + " on abstract classes requires a no-args constructor",
                                    element);
                    continue;
                }

                try {
                    if (annotationName.equals(Immutable.class.getCanonicalName())) {
                        immutableSubcomponent.generator().generate((TypeElement) element);
                    } else if (annotationName.equals(Mutable.class.getCanonicalName())) {
                        mutableSubcomponent.generator().generate((TypeElement) element);
                    }
                } catch (IOException e) {
                    processingEnv
                            .getMessager()
                            .printMessage(
                                    Diagnostic.Kind.ERROR,
                                    "Failed to generate implementation: " + e.getMessage(),
                                    element);
                }
            }
        }
        return false;
    }

    private boolean hasNoArgsConstructor(TypeElement element) {
        List<ExecutableElement> constructors =
                ElementFilter.constructorsIn(element.getEnclosedElements());
        if (constructors.isEmpty()) {
            return true;
        }
        return constructors.stream().anyMatch(c -> c.getParameters().isEmpty());
    }
}
```

**Step 2: Run tests — validation tests should pass, structure tests still fail**

```bash
./gradlew :processor:test --tests 'ImmutableProcessorSpec' --tests 'MutableProcessorSpec'
```

Expected:
- `'fails when @Immutable applied to abstract class with no no-args constructor'` → PASS
- `'fails when @Mutable applied to abstract class with no no-args constructor'` → PASS
- `'fails when @Immutable applied to a class'` → still PASS (concrete class still rejected)
- Structure/generation tests for abstract class → still FAIL (wrong class structure generated)

**Step 3: Commit**

```bash
git add processor/src/main/java/io/github/joke/caffeinate/CaffeinateProcessor.java
git commit -m "feat: accept abstract classes in CaffeinateProcessor with no-args constructor validation"
```

---

### Task 5: Add `superclass` to `ClassModel` and update `ClassStructureStrategy`

**Files:**
- Modify: `processor/src/main/java/io/github/joke/caffeinate/strategy/ClassModel.java`
- Modify: `processor/src/main/java/io/github/joke/caffeinate/strategy/ClassStructureStrategy.java`

**Step 1: Add `superclass` field to `ClassModel`**

Add the field, getter, and setter inside `ClassModel.java` (after the `superinterfaces` field):

```java
private TypeName superclass = null;
```

Add getter and setter:

```java
public TypeName getSuperclass() {
    return superclass;
}

public void setSuperclass(TypeName superclass) {
    this.superclass = superclass;
}
```

**Step 2: Update `ClassStructureStrategy` to set `extends` vs `implements`**

Replace the body of `ClassStructureStrategy.java`:

```java
package io.github.joke.caffeinate.strategy;

import com.palantir.javapoet.ClassName;
import javax.inject.Inject;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

public class ClassStructureStrategy implements GenerationStrategy {

    @Inject
    ClassStructureStrategy() {}

    @Override
    public void generate(TypeElement source, ClassModel model) {
        model.setClassName(source.getSimpleName() + "Impl");
        model.getModifiers().add(Modifier.PUBLIC);
        if (source.getKind() == ElementKind.INTERFACE) {
            model.getSuperinterfaces().add(ClassName.get(source));
        } else {
            model.setSuperclass(ClassName.get(source));
        }
    }
}
```

**Step 3: Run tests — expect no change yet (generators don't use superclass yet)**

```bash
./gradlew :processor:test --tests 'ImmutableProcessorSpec' --tests 'MutableProcessorSpec'
```

Expected: same as before — abstract class generation tests still fail.

**Step 4: Commit**

```bash
git add processor/src/main/java/io/github/joke/caffeinate/strategy/ClassModel.java \
        processor/src/main/java/io/github/joke/caffeinate/strategy/ClassStructureStrategy.java
git commit -m "feat: add superclass field to ClassModel and detect extends vs implements in ClassStructureStrategy"
```

---

### Task 6: Apply `superclass` in generators

**Files:**
- Modify: `processor/src/main/java/io/github/joke/caffeinate/immutable/ImmutableGenerator.java`
- Modify: `processor/src/main/java/io/github/joke/caffeinate/mutable/MutableGenerator.java`

**Step 1: Update `ImmutableGenerator` to apply superclass**

In `ImmutableGenerator.java`, after the `for (TypeName superinterface ...)` loop, add:

```java
if (model.getSuperclass() != null) {
    builder.superclass(model.getSuperclass());
}
```

**Step 2: Read `MutableGenerator` and apply the same change**

Open `processor/src/main/java/io/github/joke/caffeinate/mutable/MutableGenerator.java` and apply the identical change in the same position.

**Step 3: Run tests — class declaration should now be correct**

```bash
./gradlew :processor:test --tests 'ImmutableProcessorSpec' --tests 'MutableProcessorSpec'
```

Expected:
- Tests checking `extends AbstractEntity` → PASS
- Tests checking `super()` in constructor → still FAIL

**Step 4: Commit**

```bash
git add processor/src/main/java/io/github/joke/caffeinate/immutable/ImmutableGenerator.java \
        processor/src/main/java/io/github/joke/caffeinate/mutable/MutableGenerator.java
git commit -m "feat: apply superclass from ClassModel in generators"
```

---

### Task 7: Add `super()` to constructor strategies

**Files:**
- Modify: `processor/src/main/java/io/github/joke/caffeinate/strategy/ConstructorStrategy.java`
- Modify: `processor/src/main/java/io/github/joke/caffeinate/mutable/MutableConstructorStrategy.java`

**Step 1: Update `ConstructorStrategy` to prepend `super()` for abstract class sources**

Replace the body of `ConstructorStrategy.java`:

```java
package io.github.joke.caffeinate.strategy;

import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import javax.inject.Inject;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

public class ConstructorStrategy implements GenerationStrategy {

    @Inject
    ConstructorStrategy() {}

    @Override
    public void generate(TypeElement source, ClassModel model) {
        if (model.getProperties().isEmpty()) {
            return;
        }

        MethodSpec.Builder constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

        if (source.getKind() != ElementKind.INTERFACE) {
            constructor.addStatement("super()");
        }

        for (Property property : model.getProperties()) {
            constructor.addParameter(ParameterSpec.builder(property.getType(), property.getFieldName())
                    .build());
            constructor.addStatement("this.$N = $N", property.getFieldName(), property.getFieldName());
        }

        model.getMethods().add(constructor.build());
    }
}
```

**Step 2: Update `MutableConstructorStrategy` to prepend `super()` for abstract class sources**

Replace the body of `MutableConstructorStrategy.java`:

```java
package io.github.joke.caffeinate.mutable;

import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import io.github.joke.caffeinate.strategy.ClassModel;
import io.github.joke.caffeinate.strategy.GenerationStrategy;
import io.github.joke.caffeinate.strategy.Property;
import javax.inject.Inject;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

public class MutableConstructorStrategy implements GenerationStrategy {

    @Inject
    MutableConstructorStrategy() {}

    @Override
    public void generate(TypeElement source, ClassModel model) {
        MethodSpec.Builder noArgs = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        if (source.getKind() != ElementKind.INTERFACE) {
            noArgs.addStatement("super()");
        }
        model.getMethods().add(noArgs.build());

        if (!model.getProperties().isEmpty()) {
            MethodSpec.Builder allArgs = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
            if (source.getKind() != ElementKind.INTERFACE) {
                allArgs.addStatement("super()");
            }

            for (Property property : model.getProperties()) {
                allArgs.addParameter(
                        ParameterSpec.builder(property.getType(), property.getFieldName()).build());
                allArgs.addStatement("this.$N = $N", property.getFieldName(), property.getFieldName());
            }

            model.getMethods().add(allArgs.build());
        }
    }
}
```

**Step 3: Run all tests**

```bash
./gradlew :processor:test
```

Expected: ALL tests PASS, including all new abstract class and hierarchy tests.

**Step 4: Run the full build to verify no ErrorProne/NullAway warnings**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL with no warnings or errors.

**Step 5: Commit**

```bash
git add processor/src/main/java/io/github/joke/caffeinate/strategy/ConstructorStrategy.java \
        processor/src/main/java/io/github/joke/caffeinate/mutable/MutableConstructorStrategy.java
git commit -m "feat: add super() call in constructors when source is abstract class"
```

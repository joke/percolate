# Nullable Annotation Propagation — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Propagate any `@Nullable` annotation (matched by simple name) from a source getter method to every generated site that represents that property: field, getter method, constructor parameter, and setter parameter (mutable only).

**Architecture:** `PropertyUtils.extractProperty()` scans the method's annotation mirrors for any whose simple name is `"Nullable"`, converts them to `AnnotationSpec` once at extraction time, and stores the list in `Property`. Each downstream strategy reads `property.getAnnotations()` and calls `.addAnnotation(spec)` on its builder — no new classes, minimal diff per file.

**Tech Stack:** Java 11 annotation processing (`javax.lang.model`), Palantir JavaPoet (`AnnotationSpec`, `FieldSpec`, `MethodSpec`, `ParameterSpec`), Spock + Google Compile Testing.

---

### Task 1: Write failing tests — `@Nullable` propagation in `@Immutable`

**Files:**
- Modify: `processor/src/test/groovy/io/github/joke/caffeinate/ImmutableProcessorSpec.groovy`

**Step 1: Append two tests to `ImmutableProcessorSpec.groovy`**

```groovy
def 'propagates @Nullable to field, getter, and constructor parameter'() {
    given:
    def nullable = JavaFileObjects.forSourceString('test.Nullable', '''\
        package test;
        public @interface Nullable {}
    ''')
    def source = JavaFileObjects.forSourceString('test.Person', '''\
        package test;
        import io.github.joke.caffeinate.Immutable;
        @Immutable
        public interface Person {
            @Nullable String getName();
            int getAge();
        }
    ''')

    when:
    def compilation = javac()
        .withProcessors(new CaffeinateProcessor())
        .compile(nullable, source)

    then:
    compilation.status() == Compilation.Status.SUCCESS

    and:
    def generated = compilation.generatedSourceFile('test.PersonImpl')
        .get().getCharContent(true).toString()
    generated.count('@Nullable') == 3       // field + getter method + constructor param
    generated.contains('@Nullable String name')   // constructor param
    !generated.contains('@Nullable int age')      // non-nullable property unaffected
}

def 'propagates @Nullable from inherited getter to field, getter, and constructor parameter'() {
    given:
    def nullable = JavaFileObjects.forSourceString('test.Nullable', '''\
        package test;
        public @interface Nullable {}
    ''')
    def named = JavaFileObjects.forSourceString('test.Named', '''\
        package test;
        interface Named { @Nullable String getName(); }
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
        .compile(nullable, named, source)

    then:
    compilation.status() == Compilation.Status.SUCCESS

    and:
    def generated = compilation.generatedSourceFile('test.PersonImpl')
        .get().getCharContent(true).toString()
    generated.count('@Nullable') == 3       // field + getter method + constructor param
    generated.contains('@Nullable String name')
    !generated.contains('@Nullable int age')
}
```

**Step 2: Run the new tests and verify they fail**

```bash
./gradlew :processor:test --tests 'ImmutableProcessorSpec' 2>&1 | tail -30
```

Expected: both new tests FAIL (`count('@Nullable')` returns 0, not 3).

**Step 3: Commit the failing tests**

```bash
git add processor/src/test/groovy/io/github/joke/caffeinate/ImmutableProcessorSpec.groovy
git commit -m "test: add failing tests for @Nullable propagation in @Immutable"
```

---

### Task 2: Write failing tests — `@Nullable` propagation in `@Mutable`

**Files:**
- Modify: `processor/src/test/groovy/io/github/joke/caffeinate/MutableProcessorSpec.groovy`

**Step 1: Append one test to `MutableProcessorSpec.groovy`**

```groovy
def 'propagates @Nullable to field, getter, constructor parameter, and setter parameter'() {
    given:
    def nullable = JavaFileObjects.forSourceString('test.Nullable', '''\
        package test;
        public @interface Nullable {}
    ''')
    def source = JavaFileObjects.forSourceString('test.Person', '''\
        package test;
        import io.github.joke.caffeinate.Mutable;
        @Mutable
        public interface Person {
            @Nullable String getName();
            int getAge();
        }
    ''')

    when:
    def compilation = javac()
        .withProcessors(new CaffeinateProcessor())
        .compile(nullable, source)

    then:
    compilation.status() == Compilation.Status.SUCCESS

    and:
    def generated = compilation.generatedSourceFile('test.PersonImpl')
        .get().getCharContent(true).toString()
    generated.count('@Nullable') == 4       // field + getter + all-args constructor param + setter param
    generated.contains('@Nullable String name')   // setter and all-args constructor param
    !generated.contains('@Nullable int age')      // non-nullable property unaffected
}
```

**Step 2: Run the new test and verify it fails**

```bash
./gradlew :processor:test --tests 'MutableProcessorSpec' 2>&1 | tail -20
```

Expected: new test FAILS (`count('@Nullable')` returns 0).

**Step 3: Commit the failing test**

```bash
git add processor/src/test/groovy/io/github/joke/caffeinate/MutableProcessorSpec.groovy
git commit -m "test: add failing test for @Nullable propagation in @Mutable"
```

---

### Task 3: Enrich `Property` and update `PropertyUtils`

**Files:**
- Modify: `processor/src/main/java/io/github/joke/caffeinate/strategy/Property.java`
- Modify: `processor/src/main/java/io/github/joke/caffeinate/strategy/PropertyUtils.java`

**Step 1: Replace `Property.java` with the version below**

```java
package io.github.joke.caffeinate.strategy;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.TypeName;
import java.util.List;

public class Property {

    private final String fieldName;
    private final TypeName type;
    private final String getterName;
    private final List<AnnotationSpec> annotations;

    public Property(String fieldName, TypeName type, String getterName, List<AnnotationSpec> annotations) {
        this.fieldName = fieldName;
        this.type = type;
        this.getterName = getterName;
        this.annotations = List.copyOf(annotations);
    }

    public String getFieldName() {
        return fieldName;
    }

    public TypeName getType() {
        return type;
    }

    public String getGetterName() {
        return getterName;
    }

    public List<AnnotationSpec> getAnnotations() {
        return annotations;
    }
}
```

**Step 2: Replace the body of `extractProperty()` in `PropertyUtils.java`**

Replace only the `extractProperty` method (leave `isGetterMethod`, `isSetterMethod`, `setterNameForField` unchanged):

```java
public static Property extractProperty(ExecutableElement method) {
    String methodName = method.getSimpleName().toString();
    String fieldName;

    if (methodName.startsWith("get") && methodName.length() > 3) {
        fieldName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
    } else if (methodName.startsWith("is") && methodName.length() > 2) {
        fieldName = Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
    } else {
        throw new IllegalArgumentException("Not a getter method: " + methodName);
    }

    TypeName type = TypeName.get(method.getReturnType());

    List<AnnotationSpec> annotations = method.getAnnotationMirrors().stream()
            .filter(m -> m.getAnnotationType().asElement().getSimpleName().contentEquals("Nullable"))
            .map(AnnotationSpec::get)
            .collect(java.util.stream.Collectors.toList());

    return new Property(fieldName, type, methodName, annotations);
}
```

Also add the import at the top of `PropertyUtils.java`:
```java
import com.palantir.javapoet.AnnotationSpec;
import java.util.List;
```

**Step 3: Run the tests — they still fail (strategies not updated yet)**

```bash
./gradlew :processor:test --tests 'ImmutableProcessorSpec' --tests 'MutableProcessorSpec' 2>&1 | tail -20
```

Expected: new tests still FAIL; all pre-existing tests still PASS (empty annotation list is a no-op).

**Step 4: Commit**

```bash
git add processor/src/main/java/io/github/joke/caffeinate/strategy/Property.java \
        processor/src/main/java/io/github/joke/caffeinate/strategy/PropertyUtils.java
git commit -m "feat: enrich Property with nullable annotations list and extract in PropertyUtils"
```

---

### Task 4: Apply annotations in `FieldStrategy` and `MutableFieldStrategy`

**Files:**
- Modify: `processor/src/main/java/io/github/joke/caffeinate/strategy/FieldStrategy.java`
- Modify: `processor/src/main/java/io/github/joke/caffeinate/mutable/MutableFieldStrategy.java`

**Step 1: Replace the `generate` method body in `FieldStrategy.java`**

```java
@Override
public void generate(TypeElement source, ClassModel model) {
    for (Property property : model.getProperties()) {
        FieldSpec.Builder field = FieldSpec.builder(
                        property.getType(), property.getFieldName(), Modifier.PRIVATE, Modifier.FINAL);
        property.getAnnotations().forEach(field::addAnnotation);
        model.getFields().add(field.build());
    }
}
```

**Step 2: Replace the `generate` method body in `MutableFieldStrategy.java`**

```java
@Override
public void generate(TypeElement source, ClassModel model) {
    for (Property property : model.getProperties()) {
        FieldSpec.Builder field = FieldSpec.builder(
                        property.getType(), property.getFieldName(), Modifier.PRIVATE);
        property.getAnnotations().forEach(field::addAnnotation);
        model.getFields().add(field.build());
    }
}
```

**Step 3: Run tests — field count now contributes 1 of the expected 3/4**

```bash
./gradlew :processor:test --tests 'ImmutableProcessorSpec' --tests 'MutableProcessorSpec' 2>&1 | tail -20
```

Expected: new tests still FAIL (count still not 3/4); all pre-existing tests still PASS.

**Step 4: Commit**

```bash
git add processor/src/main/java/io/github/joke/caffeinate/strategy/FieldStrategy.java \
        processor/src/main/java/io/github/joke/caffeinate/mutable/MutableFieldStrategy.java
git commit -m "feat: apply @Nullable annotations to generated fields"
```

---

### Task 5: Apply annotations in `GetterStrategy`

**Files:**
- Modify: `processor/src/main/java/io/github/joke/caffeinate/strategy/GetterStrategy.java`

**Step 1: Replace the `generate` method body**

```java
@Override
public void generate(TypeElement source, ClassModel model) {
    for (Property property : model.getProperties()) {
        MethodSpec.Builder getter = MethodSpec.methodBuilder(property.getGetterName())
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(property.getType())
                .addStatement("return this.$N", property.getFieldName());
        property.getAnnotations().forEach(getter::addAnnotation);
        model.getMethods().add(getter.build());
    }
}
```

**Step 2: Run tests**

```bash
./gradlew :processor:test --tests 'ImmutableProcessorSpec' --tests 'MutableProcessorSpec' 2>&1 | tail -20
```

Expected: new tests still FAIL (constructor param still missing); pre-existing tests still PASS.

**Step 3: Commit**

```bash
git add processor/src/main/java/io/github/joke/caffeinate/strategy/GetterStrategy.java
git commit -m "feat: apply @Nullable annotations to generated getters"
```

---

### Task 6: Apply annotations in `ConstructorStrategy` and `MutableConstructorStrategy`

**Files:**
- Modify: `processor/src/main/java/io/github/joke/caffeinate/strategy/ConstructorStrategy.java`
- Modify: `processor/src/main/java/io/github/joke/caffeinate/mutable/MutableConstructorStrategy.java`

**Step 1: Replace the `generate` method body in `ConstructorStrategy.java`**

```java
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
        ParameterSpec.Builder param = ParameterSpec.builder(
                property.getType(), property.getFieldName());
        property.getAnnotations().forEach(param::addAnnotation);
        constructor.addParameter(param.build());
        constructor.addStatement("this.$N = $N", property.getFieldName(), property.getFieldName());
    }

    model.getMethods().add(constructor.build());
}
```

**Step 2: Replace the `generate` method body in `MutableConstructorStrategy.java`**

The no-args constructor has no parameters — no annotation change needed there. The all-args constructor parameters get annotated:

```java
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
            ParameterSpec.Builder param = ParameterSpec.builder(
                    property.getType(), property.getFieldName());
            property.getAnnotations().forEach(param::addAnnotation);
            allArgs.addParameter(param.build());
            allArgs.addStatement("this.$N = $N", property.getFieldName(), property.getFieldName());
        }

        model.getMethods().add(allArgs.build());
    }
}
```

**Step 3: Run tests — Immutable tests should now pass; Mutable still missing setter**

```bash
./gradlew :processor:test --tests 'ImmutableProcessorSpec' --tests 'MutableProcessorSpec' 2>&1 | tail -20
```

Expected:
- Both new `ImmutableProcessorSpec` tests → PASS
- New `MutableProcessorSpec` test → still FAIL (setter param not yet annotated, count is 3 not 4)
- All pre-existing tests → PASS

**Step 4: Commit**

```bash
git add processor/src/main/java/io/github/joke/caffeinate/strategy/ConstructorStrategy.java \
        processor/src/main/java/io/github/joke/caffeinate/mutable/MutableConstructorStrategy.java
git commit -m "feat: apply @Nullable annotations to generated constructor parameters"
```

---

### Task 7: Apply annotations in `SetterStrategy` — verify all tests pass and full build succeeds

**Files:**
- Modify: `processor/src/main/java/io/github/joke/caffeinate/mutable/SetterStrategy.java`

**Step 1: Replace the `generate` method body**

```java
@Override
public void generate(TypeElement source, ClassModel model) {
    for (Property property : model.getProperties()) {
        ParameterSpec.Builder param = ParameterSpec.builder(
                property.getType(), property.getFieldName());
        property.getAnnotations().forEach(param::addAnnotation);
        MethodSpec setter = MethodSpec.methodBuilder(
                        PropertyUtils.setterNameForField(property.getFieldName()))
                .addModifiers(Modifier.PUBLIC)
                .returns(void.class)
                .addParameter(param.build())
                .addStatement("this.$N = $N", property.getFieldName(), property.getFieldName())
                .build();
        model.getMethods().add(setter);
    }
}
```

**Step 2: Run all tests**

```bash
./gradlew :processor:test 2>&1 | tail -20
```

Expected: ALL tests PASS, including all three new nullable propagation tests.

**Step 3: Run the full build**

```bash
./gradlew build 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` with no warnings or errors.

**Step 4: Commit**

```bash
git add processor/src/main/java/io/github/joke/caffeinate/mutable/SetterStrategy.java
git commit -m "feat: apply @Nullable annotations to generated setter parameters"
```

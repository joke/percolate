# Nullable Annotation Propagation — Design

**Date:** 2026-02-19

## Problem

When a source interface or abstract class annotates a getter with `@Nullable`, the generated
implementation currently drops that annotation entirely. The generated field, getter method,
constructor parameter, and setter parameter are all emitted without any nullability information,
breaking null-safety tooling (NullAway, JSpecify, JetBrains, etc.) that relies on those
annotations being present in the generated code.

## Decision

Propagate any annotation whose simple name is `"Nullable"` — regardless of package — from
the source getter method to every generated site that represents that property's nullability.
Conversion from `javax.lang.model.AnnotationMirror` to `com.palantir.javapoet.AnnotationSpec`
happens once at extraction time in `PropertyUtils.extractProperty()`. The list flows through
`Property` and each strategy adds the annotations to its respective builder.

## Approach

**Approach A — Enrich `Property` with `List<AnnotationSpec>`.**
`PropertyUtils.extractProperty()` collects annotations named `"Nullable"` from the method,
converts them to `AnnotationSpec`, and stores the list in `Property`. All downstream strategies
call `.addAnnotation(spec)` on their builders. No new classes; minimal change surface.

## Architecture

### No new files

### Modified files

| File | Change |
|------|--------|
| `strategy/Property.java` | Add `List<AnnotationSpec> annotations` field and getter |
| `strategy/PropertyUtils.java` | Collect `@Nullable` annotations in `extractProperty()` |
| `strategy/FieldStrategy.java` | Apply annotations to `FieldSpec` |
| `mutable/MutableFieldStrategy.java` | Apply annotations to `FieldSpec` |
| `strategy/GetterStrategy.java` | Apply annotations to `MethodSpec` |
| `strategy/ConstructorStrategy.java` | Apply annotations to `ParameterSpec` |
| `mutable/MutableConstructorStrategy.java` | Apply annotations to all-args `ParameterSpec` |
| `mutable/SetterStrategy.java` | Apply annotations to setter `ParameterSpec` |

## Section 1 — Property Model and Extraction

**`Property`** gains one new field:

```java
private final List<AnnotationSpec> annotations;
```

Constructor is extended:

```java
public Property(String fieldName, TypeName type, String getterName, List<AnnotationSpec> annotations) {
    this.fieldName = fieldName;
    this.type = type;
    this.getterName = getterName;
    this.annotations = List.copyOf(annotations);
}

public List<AnnotationSpec> getAnnotations() {
    return annotations;
}
```

**`PropertyUtils.extractProperty()`** collects nullable annotations before constructing `Property`:

```java
public static Property extractProperty(ExecutableElement method) {
    // ... existing fieldName / type derivation unchanged ...

    List<AnnotationSpec> annotations = method.getAnnotationMirrors().stream()
            .filter(m -> m.getAnnotationType().asElement().getSimpleName().contentEquals("Nullable"))
            .map(AnnotationSpec::get)
            .collect(java.util.stream.Collectors.toList());

    return new Property(fieldName, type, methodName, annotations);
}
```

If no annotation is named `"Nullable"`, the list is empty and behaviour is identical to today.

## Section 2 — Strategy Changes

Each strategy adds a small loop inside the existing property iteration. No restructuring.

**`FieldStrategy` and `MutableFieldStrategy`:**
```java
FieldSpec.Builder field = FieldSpec.builder(...)
property.getAnnotations().forEach(field::addAnnotation);
model.getFields().add(field.build());
```

**`GetterStrategy`:**
```java
MethodSpec.Builder getter = MethodSpec.methodBuilder(...)
property.getAnnotations().forEach(getter::addAnnotation);
model.getMethods().add(getter.build());
```

Adding as a declaration annotation on the method is correct: `@Nullable` annotations that
target `METHOD` or `TYPE_USE` (which covers JSpecify, JetBrains, Checker Framework, etc.)
are all interpreted by null-safety tools as annotating the return type when placed on a method.

**`ConstructorStrategy`:**
```java
ParameterSpec.Builder param = ParameterSpec.builder(property.getType(), property.getFieldName());
property.getAnnotations().forEach(param::addAnnotation);
constructor.addParameter(param.build());
```

**`MutableConstructorStrategy`** — same change, applied only to the all-args constructor
parameters. The no-args constructor has no parameters; no change needed there.

**`SetterStrategy`:**
```java
ParameterSpec.Builder param = ParameterSpec.builder(property.getType(), property.getFieldName());
property.getAnnotations().forEach(param::addAnnotation);
setter.addParameter(param.build());
```

## Section 3 — Testing

Tests use a self-contained `@interface Nullable {}` defined inline in test source strings
(no external dependency). Assertions use `getCharContent().toString()` string checks.

**`ImmutableProcessorSpec` additions:**
- `@Nullable` on directly declared getter → field, getter, constructor parameter all contain `@Nullable`
- `@Nullable` on getter inherited from a parent interface → same propagation (exercises `TypeHierarchyResolver`)
- Non-nullable getter → `@Nullable` absent from generated output (negative case)

**`MutableProcessorSpec` additions:**
- `@Nullable` on directly declared getter → field, getter, all-args constructor parameter, and setter parameter all contain `@Nullable`
- Non-nullable property → unaffected

## No behaviour changes for non-nullable properties

All existing tests pass unchanged. An empty annotation list produces no extra calls to
`addAnnotation`, leaving the generated output identical to today.

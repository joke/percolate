# Reference

This page is a comprehensive reference for all annotations, naming conventions, and validation rules in the Caffeinate annotation processor.

## Annotations

| Annotation | Target | Description |
|---|---|---|
| `@Immutable` | Interface | Generates immutable implementation with `private final` fields, all-args constructor, getters |
| `@Mutable` | Interface | Generates mutable implementation with `private` fields, no-args + all-args constructors, getters, setters |
| `@ToString` | Interface | Customizes `toString()` generation (styles: `STRING_JOINER`, `TO_STRING_BUILDER`) |

## Naming conventions

### Method-to-property mapping

The processor derives property names and types from the method signatures declared in the annotated interface.

| Method pattern | Property name | Type |
|---|---|---|
| `getFirstName()` | `firstName` | Return type of getter |
| `isActive()` | `active` | `boolean` |
| `setFirstName(String)` | `firstName` (must match getter) | Parameter type (must match getter return type) |

- The `get` or `is` prefix is stripped and the remaining name is decapitalized to produce the field name.
- Setter names must follow the `set*` pattern and the derived field name and parameter type must match an existing getter-derived property.

### Generated class naming

The generated class is always named `<InterfaceName>Impl` and placed in the same package as the source interface. For example, an interface `com.example.Person` produces `com.example.PersonImpl`.

## Validation rules

The processor reports compilation errors when annotated interfaces contain methods that do not conform to the expected patterns. The tables below list every validation rule along with the exact error message produced.

### Both `@Immutable` and `@Mutable`

| Condition | Error message |
|---|---|
| Annotation applied to a class or enum (not an interface) | `@Immutable can only be applied to interfaces` / `@Mutable can only be applied to interfaces` |

### `@Immutable`

These rules are enforced by `PropertyDiscoveryStrategy`.

| Condition | Error message |
|---|---|
| Method has parameters | `Methods in @Immutable interfaces must have no parameters` |
| Method returns void | `Methods in @Immutable interfaces must not return void` |
| Method name doesn't start with `get` or `is` | `Methods in @Immutable interfaces must follow get*/is* naming convention` |

### `@Mutable`

These rules are enforced by `MutablePropertyDiscoveryStrategy`.

| Condition | Error message |
|---|---|
| Non-setter method has parameters | `Methods in @Mutable interfaces must have no parameters (except setters)` |
| Void method doesn't follow `set*` convention | `Void methods in @Mutable interfaces must follow set* naming convention` |
| Method doesn't follow `get*`/`is*`/`set*` convention | `Methods in @Mutable interfaces must follow get*/is*/set* naming convention` |

### `@Mutable` setter validation

These rules are enforced by `SetterValidationStrategy` after property discovery. Each declared setter is checked against the getter-derived properties.

| Condition | Error message |
|---|---|
| Setter doesn't match any getter-derived property (by field name and type) | `Setter setX does not match any getter-derived property` (where `setX` is the actual setter name) |

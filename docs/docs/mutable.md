# @Mutable

## Overview

`@Mutable` generates an implementation class with `private` (non-final) fields, both a no-args and an all-args constructor, getter methods, and setter methods. The generated class is named `<InterfaceName>Impl` and placed in the same package.

## Basic example

Annotate an interface with `@Mutable` and the processor generates a concrete implementation with constructors, getters, and setters.

=== "Your interface"

    ```java
    import io.github.joke.caffeinate.Mutable;

    @Mutable
    public interface Person {
        String getFirstName();
        int getAge();
    }
    ```

=== "Generated implementation"

    ```java
    public class PersonImpl implements Person {
        private String firstName;
        private int age;

        public PersonImpl() {
        }

        public PersonImpl(String firstName, int age) {
            this.firstName = firstName;
            this.age = age;
        }

        @Override
        public String getFirstName() {
            return this.firstName;
        }

        @Override
        public int getAge() {
            return this.age;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }
    ```

## Key differences from @Immutable

| Feature | `@Immutable` | `@Mutable` |
|---|---|---|
| Fields | `private final` | `private` (non-final) |
| No-args constructor | Not generated | Always generated |
| All-args constructor | Always generated (when properties exist) | Generated when properties exist |
| Setter methods | Not generated | Generated for every property |

## Declaring setters in the interface

Users may optionally declare setter methods in the interface. If present, they are validated at compile time: the setter name and parameter type must match a getter-derived property. If they don't match, a compilation error occurs. Setters are generated regardless of whether they are declared in the interface.

=== "Your interface"

    ```java
    @Mutable
    public interface Person {
        String getFirstName();
        void setFirstName(String firstName); // optional — validated if present
    }
    ```

=== "Generated implementation"

    ```java
    public class PersonImpl implements Person {
        private String firstName;

        public PersonImpl() {
        }

        public PersonImpl(String firstName) {
            this.firstName = firstName;
        }

        @Override
        public String getFirstName() {
            return this.firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }
    }
    ```

## Boolean properties

Methods that follow the `is*()` naming convention are treated as boolean properties, the same as with `@Immutable`. The `is` prefix is stripped to derive the field name. The getter stays `isActive()` while the setter is `setActive(boolean active)`.

=== "Your interface"

    ```java
    @Mutable
    public interface Status {
        boolean isActive();
    }
    ```

=== "Generated implementation"

    ```java
    public class StatusImpl implements Status {
        private boolean active;

        public StatusImpl() {
        }

        public StatusImpl(boolean active) {
            this.active = active;
        }

        @Override
        public boolean isActive() {
            return this.active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
    ```

## Generated class naming

The generated class is always named `<InterfaceName>Impl` and placed in the same package as the annotated interface. For example, an interface `com.example.Person` produces `com.example.PersonImpl`.

## Notes

- `@Override` is always added to generated getter methods. Setters do not get `@Override`.
- If the interface has no getter methods, no all-args constructor or setters are generated -- the result is a class with only a no-args constructor.
- Fields are `private` (non-final) -- the object is mutable.
- Constructor parameter order matches the declaration order in the interface.

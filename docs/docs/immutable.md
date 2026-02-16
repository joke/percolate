# @Immutable

## Overview

`@Immutable` generates an implementation class with `private final` fields, an all-args constructor, and getter methods. The generated class is named `<InterfaceName>Impl` and placed in the same package.

## Basic example

Annotate an interface with `@Immutable` and the processor generates a concrete implementation with a constructor and getters.

=== "Your interface"

    ```java
    import io.github.joke.objects.Immutable;

    @Immutable
    public interface Greeting {
        String getMessage();
    }
    ```

=== "Generated implementation"

    ```java
    public class GreetingImpl implements Greeting {
        private final String message;

        public GreetingImpl(String message) {
            this.message = message;
        }

        @Override
        public String getMessage() {
            return this.message;
        }
    }
    ```

## Multiple properties

Interfaces can declare any number of getter methods. The constructor parameters follow the same order as the method declarations.

=== "Your interface"

    ```java
    @Immutable
    public interface Person {
        String getFirstName();
        int getAge();
    }
    ```

=== "Generated implementation"

    ```java
    public class PersonImpl implements Person {
        private final String firstName;
        private final int age;

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
    }
    ```

## Boolean properties

Methods that follow the `is*()` naming convention are treated as boolean properties. The `is` prefix is stripped to derive the field name, just like `get` is stripped for other types.

=== "Your interface"

    ```java
    @Immutable
    public interface Status {
        boolean isActive();
    }
    ```

=== "Generated implementation"

    ```java
    public class StatusImpl implements Status {
        private final boolean active;

        public StatusImpl(boolean active) {
            this.active = active;
        }

        @Override
        public boolean isActive() {
            return this.active;
        }
    }
    ```

## Generated class naming

The generated class is always named `<InterfaceName>Impl` and placed in the same package as the annotated interface. For example, an interface `com.example.Greeting` produces `com.example.GreetingImpl`.

## Notes

- `@Override` is always added to generated getter methods.
- If the interface has no getter methods, no constructor is generated -- the result is simply `public class FooImpl implements Foo {}`.
- Fields are `private final` -- the object is truly immutable.
- Constructor parameter order matches the declaration order in the interface.

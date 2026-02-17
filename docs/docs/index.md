# Caffeinate

Caffeinate is a Java annotation processor that generates implementation classes from annotated interfaces.

## Quick Example

Define an interface and annotate it with `@Immutable` -- Objects generates the implementation class at compile time.

=== "Your interface"

    ```java
    package com.example;

    import io.github.joke.caffeinate.Immutable;

    @Immutable
    public interface Person {
        String getFirstName();
        int getAge();
    }
    ```

=== "Generated implementation"

    ```java
    package com.example;

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

## Learn More

- [Getting Started](getting-started.md) -- installation and setup
- [@Immutable](immutable.md) -- generate immutable implementation classes
- [@Mutable](mutable.md) -- generate mutable implementation classes with setters
- [Reference](reference.md) -- full annotation and configuration reference

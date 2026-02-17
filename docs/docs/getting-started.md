# Getting Started

## Requirements

- Java 11 or later

## Installation

Caffeinate consists of two artifacts:

1. **`io.github.joke.caffeinate:annotations`** -- provides the annotations (`@Immutable`, `@Mutable`, etc.). This is a compile-only dependency; it is not needed at runtime.
2. **`io.github.joke.caffeinate:processor`** -- the annotation processor that generates implementation classes at compile time.

Add both to your project:

=== "Gradle"

    ```groovy title="build.gradle"
    dependencies {
        compileOnly 'io.github.joke.caffeinate:annotations:VERSION'
        annotationProcessor 'io.github.joke.caffeinate:processor:VERSION'
    }
    ```

=== "Maven"

    ```xml title="pom.xml"
    <dependencies>
        <dependency>
            <groupId>io.github.joke.caffeinate</groupId>
            <artifactId>annotations</artifactId>
            <version>VERSION</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>io.github.joke.caffeinate</groupId>
            <artifactId>processor</artifactId>
            <version>VERSION</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
    ```

!!! note
    Replace `VERSION` with the latest release version. There are no published versions yet.

## First annotation

Once the dependencies are in place, annotate an interface with `@Immutable` and build your project. The processor generates an implementation class automatically.

```java
package com.example;

import io.github.joke.caffeinate.Immutable;

@Immutable
public interface Person {
    String getFirstName();
    int getAge();
}
```

After compilation, a `PersonImpl` class is generated in the same package. Use it directly:

```java
Person person = new PersonImpl("Alice", 30);
person.getFirstName(); // "Alice"
person.getAge();       // 30
```

## Next steps

- [@Immutable](immutable.md) -- generate immutable implementation classes
- [@Mutable](mutable.md) -- generate mutable implementation classes with setters
- [Reference](reference.md) -- full annotation and configuration reference

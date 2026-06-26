# Percolate

[![build](https://github.com/joke/percolate/actions/workflows/build.yml/badge.svg)](https://github.com/joke/percolate/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

A Java annotation processor that generates bean mappers

[Documentation](https://joke.github.io/percolate/)

## Getting started

Percolate is a compile-time-only tool: it runs during annotation processing and the generated mappers
have **no runtime dependency** on percolate. Add the `percolate` convenience starter to your
`annotationProcessor` configuration — it bundles the engine and the builtin strategies — and the
annotations to `compileOnly`. Versions are managed by the `percolate-bom` platform.

```groovy
dependencies {
    annotationProcessor platform('io.github.joke.percolate:percolate-bom:<version>')
    annotationProcessor 'io.github.joke.percolate:percolate'

    compileOnly platform('io.github.joke.percolate:percolate-bom:<version>')
    compileOnly 'io.github.joke.percolate:percolate-annotations'

    // optional: reactive (Flux/Mono) container support
    // annotationProcessor 'io.github.joke.percolate:percolate-reactor'
}
```

Declare a mapper interface and percolate generates the implementation:

```java
@Mapper
public interface PersonMapper {
    @Map(target = "firstName", source = "person.firstName")
    @Map(target = "age", source = "person.age")
    Human map(Person person);
}
```

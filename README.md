# Percolate

[![build](https://github.com/joke/percolate/actions/workflows/build.yml/badge.svg)](https://github.com/joke/percolate/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

Percolate is a Java annotation processor that generates bean mappers at compile time. Declare a
`@Mapper` interface describing how one bean turns into another, and percolate generates the
implementation during annotation processing — the generated code has **no runtime dependency** on
percolate, just plain Java.

See the [documentation](https://joke.github.io/percolate/) for setup instructions and the full user
manual.

## Features

- **Zero runtime footprint** — percolate runs only at compile time; nothing it ships is on the classpath
  of the code it generates.
- **Field-by-field control** via the `@Map` annotation (`target`/`source`), including **nested paths**
  (`"address.city"`) on either side.
- **Constants** — supply a fixed literal for a target with `constant()`, independent of any source.
- **Defaults & null-safety** — `defaultValue` fallbacks for absent sources (`null` or empty `Optional`),
  with nullability read from [JSpecify](https://jspecify.dev/) `@Nullable`/`@NullMarked` annotations
  (among others) on the source model, not just from `Optional`.
- **Collections** — `List`, `Set`, and other container types are mapped element-by-element, including
  nested element mapping.
- **Optionals** — `Optional` sources and targets are unwrapped and rewrapped automatically.
- **Temporal mapping** — conversions between `Instant`, `LocalDateTime`, and related date/time types,
  including time zone handling.
- **Enum mapping** — identically-named constants map automatically, with `@MapEnum` overrides for the
  ones that don't line up.
- **Conversion methods** — plug in your own conversion methods, including default methods, for types
  percolate doesn't convert natively.
- **`@Ambient` parameters** — thread extra context (a locale, a lookup, ...) down through a chain of
  nested mapper calls without adding it to every source's shape.
- **Reactive containers** — map `Flux`/`Mono` pipelines directly, or bridge a reactive source into a
  blocking mapper, via the optional `reactor` / `reactor-blocking` modules.
- **Compile-time switches** — tune the generated code style (final locals/parameters, `var`, nullable
  annotations, doc tags, switch style) through processor options.
- **Extensible SPI** — teach percolate new conversions by writing custom strategies.

## Example

```java
@Mapper
public interface PersonMapper {
    @Map(target = "firstName", source = "person.firstName")
    @Map(target = "age", source = "person.age")
    @Map(target = "address.city", source = "person.homeAddress.city")
    Human map(Person person);
}
```

Percolate generates a `PersonMapperImpl` — a plain class with no percolate dependency.

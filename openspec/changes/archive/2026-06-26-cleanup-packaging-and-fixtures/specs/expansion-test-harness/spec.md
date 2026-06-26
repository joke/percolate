## ADDED Requirements

### Requirement: Type resolution by Class literal

`TypeUniverse` SHALL expose `static javax.lang.model.element.TypeElement of(Class<?> type)` that resolves the given class through the same `Elements`/`JavacTask` substrate as `element(String)` (e.g. by canonical name). It is a rename-safe, IDE-tracked alternative to passing a fully-qualified string, and SHALL be the preferred way to resolve a fixture type that exists as a compiled `Class` on the test classpath. `element(String)` remains for JDK types and genuinely dynamic names.

`TypeUniverse` SHALL NOT retain members that exist only to serve removed test layers: it SHALL NOT expose a `pool()` of `TypeMirror`s (a fossil of the removed jqwik property layer) and SHALL NOT carry constants resolved by nothing else.

#### Scenario: of(Class) resolves a fixture from a Class literal

- **WHEN** a spec calls `TypeUniverse.of(SomeFixture.class)`
- **THEN** it returns a non-null `TypeElement` for that class, drawn from the same substrate as `TypeUniverse.element(...)`, such that `Types.isSameType` comparisons with other `TypeUniverse` types behave consistently

#### Scenario: of(Class) and element(String) agree

- **WHEN** both `TypeUniverse.of(SomeFixture.class)` and `TypeUniverse.element("<fully-qualified SomeFixture>")` are resolved
- **THEN** they return the same `TypeElement`

# CLAUDE.md

## Project Overview

Objects is a Java annotation processor in the style of mapstruct.
It generates mapper classes at compile time using a dependency graph (DAG)

## Build Commands

```bash
./gradlew build          # Full build (compile, test, check)
./gradlew check          # Run all checks (ErrorProne, NullAway)
./gradlew test           # Run all tests
./gradlew :processor:test                        # Run processor module tests only
./gradlew :processor:test --tests 'SpecName'     # Run a single Spock spec
```

## Architecture

**Module structure:**
- `dependencies/` — Java platform BOM for centralized version management
- `processor/` — Main annotation processor implementation
- `annotations` — Annotations that the processor can process
- `test-classes` — Example classes for the developers. CRITICAL DO NOT MODIFY!
- `test-mapper` — Exaple mapper for the developers. CRITICAL DO NOT MODIFY!

Planned modules (commented out in settings.gradle): `bom`, `tests`.

**Key libraries:**
- **Dagger 2** for dependency injection within the processor
- **Palantir JavaPoet** for Java source code generation
- **Google Auto Service** for `META-INF/services` registration
- **JGraphT** for graph modeling and algorithms
- **Spock Framework** (Groovy) + **Google Compile Testing** for tests

## Java style

* Prefer streams when working with collections
* ALWAYS prefer immutability
* Prefer a functional java style:
  * Functions must only a few lines long. 
  * They should do exactly one thing and only one thing.
  * Functions must have expressive name describing what this function does.
  * They should not mix hierarchies.
* ALWAYS prefer static imports

## Code Quality

The build enforces strict quality standards — all warnings are errors (`-Werror`).

- **ErrorProne** is applied to all Java subprojects automatically
- **NullAway** runs in JSpecify mode with `onlyNullMarked = true` — only classes/packages annotated with `@NullMarked` are checked
- Java compilation targets release 11 with `-parameters` flag

## Conventions

- Group ID: `io.github.joke.percolate`
- Package root: `io.github.joke.percolate`
- Tests use Spock (Groovy BDD framework) — test files are `*.groovy` specs
- Processor compile-time tests use Google Compile Testing to verify generated code

# CLAUDE.md

## Project Overview

Objects is a Java annotation processor in the style of Lombok and Immutables. It generates boilerplate code at compile time.

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

Planned modules (commented out in settings.gradle): `bom`, `tests`.

**Key libraries:**
- **Dagger 2** for dependency injection within the processor
- **Palantir JavaPoet** for Java source code generation
- **Google Auto Service** for `META-INF/services` registration
- **Spock Framework** (Groovy) + **Google Compile Testing** for tests

## Code Quality

The build enforces strict quality standards — all warnings are errors (`-Werror`).

- **ErrorProne** is applied to all Java subprojects automatically
- **NullAway** runs in JSpecify mode with `onlyNullMarked = true` — only classes/packages annotated with `@NullMarked` are checked
- Java compilation targets release 11 with `-parameters` flag

## Conventions

- Group ID: `io.github.joke.objects`
- Package root: `io.github.joke.objects`
- Tests use Spock (Groovy BDD framework) — test files are `*.groovy` specs
- Processor compile-time tests use Google Compile Testing to verify generated code

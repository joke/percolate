# Core Annotation Processor Design

## Goal

Implement the initial annotation processor for the Objects project. Given an interface annotated with `@Immutable`, generate an implementation class.

**Input:**
```java
@Immutable
public interface Person {}
```

**Output:**
```java
public class PersonImpl implements Person {}
```

## Architecture

### Dagger Component Structure

Subcomponent-per-annotation-type approach. The root component manages processor lifecycle and `ProcessingEnvironment` utilities. Each annotation type gets its own subcomponent with independently wired strategies.

```
ProcessorComponent (root)
  ├── provides: Messager, Filer, Elements, Types
  ├── provides: ImmutableSubcomponent.Factory
  │
  └── ImmutableSubcomponent
        ├── installs: ImmutableModule
        └── provides: Set<GenerationStrategy>
```

Adding `@Mutable` support later means adding a `MutableSubcomponent` with its own module — no changes to existing code.

### Processing Flow

1. `ObjectsProcessor.process()` iterates elements annotated with `@Immutable`
2. Validates each element is an interface — emits compile error and skips if not
3. Obtains `ImmutableSubcomponent` from root component
4. Delegates to `ImmutableGenerator`
5. Generator creates empty `ClassModel`, runs each strategy, converts to `TypeSpec`, writes via `Filer`

### Strategy Pattern

Strategies operate on a mutable `ClassModel` rather than JavaPoet's `TypeSpec.Builder` directly. This decouples strategies from JavaPoet and allows any strategy to set/override any attribute (including the class name).

```java
public interface GenerationStrategy {
    void generate(TypeElement source, ClassModel model);
}
```

```java
public class ClassModel {
    private String className;
    private final List<Modifier> modifiers;
    private final List<TypeName> superinterfaces;
    // future: fields, methods, annotations
}
```

The generator converts `ClassModel` to `TypeSpec` after all strategies have run.

**Initial strategy — `ClassStructureStrategy`:**
- Sets `className` to `<SourceName>Impl`
- Adds `PUBLIC` modifier
- Adds the source interface to superinterfaces

## Package Structure

```
io.github.joke.objects
├── ObjectsProcessor                  # @AutoService entry point
│
├── component/
│   ├── ProcessorComponent            # Root @Component
│   └── ProcessorModule               # Provides ProcessingEnvironment utilities
│
├── immutable/
│   ├── ImmutableSubcomponent         # @Subcomponent for @Immutable
│   ├── ImmutableModule               # Binds strategies into Set
│   └── ImmutableGenerator            # Orchestrates strategies, writes file
│
└── strategy/
    ├── GenerationStrategy            # Strategy interface
    ├── ClassModel                    # Mutable model populated by strategies
    └── ClassStructureStrategy        # Name, visibility, implements clause
```

## Validation

Handled in `ObjectsProcessor` before delegation to the generator:

- `@Immutable` on a non-interface type (concrete class, enum, etc.) → compile error via `Messager`
- Only interfaces are passed to the generator

## Testing

Spock specs with Google Compile Testing:

1. **Happy path** — `@Immutable` on an interface generates correct `<Name>Impl` source
2. **Compile error** — `@Immutable` on a concrete class produces error
3. **Compile error** — `@Immutable` on an enum produces error

Tests compile source snippets, run them through `ObjectsProcessor`, and assert on compilation status and generated output.

## Build Changes

1. Uncomment `include 'annotations'` in `settings.gradle`
2. Add `implementation project(':annotations')` to `processor/build.gradle`
3. Annotate new classes with `@NullMarked` for NullAway compliance

## Naming Convention

Fixed `<Name>Impl` for this iteration. Future iterations will make this configurable via `@Name` annotation with pluggable naming strategies.

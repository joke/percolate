# Extension Points (SPI)

The processor is extended through three service-provider interfaces, all discovered via
`ServiceLoader` at compile time (registered by `@AutoService`).

---

## `ConversionProvider`

```java
public interface ConversionProvider {
    int priority();
    boolean canHandle(TypeMirror source, TypeMirror target,
                      MethodRegistry registry, ProcessingEnvironment env);
    ConversionFragment provide(TypeMirror source, TypeMirror target,
                               MethodRegistry registry, ProcessingEnvironment env);
}
```

`WiringStage` queries providers in ascending `priority()` order.  The first provider that returns
`true` from `canHandle` is asked to `provide` a `ConversionFragment`.  A fragment is an ordered
list of `MappingNode`s that the stage splices between the incompatible source and target.

### Built-in providers (in priority order)

| Provider | Handles |
|----------|---------|
| `MapperMethodProvider` | Delegates to another abstract method in the same mapper when its signature matches `source → target` |
| `ListProvider` | `Collection<A> → List<B>` — inserts `CollectionIterationNode` + inner conversion + `CollectionCollectNode` |
| `OptionalProvider` | `Optional<A> → Optional<B>`, `T → Optional<T>`, `Optional<T> → T` |
| `SubtypeProvider` | Direct subtype / supertype assignments (no conversion node needed) |
| `PrimitiveWideningProvider` | Widening primitive conversions (e.g. `int → long`) |
| `EnumProvider` | Enum-to-enum conversion via `TargetEnum.valueOf(source.name())` |

---

## `ObjectCreationStrategy`

```java
public interface ObjectCreationStrategy {
    boolean canCreate(TypeElement type, ProcessingEnvironment env);
    CreationDescriptor describe(TypeElement type, ProcessingEnvironment env);
}
```

`WiringStage` uses the first strategy that `canCreate` the target type to obtain a
`CreationDescriptor`, which holds the constructor `ExecutableElement` and its parameter list.

### Built-in strategies

| Strategy | Behaviour |
|----------|-----------|
| `ConstructorCreationStrategy` | Selects the single public constructor of the target type |

---

## `PropertyDiscoveryStrategy`

```java
public interface PropertyDiscoveryStrategy {
    boolean canDiscover(TypeElement type, ProcessingEnvironment env);
    List<Property> discover(TypeElement type, ProcessingEnvironment env);
}
```

`BindingStage` queries strategies to enumerate readable properties of a source `TypeElement`.

### Built-in strategies

| Strategy | Behaviour |
|----------|-----------|
| `GetterPropertyStrategy` | Discovers properties via JavaBeans-style `getX()` / `isX()` methods |
| `FieldPropertyStrategy` | Discovers properties via public fields |

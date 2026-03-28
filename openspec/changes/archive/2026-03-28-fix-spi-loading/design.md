## Context

The processor module uses Java's `ServiceLoader` to discover `SourcePropertyDiscovery` and `TargetPropertyDiscovery` SPI implementations at annotation-processing time. Two problems prevent this from working in consumer projects:

1. `ServiceLoader.load(serviceClass)` defaults to the thread context classloader, which during annotation processing is the application classloader (user's project), not the processor's classloader where META-INF/services files reside.
2. SPI implementations are registered via hand-written META-INF/services files instead of using the already-configured AutoService annotation processor.

## Goals / Non-Goals

**Goals:**
- SPI implementations are discovered when the processor runs in any consumer project
- SPI registration uses AutoService consistently (same as `PercolateProcessor` itself)
- Eliminate manually maintained META-INF/services files

**Non-Goals:**
- Changing the SPI interfaces or their contracts
- Supporting SPI implementations provided by consumer projects (different classloader concern — future work)
- Modifying the priority-based resolution logic

## Decisions

### Decision 1: Use `DiscoverStage.class.getClassLoader()` for ServiceLoader

Change `ServiceLoader.load(serviceClass)` to `ServiceLoader.load(serviceClass, DiscoverStage.class.getClassLoader())`.

**Rationale**: The processor's own classloader always has access to the processor JAR's META-INF/services entries. The thread context classloader is unreliable during annotation processing — it typically points at the user's compilation classpath.

**Alternative considered**: Using `processingEnv.getClass().getClassLoader()` — rejected because it's less direct and would require threading the processing environment through to `DiscoverStage`.

### Decision 2: Add @AutoService to SPI implementations, delete manual service files

Annotate each implementation with `@AutoService` targeting its SPI interface. Remove the hand-written files from `src/main/resources/META-INF/services/`.

**Rationale**: AutoService is already configured in the build (`annotationProcessor` and `compileOnly` dependencies) and used for `PercolateProcessor`. Using it consistently avoids the risk of manual files drifting out of sync with the code.

**Alternative considered**: Keep manual files — rejected because it defeats the purpose of having AutoService configured and creates a maintenance burden.

## Risks / Trade-offs

- **[Risk] Inner class registration with AutoService**: `FieldDiscovery.Source` and `FieldDiscovery.Target` are static inner classes. AutoService handles inner classes correctly (generates `OuterClass$Inner` entries). → Verified: AutoService supports this pattern.
- **[Risk] Consumer-provided SPIs won't be loaded**: Using the processor's classloader means only SPIs bundled with the processor JAR are discovered. Consumer-provided SPIs on the `annotationProcessor` classpath would require a different loading strategy. → Acceptable for now; the existing spec scenarios for custom SPIs on annotationProcessor classpath are aspirational and not yet supported. This can be addressed separately.

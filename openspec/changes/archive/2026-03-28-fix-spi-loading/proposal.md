## Why

The SPI-based property discovery system does not work when the processor runs in a consumer project. `ServiceLoader.load(serviceClass)` uses the thread context classloader, which points at the user's project — not the processor JAR where `META-INF/services` files reside. Additionally, the built-in SPI implementations lack `@AutoService` annotations, relying on manually maintained service files instead of leveraging the already-configured AutoService annotation processor.

## What Changes

- Add `@AutoService` annotations to all SPI implementations (`GetterDiscovery`, `ConstructorDiscovery`, `FieldDiscovery.Source`, `FieldDiscovery.Target`)
- Delete manually created `META-INF/services` files for `SourcePropertyDiscovery` and `TargetPropertyDiscovery`
- Fix `ServiceLoader.load()` call in `DiscoverStage` to use the processor's classloader

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `property-discovery`: Fix ServiceLoader classloader usage so SPIs are actually discovered when running as an annotation processor in consumer projects. Switch from manual META-INF/services registration to AutoService-generated registration.

## Impact

- **Code**: `DiscoverStage`, `GetterDiscovery`, `ConstructorDiscovery`, `FieldDiscovery` in the `processor` module
- **Resources**: Removal of hand-written `META-INF/services` files (replaced by AutoService-generated ones in build output)
- **Dependencies**: No new dependencies — AutoService is already configured
- **Teams**: Processor module only; no API changes for consumers
